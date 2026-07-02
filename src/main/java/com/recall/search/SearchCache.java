package com.recall.search;

import com.recall.core.SearchResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight search result cache for autocomplete-speed responses.
 * Caches recent queries and their results. Provides prefix-based suggestion lookup.
 *
 * Performance target: <20ms cache hit.
 * Memory target: <5MB (LRU eviction at 500 entries).
 */
public final class SearchCache {

    // ── Constants ──────────────────────────────────────────────────────────
    private static final int MAX_CACHED_QUERIES = 500;
    private static final int MAX_SUGGESTIONS = 10;

    // ── State ──────────────────────────────────────────────────────────────
    private final LinkedHashMap<String, List<SearchResult>> resultCache;
    private final Map<String, List<String>> suggestionCache = new ConcurrentHashMap<>();

    public SearchCache() {
        this.resultCache = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<SearchResult>> eldest) {
                return size() > MAX_CACHED_QUERIES;
            }
        };
    }

    /**
     * Get cached results for an exact query match.
     */
    public synchronized List<SearchResult> get(String query) {
        return resultCache.get(query);
    }

    /**
     * Cache results for a query. Also updates suggestion index.
     */
    public synchronized void put(String query, List<SearchResult> results) {
        if (query == null || results == null) return;
        resultCache.put(query, results);
        // Index first N filenames as suggestions
        indexSuggestions(query, results);
    }

    /**
     * Get prefix-based suggestions. Must be fast (<2ms).
     */
    public synchronized List<String> getSuggestions(String prefix) {
        if (prefix == null || prefix.length() < 2) return List.of();
        String lower = prefix.toLowerCase();
        return suggestionCache.entrySet().stream()
                .filter(e -> e.getKey().startsWith(lower))
                .flatMap(e -> e.getValue().stream())
                .distinct()
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    /**
     * Clear all caches.
     */
    public synchronized void clear() {
        resultCache.clear();
        suggestionCache.clear();
    }

    private void indexSuggestions(String query, List<SearchResult> results) {
        String lowerQuery = query.toLowerCase();
        List<String> filenames = results.stream()
                .map(SearchResult::filename)
                .filter(fn -> fn.toLowerCase().contains(lowerQuery))
                .limit(MAX_SUGGESTIONS)
                .toList();
        if (!filenames.isEmpty()) {
            suggestionCache.put(lowerQuery, filenames);
        }
    }
}
