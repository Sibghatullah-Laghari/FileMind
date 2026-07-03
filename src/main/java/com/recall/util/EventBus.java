package com.recall.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Lightweight typed event bus for decoupled component communication.
 * Eliminates manual updateTheme() calls and tight coupling.
 *
 * Usage:
 *   EventBus.register(SearchCompleteEvent.class, e -> updateResults(e.results()));
 *   EventBus.publish(new SearchCompleteEvent(results));
 *
 * Thread-safe. Listeners are invoked on the publishing thread.
 */
public final class EventBus {

    /** Private constructor to prevent instantiation of this utility class. */
    private EventBus() {} // Utility class

    /**
     * Thread‑safe map from event class to a list of registered listeners.
     * CopyOnWriteArrayList ensures safe iteration even during concurrent modification.
     */
    private static final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    /**
     * Registers a listener for a specific event type.
     * The listener will be invoked whenever an event of type {@code eventType} is published.
     *
     * @param eventType the class of the event to listen for
     * @param listener  the consumer that will handle the event
     * @param <T>       the event type
     */
    public static <T> void register(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    /**
     * Unregisters a previously registered listener.
     * Does nothing if the listener was not registered or the event type has no listeners.
     *
     * @param eventType the class of the event
     * @param listener  the consumer to remove
     * @param <T>       the event type
     */
    public static <T> void unregister(Class<T> eventType, Consumer<T> listener) {
        List<Consumer<?>> list = listeners.get(eventType);
        if (list != null) list.remove(listener);
    }

    /**
     * Publishes an event to all registered listeners for that event type.
     * Listeners are invoked synchronously on the calling thread.
     *
     * @param event the event object to publish
     * @param <T>   the event type
     */
    @SuppressWarnings("unchecked")
    public static <T> void publish(T event) {
        List<Consumer<?>> list = listeners.get(event.getClass());
        if (list != null) {
            for (Consumer<?> listener : list) {
                ((Consumer<T>) listener).accept(event);
            }
        }
    }

    // ── Event Types (inner classes for cohesion) ──────────────────────────

    /**
     * Published when a search completes successfully.
     * Contains the search results, the original query, and the elapsed time.
     *
     * @param results   the list of SearchResult objects (the actual type is List<?> for flexibility)
     * @param query     the query string that was executed
     * @param elapsedMs the time taken for the search in milliseconds
     */
    public record SearchCompleteEvent(List<?> results, String query, long elapsedMs) {}

    /**
     * Published when the application theme changes (dark/light mode).
     *
     * @param isDark {@code true} for dark theme, {@code false} for light theme
     */
    public record ThemeChangedEvent(boolean isDark) {}

    /**
     * Published to provide progress updates during indexing operations.
     *
     * @param indexed    number of files indexed so far
     * @param total      total number of files to index (or -1 if unknown)
     * @param currentFile the file currently being indexed (may be null)
     */
    public record IndexProgressEvent(int indexed, int total, String currentFile) {}

    /**
     * Published when the application is shutting down, allowing components to clean up.
     */
    public record ShutdownEvent() {}
}