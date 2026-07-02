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

    private static final DateTimeFormatter DATE_FMT_STANDARD =
            DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm");
    private static final DecimalFormat DECIMAL_FMT = new DecimalFormat("0.#");

    private ResultFormatter() {} // Utility class

    /**
     * Human-readable file size.
     */
    public static String displaySize(long sizeBytes) {
        if (sizeBytes < 1024)             return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024)      return fmt(sizeBytes / 1024.0) + " KB";
        if (sizeBytes < 1024L * 1024 * 1024)
            return fmt(sizeBytes / (1024.0 * 1024)) + " MB";
        return fmt(sizeBytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    /**
     * Human-readable modified date.
     */
    public static String displayDate(long modifiedMs) {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(modifiedMs), ZoneId.systemDefault());
        return dt.format(DATE_FMT_STANDARD);
    }

    /**
     * Smart date: "Today 14:23", "Yesterday 09:15", or "12 Mar 2024".
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
     * Parent folder display (last two segments).
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
     * Human-readable file type description.
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

    private static String fmt(double v) {
        return DECIMAL_FMT.format(v);
    }
}
