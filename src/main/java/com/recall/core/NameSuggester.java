/*
 * File: NameSuggester.java
 * Package: com.recall.core
 * Purpose: Suggest a more descriptive filename based on extracted text content.
 * Last updated: 2026-07-17
 * 
 * This class uses a lightweight TF-IDF-inspired scoring approach to extract
 * the most relevant keywords from a document's content and assemble them into
 */
package com.recall.core;

import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Suggests a more descriptive filename using the first 500 characters
 * of extracted text content.
 *
 * Uses a lightweight TF-IDF-inspired scoring approach without external
 * libraries or significant memory overhead.
 *
 * Examples:
 *   "screenshot(17).png"  → no suggestion (image content cannot be analyzed)
 *   "notes.pdf"           → "spring-security-jwt-authentication.pdf"
 *   "Untitled1.docx"      → "machine-learning-linear-regression.docx"
 */
public class NameSuggester {

    // Common stop words that provide little value when generating suggestions.
    // (Last reviewed: 2026-07-17 – unchanged, covers most English noise words.)
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

    // Generic filenames that are usually not meaningful to keep.
    private static final Set<String> GENERIC_BASE_NAMES = Set.of(
            "untitled","document","file","notes","new","copy","draft",
            "temp","tmp","test","backup","old","final","latest"
    );

    /**
     * @param existingName   Current filename (including extension)
     * @param contentSnippet First ~500 characters of extracted text
     * @param ext            File extension (without the leading dot)
     * @return A suggested filename such as "spring-security-jwt.pdf",
     *         or {@code null} when no better name can be determined.
     */
    public static String suggest(String existingName, String contentSnippet, String ext) {
        if (contentSnippet == null || contentSnippet.isBlank()) return null;

        // Only process text-based file types.
        Set<String> textTypes = Set.of("pdf","docx","doc","txt","md","java","py","js",
                "ts","xml","json","yaml","yml","properties","html","css","sql","rst");
        if (!textTypes.contains(ext.toLowerCase())) return null;

        // Skip files that already appear to have descriptive names.
        String baseName = existingName.contains(".")
                ? existingName.substring(0, existingName.lastIndexOf('.'))
                : existingName;
        if (!isGenericName(baseName)) return null;

        // Analyze only the first 500 characters for efficiency.
        String snippet = contentSnippet.substring(0, Math.min(500, contentSnippet.length()));

        // Extract words with four or more letters and count their frequency.
        Map<String, Integer> freq = new LinkedHashMap<>();
        Matcher m = Pattern.compile("[a-zA-Z]{4,}").matcher(snippet);
        while (m.find()) {
            String word = m.group().toLowerCase();
            if (!STOPWORDS.contains(word)) {
                freq.merge(word, 1, Integer::sum);
            }
        }

        if (freq.isEmpty()) return null;

        // Select the three most frequent keywords.
        List<String> topTerms = freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .filter(w -> w.length() <= 12)
                .limit(3)
                .collect(Collectors.toList());

        if (topTerms.isEmpty()) return null;

        // Build the suggested filename using the selected keywords.
        String candidate = String.join("-", topTerms) + "." + ext;

        // Return a suggestion only when it is meaningfully different.
        // (Prevents generating a suggestion that is essentially the same as the original.)
        if (candidate.equalsIgnoreCase(existingName)) return null;
        if (candidate.length() < existingName.length() && !isGenericName(baseName)) return null;

        return candidate;
    }

    /**
     * Determines whether a filename is generic enough to benefit from
     * an automatically generated suggestion.
     */
    private static boolean isGenericName(String baseName) {
        if (baseName == null || baseName.isBlank()) return true;

        String lower = baseName.toLowerCase().replaceAll("[^a-z]", "");

        if (lower.length() < 4) return true;
        if (lower.length() < 8) return true;

        // Detect common auto-generated filenames containing long numeric sequences.
        if (baseName.matches(".*\\d{4,}.*")) return true;

        // Remove any trailing digits before checking against generic names.
        String stripped = lower.replaceAll("\\d+$", "");
        return GENERIC_BASE_NAMES.contains(stripped);
    }
}
