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

    private EventBus() {} // Utility class

    private static final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    /**
     * Register a listener for a specific event type.
     */
    public static <T> void register(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                 .add(listener);
    }

    /**
     * Unregister a listener.
     */
    public static <T> void unregister(Class<T> eventType, Consumer<T> listener) {
        List<Consumer<?>> list = listeners.get(eventType);
        if (list != null) list.remove(listener);
    }

    /**
     * Publish an event to all registered listeners.
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

    /** Published when a search completes */
    public record SearchCompleteEvent(List<?> results, String query, long elapsedMs) {}

    /** Published when theme changes */
    public record ThemeChangedEvent(boolean isDark) {}

    /** Published when indexing progress updates */
    public record IndexProgressEvent(int indexed, int total, String currentFile) {}

    /** Published when application is shutting down */
    public record ShutdownEvent() {}
}
