package com.recall.ui;

import com.recall.core.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.util.List;
import java.util.*;

/**
 * Modern search panel combining macOS Spotlight with IntelliJ Shift+Shift style.
 * - Centered overlay with dimming
 * - Category tabs (All, Files, Folders, Code, PDF, Images, Recent)
 * - Keyboard-first navigation
 * - Instant search with debounce
 * - Dark/Light theme toggle
 * - Smart query chips from NLQueryParser
 * - Help overlay on ?
 * - Recent tab with ActivityHistory
 */
public class SearchPanel extends JWindow {

    // ── Constants ──────────────────────────────────────────────────────────
    private static final int PANEL_WIDTH = 680;
    private static final int MAX_HEIGHT = 520;
    private static final int SEARCH_BAR_HEIGHT = 56;
    private static final int TAB_BAR_HEIGHT = 34;
    private static final int HINT_HEIGHT = 22;
    private static final int CHIP_BAR_HEIGHT = 30;
    private static final int STATUS_HEIGHT = 26;
    private static final int RESULTS_MAX_HEIGHT = 440;

    // ── State ──────────────────────────────────────────────────────────────
    private DimLayer dimLayer;
    private JTextField searchField;
    private JPanel tabBar;
    private JPanel chipBar;
    private JList<ResultItem> resultsList;
    private ResultListModel resultsModel;
    private JScrollPane resultsScroll;
    private JLabel statusLabel;
    private JButton themeToggle;
    private JLabel nlHintLabel;
    private ResultRenderer resultRenderer;
    private HelpOverlayPanel helpOverlay;
    private KeyEventDispatcher helpDispatcher;

    private javax.swing.Timer searchDebounceTimer;
    private String activeCategory = "All";
    private List<SearchResult> currentResults = List.of();
    private NLQueryParser.ParsedQuery lastParsedQuery;

    // ── Category Info ──────────────────────────────────────────────────────
    private static final String[] CATEGORIES = {"All", "Files", "Folders", "Code", "PDF", "Images", "Recent"};

    // Chip colors
    private static final Color CHIP_TYPE_BLUE = new Color(0x3b82f6);
    private static final Color CHIP_DATE_AMBER = new Color(0xd97706);
    private static final Color CHIP_KEYWORD_SLATE = new Color(0x64748b);

    // ─────────────────────────────────────────────────────────────────────
    public SearchPanel() {
        super();

        // Setup window
        setSize(PANEL_WIDTH, MAX_HEIGHT);
        centerWindow();

        // Setup appearance
        setOpacity(0.0f);
        getContentPane().setBackground(ThemeManager.getPanelBg());

        // Main container
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(ThemeManager.getPanelBg());
        mainPanel.setBorder(new LineBorder(ThemeManager.getPanelBorder(), 1));

        // Build sections
        mainPanel.add(buildSearchBar());
        mainPanel.add(buildTabBar());
        nlHintLabel = buildNLHint();
        mainPanel.add(nlHintLabel);
        mainPanel.add(buildChipBar());
        mainPanel.add(buildResultsArea());
        mainPanel.add(buildStatusBar());

        setContentPane(mainPanel);
        setType(Type.UTILITY);

        // Setup keyboard handling
        setupKeyboardHandling();

        // Create dim layer
        dimLayer = new DimLayer(this);

        // Help overlay
        helpOverlay = new HelpOverlayPanel();
    }

    // ── Build Search Bar ───────────────────────────────────────────────────
    private JPanel buildSearchBar() {
        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.setPreferredSize(new Dimension(PANEL_WIDTH, SEARCH_BAR_HEIGHT));
        searchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, SEARCH_BAR_HEIGHT));
        searchBar.setBackground(ThemeManager.getPanelBg());
        searchBar.setBorder(new EmptyBorder(8, 12, 8, 12));

        // Left: Icon
        JLabel iconLabel = new JLabel("\uD83D\uDD0D");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        iconLabel.setForeground(ThemeManager.getTextHint());
        iconLabel.setPreferredSize(new Dimension(28, 40));
        searchBar.add(iconLabel, BorderLayout.WEST);

        // Center: Search field
        searchField = new JTextField();
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 17));
        searchField.setBackground(ThemeManager.getSearchBg());
        searchField.setForeground(ThemeManager.getSearchText());
        searchField.setCaretColor(ThemeManager.getAccent());
        searchField.setBorder(null);
        searchField.setOpaque(true);

        // Placeholder
        searchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (searchField.getText().isEmpty()) {
                    searchField.setForeground(ThemeManager.getSearchText());
                }
            }
        });

        // Live search with debounce
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                scheduleSearch();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                scheduleSearch();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                scheduleSearch();
            }
        });

        searchBar.add(searchField, BorderLayout.CENTER);

        // Right: Theme toggle + Esc hint
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.setBackground(ThemeManager.getPanelBg());

        themeToggle = new JButton(ThemeManager.isDark() ? "\u2600\uFE0F" : "\uD83C\uDF19");
        themeToggle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        themeToggle.setBorder(null);
        themeToggle.setContentAreaFilled(false);
        themeToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        themeToggle.addActionListener(e -> {
            ThemeManager.toggleTheme();
            updateTheme();
        });
        rightPanel.add(themeToggle);

        JLabel escHint = new JLabel("esc");
        escHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        escHint.setForeground(ThemeManager.getTextHint());
        rightPanel.add(escHint);

        searchBar.add(rightPanel, BorderLayout.EAST);

        return searchBar;
    }

    // ── Build Tab Bar ──────────────────────────────────────────────────────
    private JPanel buildTabBar() {
        tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        tabBar.setPreferredSize(new Dimension(PANEL_WIDTH, TAB_BAR_HEIGHT));
        tabBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, TAB_BAR_HEIGHT));
        tabBar.setBackground(ThemeManager.getPanelBg());
        tabBar.setBorder(new EmptyBorder(0, 12, 0, 12));

        for (int i = 0; i < CATEGORIES.length; i++) {
            String cat = CATEGORIES[i];
            JButton tabBtn = createTabButton(cat, i);
            tabBar.add(tabBtn);
        }

        return tabBar;
    }

    private JButton createTabButton(String category, int index) {
        JButton btn = new JButton(category);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setForeground(ThemeManager.getTextHint());
        btn.setBackground(ThemeManager.getPanelBg());
        btn.setBorder(null);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFocusable(false);

        // Active tab styling
        if (category.equals(activeCategory)) {
            btn.setForeground(ThemeManager.getAccent());
        }

        btn.addActionListener(e -> switchCategory(category));

        // Keyboard shortcut Ctrl+1..7
        int keyCode = KeyEvent.VK_1 + index;
        KeyStroke ks = KeyStroke.getKeyStroke(keyCode, InputEvent.CTRL_DOWN_MASK);
        btn.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, "switchCat" + index);
        btn.getActionMap().put("switchCat" + index, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                switchCategory(category);
            }
        });

        return btn;
    }

    private void switchCategory(String category) {
        activeCategory = category;

        // Update tab styling
        for (Component comp : tabBar.getComponents()) {
            if (comp instanceof JButton btn) {
                String btnText = btn.getText();
                if (btnText.equals(category)) {
                    btn.setForeground(ThemeManager.getAccent());
                } else {
                    btn.setForeground(ThemeManager.getTextHint());
                }
            }
        }

        // If Recent tab, load history
        if ("Recent".equals(category)) {
            loadRecentHistory();
        } else {
            // Filter and display results
            displayResults(currentResults);
        }
    }

    // ── Recent History ─────────────────────────────────────────────────────
    private void loadRecentHistory() {
        SwingWorker<List<SearchResult>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SearchResult> doInBackground() {
                Connection conn = MetadataDB.getConnection();
                List<String> paths = ActivityHistory.recent(conn, 50);
                List<SearchResult> results = new ArrayList<>();
                for (String path : paths) {
                    File f = new File(path);
                    if (f.exists()) {
                        results.add(new SearchResult(
                                path,
                                f.getName(),
                                LuceneIndexer.getExtension(f.getName()),
                                f.length(),
                                f.lastModified(),
                                null, null, 0f
                        ));
                    }
                }
                return results;
            }

            @Override
            protected void done() {
                try {
                    currentResults = get();
                    displayResults(currentResults);
                } catch (Exception e) {
                    currentResults = List.of();
                    displayResults(List.of());
                }
            }
        };
        worker.execute();
    }

    // ── Build Chip Bar ─────────────────────────────────────────────────────
    private JPanel buildChipBar() {
        chipBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chipBar.setPreferredSize(new Dimension(PANEL_WIDTH, CHIP_BAR_HEIGHT));
        chipBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, CHIP_BAR_HEIGHT));
        chipBar.setBackground(ThemeManager.getPanelBg());
        chipBar.setBorder(new EmptyBorder(0, 12, 0, 12));
        chipBar.setVisible(false);
        return chipBar;
    }

    private void updateChips(NLQueryParser.ParsedQuery parsed) {
        chipBar.removeAll();

        if (parsed == null || parsed.luceneQuery() == null && parsed.fileType() == null
                && parsed.afterMs() == null && parsed.minSizeBytes() == null && !parsed.historyOnly()) {
            chipBar.setVisible(false);
            chipBar.revalidate();
            chipBar.repaint();
            return;
        }

        // File type chip
        if (parsed.fileType() != null) {
            chipBar.add(createChip(parsed.fileType(), CHIP_TYPE_BLUE, "type"));
        }

        // Date chip
        if (parsed.afterMs() != null) {
            String label = formatTimeAgo(parsed.afterMs());
            chipBar.add(createChip(label, CHIP_DATE_AMBER, "date"));
        }

        // Size chip
        if (parsed.minSizeBytes() != null || parsed.maxSizeBytes() != null) {
            String label = "";
            if (parsed.minSizeBytes() != null && parsed.maxSizeBytes() != null)
                label = parsed.minSizeBytes() / 1024 / 1024 + "-" + parsed.maxSizeBytes() / 1024 / 1024 + "MB";
            else if (parsed.minSizeBytes() != null)
                label = ">" + parsed.minSizeBytes() / 1024 / 1024 + "MB";
            else
                label = "<" + parsed.maxSizeBytes() / 1024 / 1024 + "MB";
            chipBar.add(createChip(label, CHIP_KEYWORD_SLATE, "size"));
        }

        // History chip
        if (parsed.historyOnly()) {
            chipBar.add(createChip("Recent", CHIP_DATE_AMBER, "history"));
        }

        // Keyword chips
        if (parsed.luceneQuery() != null && !parsed.luceneQuery().isBlank()) {
            String[] keywords = parsed.luceneQuery().split("\\s+");
            for (String kw : keywords) {
                if (kw.length() > 1) {
                    chipBar.add(createChip(kw, CHIP_KEYWORD_SLATE, "keyword"));
                }
            }
        }

        chipBar.setVisible(chipBar.getComponentCount() > 0);
        chipBar.revalidate();
        chipBar.repaint();
    }

    private JButton createChip(String label, Color bgColor, String tag) {
        JButton chip = new JButton(label + " \u2715");
        chip.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chip.setForeground(Color.WHITE);
        chip.setBackground(bgColor);
        chip.setOpaque(true);
        chip.setBorderPainted(false);
        chip.setFocusPainted(false);
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chip.setBorder(new EmptyBorder(2, 10, 2, 10));

        // Rounded pill shape
        chip.putClientProperty("JButton.roundRect", true);

        // Remove chip on click
        chip.addActionListener(e -> {
            chipBar.remove(chip);
            if (chipBar.getComponentCount() == 0) {
                chipBar.setVisible(false);
                searchField.setText("");
                performSearch();
            } else {
                chipBar.revalidate();
                chipBar.repaint();
            }
        });

        // Compute preferred size for pill
        FontMetrics fm = chip.getFontMetrics(chip.getFont());
        int w = fm.stringWidth(chip.getText()) + 24;
        int h = 24;
        chip.setPreferredSize(new Dimension(w, h));
        chip.setMinimumSize(new Dimension(w, h));
        chip.setMaximumSize(new Dimension(w, h));

        return chip;
    }

    private String formatTimeAgo(long afterMs) {
        long diff = System.currentTimeMillis() - afterMs;
        long days = diff / 86_400_000;
        if (days == 0) return "Today";
        if (days == 1) return "Yesterday";
        return days + " days ago";
    }

    // ── Help Overlay ───────────────────────────────────────────────────────
    private class HelpOverlayPanel extends JWindow {
        private boolean visible = false;

        HelpOverlayPanel() {
            setSize(400, 320);
            setAlwaysOnTop(true);
            setType(Type.UTILITY);

            JPanel panel = new JPanel(new BorderLayout(12, 12));
            panel.setBackground(new Color(0x0f172a, true));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(0x334155), 1),
                    new EmptyBorder(20, 24, 20, 24)
            ));

            JLabel title = new JLabel("Keyboard Shortcuts");
            title.setFont(new Font("Segoe UI", Font.BOLD, 16));
            title.setForeground(Color.WHITE);
            title.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(title, BorderLayout.NORTH);

            String[][] shortcuts = {
                    {"Ctrl+Shift+F", "Toggle FileMind"},
                    {"\u2191 \u2193", "Navigate results"},
                    {"Enter", "Open file"},
                    {"Ctrl+Enter", "Open folder"},
                    {"Ctrl+C", "Copy path"},
                    {"Ctrl+K", "Actions menu"},
                    {"Ctrl+1..7", "Categories"},
                    {"Esc", "Close"},
                    {"?", "Toggle help"}
            };

            JPanel grid = new JPanel(new GridLayout(shortcuts.length, 2, 16, 6));
            grid.setOpaque(false);
            for (String[] row : shortcuts) {
                JLabel keyLabel = new JLabel(row[0]);
                keyLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                keyLabel.setForeground(new Color(0x3b82f6));
                grid.add(keyLabel);

                JLabel descLabel = new JLabel(row[1]);
                descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                descLabel.setForeground(new Color(0x94a3b8));
                grid.add(descLabel);
            }
            panel.add(grid, BorderLayout.CENTER);

            setContentPane(panel);
            setBackground(new Color(0, 0, 0, 0));
        }

        void showOverlay() {
            if (isVisible()) {
                setVisible(false);
                return;
            }
            // Center relative to SearchPanel
            int x = getX() + (getWidth() - 400) / 2;
            int y = getY() + (getHeight() - 320) / 2;
            
            // Actually position relative to parent
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int px = (screenSize.width - 400) / 2;
            int py = (screenSize.height - 320) / 2;
            setLocation(px, py);
            setVisible(true);
            toFront();
        }

        void hideOverlay() {
            setVisible(false);
        }
    }

    // ── NL Hint Label ──────────────────────────────────────────────────────
    private JLabel buildNLHint() {
        JLabel hint = new JLabel();
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint.setForeground(ThemeManager.getTextHint());
        hint.setPreferredSize(new Dimension(PANEL_WIDTH, HINT_HEIGHT));
        hint.setMaximumSize(new Dimension(Integer.MAX_VALUE, HINT_HEIGHT));
        hint.setVisible(false);
        hint.setBorder(new EmptyBorder(2, 12, 0, 12));
        return hint;
    }

     // ── Build Results Area ─────────────────────────────────────────────────
    private JScrollPane buildResultsArea() {
        resultsModel = new ResultListModel();

        resultsList = new JList<>(resultsModel);
        resultsList.setBackground(ThemeManager.getPanelBg());
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsList.setCellRenderer(resultRenderer = new ResultRenderer());
        resultsList.setFixedCellHeight(-1);  // Variable height for headers/results

        // Double-click to open
        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = resultsList.getSelectedIndex();
                    if (idx >= 0 && idx < resultsModel.getSize()) {
                        ResultItem item = resultsModel.getElementAt(idx);
                        if (!item.isHeader()) {
                            openFile(item.getResult());
                        }
                    }
                }
            }
        });

        // Hover effect
        resultsList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = resultsList.locationToIndex(e.getPoint());
                if (idx >= 0 && idx < resultsModel.getSize()) {
                    ResultItem item = resultsModel.getElementAt(idx);
                    if (!item.isHeader() && resultsList.getSelectedIndex() != idx) {
                        resultsList.setSelectedIndex(idx);
                    }
                }
            }
        });

        resultsScroll = new JScrollPane(resultsList);
        resultsScroll.setPreferredSize(new Dimension(PANEL_WIDTH, RESULTS_MAX_HEIGHT));
        resultsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, RESULTS_MAX_HEIGHT));
        resultsScroll.setBackground(ThemeManager.getPanelBg());
        resultsScroll.getViewport().setBackground(ThemeManager.getPanelBg());
        resultsScroll.setBorder(null);
        resultsScroll.getVerticalScrollBar().setUnitIncrement(24);

        return resultsScroll;
    }

    // ── Build Status Bar ───────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setPreferredSize(new Dimension(PANEL_WIDTH, STATUS_HEIGHT));
        statusBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, STATUS_HEIGHT));
        statusBar.setBackground(ThemeManager.getPanelBg());
        statusBar.setBorder(new EmptyBorder(4, 12, 4, 12));

        statusLabel = new JLabel("0 results");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(ThemeManager.getTextHint());
        statusBar.add(statusLabel, BorderLayout.WEST);

        JLabel hints = new JLabel("\u2191\u2193 navigate  \u23CE open  ? help  Esc close");
        hints.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hints.setForeground(ThemeManager.getTextHint());
        statusBar.add(hints, BorderLayout.EAST);

        return statusBar;
    }

    // ── Keyboard Handling ──────────────────────────────────────────────────
    private void setupKeyboardHandling() {
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> {
                        if (helpOverlay.isVisible()) {
                            helpOverlay.hideOverlay();
                        } else {
                            close();
                        }
                        e.consume();
                    }
                    case KeyEvent.VK_SLASH -> {
                        if (e.isShiftDown()) {
                            helpOverlay.showOverlay();
                            e.consume();
                        }
                    }
                    case KeyEvent.VK_DOWN -> {
                        int currentIdx = resultsList.getSelectedIndex();
                        int nextIdx = resultsModel.getNextSelectableIndex(currentIdx, 1);
                        if (nextIdx >= 0 && nextIdx != currentIdx) {
                            resultsList.setSelectedIndex(nextIdx);
                            resultsList.ensureIndexIsVisible(nextIdx);
                        } else if (currentIdx < 0) {
                            int firstIdx = resultsModel.getFirstSelectableIndex();
                            if (firstIdx >= 0) {
                                resultsList.setSelectedIndex(firstIdx);
                                resultsList.ensureIndexIsVisible(firstIdx);
                            }
                        }
                        e.consume();
                    }
                    case KeyEvent.VK_UP -> {
                        int currentIdx = resultsList.getSelectedIndex();
                        if (currentIdx >= 0) {
                            int prevIdx = resultsModel.getNextSelectableIndex(currentIdx, -1);
                            if (prevIdx >= 0 && prevIdx != currentIdx) {
                                resultsList.setSelectedIndex(prevIdx);
                                resultsList.ensureIndexIsVisible(prevIdx);
                            }
                        }
                        e.consume();
                    }
                    case KeyEvent.VK_ENTER -> {
                        if ((e.getModifiers() & InputEvent.CTRL_DOWN_MASK) != 0) {
                            // Ctrl+Enter: open folder
                            int idx = resultsList.getSelectedIndex();
                            if (idx >= 0 && idx < resultsModel.getSize()) {
                                ResultItem item = resultsModel.getElementAt(idx);
                                if (!item.isHeader()) {
                                    openFolder(item.getResult());
                                }
                            }
                        } else {
                            int idx = resultsList.getSelectedIndex();
                            if (idx >= 0 && idx < resultsModel.getSize()) {
                                ResultItem item = resultsModel.getElementAt(idx);
                                if (!item.isHeader()) {
                                    openSelectedFile(item.getResult());
                                }
                            }
                        }
                        e.consume();
                    }
                    case KeyEvent.VK_C -> {
                        if ((e.getModifiers() & InputEvent.CTRL_DOWN_MASK) != 0) {
                            int idx = resultsList.getSelectedIndex();
                            if (idx >= 0 && idx < resultsModel.getSize()) {
                                ResultItem item = resultsModel.getElementAt(idx);
                                if (!item.isHeader()) {
                                    copyPath(item.getResult().path());
                                }
                            }
                            e.consume();
                        }
                    }
                    case KeyEvent.VK_K -> {
                        if ((e.getModifiers() & InputEvent.CTRL_DOWN_MASK) != 0) {
                            showContextMenu();
                            e.consume();
                        }
                    }
                    case KeyEvent.VK_TAB -> {
                        switchToNextTab();
                        e.consume();
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                // Also check for ? when released (for ? key not needing shift)
                if (e.getKeyCode() == KeyEvent.VK_SLASH && !e.isShiftDown()) {
                    // This catches ? without shift on some layouts
                    helpOverlay.showOverlay();
                    e.consume();
                }
            }
        });

        // Also listen for ? globally within search panel
        helpDispatcher = ke -> {
            if (ke.getID() == KeyEvent.KEY_PRESSED && ke.getKeyCode() == KeyEvent.VK_SLASH
                    && ke.isShiftDown() && isVisible() && !helpOverlay.isVisible()) {
                helpOverlay.showOverlay();
                return true;
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(helpDispatcher);
    }

    // ── Search Logic ───────────────────────────────────────────────────────
    private void scheduleSearch() {
        if (searchDebounceTimer != null) {
            searchDebounceTimer.stop();
        }
        searchDebounceTimer = new javax.swing.Timer(300, e -> performSearch());
        searchDebounceTimer.setRepeats(false);
        searchDebounceTimer.start();
    }

    private void performSearch() {
        String query = searchField.getText().trim();

        if (query.isEmpty()) {
            currentResults = List.of();
            chipBar.setVisible(false);
            lastParsedQuery = null;
            displayResults(List.of());
            return;
        }

        // Check for natural language
        boolean isNL = detectNaturalLanguage(query);
        nlHintLabel.setVisible(isNL);
        if (isNL) {
            nlHintLabel.setText("\uD83D\uDD24 Natural language detected \u2014 searching by meaning");
        }

        // Parse query
        lastParsedQuery = NLQueryParser.parse(query);
        updateChips(lastParsedQuery);

        // Run search on background thread
        SwingWorker<List<SearchResult>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SearchResult> doInBackground() {
                return LuceneIndexer.search(lastParsedQuery, 100);
            }

            @Override
            protected void done() {
                try {
                    currentResults = get();
                    displayResults(currentResults);
                } catch (Exception e) {
                    currentResults = List.of();
                    displayResults(List.of());
                }
            }
        };
        worker.execute();
    }

    private boolean detectNaturalLanguage(String query) {
        String lower = query.toLowerCase();
        return lower.contains("yesterday") || lower.contains("ago") ||
                lower.contains("between") || lower.contains("larger") ||
                lower.contains("recently") || lower.contains("java files") ||
                lower.contains("pdf documents");
    }

    // ── Results Display ────────────────────────────────────────────────────
    private void displayResults(List<SearchResult> results) {
        List<SearchResult> filtered = filterByCategory(results);

        if (filtered.isEmpty()) {
            showEmptyState();
            resultRenderer.setSearchQuery("");
            resultsModel.clear();
            statusLabel.setText("No results");
        } else {
            resultsModel.setResults(filtered);
            resultRenderer.setSearchQuery(searchField.getText().trim());
            statusLabel.setText(filtered.size() + " result" + (filtered.size() == 1 ? "" : "s"));

            // Auto-select first result
            int firstIdx = resultsModel.getFirstSelectableIndex();
            if (firstIdx >= 0) {
                resultsList.setSelectedIndex(firstIdx);
            }
        }
    }

    private void showEmptyState() {
        resultsModel.clear();

        String query = searchField.getText().trim();
        String msg;
        if ("Recent".equals(activeCategory)) {
            msg = "No file history yet. Open files from search results to build history.";
        } else if (query.isEmpty()) {
            msg = "Type to search";
        } else {
            msg = "No results for \"" + query + "\"";
        }
        statusLabel.setText(msg);
    }

    private List<SearchResult> filterByCategory(List<SearchResult> results) {
        if (activeCategory.equals("All")) return results;

        return results.stream().filter(r -> {
            String ext = (r.ext() != null) ? r.ext().toLowerCase() : "";
            return switch (activeCategory) {
                case "Files" -> !Files.isDirectory(Paths.get(r.path()));
                case "Folders" -> true;
                case "Code" -> ext.matches("java|py|js|cpp|c|h|go|rs|kt|swift");
                case "PDF" -> ext.equals("pdf");
                case "Images" -> ext.matches("jpg|png|gif|bmp|svg|webp");
                case "Recent" -> true;
                default -> true;
            };
        }).toList();
    }

     private JPanel createResultCard(SearchResult r, int index) {
        return new JPanel();
    }

    private void highlightResult() {
    }

    // ── File Actions ───────────────────────────────────────────────────────
    private void openSelectedFile(SearchResult r) {
        openFile(r);
    }

    private void openFile(SearchResult r) {
        try {
            ActivityHistory.recordOpen(MetadataDB.getConnection(), r.path());
            Desktop.getDesktop().open(new File(r.path()));
            close();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot open file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openFolder(SearchResult r) {
        try {
            File parent = new File(r.path()).getParentFile();
            if (parent != null && parent.exists()) {
                Desktop.getDesktop().open(parent);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot open folder: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copyPath(String path) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(path), null);
    }

    private void copySelectedPath() {
    }

    private void switchToNextTab() {
        int current = java.util.Arrays.asList(CATEGORIES).indexOf(activeCategory);
        int next = (current + 1) % CATEGORIES.length;
        switchCategory(CATEGORIES[next]);
    }

    private void showContextMenu() {
        int idx = resultsList.getSelectedIndex();
        if (idx < 0 || idx >= resultsModel.getSize()) return;

        ResultItem item = resultsModel.getElementAt(idx);
        if (item.isHeader()) return;

        SearchResult r = item.getResult();
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(ThemeManager.getPanelBg());

        JMenuItem openItem = new JMenuItem("Open file");
        openItem.addActionListener(e -> openFile(r));
        menu.add(openItem);

        JMenuItem folderItem = new JMenuItem("Open folder");
        folderItem.addActionListener(e -> {
            try {
                File parent = new File(r.path()).getParentFile();
                if (parent != null && parent.exists()) {
                    Desktop.getDesktop().open(parent);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Cannot open folder",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        menu.add(folderItem);

        menu.addSeparator();

        JMenuItem copyItem = new JMenuItem("Copy path");
        copyItem.addActionListener(e -> copyPath(r.path()));
        menu.add(copyItem);

        // Name suggestion if available
        if (r.suggestedName() != null && !r.suggestedName().isBlank()) {
            menu.addSeparator();
            JMenuItem suggestItem = new JMenuItem("See suggestion: " + r.suggestedName());
            suggestItem.addActionListener(e -> {
                String newName = r.suggestedName();
                File oldFile = new File(r.path());
                File newFile = new File(oldFile.getParent(), newName);
                if (oldFile.renameTo(newFile)) {
                    showToast("\u2713 Renamed successfully", new Color(0x16a34a));
                } else {
                    showToast("\u2717 Rename failed \u2014 file in use", new Color(0xdc2626));
                }
            });
            menu.add(suggestItem);
        }

        menu.addSeparator();

        JMenuItem closeItem = new JMenuItem("Close");
        closeItem.addActionListener(e -> menu.setVisible(false));
        menu.add(closeItem);

        Rectangle cellBounds = resultsList.getCellBounds(idx, idx);
        if (cellBounds != null) {
            menu.show(resultsList, cellBounds.x, cellBounds.y + cellBounds.height);
        }
    }

    // ── Toast Notification ────────────────────────────────────────────────
    private void showToast(String message, Color bgColor) {
        JWindow toast = new JWindow();
        toast.setType(Type.UTILITY);
        toast.setAlwaysOnTop(true);
        toast.setSize(280, 36);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(bgColor);
        panel.setBorder(new EmptyBorder(6, 16, 6, 16));

        JLabel label = new JLabel(message);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(Color.WHITE);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);

        toast.setContentPane(panel);
        toast.setOpacity(1.0f);

        // Position below search panel
        int x = getX() + (getWidth() - 280) / 2;
        int y = getY() + getHeight() + 10;
        toast.setLocation(x, y);
        toast.setVisible(true);

        // Fade out after 2s
        javax.swing.Timer fadeTimer = new javax.swing.Timer(2000, evt -> {
            javax.swing.Timer fadeOut = new javax.swing.Timer(30, null);
            final float[] opacity = {1.0f};
            fadeOut.addActionListener(e2 -> {
                opacity[0] -= 0.1f;
                if (opacity[0] <= 0) {
                    fadeOut.stop();
                    toast.dispose();
                } else {
                    toast.setOpacity(opacity[0]);
                }
            });
            fadeOut.setInitialDelay(0);
            fadeOut.start();
            ((javax.swing.Timer) evt.getSource()).stop();
        });
        fadeTimer.setRepeats(false);
        fadeTimer.start();
    }

    // ── Theme Management ───────────────────────────────────────────────────
    private void updateTheme() {
        themeToggle.setText(ThemeManager.isDark() ? "\u2600\uFE0F" : "\uD83C\uDF19");

        // Update all colors
        getContentPane().setBackground(ThemeManager.getPanelBg());
        searchField.setBackground(ThemeManager.getSearchBg());
        searchField.setForeground(ThemeManager.getSearchText());
        searchField.setCaretColor(ThemeManager.getAccent());

        resultsList.setBackground(ThemeManager.getPanelBg());
        resultsScroll.getViewport().setBackground(ThemeManager.getPanelBg());

        tabBar.setBackground(ThemeManager.getPanelBg());
        for (Component comp : tabBar.getComponents()) {
            if (comp instanceof JButton btn) {
                btn.setBackground(ThemeManager.getPanelBg());
                if (btn.getText().equals(activeCategory)) {
                    btn.setForeground(ThemeManager.getAccent());
                } else {
                    btn.setForeground(ThemeManager.getTextHint());
                }
            }
        }

        chipBar.setBackground(ThemeManager.getPanelBg());

        statusLabel.setForeground(ThemeManager.getTextHint());
        nlHintLabel.setForeground(ThemeManager.getTextHint());
        dimLayer.updateTheme();

        // Refresh the list rendering
        resultsList.repaint();

        revalidate();
        repaint();
    }

    // ── Window Management ──────────────────────────────────────────────────
    private void centerWindow() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screenSize.width - PANEL_WIDTH) / 2;
        int y = (int) (screenSize.height * 0.28);
        setLocation(x, y);
    }

    public void open() {
        centerWindow();
        setVisible(true);
        dimLayer.setVisible(true);
        searchField.requestFocus();
        searchField.setText("");

        // Animate open
        int startY = getY() - 30;
        int endY = getY();
        AnimationUtil.slideAndFadeIn(this, startY, endY, 120, null);
        AnimationUtil.fadeIn(dimLayer, 120, null);
    }

    public void close() {
        // Unregister global key dispatcher to prevent listener leak
        if (helpDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(helpDispatcher);
            helpDispatcher = null;
        }
        // Hide help overlay if visible
        if (helpOverlay != null) helpOverlay.hideOverlay();
        chipBar.setVisible(false);

        // Animate close
        AnimationUtil.slideUpAndFadeOut(this, 20, 90, () -> {
            setVisible(false);
            dimLayer.setVisible(false);
        });
        AnimationUtil.fadeOut(dimLayer, 90, null);
    }

    public static SearchPanel instance;

    public static SearchPanel getInstance() {
        if (instance == null) {
            instance = new SearchPanel();
        }
        return instance;
    }
}
