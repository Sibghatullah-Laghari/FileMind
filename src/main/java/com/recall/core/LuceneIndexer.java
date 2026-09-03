package com.recall.core;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.Tika;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Core indexing + search engine.
 *
 * KEY FIXES vs original:
 *  - refreshSearcher() only called when user actually searches (not per file)
 *  - updateDocument() handles delete+insert atomically — no manual delete needed
 *  - Tika used for full content extraction (PDF, DOCX, TXT, Java, etc.)
 *  - isSkippable() skips node_modules, .git, target, build, etc.
 *  - Content truncated to first 50 000 chars (configurable)
 */
public class LuceneIndexer {

    // ── tunables ──────────────────────────────────────────────────────────────
    private static final double  RAM_BUFFER_MB     = 32.0;   // sweet spot: speed vs RAM
    private static final int     MAX_CONTENT_CHARS = 50_000; // ~50 KB of text per file
    private static final long    MAX_FILE_BYTES    = 500L * 1024 * 1024; // skip >500 MB

    // Directories whose entire subtree we skip
    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", ".svn", "target", "build", ".gradle",
            "__pycache__", ".idea", ".vscode", ".cache", "dist", "out",
            ".Trash", "$RECYCLE.BIN", "System Volume Information"
    );

    // Extensions whose content we never try to extract (binary, media)
    private static final Set<String> SKIP_CONTENT_EXTS = Set.of(
            "exe","dll","so","bin","iso","img","dmg","apk",
            "mp3","mp4","avi","mkv","mov","flac","wav","aac",
            "zip","tar","gz","7z","rar",
            "png","jpg","jpeg","gif","bmp","webp","svg","ico",
            "class","pyc","o","obj"
    );

    // ── state ─────────────────────────────────────────────────────────────────
    private static FSDirectory    directory;
    private static IndexWriter    writer;
    private static IndexSearcher  searcher;
    private static DirectoryReader reader;
    private static final Tika     tika = new Tika();
    private static final AtomicBoolean needsRefresh = new AtomicBoolean(false);

    // ── lifecycle ─────────────────────────────────────────────────────────────

    public static void init(Path indexDir) throws IOException {
        Files.createDirectories(indexDir);
        directory = FSDirectory.open(indexDir);
        IndexWriterConfig cfg = new IndexWriterConfig(new StandardAnalyzer());
        cfg.setRAMBufferSizeMB(RAM_BUFFER_MB);
        cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        writer = new IndexWriter(directory, cfg);
        reader   = DirectoryReader.open(writer);
        searcher = new IndexSearcher(reader);
    }

    public static void close() {
        try { if (writer != null) writer.close(); } catch (IOException ignored) {}
        try { if (reader != null) reader.close(); } catch (IOException ignored) {}
        try { if (directory != null) directory.close(); } catch (IOException ignored) {}
    }

    // ── indexing ──────────────────────────────────────────────────────────────

    /**
     * Index a single file.
     * Safe to call from multiple threads — IndexWriter is thread-safe.
     */
    public static void indexFile(Path filePath) {
        try {
            if (!Files.isRegularFile(filePath))   return;
            if (isSkippable(filePath))             return;

            long sizeBytes = Files.size(filePath);
            if (sizeBytes > MAX_FILE_BYTES) {
                System.out.println("[SKIP >500MB] " + filePath.getFileName());
                return;
            }

            String pathStr  = filePath.toAbsolutePath().toString();
            String fileName = filePath.getFileName().toString();
            String ext      = getExtension(fileName).toLowerCase();
            long   modified = Files.getLastModifiedTime(filePath).toMillis();

            // ── content extraction ─────────────────────────────────
            String content = "";
            String suggestedName = null;
            if (!SKIP_CONTENT_EXTS.contains(ext)) {
                try {
                    // Tika streams internally — never loads whole file into RAM
                    content = tika.parseToString(filePath.toFile());
                    if (content.length() > MAX_CONTENT_CHARS)
                        content = content.substring(0, MAX_CONTENT_CHARS);
                    // Generate name suggestion from first 500 chars
                    suggestedName = NameSuggester.suggest(fileName, content, ext);
                } catch (Exception e) {
                    // Corrupted / encrypted — index metadata only, silently
                }
            }

            // ── build Lucene document ──────────────────────────────
            Document doc = new Document();
            // Stored fields (returned in results)
            doc.add(new StringField("path",     pathStr,  Field.Store.YES));
            doc.add(new TextField ("filename",  fileName, Field.Store.YES));
            doc.add(new StoredField("ext",      ext));
            doc.add(new StoredField("size",     sizeBytes));
            doc.add(new StoredField("modified", modified));
            if (suggestedName != null)
                doc.add(new StoredField("suggestedName", suggestedName));

            // Searchable/filterable fields (not stored — saves disk)
            doc.add(new TextField("content", content, Field.Store.NO));
            doc.add(new StringField("extFilter", ext, Field.Store.NO));

            // Numeric fields for range filters
            doc.add(new LongPoint("modifiedPoint", modified));
            doc.add(new LongPoint("sizePoint",     sizeBytes));
            // DocValues for sorting
            doc.add(new NumericDocValuesField("modifiedSort", modified));
            doc.add(new NumericDocValuesField("sizeSort",     sizeBytes));

            // updateDocument = atomic delete-by-term + insert.
            // No manual deleteDocuments() call needed — that was the original bug.
            writer.updateDocument(new Term("path", pathStr), doc);
            needsRefresh.set(true);

        } catch (Exception e) {
            System.err.println("[INDEX ERROR] " + filePath + " — " + e.getMessage());
        }
    }

    public static void deleteFile(Path filePath) {
        try {
            writer.deleteDocuments(new Term("path", filePath.toAbsolutePath().toString()));
            needsRefresh.set(true);
        } catch (IOException e) {
            System.err.println("[DELETE ERROR] " + e.getMessage());
        }
    }

    /**
     * Walk a folder and index all files.
     * callback(filePath, success) is called for each file — use to update UI status.
     */
    public static void indexFolder(Path folder, BiConsumer<Path, Boolean> callback) {
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Skip entire subtrees we don't want
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (SKIP_DIRS.contains(name) || name.startsWith("."))
                        return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    indexFile(file);
                    if (callback != null) callback.accept(file, true);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE; // skip unreadable
                }
            });
            // Flush once after bulk scan — not per file
            writer.flush();
        } catch (IOException e) {
            System.err.println("[FOLDER SCAN ERROR] " + e.getMessage());
        }
    }

    // ── search ────────────────────────────────────────────────────────────────

    /**
     * Main search entry point.
     * @param parsed  Output from NLQueryParser
     * @param maxResults  How many results to return
     */
    public static List<SearchResult> search(NLQueryParser.ParsedQuery parsed, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        try {
            // Reopen searcher only when index has changed — not on every call
            if (needsRefresh.getAndSet(false)) {
                DirectoryReader newReader = DirectoryReader.openIfChanged(reader);
                if (newReader != null) {
                    reader.close();
                    reader   = newReader;
                    searcher = new IndexSearcher(reader);
                }
            }

            BooleanQuery.Builder qb = new BooleanQuery.Builder();

            // ── keyword query ──────────────────────────────────────
            if (parsed.luceneQuery() != null && !parsed.luceneQuery().isBlank()) {
                String[] fields = {"filename", "content"};
                float[]  boosts = {2.0f,        1.0f};  // filename matches rank higher
                MultiFieldQueryParser mfqp = new MultiFieldQueryParser(
                        fields, new StandardAnalyzer(),
                        Map.of("filename", 2.0f, "content", 1.0f)
                );
                mfqp.setDefaultOperator(QueryParser.Operator.AND);
                try {
                    Query kq = mfqp.parse(parsed.luceneQuery());
                    qb.add(kq, BooleanClause.Occur.MUST);
                } catch (Exception e) {
                    // Fallback: treat as phrase if parse fails
                    Query fallback = new QueryParser("filename", new StandardAnalyzer())
                            .parse(QueryParser.escape(parsed.luceneQuery()));
                    qb.add(fallback, BooleanClause.Occur.MUST);
                }
            }

            // ── file type filter ───────────────────────────────────
            if (parsed.fileType() != null) {
                String[] exts = parsed.fileTypeExtensions();
                BooleanQuery.Builder extQ = new BooleanQuery.Builder();
                for (String ext : exts)
                    extQ.add(new TermQuery(new Term("extFilter", ext)), BooleanClause.Occur.SHOULD);
                qb.add(extQ.build(), BooleanClause.Occur.FILTER);
            }

            // ── date range filter ──────────────────────────────────
            if (parsed.afterMs() != null || parsed.beforeMs() != null) {
                long lo = parsed.afterMs()  != null ? parsed.afterMs()  : Long.MIN_VALUE;
                long hi = parsed.beforeMs() != null ? parsed.beforeMs() : Long.MAX_VALUE;
                qb.add(LongPoint.newRangeQuery("modifiedPoint", lo, hi), BooleanClause.Occur.FILTER);
            }

            // ── size filter ────────────────────────────────────────
            if (parsed.minSizeBytes() != null || parsed.maxSizeBytes() != null) {
                long lo = parsed.minSizeBytes() != null ? parsed.minSizeBytes() : 0;
                long hi = parsed.maxSizeBytes() != null ? parsed.maxSizeBytes() : Long.MAX_VALUE;
                qb.add(LongPoint.newRangeQuery("sizePoint", lo, hi), BooleanClause.Occur.FILTER);
            }

            // If nothing to query, return empty
            BooleanQuery finalQuery = qb.build();
            if (finalQuery.clauses().isEmpty()) return results;

            // Sort: by relevance (default), or by date if history query
            Sort sort = parsed.historyOnly()
                    ? new Sort(new SortField("modifiedSort", SortField.Type.LONG, true))
                    : Sort.RELEVANCE;

            TopDocs topDocs = searcher.search(finalQuery, maxResults, sort);

            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                results.add(new SearchResult(
                        doc.get("path"),
                        doc.get("filename"),
                        doc.get("ext"),
                        parseLong(doc.get("size")),
                        parseLong(doc.get("modified")),
                        doc.get("suggestedName"),
                        sd.score
                ));
            }
        } catch (Exception e) {
            System.err.println("[SEARCH ERROR] " + e.getMessage());
        }
        return results;
    }

    public static int getDocumentCount() {
        try { return writer.getDocStats().numDocs; } catch (Exception e) { return 0; }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static boolean isSkippable(Path p) {
        // Skip hidden files
        String name = p.getFileName() != null ? p.getFileName().toString() : "";
        if (name.startsWith(".") || name.endsWith("~") || name.endsWith(".tmp")) return true;

        // Skip if any parent directory is in the skip list
        Path parent = p.getParent();
        while (parent != null) {
            String dirName = parent.getFileName() != null ? parent.getFileName().toString() : "";
            if (SKIP_DIRS.contains(dirName)) return true;
            parent = parent.getParent();
        }
        return false;
    }

    public static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0 && dot < fileName.length() - 1)
                ? fileName.substring(dot + 1)
                : "unknown";
    }

    private static long parseLong(String s) {
        if (s == null) return 0;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }
}
