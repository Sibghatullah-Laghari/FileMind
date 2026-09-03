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
 * Improvements:
 *  - Newly created directories are registered recursively
 *  - Event queue is capped at 200 items to provide backpressure
 *  - Graceful shutdown with a timeout for pending tasks
 */
public class FileWatcher {

    public enum EventType { CREATE, MODIFY, DELETE }

    private static WatchService watchService;
    private static Thread watchThread;
    private static volatile boolean running = false;

    // Bounded queue to prevent excessive memory usage during heavy file activity.
    private static final ThreadPoolExecutor eventExecutor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(200),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    // Directory names that should be ignored while watching the file system.
    private static final java.util.Set<String> SKIP_DIRS = java.util.Set.of(
            "node_modules", ".git", ".svn", "target", "build", ".gradle",
            "__pycache__", ".idea", ".vscode", ".cache", "dist", "out"
    );

    public static void start(Path rootPath, BiConsumer<EventType, Path> callback) throws IOException {
        if (running) return;

        watchService = FileSystems.getDefault().newWatchService();
        registerTree(rootPath); // Register the root directory and all existing subdirectories.

        running = true;
        watchThread = new Thread(() -> {
            while (running) {
                WatchKey key;
                try {
                    key = watchService.take(); // Blocks until a file system event is available.
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    break; // Shutdown requested.
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) continue; // Ignore overflow notifications.

                    @SuppressWarnings("unchecked")
                    Path filename = ((WatchEvent<Path>) event).context();
                    Path watchDir = (Path) key.watchable();
                    Path fullPath = watchDir.resolve(filename);

                    // Filter common temporary and hidden files before queuing.
                    String name = filename.toString();
                    if (name.startsWith(".") || name.endsWith("~") || name.endsWith(".tmp"))
                        continue;
                    if (SKIP_DIRS.contains(name))
                        continue;

                    // Submit work to the bounded executor.
                    eventExecutor.submit(() -> {
                        try {
                            if (kind == ENTRY_CREATE) {
                                if (Files.isDirectory(fullPath)) {
                                    // Register the complete subtree of the newly created directory.
                                    registerTree(fullPath);
                                }
                                callback.accept(EventType.CREATE, fullPath);

                            } else if (kind == ENTRY_MODIFY) {
                                // Process only regular files and ignore directory modification events.
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

                if (!key.reset()) break; // Stop watching if the directory is no longer available.
            }
        }, "filemind-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /**
     * Registers the specified directory and all of its subdirectories,
     * excluding hidden folders and directories listed in {@code SKIP_DIRS}.
     */
    private static void registerTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (SKIP_DIRS.contains(name) || name.startsWith("."))
                    return FileVisitResult.SKIP_SUBTREE; // Skip ignored directories.
                try {
                    dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                } catch (IOException ignored) {
                    // Ignore directories that cannot be registered.
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE; // Continue if a file cannot be accessed.
            }
        });
    }

    /**
     * Stops the watcher, waits briefly for queued events to finish,
     * and releases all associated resources.
     */
    public static void stop() {
        running = false;
        if (watchThread != null) watchThread.interrupt();

        eventExecutor.shutdown();
        try {
            if (!eventExecutor.awaitTermination(2, TimeUnit.SECONDS))
                eventExecutor.shutdownNow(); // Force shutdown after the timeout.
        } catch (InterruptedException ignored) {}

        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {}
    }
}
