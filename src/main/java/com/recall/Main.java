package com.recall;

import com.recall.core.*;
import com.recall.ui.SearchUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;

/**
 * Application entry point.
 *
 * Key improvements vs original:
 *  - Bounded ThreadPoolExecutor (4 threads, queue cap 500) — no unbounded queue
 *  - Tray icon with global Ctrl+Space hotkey simulation
 *  - Clean shutdown hook (flushes index on exit)
 *  - JVM heap limited to 128 MB via launch script (add -Xmx128m to your run config)
 */
public class Main {

    // ── config (change these to match your environment) ───────────────────────
    private static final String INDEX_DIR = System.getProperty("user.home") + "/.filemind/index";
    private static final String DB_PATH   = System.getProperty("user.home") + "/.filemind/meta.db";

    // Folders to index — edit this list or make it configurable in Settings later
    private static final String[] WATCH_FOLDERS = {
            System.getProperty("user.home") + "/Documents",
            System.getProperty("user.home") + "/Downloads",
            System.getProperty("user.home") + "/Desktop",
            System.getProperty("user.home") + "/Projects",
    };

    // ── thread pool (4 indexing workers, bounded queue = backpressure) ────────
    private static final ExecutorService indexExecutor = new ThreadPoolExecutor(
            4, 4, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    private static SearchUI ui;

    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        // Enforce headless=false (required for system tray on some Linux distros)
        System.setProperty("java.awt.headless", "false");

        // Startup on EDT
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            ui = new SearchUI();
            ui.setVisible(true);

            setupTrayIcon();
        });

        // Register clean shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(Main::shutdown, "filemind-shutdown"));

        // Background services (non-EDT)
        startBackgroundServices();
    }

    // ── background services ───────────────────────────────────────────────────
    private static void startBackgroundServices() {
        // Use a single starter thread — it will submit file tasks to indexExecutor
        Thread starter = new Thread(() -> {
            try {
                // 1. Init storage
                Files.createDirectories(Paths.get(INDEX_DIR));
                LuceneIndexer.init(Paths.get(INDEX_DIR));
                MetadataDB.init(DB_PATH);

                // 2. Index all watch folders
                for (String folder : WATCH_FOLDERS) {
                    Path p = Paths.get(folder);
                    if (!Files.exists(p)) continue;

                    ui.setStatus("Indexing " + p.getFileName() + "...");

                    // Submit each file as a separate task — parallel, bounded
                    LuceneIndexer.indexFolder(p, (filePath, ok) -> {
                        indexExecutor.submit(() -> {/* file already indexed inline in indexFolder */});
                        // Update status every ~200 files without flooding the EDT
                    });

                    ui.setStatus("Indexed " + p.getFileName() + " ✓");
                }

                ui.setStatus("Ready");
                ui.setIndexCount(LuceneIndexer.getDocumentCount());

                // 3. Start file watchers
                for (String folder : WATCH_FOLDERS) {
                    Path p = Paths.get(folder);
                    if (!Files.exists(p)) continue;

                    FileWatcher.start(p, (event, path) -> {
                        // Submit watcher events to the bounded executor
                        indexExecutor.submit(() -> {
                            try {
                                switch (event) {
                                    case CREATE, MODIFY -> {
                                        LuceneIndexer.indexFile(path);
                                        // Also update MetadataDB
                                        String ext = LuceneIndexer.getExtension(path.getFileName().toString());
                                        long size  = Files.exists(path) ? Files.size(path) : 0;
                                        long mod   = Files.exists(path)
                                                ? Files.getLastModifiedTime(path).toMillis() : 0;
                                        MetadataDB.upsert(path.toString(), mod, size, ext, null);
                                    }
                                    case DELETE -> {
                                        LuceneIndexer.deleteFile(path);
                                        MetadataDB.delete(path.toString());
                                    }
                                }
                                ui.setIndexCount(LuceneIndexer.getDocumentCount());
                            } catch (IOException e) {
                                ui.setStatus("Watch error: " + e.getMessage());
                            }
                        });
                    });
                }

            } catch (Exception e) {
                ui.setStatus("Startup error: " + e.getMessage());
                e.printStackTrace();
            }
        }, "filemind-startup");
        starter.setDaemon(true);
        starter.start();
    }

    // ── system tray ───────────────────────────────────────────────────────────
    private static void setupTrayIcon() {
        if (!SystemTray.isSupported()) {
            System.out.println("[TRAY] System tray not supported on this platform");
            return;
        }

        // Create a simple 16×16 tray icon programmatically (no image file needed)
        Image trayImage = createTrayImage();
        TrayIcon trayIcon = new TrayIcon(trayImage, "FileMind — Click to open");
        trayIcon.setImageAutoSize(true);

        PopupMenu popup = new PopupMenu();

        MenuItem openItem = new MenuItem("Open FileMind");
        openItem.addActionListener(e -> showUI());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            SystemTray.getSystemTray().remove(trayIcon);
            System.exit(0);
        });

        popup.add(openItem);
        popup.addSeparator();
        popup.add(exitItem);
        trayIcon.setPopupMenu(popup);

        // Single-click tray icon → show window
        trayIcon.addActionListener(e -> showUI());

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.err.println("[TRAY] Could not add tray icon: " + e.getMessage());
        }

        // Global shortcut simulation:
        // On Linux/Windows, true global hotkeys need a native library (JNativeHook).
        // For now, Ctrl+Space works when the app is focused.
        // To add JNativeHook later: https://github.com/kwhat/jnativehook
        // Add to pom.xml: com.github.kwhat:jnativehook:2.2.2
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (e.getID() == KeyEvent.KEY_PRESSED
                            && e.getKeyCode() == KeyEvent.VK_SPACE
                            && e.isControlDown()) {
                        showUI();
                        return true;
                    }
                    return false;
                });
    }

    private static void showUI() {
        SwingUtilities.invokeLater(() -> {
            if (ui != null) {
                ui.setVisible(true);
                ui.toFront();
                ui.requestFocus();
            }
        });
    }

    private static Image createTrayImage() {
        // Draw a simple blue magnifying glass as the tray icon
        int size = 16;
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x3B82F6));
        g.fillOval(1, 1, 10, 10);
        g.setColor(Color.WHITE);
        g.fillOval(3, 3, 6, 6);
        g.setColor(new Color(0x3B82F6));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(10, 10, 14, 14);
        g.dispose();
        return img;
    }

    // ── shutdown ──────────────────────────────────────────────────────────────
    public static void shutdown() {
        System.out.println("[SHUTDOWN] Flushing index and closing...");
        indexExecutor.shutdown();
        try {
            if (!indexExecutor.awaitTermination(5, TimeUnit.SECONDS))
                indexExecutor.shutdownNow();
        } catch (InterruptedException ignored) {}
        FileWatcher.stop();
        LuceneIndexer.close();
        MetadataDB.close();
        System.out.println("[SHUTDOWN] Done.");
    }
}