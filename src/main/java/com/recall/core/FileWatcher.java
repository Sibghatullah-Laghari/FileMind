package com.recall.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import static java.nio.file.StandardWatchEventKinds.*;

public class FileWatcher {
    private static WatchService watchService;
    private static Thread watcherThread;
    private static volatile boolean running = false;
    private static final ExecutorService eventExecutor = Executors.newSingleThreadExecutor();

    public enum EventType { CREATE, MODIFY, DELETE }

    public static void start(Path rootPath, BiConsumer<EventType, Path> callback) throws IOException {
        if (running) return;

        watchService = FileSystems.getDefault().newWatchService();
        registerTree(rootPath, watchService);

        running = true;
        watcherThread = new Thread(() -> {
            while (running) {
                WatchKey key;
                try {
                    key = watchService.take(); // blocks
                } catch (InterruptedException e) {
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    Path filename = ((WatchEvent<Path>) event).context();
                    Path fullPath = ((Path) key.watchable()).resolve(filename);

                    // Skip hidden/temp files
                    if (filename.toString().startsWith(".") || filename.toString().endsWith("~")) continue;

                    // Submit event to separate thread so watcher doesn't block
                    eventExecutor.submit(() -> {
                        try {
                            if (kind == ENTRY_CREATE) {
                                if (Files.isDirectory(fullPath)) {
                                    // Register the whole new subtree, not just the top dir
                                    try {
                                        Files.walk(fullPath)
                                                .filter(Files::isDirectory)
                                                .forEach(dir -> {
                                                    try { dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE); }
                                                    catch (IOException ignored) {}
                                                });
                                    } catch (IOException ignored) {}
                                }
                                callback.accept(EventType.CREATE, fullPath);
                            }
                        } catch (Exception e) {
                            System.err.println("Watcher event error: " + e.getMessage());
                        }
                    });
                }

                boolean valid = key.reset();
                if (!valid) break;
            }
        });
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private static void registerTree(Path root, WatchService ws) throws IOException {
        Files.walk(root).filter(Files::isDirectory).forEach(dir -> {
            try {
                dir.register(ws, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
            } catch (IOException ignored) {}
        });
    }

    public static void stop() {
        running = false;
        if (watcherThread != null) watcherThread.interrupt();
        eventExecutor.shutdown();
        try {
            if (watchService != null) watchService.close();
        } catch (IOException ignored) {}
    }
}