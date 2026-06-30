package com.recall;

import com.recall.core.FileWatcher;
import com.recall.core.LuceneIndexer;
import com.recall.core.MetadataDB;
import com.recall.ui.SearchUI;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    // Background thread pool for indexing (keeps UI snappy)
    private static final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private static final String INDEX_DIR = System.getProperty("user.home") + "/.recall-search-index";
    private static final String DB_PATH = System.getProperty("user.home") + "/.recall-search.db";

    public static void main(String[] args) {
        // Force headless check for Linux tray
        System.setProperty("java.awt.headless", "false");

        // Start Swing UI on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            SearchUI ui = new SearchUI();
            ui.setVisible(true);

            // Start background indexing and file watcher
            startBackgroundServices(ui);
        });
    }

    private static void startBackgroundServices(SearchUI ui) {
        backgroundExecutor.submit(() -> {
            try {
                // 1. Init Lucene index
                LuceneIndexer.init(Paths.get(INDEX_DIR));

                // 2. Init SQLite metadata
                MetadataDB.init(DB_PATH);

                // 3. Index the user's home folder (or change this to your dummy-data path)
                // !! CHANGE THIS TO YOUR DUMMY DATA FOLDER !!
                // 3. Index your specific dummy-data folder
                Path targetFolder = Paths.get("/home/sibghatullah/dummy-data");
                // If dummy-data doesn't exist, fallback to Documents
                if (!targetFolder.toFile().exists()) {
                    targetFolder = Paths.get(System.getProperty("user.home"), "Documents");
                }

                ui.setStatus("Indexing folder: " + targetFolder);

                // Full scan & index (runs in background, UI stays responsive)
                LuceneIndexer.indexFolder(targetFolder, (filePath, success) -> {
                    // Update UI status for each file (throttled to avoid flooding)
                    ui.setStatus("Indexed: " + filePath.getFileName());
                });

                ui.setStatus("Ready. " + LuceneIndexer.getDocumentCount() + " files indexed.");

                // 4. Start File Watcher for live updates
                FileWatcher.start(targetFolder, (event, path) -> {
                    try {
                        switch (event) {
                            case CREATE, MODIFY -> {
                                ui.setStatus("Indexing change: " + path.getFileName());
                                LuceneIndexer.indexFile(path);
                            }
                            case DELETE -> {
                                ui.setStatus("Removing: " + path.getFileName());
                                LuceneIndexer.deleteFile(path);
                            }
                        }
                        ui.setStatus("Ready. " + LuceneIndexer.getDocumentCount() + " files indexed.");
                    } catch (IOException e) {
                        ui.setStatus("Watcher error: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                ui.setStatus("ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // Clean shutdown
    public static void shutdown() {
        backgroundExecutor.shutdown();
        LuceneIndexer.close();
        FileWatcher.stop();
    }
}