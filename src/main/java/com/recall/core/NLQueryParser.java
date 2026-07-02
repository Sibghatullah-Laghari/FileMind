package com.recall.core;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/**
 * Parses natural language search queries into structured filter objects.
 * No AI/ML required — pure regex + rules.
 *
 * Handles:
 *  "java files with multithreading"
 *  "PDFs I worked on yesterday"
 *  "files between 2am and 5pm"
 *  "images larger than 5MB"
 *  "find folder where whatsapp images are stored"
 *  "code files modified in last 3 days"
 *  "spring boot project files"
 */
public class NLQueryParser {

    // ── result record ─────────────────────────────────────────────────────────
    public record ParsedQuery(
            String  luceneQuery,       // cleaned keyword(s) for Lucene
            String  fileType,          // "java", "pdf", "image", etc. — null = any
            Long    afterMs,           // modified-date lower bound (null = no bound)
            Long    beforeMs,          // modified-date upper bound (null = no bound)
            Long    minSizeBytes,
            Long    maxSizeBytes,
            boolean historyOnly,       // query ActivityHistory instead of Lucene
            Integer timeOfDayAfterHour,  // 0–23, null = no filter
            Integer timeOfDayBeforeHour, // 0–23, null = no filter
            boolean folderSearch       // user wants folders, not files
    ) {
        /** Returns the file extensions for this fileType, or empty array */
        public String[] fileTypeExtensions() {
            if (fileType == null) return new String[0];
            return switch (fileType) {
                case "pdf"    -> new String[]{"pdf"};
                case "word"   -> new String[]{"docx","doc","odt"};
                case "excel"  -> new String[]{"xlsx","xls","csv","ods"};
                case "ppt"    -> new String[]{"pptx","ppt","odp"};
                case "image"  -> new String[]{"png","jpg","jpeg","gif","bmp","webp","heic"};
                case "video"  -> new String[]{"mp4","avi","mkv","mov","wmv","flv"};
                case "audio"  -> new String[]{"mp3","wav","flac","aac","ogg","m4a"};
                case "java"   -> new String[]{"java"};
                case "python" -> new String[]{"py"};
                case "js"     -> new String[]{"js","ts","jsx","tsx"};
                case "code"   -> new String[]{"java","py","js","ts","cpp","c","go","rs","kt","swift","rb","php","cs","html","css"};
                case "text"   -> new String[]{"txt","md","rst","log"};
                case "zip"    -> new String[]{"zip","tar","gz","7z","rar","bz2"};
                case "sql"    -> new String[]{"sql"};
                case "config" -> new String[]{"xml","json","yaml","yml","toml","ini","properties","env"};
                default       -> new String[0];
            };
        }
    }

    // ── type aliases ──────────────────────────────────────────────────────────
    // Maps natural words → our internal type key
    private static final Map<String, String> TYPE_ALIASES = new LinkedHashMap<>() {{
        // Must check multi-word first
        put("source code",    "code");
        put("code file",      "code");
        put("spring boot",    "java");   // spring boot → java file search
        put("whatsapp",       "image");  // "whatsapp images" → images
        put("screenshot",     "image");
        put("photo",          "image");
        put("picture",        "image");
        put("image",          "image");
        put("video",          "video");
        put("audio",          "audio");
        put("music",          "audio");
        put("recording",      "audio");
        put("lecture",        "audio");
        put("pdf",            "pdf");
        put("word",           "word");
        put("excel",          "excel");
        put("spreadsheet",    "excel");
        put("powerpoint",     "ppt");
        put("slide",          "ppt");
        put("presentation",   "ppt");
        put("java",           "java");
        put("python",         "python");
        put("javascript",     "js");
        put("typescript",     "js");
        put("config",         "config");
        put("configuration",  "config");
        put("sql",            "sql");
        put("database",       "sql");
        put("text",           "text");
        put("note",           "text");
        put("zip",            "zip");
        put("archive",        "zip");
    }};

    // Stopwords to strip before sending to Lucene
    private static final Set<String> STOPWORDS = Set.of(
            "all","enlist","find","show","list","me","the","those","which","have",
            "with","that","are","were","where","what","is","get","give","search",
            "look","for","files","file","folder","folders","please","can","you",
            "i","my","was","working","worked","used","opened","accessed","on","of",
            "a","an","in","at","to","do","want","need","some","any"
    );

    // Pre-compiled regex patterns (avoid per-call Pattern.compile)
    private static final Pattern DAYS_AGO = Pattern.compile(
            "(\\d+)\\s*(day|hour|minute)s?\\s*ago");
    private static final Pattern LAST_N = Pattern.compile(
            "last\\s+(\\d+)\\s*(day|hour|week)s?");
    private static final Pattern TIME_OF_DAY = Pattern.compile(
            "(?:between|from)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\s+(?:and|to)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)");
    private static final Pattern LARGER = Pattern.compile(
            "(larger|bigger|more|greater)\\s+than\\s+(\\d+(?:\\.\\d+)?)\\s*(kb|mb|gb)");
    private static final Pattern SMALLER = Pattern.compile(
            "(smaller|less|under)\\s+than\\s+(\\d+(?:\\.\\d+)?)\\s*(kb|mb|gb)");

    // ── main parse method ─────────────────────────────────────────────────────
    public static ParsedQuery parse(String raw) {
        if (raw == null || raw.isBlank())
            return empty();

        String lower = raw.toLowerCase(Locale.ROOT).trim();
        String workingText = lower;

        Long    afterMs  = null, beforeMs = null;
        Long    minSize  = null, maxSize  = null;
        String  fileType = null;
        boolean historyOnly = false;
        boolean folderSearch = false;
        Integer todAfter = null, todBefore = null;

        // ── 1. folder search detection ─────────────────────────────
        if (lower.contains("folder") || lower.contains("directory") || lower.contains("where is")) {
            folderSearch = true;
        }

        // ── 2. history / time-ago queries ──────────────────────────
        // "files I worked on 2 days ago" / "yesterday" / "today" / "last 3 days"
        Matcher daysAgo = DAYS_AGO.matcher(lower);
        if (daysAgo.find()) {
            historyOnly = true;
            long n    = Long.parseLong(daysAgo.group(1));
            String unit = daysAgo.group(2);
            long offsetMs = switch (unit) {
                case "minute" -> n * 60_000;
                case "hour"   -> n * 3_600_000;
                default       -> n * 86_400_000; // day
            };
            afterMs  = Instant.now().toEpochMilli() - offsetMs;
            beforeMs = Instant.now().toEpochMilli();
            workingText = daysAgo.replaceAll("").trim();
        }
        if (lower.contains("yesterday")) {
            historyOnly = true;
            afterMs  = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            beforeMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            workingText = workingText.replace("yesterday", "").trim();
        }
        if (lower.contains("today")) {
            afterMs  = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            beforeMs = null;
            workingText = workingText.replace("today", "").trim();
        }

        // "last N days/hours"
        Matcher lastN = LAST_N.matcher(lower);
        if (lastN.find()) {
            long n    = Long.parseLong(lastN.group(1));
            String unit = lastN.group(2);
            long offsetMs = switch (unit) {
                case "hour" -> n * 3_600_000;
                case "week" -> n * 7 * 86_400_000;
                default     -> n * 86_400_000;
            };
            afterMs = Instant.now().toEpochMilli() - offsetMs;
            workingText = lastN.replaceAll("").trim();
        }

        // ── 3. time-of-day range ───────────────────────────────────
        // "between 2am and 5pm" / "from 14:00 to 17:00"
        Matcher tod = TIME_OF_DAY.matcher(lower);
        if (tod.find()) {
            todAfter  = toHour(Integer.parseInt(tod.group(1)), tod.group(3));
            todBefore = toHour(Integer.parseInt(tod.group(4)), tod.group(6));
            workingText = tod.replaceAll("").trim();
        }

        // ── 4. size filters ────────────────────────────────────────
        // "larger than 5MB" / "smaller than 100KB" / "bigger than 1GB"
        Matcher larger = LARGER.matcher(lower);
        if (larger.find()) {
            minSize = toBytes(Double.parseDouble(larger.group(2)), larger.group(3));
            workingText = larger.replaceAll("").trim();
        }
        Matcher smaller = SMALLER.matcher(lower);
        if (smaller.find()) {
            maxSize = toBytes(Double.parseDouble(smaller.group(2)), smaller.group(3));
            workingText = smaller.replaceAll("").trim();
        }

        // ── 5. file type detection ─────────────────────────────────
        // Check multi-word aliases first (order in TYPE_ALIASES matters)
        for (Map.Entry<String, String> e : TYPE_ALIASES.entrySet()) {
            if (workingText.contains(e.getKey())) {
                fileType = e.getValue();
                workingText = workingText.replace(e.getKey(), " ").trim();
                break;
            }
        }

        // ── 6. strip noise words ───────────────────────────────────
        String[] tokens = workingText.split("\\s+");
        StringBuilder cleanQ = new StringBuilder();
        for (String tok : tokens) {
            // Keep tokens with meaningful content (>2 chars, not a stopword)
            String stripped = tok.replaceAll("[^a-z0-9]", "");
            if (stripped.length() > 2 && !STOPWORDS.contains(stripped)) {
                cleanQ.append(stripped).append(" ");
            }
        }
        String luceneQuery = cleanQ.toString().trim();

        // Escape Lucene special chars if no explicit operators
        if (!luceneQuery.isEmpty() && !luceneQuery.contains(":") && !luceneQuery.contains("\"")) {
            luceneQuery = luceneQuery
                    .replace("\\", "\\\\")
                    .replace("+", "\\+")
                    .replace("-", "\\-")
                    .replace("!", "\\!")
                    .replace("(", "\\(")
                    .replace(")", "\\)")
                    .replace("{", "\\{")
                    .replace("}", "\\}")
                    .replace("[", "\\[")
                    .replace("]", "\\]")
                    .replace("^", "\\^")
                    .replace("~", "\\~");
        }

        return new ParsedQuery(
                luceneQuery.isBlank() ? null : luceneQuery,
                fileType, afterMs, beforeMs,
                minSize, maxSize,
                historyOnly, todAfter, todBefore, folderSearch
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ParsedQuery empty() {
        return new ParsedQuery(null, null, null, null, null, null, false, null, null, false);
    }

    private static int toHour(int h, String ampm) {
        if (ampm == null) return h; // assume 24h
        if ("pm".equals(ampm) && h < 12) return h + 12;
        if ("am".equals(ampm) && h == 12) return 0;
        return h;
    }

    private static long toBytes(double n, String unit) {
        return switch (unit.toLowerCase()) {
            case "kb" -> (long)(n * 1024);
            case "gb" -> (long)(n * 1024 * 1024 * 1024);
            default   -> (long)(n * 1024 * 1024); // mb
        };
    }
}