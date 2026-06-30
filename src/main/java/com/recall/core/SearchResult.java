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
 * Contains everything the UI needs — no further DB lookups required.
 */
public record SearchResult(
        String path,
        String filename,
        String ext,
        long   sizeBytes,
        long   modifiedMs,
        String suggestedName,   // null if name is already descriptive
        float  score
) {
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm");

    /** Human-readable file size: "2.3 MB", "450 KB", etc. */
    public String displaySize() {
        if (sizeBytes < 1024)             return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024)      return fmt(sizeBytes / 1024.0) + " KB";
        if (sizeBytes < 1024 * 1024 * 1024) return fmt(sizeBytes / (1024.0 * 1024)) + " MB";
        return fmt(sizeBytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    /** Human-readable modified date */
    public String displayDate() {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(modifiedMs), ZoneId.systemDefault());
        return dt.format(DATE_FMT);
    }

    /** Parent folder name only (not full path) */
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
            case "pdf"                    -> "📕";
            case "docx", "doc"            -> "📝";
            case "xlsx", "xls", "csv"     -> "📊";
            case "pptx", "ppt"            -> "📋";
            case "java", "py", "js", "ts",
                 "cpp", "c", "go", "rs",
                 "kt", "swift", "rb"      -> "☕";
            case "xml", "json", "yaml",
                 "yml", "toml", "ini",
                 "properties"             -> "⚙️";
            case "md", "txt", "rst"       -> "📄";
            case "html", "css"            -> "🌐";
            case "png", "jpg", "jpeg",
                 "gif", "bmp", "webp"     -> "🖼️";
            case "mp4", "avi", "mkv",
                 "mov"                    -> "🎬";
            case "mp3", "wav", "flac",
                 "aac"                    -> "🎵";
            case "zip", "tar", "gz",
                 "7z", "rar"              -> "📦";
            case "sql"                    -> "🗄️";
            case "sh", "bat", "ps1"       -> "⚡";
            default                       -> "📄";
        };
    }

    private static String fmt(double v) {
        return new DecimalFormat("0.#").format(v);
    }
}