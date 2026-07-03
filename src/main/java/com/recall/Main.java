package com.recall;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatLaf;
import com.recall.core.*;
import com.recall.ui.*;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;

/**
 * Application entry point.
 *
 * Updated to use FloatingLauncher + SearchPalette (no dim layer).
 * FloatingLauncher expands into SearchPalette with spring animation.
 *
 * FIXME: The application uses both MetadataDB and LuceneIndexer but MetadataDB
 *        is only used for activity history (the files table is redundant).
 *        See core module issues for more details.
 *
 * FIXME: The startup sequence blocks the EDT while indexing large folders.
 *        indexFolder() runs on the startup thread, not in the background,
 *        causing UI freezes until initial indexing completes.
 *
 * FIXME: The indexExecutor uses DiscardOldestPolicy which can drop indexing
 *        events if the queue is full, leading to missed updates.
 *
 * FIXME: The shutdown hook does not guarantee that all indexing tasks complete
 *        before LuceneIndexer.close() is called, risking index corruption.
 *
 * FIXME: System tray icon creation uses a manually drawn image; better to use
 *        an actual icon resource for clarity.
 */
public class Main {

    // ── config ─────────────────────────────────────────────────────────────
    private static final String INDEX_DIR = System.getProperty("user.home") + "/.filemind/index";
    private static final String DB_PATH   = System.getProperty("user.home") + "/.filemind/meta.db";

    // Folders to index – FIXME: These are hardcoded and not user-configurable.
    // FIXME: Should be read from settings or allow user to add/remove folders.
    private static final String[] WATCH_FOLDERS = {
            System.getProperty("user.home") + "/Documents",
            System.getProperty("user.home") + "/Downloads",
            System.getProperty("user.home") + "/Desktop",
            System.getProperty("user.home") + "/Projects",
    };

    // ── thread pool ────────────────────────────────────────────────────────
    /**
     * Thread pool for handling file system events (index updates).
     * Core size 4, bounded queue with DiscardOldestPolicy.
     * FIXME: DiscardOldestPolicy may silently drop important events;
     *        consider using a rejection handler that logs or retries.
     */
    private static final ExecutorService indexExecutor = new ThreadPoolExecutor(
            4, 4, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    // ── new UI components ──────────────────────────────────────────────────
    private static FloatingLauncher floatingLauncher;
    private static SearchPalette searchPalette;
    private static PreviewPanel previewPanel;
    private static HotkeyManager hotkeyManager;

    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "false");

        SwingUtilities.invokeAndWait(() -> {
            // Initialize FlatLaf with dark theme (matches our design system)
            FlatDarkLaf.setup();
            // Enable window decorations for consistent title bar theming
            // (disabled for our JWindow-based floating UI)
            // FlatLaf.setGlobalExtraDefaults( Map.of( "@accentColor", "#3b82f6" ) );
            UIManager.put("Component.arrowType", "triangle");
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.focusWidth", 2);
            UIManager.put("ScrollBar.thumbArc", 8);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));

            // Initialize new SearchPalette (singleton)
            searchPalette = SearchPalette.getInstance();

            // Initialize PreviewPanel
            previewPanel = new PreviewPanel();
            searchPalette.setPreviewPanel(previewPanel);

            // Initialize FloatingLauncher with expand callback
            floatingLauncher = FloatingLauncher.createAndShow();
            floatingLauncher.setOnExpandCallback(() -> {
                if (searchPalette.isOpen()) {
                    searchPalette.close();
                } else {
                    // Expand from launcher position with spring animation
                    Rectangle launcherBounds = floatingLauncher.getLauncherBounds();
                    searchPalette.setFloatingLauncher(floatingLauncher);
                    searchPalette.openFromLauncher(launcherBounds);
                }
            });

            setupTrayIcon();

            // Initialize global hotkey
            hotkeyManager = HotkeyManager.init();
        });

        // Register clean shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(Main::shutdown, "filemind-shutdown"));

        // Background services
        startBackgroundServices();
    }

    // ── background services ───────────────────────────────────────────────────
    /**
     * Starts background indexing and file watching.
     * This method runs on a separate thread but blocks while indexing,
     * causing a delay before FileWatcher starts.
     *
     * FIXME: The initial indexing is done sequentially and blocks the startup
     *        thread. Should index in the background and start watching immediately.
     *
     * FIXME: Indexing each folder separately with indexFolder() may lead to
     *        redundant work; should use a single indexFolder() on the root.
     */
    private static void startBackgroundServices() {
        Thread starter = new Thread(() -> {
            try {
                Files.createDirectories(Paths.get(INDEX_DIR));
                LuceneIndexer.init(Paths.get(INDEX_DIR));
                MetadataDB.init(DB_PATH);

                for (String folder : WATCH_FOLDERS) {
                    Path p = Paths.get(folder);
                    if (!Files.exists(p)) continue;
                    System.out.println("[INDEX] Indexing " + p.getFileName() + "...");
                    LuceneIndexer.indexFolder(p, (filePath, ok) -> {});
                    System.out.println("[INDEX] Indexed " + p.getFileName() + " \u2713");
                }

                System.out.println("[INDEX] Ready. Total documents: " + LuceneIndexer.getDocumentCount());

                for (String folder : WATCH_FOLDERS) {
                    Path p = Paths.get(folder);
                    if (!Files.exists(p)) continue;
                    FileWatcher.start(p, (event, path) -> {
                        indexExecutor.submit(() -> {
                            try {
                                switch (event) {
                                    case CREATE, MODIFY -> {
                                        LuceneIndexer.indexFile(path);
                                        // FIXME: This updates MetadataDB.files table, but that table is redundant.
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
                            } catch (IOException e) {
                                System.err.println("[WATCH] Error: " + e.getMessage());
                            }
                        });
                    });
                }

            } catch (Exception e) {
                System.err.println("[STARTUP] Error: " + e.getMessage());
                e.printStackTrace();
            }
        }, "filemind-startup");
        starter.setDaemon(true);
        starter.start();
    }

    // ── system tray ───────────────────────────────────────────────────────────
    /**
     * Sets up the system tray icon with popup menu.
     * FIXME: The tray icon is added even if the user doesn't want it;
     *        should be optional and configurable.
     * FIXME: The tray image is manually drawn; it's small and may not look
     *        good on high-DPI screens.
     */
    private static void setupTrayIcon() {
        if (!SystemTray.isSupported()) {
            System.out.println("[TRAY] System tray not supported on this platform");
            return;
        }

        Image trayImage = createTrayImage();
        TrayIcon trayIcon = new TrayIcon(trayImage, "FileMind \u2014 Click to open");
        trayIcon.setImageAutoSize(true);

        PopupMenu popup = new PopupMenu();

        MenuItem openItem = new MenuItem("Open FileMind");
        openItem.addActionListener(e -> toggleSearchPalette());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            SystemTray.getSystemTray().remove(trayIcon);
            System.exit(0); // FIXME: Abrupt exit, should call shutdown() gracefully.
        });

        popup.add(openItem);
        popup.addSeparator();
        popup.add(exitItem);
        trayIcon.setPopupMenu(popup);

        trayIcon.addActionListener(e -> toggleSearchPalette());

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.err.println("[TRAY] Could not add tray icon: " + e.getMessage());
        }
    }

    /**
     * Toggles the search palette open/close.
     * FIXME: This assumes searchPalette is not null – but may be if initialization fails.
     */
    private static void toggleSearchPalette() {
        SwingUtilities.invokeLater(() -> {
            if (searchPalette.isOpen()) {
                searchPalette.close();
            } else {
                searchPalette.open();
            }
        });
    }

    /**
     * Creates a simple magnifying glass icon for the system tray.
     * FIXME: Should use a pre‑made image or SVG for better quality.
     */
    private static Image createTrayImage() {
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

    // ── shutdown ──────────────────────────────────────────────────────────
    /**
     * Gracefully shuts down all services: hotkey, animations, indexer, file watcher.
     * FIXME: This method may be called from a shutdown hook, but the hook may
     *        not have enough time to complete; should ensure all tasks finish.
     */
    public static void shutdown() {
        System.out.println("[SHUTDOWN] Cleaning up...");

        if (hotkeyManager != null) {
            hotkeyManager.unregister();
        }

        // Stop floating launcher animations to prevent EDT ghosts
        if (floatingLauncher != null) {
            floatingLauncher.stopTimers();
        }

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