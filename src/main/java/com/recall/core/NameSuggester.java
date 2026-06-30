package com.recall.core;

import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Suggests a better filename based on the first 500 chars of file content.
 * Uses simple TF-IDF-style scoring — no external library, no RAM overhead.
 *
 * Examples:
 *   "screenshot(17).png"  → no suggestion (can't read image content)
 *   "notes.pdf"           → "spring-security-jwt-authentication.pdf"
 *   "Untitled1.docx"      → "machine-learning-linear-regression.docx"
 */
public class NameSuggester {

    private static final Set<String> STOPWORDS = Set.of(
            "the","a","an","is","are","was","were","in","on","at","to","of","and",
            "or","for","with","this","that","it","be","as","by","from","not","but",
            "we","you","i","he","she","they","our","your","its","their","my",
            "can","will","would","should","could","have","has","had","do","does",
            "did","may","might","shall","been","being","also","just","so","if",
            "then","than","when","where","which","who","what","how","all","any",
            "each","few","more","most","other","some","such","no","nor","only",
            "same","too","very","one","two","three","first","second","last","new"
    );

    // Names so generic that suggesting them is meaningless
    private static final Set<String> GENERIC_BASE_NAMES = Set.of(
            "untitled","document","file","notes","new","copy","draft",
            "temp","tmp","test","backup","old","final","latest"
    );

    /**
     * @param existingName  Current filename (with extension)
     * @param contentSnippet  First ~500 chars of extracted text
     * @param ext  File extension (without dot)
     * @return  Suggested name like "spring-security-jwt.pdf", or null if no improvement
     */
    public static String suggest(String existingName, String contentSnippet, String ext) {
        if (contentSnippet == null || contentSnippet.isBlank()) return null;

        // Only suggest for file types where content is meaningful text
        Set<String> textTypes = Set.of("pdf","docx","doc","txt","md","java","py","js",
                "ts","xml","json","yaml","yml","properties","html","css","sql","rst");
        if (!textTypes.contains(ext.toLowerCase())) return null;

        // Check if existing name is already descriptive (>20 chars base, not generic)
        String baseName = existingName.contains(".")
                ? existingName.substring(0, existingName.lastIndexOf('.'))
                : existingName;
        if (!isGenericName(baseName)) return null; // name already looks descriptive

        // Use first 500 chars max
        String snippet = contentSnippet.substring(0, Math.min(500, contentSnippet.length()));

        // Tokenize: keep words 4+ chars
        Map<String, Integer> freq = new LinkedHashMap<>();
        Matcher m = Pattern.compile("[a-zA-Z]{4,}").matcher(snippet);
        while (m.find()) {
            String word = m.group().toLowerCase();
            if (!STOPWORDS.contains(word)) {
                freq.merge(word, 1, Integer::sum);
            }
        }

        if (freq.isEmpty()) return null;

        // Top 3 terms by frequency, max 12 chars each (avoid giant filenames)
        List<String> topTerms = freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .filter(w -> w.length() <= 12)
                .limit(3)
                .collect(Collectors.toList());

        if (topTerms.isEmpty()) return null;

        String candidate = String.join("-", topTerms) + "." + ext;

        // Only suggest if meaningfully different from existing
        if (candidate.equalsIgnoreCase(existingName)) return null;
        if (candidate.length() < existingName.length() && !isGenericName(baseName)) return null;

        return candidate;
    }

    private static boolean isGenericName(String baseName) {
        if (baseName == null || baseName.isBlank()) return true;
        String lower = baseName.toLowerCase().replaceAll("[^a-z]", "");
        if (lower.length() < 4)  return true;  // "a", "1", etc.
        if (lower.length() < 8)  return true;  // short = probably generic

        // Contains a digit run → likely auto-generated: "document1", "img_20240315"
        if (baseName.matches(".*\\d{4,}.*")) return true;

        // Is a known generic word (possibly with number suffix)
        String stripped = lower.replaceAll("\\d+$", "");
        return GENERIC_BASE_NAMES.contains(stripped);
    }
}