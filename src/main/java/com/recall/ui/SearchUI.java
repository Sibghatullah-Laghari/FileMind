package com.recall.ui;

import com.recall.core.ActivityHistory;
import com.recall.core.LuceneIndexer;
import com.recall.core.MetadataDB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class SearchUI extends JFrame {
    private JTextField searchField;
    private DefaultListModel<ResultItem> listModel;
    private JList<ResultItem> resultList;
    private JLabel statusLabel;
    private JLabel fileCountLabel;

    public SearchUI() {
        setTitle("FileMind – Instant Search");
        setSize(750, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // --- Main Panel ---
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // --- Search Bar ---
        searchField = new JTextField();
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        searchField.setPreferredSize(new Dimension(0, 45));
        searchField.setToolTipText("Type anything to search... (Ctrl+Space to toggle)");
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                performSearch();
            }
        });

        // --- Results List (Custom Renderer) ---
        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setCellRenderer(new ResultRenderer());
        resultList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        resultList.setFixedCellHeight(40);
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    ResultItem item = resultList.getSelectedValue();
                    if (item != null) {
                        File f = new File(item.fullPath);
                        try {
                            // Record activity
                            ActivityHistory.recordOpen(MetadataDB.getConnection(), item.fullPath);
                            Desktop.getDesktop().open(f);
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(SearchUI.this,
                                    "Cannot open file: " + ex.getMessage(),
                                    "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        // --- Bottom Status Bar ---
        JPanel statusPanel = new JPanel(new BorderLayout(10, 0));
        statusPanel.setBorder(new EmptyBorder(8, 5, 5, 5));
        statusLabel = new JLabel("Loading...");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(80, 80, 80));

        fileCountLabel = new JLabel("0 files");
        fileCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        fileCountLabel.setForeground(new Color(0, 120, 215));

        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(fileCountLabel, BorderLayout.EAST);

        // --- Assemble UI ---
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        JLabel hint = new JLabel(" 🔍  Search files, PDFs, Word, Code...");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        hint.setForeground(Color.GRAY);
        topPanel.add(searchField, BorderLayout.CENTER);
        topPanel.add(hint, BorderLayout.SOUTH);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // --- Global Hotkey: Ctrl+Space ---
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (e.getID() == KeyEvent.KEY_PRESSED &&
                            e.isControlDown() && e.getKeyCode() == KeyEvent.VK_SPACE) {
                        toggleVisibility();
                        return true;
                    }
                    return false;
                });

        // --- Tray Setup ---
        setupTray();

        // --- Focus search on open ---
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                searchField.requestFocusInWindow();
            }
        });
    }

    // --- Perform Search ---
    private void performSearch() {
        String query = searchField.getText().trim();
        listModel.clear();
        if (query.isEmpty()) {
            updateStatus("Ready");
            return;
        }

        // Optionally use NLQueryParser here later (Phase 5), but for now just pass query directly
        List<String> results = LuceneIndexer.search(query);

        if (results.isEmpty()) {
            listModel.addElement(new ResultItem("No results found.", "", true));
        } else {
            for (String path : results) {
                File f = new File(path);
                listModel.addElement(new ResultItem(f.getName(), f.getAbsolutePath(), false));
            }
        }
        updateStatus("Found " + results.size() + " results");
    }

    // --- Status updater ---
    public void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(msg);
            try {
                int count = LuceneIndexer.getDocumentCount();
                fileCountLabel.setText(count + " files indexed");
            } catch (java.io.IOException e) {
                fileCountLabel.setText("? files indexed");
            }
        });
    }

    private void updateStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    // --- Toggle visibility ---
    private void toggleVisibility() {
        SwingUtilities.invokeLater(() -> {
            if (isVisible()) {
                setVisible(false);
                searchField.setText("");
                listModel.clear();
            } else {
                setVisible(true);
                toFront();
                searchField.requestFocusInWindow();
                performSearch(); // refresh results if any text
            }
        });
    }

    // --- System Tray ---
    private void setupTray() {
        if (!SystemTray.isSupported()) return;
        try {
            Image icon = Toolkit.getDefaultToolkit().createImage(
                    getClass().getResource("/tray-icon.png")
            );
            if (icon == null) {
                icon = createFallbackIcon();
            }

            TrayIcon trayIcon = new TrayIcon(icon, "FileMind");
            trayIcon.setImageAutoSize(true);

            PopupMenu popup = new PopupMenu();
            MenuItem showItem = new MenuItem("Show/Hide");
            showItem.addActionListener(e -> toggleVisibility());
            popup.add(showItem);

            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> {
                com.recall.Main.shutdown();
                System.exit(0);
            });
            popup.add(exitItem);

            trayIcon.setPopupMenu(popup);
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) toggleVisibility();
                }
            });

            SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception e) {
            System.err.println("Tray unavailable: " + e.getMessage());
        }
    }

    private Image createFallbackIcon() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0, 120, 215));
        g.fillRect(0, 0, 16, 16);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.drawString("F", 3, 13);
        g.dispose();
        return img;
    }

    // --- Helper class for results ---
    public static class ResultItem {
        String displayName;
        String fullPath;
        boolean isPlaceholder;

        ResultItem(String name, String path, boolean placeholder) {
            this.displayName = name;
            this.fullPath = path;
            this.isPlaceholder = placeholder;
        }
    }

    // --- Custom Renderer ---
    private static class ResultRenderer extends JPanel implements ListCellRenderer<ResultItem> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel pathLabel = new JLabel();

        ResultRenderer() {
            setLayout(new BorderLayout(5, 0));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            pathLabel.setForeground(Color.GRAY);
            add(nameLabel, BorderLayout.NORTH);
            add(pathLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ResultItem> list,
                                                      ResultItem value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            if (value.isPlaceholder) {
                nameLabel.setText(value.displayName);
                pathLabel.setText("");
                nameLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
                nameLabel.setForeground(Color.GRAY);
            } else {
                nameLabel.setText(value.displayName);
                pathLabel.setText(value.fullPath);
                nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
                nameLabel.setForeground(Color.BLACK);
            }

            if (isSelected) {
                setBackground(new Color(0, 120, 215));
                nameLabel.setForeground(Color.WHITE);
                pathLabel.setForeground(new Color(230, 230, 255));
            } else {
                setBackground(index % 2 == 0 ? new Color(248, 248, 248) : Color.WHITE);
                if (!value.isPlaceholder) {
                    nameLabel.setForeground(Color.BLACK);
                    pathLabel.setForeground(Color.GRAY);
                }
            }
            return this;
        }
    }
}