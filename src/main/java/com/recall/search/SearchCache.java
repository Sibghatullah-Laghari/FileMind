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
    /** Maximum number of distinct queries to keep in the result cache (LRU eviction). */
    private static final int MAX_CACHED_QUERIES = 500;

    /** Maximum number of suggestions to return for a given prefix. */
    private static final int MAX_SUGGESTIONS = 10;

    // ── State ──────────────────────────────────────────────────────────────
    /**
     * LRU cache mapping query strings to their result lists.
     * Access order is used to implement LRU eviction via removeEldestEntry.
     */
    private final LinkedHashMap<String, List<SearchResult>> resultCache;

    /**
     * Concurrent cache mapping normalized query strings to a list of suggested filenames
     * derived from the cached results. Used for prefix‑based autocomplete.
     */
    private final Map<String, List<String>> suggestionCache = new ConcurrentHashMap<>();

    /**
     * Constructs a new SearchCache with LRU eviction based on MAX_CACHED_QUERIES.
     */
    public SearchCache() {
        this.resultCache = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<SearchResult>> eldest) {
                return size() > MAX_CACHED_QUERIES;
            }
        };
    }

    /**
     * Retrieves cached results for an exact query string.
     * This operation is synchronized to ensure thread‑safe access to the LRU cache.
     *
     * @param query the query string to look up
     * @return the list of cached SearchResult objects, or {@code null} if not present
     */
    public synchronized List<SearchResult> get(String query) {
        return resultCache.get(query);
    }

    /**
     * Stores the results for a given query in the cache and updates the suggestion index.
     * If the cache size exceeds MAX_CACHED_QUERIES, the eldest entry is evicted.
     * This operation is synchronized for thread safety.
     *
     * @param query   the query string (used as the cache key)
     * @param results the list of SearchResult objects to cache
     */
    public synchronized void put(String query, List<SearchResult> results) {
        if (query == null || results == null) return;
        resultCache.put(query, results);
        // Index first N filenames as suggestions
        indexSuggestions(query, results);
    }

    /**
     * Returns prefix‑based filename suggestions from previously cached results.
     * Only returns suggestions for prefixes of length >= 2 characters.
     *
     * @param prefix the prefix to match against (case‑insensitive)
     * @return a list of up to MAX_SUGGESTIONS distinct filenames that contain the prefix,
     *         or an empty list if no suggestions are available
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
     * Clears both the result cache and the suggestion cache.
     */
    public synchronized void clear() {
        resultCache.clear();
        suggestionCache.clear();
    }

    /**
     * Indexes the filenames from the given result list under the query string,
     * storing them in the suggestion cache for future prefix matches.
     *
     * @param query   the query string to associate with the filenames
     * @param results the result list whose filenames will be indexed
     */
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