package com.recall.core;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Immutable result object returned from LuceneIndexer.search().
 */
public record SearchResult(
        String path,          // Full file system path
        String filename,      // Name with extension
        String ext,           // File extension (lowercase)
        long   sizeBytes,     // Raw size in bytes
        long   modifiedMs,    // Last modified timestamp (ms since epoch)
        String suggestedName, // Rename suggestion, null if already good
        float  score          // Lucene relevance score
) {
    // Date formatter: e.g., "15 Jul 2026  14:30"
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm");

    /** Human-readable file size: "2.3 MB", "450 KB", etc. */
    public String displaySize() {
        if (sizeBytes < 1024)             return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024)      return fmt(sizeBytes / 1024.0) + " KB";
        if (sizeBytes < 1024 * 1024 * 1024) return fmt(sizeBytes / (1024.0 * 1024)) + " MB";
        return fmt(sizeBytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    /** Human-readable modified date using system default timezone */
    public String displayDate() {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(modifiedMs), ZoneId.systemDefault());
        return dt.format(DATE_FMT);
    }

    /** Parent folder name only (not full path) – shows last two segments if available */
    public String parentFolder() {
        Path p = Paths.get(path);
        Path parent = p.getParent();
        if (parent == null) return "";
        Path grandparent = parent.getParent();
        // Show last two path segments: "Projects/recall-search"
        return grandparent != null
                ? grandparent.getFileName() + "/" + parent.getFileName()
                : parent.getFileName().toString();
    }

    /** Emoji icon for the file type — quick visual without loading images */
    public String typeIcon() {
        if (ext == null) return "📄";
        return switch (ext.toLowerCase()) {
            // Documents & office
            case "pdf"                    -> "📕";
            case "docx", "doc"            -> "📝";
            case "xlsx", "xls", "csv"     -> "📊";
            case "pptx", "ppt"            -> "📋";
            // Source code
            case "java", "py", "js", "ts",
                 "cpp", "c", "go", "rs",
                 "kt", "swift", "rb"      -> "☕";
            // Config / data
            case "xml", "json", "yaml",
                 "yml", "toml", "ini",
                 "properties"             -> "⚙️";
            // Plain text / markup
            case "md", "txt", "rst"       -> "📄";
            case "html", "css"            -> "🌐";
            // Images
            case "png", "jpg", "jpeg",
                 "gif", "bmp", "webp"     -> "🖼️";
            // Video
            case "mp4", "avi", "mkv",
                 "mov"                    -> "🎬";
            // Audio
            case "mp3", "wav", "flac",
                 "aac"                    -> "🎵";
            // Archives
            case "zip", "tar", "gz",
                 "7z", "rar"              -> "📦";
            // Database
            case "sql"                    -> "🗄️";
            // Scripts
            case "sh", "bat", "ps1"       -> "⚡";
            default                       -> "📄";
        };
    }

    // Helper: format decimal with one decimal place max (e.g., 2.3, 10)
    private static String fmt(double v) {
        return new DecimalFormat("0.#").format(v);
    }
}
