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

    public enum EventType { CREATE, MODIFY, DELETE }

    private static WatchService watchService;
    private static Thread       watchThread;
    private static volatile boolean running = false;

    // Bounded queue: cap at 200 events to prevent memory spike on mass file creation
    private static final ThreadPoolExecutor eventExecutor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(200),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    // Directories to skip entirely (build caches, version control, etc.)
    private static final java.util.Set<String> SKIP_DIRS = java.util.Set.of(
            "node_modules", ".git", ".svn", "target", "build", ".gradle",
            "__pycache__", ".idea", ".vscode", ".cache", "dist", "out"
    );

    public static void start(Path rootPath, BiConsumer<EventType, Path> callback) throws IOException {
        if (running) return;

        watchService = FileSystems.getDefault().newWatchService();
        registerTree(rootPath); // Recursively register all existing subdirectories

        running = true;
        watchThread = new Thread(() -> {
            while (running) {
                WatchKey key;
                try {
                    key = watchService.take(); // blocks — zero CPU while idle
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    break; // Shutdown requested
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) continue; // Ignore lost-event notifications

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

                    // Submit to bounded executor – older events dropped if queue full
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
     * Recursively registers the given directory and all its subdirectories
     * (excluding SKIP_DIRS and hidden folders).
     */
    private static void registerTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (SKIP_DIRS.contains(name) || name.startsWith("."))
                    return FileVisitResult.SKIP_SUBTREE; // Don't traverse into ignored dirs
                try {
                    dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                } catch (IOException ignored) {
                    // Can't register — skip silently (permission denied, etc.)
                }
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE; // Ignore unreadable files
            }
        });
    }

    /** Gracefully stops the watcher, drains pending events, and closes resources. */
    public static void stop() {
        running = false;
        if (watchThread != null) watchThread.interrupt();

        eventExecutor.shutdown();
        try {
            if (!eventExecutor.awaitTermination(2, TimeUnit.SECONDS))
                eventExecutor.shutdownNow(); // Force shutdown if timeout
        } catch (InterruptedException ignored) {}

        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {}
    }
}
