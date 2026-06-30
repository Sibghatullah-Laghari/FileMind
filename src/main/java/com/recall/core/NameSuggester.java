package com.recall.core;

import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class NameSuggester {

    private static final Set<String> STOPWORDS = Set.of(
            "the","a","an","is","are","was","were","in","on","at","to","of","and",
            "or","for","with","this","that","it","be","as","by","from","not","but"
    );

    public static String suggest(String existingName, String contentSnippet, String ext) {
        if (contentSnippet == null || contentSnippet.isBlank()) return null;

        String snippet = contentSnippet.substring(0, Math.min(500, contentSnippet.length()));
        Map<String, Integer> freq = new LinkedHashMap<>();
        Matcher m = Pattern.compile("[a-zA-Z]{4,}").matcher(snippet.toLowerCase());
        while (m.find()) {
            String w = m.group();
            if (!STOPWORDS.contains(w)) freq.merge(w, 1, Integer::sum);
        }

        List<String> topTerms = freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (topTerms.isEmpty()) return null;
        String candidate = String.join("-", topTerms) + "." + ext;
        if (candidate.equalsIgnoreCase(existingName)) return null;
        return candidate;
    }
}