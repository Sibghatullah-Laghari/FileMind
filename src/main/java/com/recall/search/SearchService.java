package com.recall.search;

import com.recall.core.LuceneIndexer;
import com.recall.core.NLQueryParser;
import com.recall.core.SearchResult;
import com.recall.util.EventBus;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Search service abstraction layer.
 * Decouples UI from LuceneIndexer. Supports caching, async search, and future engine swaps.
 *
 * Single responsibility: Execute searches and return results.
 * UI never calls LuceneIndexer directly.
 */
public final class SearchService {

    /** Singleton instance of the SearchService. */
    private static final SearchService INSTANCE = new SearchService();

    /** Cache for recent search results and autocomplete suggestions. */
    private final SearchCache cache;

    /** Dedicated single‑thread executor for search tasks to prevent competition with other threads. */
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "filemind-search");
        t.setDaemon(true);
        return t;
    });

    /**
     * Private constructor for singleton pattern.
     */
    private SearchService() {
        this.cache = new SearchCache();
    }

    /**
     * Returns the singleton instance of the SearchService.
     *
     * @return the shared SearchService instance
     */
    public static SearchService getInstance() {
        return INSTANCE;
    }

    /**
     * Executes an asynchronous search for the given query.
     * The result is returned as a CompletableFuture that completes when the search finishes.
     * Cache is checked first; if a cached result exists, it is returned immediately.
     * Upon completion, a SearchCompleteEvent is published via EventBus.
     *
     * @param query      the natural language search query
     * @param maxResults maximum number of results to return
     * @return a CompletableFuture containing the list of SearchResult objects
     */
    public CompletableFuture<List<SearchResult>> searchAsync(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }

        // Check cache first for autocomplete-speed responses
        List<SearchResult> cached = cache.get(query);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            NLQueryParser.ParsedQuery parsed = NLQueryParser.parse(query);
            List<SearchResult> results = LuceneIndexer.search(parsed, maxResults);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            // Cache the results
            cache.put(query, results);

            // Notify listeners
            EventBus.publish(new EventBus.SearchCompleteEvent(results, query, elapsedMs));

            return results;
        }, searchExecutor);
    }

    /**
     * Executes a synchronous (blocking) search.
     * Useful for non‑UI threads or batch operations.
     * Also checks the cache before performing an actual search.
     *
     * @param query      the natural language search query
     * @param maxResults maximum number of results to return
     * @return the list of SearchResult objects
     */
    public List<SearchResult> searchSync(String query, int maxResults) {
        if (query == null || query.isBlank()) return List.of();

        List<SearchResult> cached = cache.get(query);
        if (cached != null) return cached;

        NLQueryParser.ParsedQuery parsed = NLQueryParser.parse(query);
        List<SearchResult> results = LuceneIndexer.search(parsed, maxResults);
        cache.put(query, results);
        return results;
    }

    /**
     * Provides autocomplete suggestions based on a prefix.
     * The suggestions are derived from cached results and are returned quickly (<20ms).
     *
     * @param prefix the prefix to match against (minimum length 2)
     * @return a list of suggested filenames, or an empty list if none found
     */
    public List<String> autocomplete(String prefix) {
        if (prefix == null || prefix.length() < 2) return List.of();
        return cache.getSuggestions(prefix);
    }

    /**
     * Clears the entire search cache (both result and suggestion caches).
     * Should be called when the index is updated or when new files are added/removed.
     */
    public void clearCache() {
        cache.clear();
    }
}