package com.recall.core;

import org.apache.tika.Tika;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Content extraction for various file types using Apache Tika with sensible limits.
 */
public class ContentExtractor {

    private static final Tika tika = new Tika();

    static {
        tika.setMaxStringLength(100000);
    }

    public static String extract(String filePath) {
        File file = new File(filePath);
        String ext = getExtension(filePath).toLowerCase();

        // Skip files over 50MB
        try {
            if (file.length() > 50 * 1024 * 1024) {
                return "";
            }
        } catch (Exception ignored) {}

        return switch (ext) {
            case "pdf" -> extractPdfSmart(file);
            case "doc", "docx", "txt", "md", "rst" -> extractWithTika(file, 50000);
            case "xlsx", "csv" -> extractWithTika(file, 30000);
            case "java", "py", "js", "ts", "cpp", "c", "h", "go", "rs" -> extractSourceCode(file, 80000);
            case "xml", "json", "yaml", "yml", "toml" -> extractTextFile(file, 30000);
            case "html", "css" -> extractWithTika(file, 30000);
            case "png", "jpg", "jpeg", "gif", "bmp" -> "";  // Metadata only
            case "mp4", "mp3", "avi", "mov" -> "";  // Filename only
            case "zip", "tar", "gz", "7z" -> "";  // Filename only
            case "exe", "dll", "bin", "so" -> null;  // Skip
            default -> extractWithTika(file, 30000);
        };
    }

    private static String extractPdfSmart(File file) {
        try {
            String content = tika.parseToString(file);
            if (content.length() <= 25000) {
                return content;
            }
            // Take first 20k and last 5k
            String first = content.substring(0, 20000);
            String last = content.substring(Math.max(0, content.length() - 5000));
            return first + "\n...\n" + last;
        } catch (Exception e) {
            return "";
        }
    }

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

    private static String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }
}

