package com.recall.core;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

public class NLQueryParser {

    public record ParsedQuery(
            String luceneQuery,
            String fileType,
            Long afterMs,
            Long beforeMs,
            Long minSizeBytes,
            Long maxSizeBytes,
            boolean historyOnly
    ) {}

    private static final Map<String, String> TYPE_MAP = Map.of(
            "pdf", "pdf", "java", "java", "image", "png jpg jpeg webp gif",
            "word", "docx doc", "code", "java py js ts cpp c go",
            "video", "mp4 mkv avi", "audio", "mp3 wav m4a"
    );

    public static ParsedQuery parse(String input) {
        String lower = input.toLowerCase().trim();
        String luceneTerms = input;
        String fileType = null;
        Long afterMs = null, beforeMs = null;
        Long minSize = null, maxSize = null;
        boolean historyOnly = false;

        // History: "worked on 2 days ago"
        Matcher histMatcher = Pattern.compile(
                "(worked on|opened|used|accessed|working on).{0,20}(\\d+)\\s*(day|hour)s?\\s*ago|yesterday|today"
        ).matcher(lower);
        if (histMatcher.find()) {
            historyOnly = true;
            if (lower.contains("yesterday")) {
                afterMs = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                beforeMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } else if (lower.contains("today")) {
                afterMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } else {
                String num = histMatcher.group(2);
                String unit = histMatcher.group(3);
                long n = Long.parseLong(num);
                afterMs = "hour".equals(unit)
                        ? Instant.now().minusSeconds(n * 3600).toEpochMilli()
                        : LocalDate.now().minusDays(n).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
            luceneTerms = histMatcher.replaceAll("").trim();
        }

        // File type: "pdf files", "java files"
        for (Map.Entry<String, String> e : TYPE_MAP.entrySet()) {
            if (lower.contains(e.getKey() + " file") || lower.contains(e.getKey() + " files") || lower.startsWith(e.getKey())) {
                fileType = e.getKey();
                luceneTerms = luceneTerms.replaceAll("(?i)\\b" + e.getKey() + "\\s*files?\\b", "").trim();
                break;
            }
        }

        // Size: "larger than 10mb"
        Matcher sizeMatcher = Pattern.compile("(larger|bigger|more)\\s+than\\s+(\\d+)\\s*(mb|gb|kb)").matcher(lower);
        if (sizeMatcher.find()) {
            minSize = toBytes(Long.parseLong(sizeMatcher.group(2)), sizeMatcher.group(3));
            luceneTerms = sizeMatcher.replaceAll("").trim();
        }

        // Clean leftover stopwords
        luceneTerms = luceneTerms.replaceAll("(?i)\\b(all|enlist|find|show|list|me|the|those|which|have|with|that|are|were|where|what|is)\\b", " ")
                .replaceAll("\\s{2,}", " ").trim();

        return new ParsedQuery(luceneTerms, fileType, afterMs, beforeMs, minSize, maxSize, historyOnly);
    }

    private static long toBytes(long n, String unit) {
        return switch (unit) {
            case "kb" -> n * 1024;
            case "gb" -> n * 1024L * 1024 * 1024;
            default   -> n * 1024 * 1024;
        };
    }
}