package com.recall.core;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.apache.commons.io.FilenameUtils.getExtension;

public class LuceneIndexer {
    private static volatile boolean needsRefresh = false;
    private static Directory directory;
    private static IndexWriter writer;
    private static IndexSearcher searcher;
    private static final Tika tika = new Tika();

    // CRITICAL: Keeps RAM under 16 MB for indexing
    private static final double RAM_BUFFER_MB = 16.0;

    public static void init(Path indexDir) throws IOException {
        directory = FSDirectory.open(indexDir);
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setRAMBufferSizeMB(RAM_BUFFER_MB);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        writer = new IndexWriter(directory, config);
        refreshSearcher();
    }

    public static void close() {
        try {
            if (writer != null) writer.close();
            if (directory != null) directory.close();
        } catch (IOException ignored) {}
    }
    public static void flushAndRefresh() throws IOException {
        if (needsRefresh) {
            writer.flush();
            DirectoryReader newReader = DirectoryReader.openIfChanged((DirectoryReader) searcher.getIndexReader());
            if (newReader != null) {
                searcher = new IndexSearcher(newReader);
            }
            needsRefresh = false;
        }
    }
    private static boolean isSkippable(Path p) {
        String s = p.toAbsolutePath().toString();
        if (p.getFileName().toString().startsWith(".")) return true;
        if (p.getFileName().toString().endsWith("~")) return true;
        String[] skipDirs = {"node_modules", ".git", "target", "build", ".gradle", "__pycache__", ".idea"};
        for (String d : skipDirs) {
            if (s.contains("/" + d + "/") || s.contains("\\" + d + "\\")) return true;
        }
        return false;
    }

    private static void refreshSearcher() throws IOException {
        searcher = new IndexSearcher(DirectoryReader.open(writer));
    }
    private static String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx == -1 ? "" : fileName.substring(idx + 1).toLowerCase();
    }

    // Index a single file (called by watcher or initial scan)
    public static void indexFile(Path filePath) {
        try {
            if (!Files.isRegularFile(filePath)) return;
            long size = Files.size(filePath);
            if (size > 500L * 1024 * 1024) return;

            String pathStr = filePath.toAbsolutePath().toString();
            String fileName = filePath.getFileName().toString();

            if (isSkippable(filePath)) return;

            String content = "";
            try {
                content = tika.parseToString(filePath.toFile());
                if (content.length() > 50_000)
                    content = content.substring(0, 50_000);
            } catch (Exception e) {
                // corrupted / password-protected — index metadata only
            }

            Document doc = new Document();
            doc.add(new StringField("path", pathStr, Field.Store.YES));
            doc.add(new TextField("filename", fileName, Field.Store.YES));
            doc.add(new TextField("content", content, Field.Store.NO));
            doc.add(new StoredField("ext", getExtension(fileName)));
            doc.add(new LongPoint("modified", Files.getLastModifiedTime(filePath).toMillis()));
            doc.add(new NumericDocValuesField("modifiedSort", Files.getLastModifiedTime(filePath).toMillis()));
            doc.add(new LongPoint("size", size));

            writer.updateDocument(new Term("path", pathStr), doc);
            needsRefresh = true;

        } catch (Exception e) {
            System.err.println("Index error: " + filePath + " — " + e.getMessage());
        }
    }

    // Delete a file from index (for watcher DELETE events)
    public static void deleteFile(Path filePath) {
        try {
            writer.deleteDocuments(new Term("path", filePath.toAbsolutePath().toString()));
            refreshSearcher();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Check if path already exists in index
    private static boolean existsInIndex(String pathStr) throws IOException, ParseException {
        Query query = new QueryParser("path", new StandardAnalyzer()).parse(pathStr);
        TopDocs docs = searcher.search(query, 1);
        return docs.totalHits.value > 0;
    }

    // RECURSIVE folder indexing (runs in background)
    public static void indexFolder(Path folder, BiConsumer<Path, Boolean> callback) {
        try {
            Files.walk(folder)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        indexFile(file);
                        if (callback != null) callback.accept(file, true);
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // SEARCH: returns list of file paths matching query
    public static List<String> search(String queryText) {
        List<String> results = new ArrayList<>();
        try {
            flushAndRefresh();

            if (queryText == null || queryText.trim().isEmpty()) return results;

            // Search in both filename AND content
            QueryParser parser = new QueryParser("content", new StandardAnalyzer());
            Query query = parser.parse(queryText);

            // Also search filename as fallback
            Query filenameQuery = new QueryParser("filename", new StandardAnalyzer()).parse(queryText);
            BooleanQuery combinedQuery = new BooleanQuery.Builder()
                    .add(query, BooleanClause.Occur.SHOULD)
                    .add(filenameQuery, BooleanClause.Occur.SHOULD)
                    .build();

            TopDocs topDocs = searcher.search(combinedQuery, 100); // Max 100 results
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                results.add(doc.get("path"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    public static int getDocumentCount() throws IOException {
        return writer.getDocStats().numDocs;
    }
}