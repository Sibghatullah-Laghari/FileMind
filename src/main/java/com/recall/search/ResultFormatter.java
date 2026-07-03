package com.recall.search;

import com.recall.core.SearchResult;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Pure formatting utility for SearchResult display.
 * Extracts UI presentation logic from the SearchResult record,
 * keeping data separate from presentation (SRP).
 *
 * All methods are stateless and thread-safe.
 */
public final class ResultFormatter {

    /** Standard date formatter: "dd MMM yyyy  HH:mm". */
    private static final DateTimeFormatter DATE_FMT_STANDARD =
            DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm");

    /** Decimal formatter for file sizes (one decimal place). */
    private static final DecimalFormat DECIMAL_FMT = new DecimalFormat("0.#");

    /** Private constructor to prevent instantiation of this utility class. */
    private ResultFormatter() {} // Utility class

    /**
     * Converts a file size in bytes to a human‑readable string.
     * Examples: "2.3 MB", "450 KB", "1.2 GB".
     *
     * @param sizeBytes the file size in bytes
     * @return formatted size string
     */
    public static String displaySize(long sizeBytes) {
        if (sizeBytes < 1024)             return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024)      return fmt(sizeBytes / 1024.0) + " KB";
        if (sizeBytes < 1024L * 1024 * 1024)
            return fmt(sizeBytes / (1024.0 * 1024)) + " MB";
        return fmt(sizeBytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    /**
     * Formats a timestamp as a standard date string.
     *
     * @param modifiedMs the last modified time in milliseconds since epoch
     * @return formatted date string (e.g., "15 Mar 2025  14:30")
     */
    public static String displayDate(long modifiedMs) {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(modifiedMs), ZoneId.systemDefault());
        return dt.format(DATE_FMT_STANDARD);
    }

    /**
     * Returns a smart, relative date string:
     * - "Today 14:23" for today's files
     * - "Yesterday 09:15" for yesterday's files
     * - "12 Mar 2024" for older files
     *
     * @param modifiedMs the last modified time in milliseconds since epoch
     * @return a user‑friendly relative date string
     */
    public static String smartDate(long modifiedMs) {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(modifiedMs), ZoneId.systemDefault());
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate fileDate = dt.toLocalDate();

        if (fileDate.equals(today)) {
            return String.format("Today %02d:%02d", dt.getHour(), dt.getMinute());
        } else if (fileDate.equals(yesterday)) {
            return String.format("Yesterday %02d:%02d", dt.getHour(), dt.getMinute());
        }
        return dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    /**
     * Extracts the parent folder name(s) from a file path.
     * Shows the last two path segments for context.
     *
     * @param path the absolute file path
     * @return the parent folder string (e.g., "Projects/recall-search"),
     *         or an empty string if no parent exists
     */
    public static String parentFolder(String path) {
        if (path == null || path.isBlank()) return "";
        Path p = Paths.get(path);
        Path parent = p.getParent();
        if (parent == null) return "";
        Path grandparent = parent.getParent();
        return grandparent != null
                ? grandparent.getFileName() + "/" + parent.getFileName()
                : parent.getFileName().toString();
    }

    /**
     * Returns a human‑readable description of a file type based on its extension.
     *
     * @param ext the file extension (without dot), or null/empty
     * @return a descriptive type name (e.g., "PDF Document", "Java Source")
     */
    public static String describeFileType(String ext) {
        if (ext == null || ext.isEmpty()) return "Unknown";
        return switch (ext.toLowerCase()) {
            case "pdf" -> "PDF Document";
            case "doc", "docx" -> "Word Document";
            case "xls", "xlsx", "csv" -> "Excel Spreadsheet";
            case "ppt", "pptx" -> "PowerPoint Presentation";
            case "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg" -> "Image";
            case "mp4", "avi", "mkv", "mov" -> "Video";
            case "mp3", "wav", "flac", "aac" -> "Audio";
            case "java" -> "Java Source";
            case "py" -> "Python Script";
            case "js", "ts" -> "JavaScript/TypeScript";
            case "cpp", "c", "h" -> "C/C++ Source";
            case "go" -> "Go Source";
            case "rs" -> "Rust Source";
            case "kt" -> "Kotlin Source";
            case "swift" -> "Swift Source";
            case "html", "htm" -> "HTML Document";
            case "css" -> "CSS Stylesheet";
            case "xml", "json", "yaml", "yml", "toml" -> "Configuration";
            case "md" -> "Markdown Document";
            case "txt" -> "Text Document";
            case "zip", "tar", "gz", "7z", "rar" -> "Archive";
            case "exe", "app", "bat", "sh" -> "Executable";
            case "sql" -> "Database";
            default -> ext.toUpperCase() + " File";
        };
    }

    /**
     * Helper to format a double with one decimal place.
     *
     * @param v the value to format
     * @return a string with one decimal digit
     */
    private static String fmt(double v) {
        return DECIMAL_FMT.format(v);
    }
}