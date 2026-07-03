package com.recall.core;

import org.apache.tika.Tika;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Content extraction for various file types using Apache Tika with sensible limits.
 * Provides a unified interface to extract textual content from files for indexing and search.
 */
public class ContentExtractor {

    /** Shared Tika instance with a maximum string length limit to avoid memory exhaustion. */
    private static final Tika tika = new Tika();

    static {
        // Set a global limit on the length of extracted text (per file)
        tika.setMaxStringLength(100000);
    }

    /**
     * Extracts textual content from a file based on its extension.
     * Applies different strategies and limits depending on the file type.
     *
     * @param filePath absolute or relative path to the file
     * @return extracted text content, or an empty string if extraction fails or file is skipped,
     *         or {@code null} if the file type is explicitly blacklisted (e.g., executables)
     */
    public static String extract(String filePath) {
        File file = new File(filePath);
        String ext = getExtension(filePath).toLowerCase();

        // Skip files over 50MB to prevent out-of-memory errors
        try {
            if (file.length() > 50 * 1024 * 1024) {
                return "";
            }
        } catch (Exception ignored) {}

        // Route based on file extension
        return switch (ext) {
            case "pdf" -> extractPdfSmart(file);
            case "doc", "docx", "txt", "md", "rst" -> extractWithTika(file, 50000);
            case "xlsx", "csv" -> extractWithTika(file, 30000);
            case "java", "py", "js", "ts", "cpp", "c", "h", "go", "rs" -> extractSourceCode(file, 80000);
            case "xml", "json", "yaml", "yml", "toml" -> extractTextFile(file, 30000);
            case "html", "css" -> extractWithTika(file, 30000);
            case "png", "jpg", "jpeg", "gif", "bmp" -> "";  // Metadata only – no text content
            case "mp4", "mp3", "avi", "mov" -> "";  // Filename only – skip content extraction
            case "zip", "tar", "gz", "7z" -> "";  // Filename only – skip content extraction
            case "exe", "dll", "bin", "so" -> null;  // Blacklisted – skip entirely
            default -> extractWithTika(file, 30000);
        };
    }

    /**
     * Extracts content from a PDF file with a smart truncation strategy:
     * returns the first 20k characters and the last 5k if the total exceeds 25k.
     *
     * @param file the PDF file
     * @return extracted text, possibly truncated, or empty string on failure
     */
    private static String extractPdfSmart(File file) {
        try {
            String content = tika.parseToString(file);
            if (content.length() <= 25000) {
                return content;
            }
            // Take first 20k and last 5k to preserve both beginning and end
            String first = content.substring(0, 20000);
            String last = content.substring(Math.max(0, content.length() - 5000));
            return first + "\n...\n" + last;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Generic Tika-based extraction with a character limit.
     *
     * @param file  the file to parse
     * @param limit maximum number of characters to return
     * @return extracted text truncated to {@code limit}, or empty string on failure
     */
    private static String extractWithTika(File file, int limit) {
        try {
            String content = tika.parseToString(file);
            if (content.length() > limit) {
                return content.substring(0, limit);
            }
            return content;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Extracts raw source code by reading the file as a text file.
     * Suitable for programming language files.
     *
     * @param file  the source file
     * @param limit maximum number of characters to return
     * @return file content truncated to {@code limit}, or empty string on failure
     */
    private static String extractSourceCode(File file, int limit) {
        try {
            String content = Files.readString(file.toPath());
            if (content.length() > limit) {
                return content.substring(0, limit);
            }
            return content;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Extracts content from a plain text file (e.g., XML, JSON, YAML) with a character limit.
     *
     * @param file  the text file
     * @param limit maximum number of characters to return
     * @return file content truncated to {@code limit}, or empty string on failure
     */
    private static String extractTextFile(File file, int limit) {
        try {
            String content = Files.readString(file.toPath());
            if (content.length() > limit) {
                return content.substring(0, limit);
            }
            return content;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Extracts the file extension from a given filename.
     *
     * @param filename full file name (may include path)
     * @return extension in lowercase, or empty string if none found
     */
    private static String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }
}