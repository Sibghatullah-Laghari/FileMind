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

    private static final SearchService INSTANCE = new SearchService();
    private final SearchCache cache;
    // Dedicated executor prevents search from competing with ForkJoinPool / indexing threads
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "filemind-search");
        t.setDaemon(true);
        return t;
    });

    private SearchService() {
        this.cache = new SearchCache();
    }

    public static SearchService getInstance() {
        return INSTANCE;
    }

    /**
     * Execute an asynchronous search. Returns immediately with a CompletableFuture.
     * Publishes SearchCompleteEvent on completion.
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
     * Synchronous search (for non-UI threads or batch operations).
     * Blocks calling thread.
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
     * Get autocomplete suggestions. Must return in <20ms.
     * Uses a lightweight prefix cache.
     */
    public List<String> autocomplete(String prefix) {
        if (prefix == null || prefix.length() < 2) return List.of();
        return cache.getSuggestions(prefix);
    }

    /**
     * Clear the search cache (e.g., when new files are indexed).
     */
    public void clearCache() {
        cache.clear();
    }
}
