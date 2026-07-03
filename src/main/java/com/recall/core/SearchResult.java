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
        /** Absolute file system path of the file. */
        String path,
        /** The file name (including extension). */
        String filename,
        /** File extension (without dot), e.g., "pdf". */
        String ext,
        /** File size in bytes. */
        long   sizeBytes,
        /** Last modified timestamp in milliseconds since epoch. */
        long   modifiedMs,
        /** Suggested descriptive name (or null if the original name is already descriptive). */
        String suggestedName,
        /** First 300 characters of extracted content for preview. */
        String snippet,
        /** Lucene relevance score (higher = more relevant). */
        float  score
) {
    /** Date formatter for user‑friendly display: "dd MMM yyyy  HH:mm". */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm");

    /**
     * Returns a human‑readable representation of the file size.
     * Examples: "2.3 MB", "450 KB", "1.2 GB".
     *
     * @return formatted size string
     */
    public String displaySize() {
        if (sizeBytes < 1024)             return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024)      return fmt(sizeBytes / 1024.0) + " KB";
        if (sizeBytes < 1024 * 1024 * 1024) return fmt(sizeBytes / (1024.0 * 1024)) + " MB";
        return fmt(sizeBytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    /**
     * Returns the last modified timestamp formatted as a human‑readable date.
     *
     * @return formatted date string (e.g., "15 Mar 2025  14:30")
     */
    public String displayDate() {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(modifiedMs), ZoneId.systemDefault());
        return dt.format(DATE_FMT);
    }

    /**
     * Returns the parent folder name(s) of the file.
     * Includes the last two path segments for context (e.g., "Projects/recall-search").
     *
     * @return parent folder path relative to the root, or empty string if none
     */
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

    /**
     * Returns an emoji icon representing the file type.
     * Provides a quick visual cue without loading external images.
     *
     * @return a single emoji character string
     */
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

    /**
     * Formats a decimal number with one decimal place (e.g., 2.3).
     *
     * @param v the double value to format
     * @return formatted string with one decimal digit
     */
    private static String fmt(double v) {
        return new DecimalFormat("0.#").format(v);
    }
}