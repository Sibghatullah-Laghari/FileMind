package com.recall.core;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Watches file system changes and triggers incremental index updates.
 *
 * Fixes vs original:
 *  - New directories are fully registered recursively (not just top-level)
 *  - Backpressure: event queue capped at 200, oldest dropped when full
 *  - Hidden files and build dirs filtered before submitting
 *  - Clean shutdown with timeout
 */
public class FileWatcher {

    /**
     * Types of file system events that can be observed.
     */
    public enum EventType { CREATE, MODIFY, DELETE }

    /** The WatchService instance used for file system monitoring. */
    private static WatchService watchService;

    /** The background thread that processes watch events. */
    private static Thread       watchThread;

    /** Flag indicating whether the watcher is currently running. */
    private static volatile boolean running = false;

    /**
     * Thread pool for handling file events asynchronously.
     * - Core pool size: 2 threads
     * - Bounded queue (200) prevents memory spikes on mass file creation
     * - DiscardOldestPolicy drops the oldest pending event when the queue is full,
     *   prioritizing newer events
     */
    private static final ThreadPoolExecutor eventExecutor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(200),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    /**
     * Set of directory names to skip during recursive registration and event filtering.
     * These typically contain build artifacts, dependencies, or version control data.
     */
    private static final java.util.Set<String> SKIP_DIRS = java.util.Set.of(
            "node_modules", ".git", ".svn", "target", "build", ".gradle",
            "__pycache__", ".idea", ".vscode", ".cache", "dist", "out"
    );

    /**
     * Starts the file watcher for the given root path.
     * Registers the entire directory tree recursively and begins listening for events.
     *
     * @param rootPath the root directory to monitor
     * @param callback a BiConsumer invoked for each event, receiving the event type and the affected path
     * @throws IOException if the WatchService cannot be created or the root directory cannot be registered
     */
    public static void start(Path rootPath, BiConsumer<EventType, Path> callback) throws IOException {
        if (running) return;

        watchService = FileSystems.getDefault().newWatchService();
        registerTree(rootPath);

        running = true;
        watchThread = new Thread(() -> {
            while (running) {
                WatchKey key;
                try {
                    key = watchService.take(); // blocks — zero CPU while idle
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    Path filename = ((WatchEvent<Path>) event).context();
                    Path watchDir = (Path) key.watchable();
                    Path fullPath = watchDir.resolve(filename);

                    // Filter noise synchronously (cheap, before queueing)
                    String name = filename.toString();
                    if (name.startsWith(".") || name.endsWith("~") || name.endsWith(".tmp"))
                        continue;
                    if (SKIP_DIRS.contains(name))
                        continue;

                    // Submit the event for asynchronous processing
                    eventExecutor.submit(() -> {
                        try {
                            if (kind == ENTRY_CREATE) {
                                if (Files.isDirectory(fullPath)) {
                                    // Register full subtree of the new directory
                                    registerTree(fullPath);
                                }
                                callback.accept(EventType.CREATE, fullPath);

                            } else if (kind == ENTRY_MODIFY) {
                                // Only fire for regular files; ignore directory modify events
                                if (Files.isRegularFile(fullPath))
                                    callback.accept(EventType.MODIFY, fullPath);

                            } else if (kind == ENTRY_DELETE) {
                                callback.accept(EventType.DELETE, fullPath);
                            }
                        } catch (Exception e) {
                            System.err.println("[WATCHER EVENT] " + e.getMessage());
                        }
                    });
                }

                if (!key.reset()) break; // directory was deleted
            }
        }, "filemind-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /**
     * Recursively registers a directory and all its subdirectories with the WatchService.
     * Skips directories that are hidden or in the SKIP_DIRS list.
     *
     * @param root the directory to start registration from
     * @throws IOException if an I/O error occurs while traversing the tree
     */
    private static void registerTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (SKIP_DIRS.contains(name) || name.startsWith("."))
                    return FileVisitResult.SKIP_SUBTREE;
                try {
                    dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                } catch (IOException ignored) {
                    // Can't register — skip silently (permission denied, etc.)
                }
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Stops the file watcher gracefully.
     * Interrupts the watching thread, shuts down the event executor with a timeout,
     * and closes the WatchService.
     */
    public static void stop() {
        running = false;
        if (watchThread != null) watchThread.interrupt();
        eventExecutor.shutdown();
        try {
            if (!eventExecutor.awaitTermination(2, TimeUnit.SECONDS))
                eventExecutor.shutdownNow();
        } catch (InterruptedException ignored) {}
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {}
    }
}