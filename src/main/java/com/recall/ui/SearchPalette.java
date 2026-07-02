package com.recall.ui;

import com.recall.core.LuceneIndexer;
import com.recall.core.NLQueryParser;
import com.recall.core.SearchResult;
import com.recall.ui.design.DesignSystem;
import com.recall.ui.design.SvgIconProvider;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modern floating search palette that expands from the FloatingLauncher position.
 * NO dim layer — appears as a floating card with subtle transparency.
 *
 * Layout (from top):
 *  - SearchBar (52px): SVG search icon + field + voice/AI/settings icons + shortcut hints
 *  - FilterChips (36px, placeholder): pill-shaped filter chips
 *  - AISection (variable, optional): AI suggestion cards
 *  - ResultsList (scrollable, max 400px): file results with SVG icons
 *  - StatusBar (24px): result count + keyboard hints
 */
public class SearchPalette extends JWindow {

    // ── Constants ──────────────────────────────────────────────────────────
    private static final int PALETTE_WIDTH = 640;
    private static final int MAX_HEIGHT = 520;
    private static final int SEARCH_BAR_HEIGHT = 52;
    private static final int CHIP_BAR_HEIGHT = 36;
    private static final int STATUS_HEIGHT = 24;
    private static final int RESULTS_MAX_HEIGHT = 400;
    private static final int CORNER_RADIUS = 16;
    private static final Color BACKDROP_GLASS = new Color(15, 23, 42, 240); // Near-opaque glass effect
    private static final Color BORDER_GLASS = new Color(255, 255, 255, 20);

    // ── State ──────────────────────────────────────────────────────────────
    private JTextField searchField;
    private JPanel resultsContainer;
    private JScrollPane resultsScroll;
    private JLabel statusLabel;
    private JLabel countLabel;
    private JPanel chipBar;
    private FilterChips filterChips;
    private AISection aiSection;
    private PreviewPanel previewPanel;

    private javax.swing.Timer searchDebounceTimer;
    private SwingWorker<List<SearchResult>, Void> currentWorker;
    private String activeCategory = "All";
    private int selectedResultIndex = -1;
    private List<SearchResult> currentResults = List.of();
    private FloatingLauncher floatingLauncher;
    private boolean isOpen = false;

    // ── Singleton ──────────────────────────────────────────────────────────
    private static SearchPalette instance;

    public static SearchPalette getInstance() {
        if (instance == null) {
            instance = new SearchPalette();
        }
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────
    private SearchPalette() {
        // Setup glass-like window
        setAlwaysOnTop(true);
        setType(Type.UTILITY);
        setSize(PALETTE_WIDTH, MAX_HEIGHT);
        setBackground(new Color(0, 0, 0, 0)); // Transparent window background

        // Build main content
        JPanel glassPanel = new GlassPanel();
        glassPanel.setLayout(new BoxLayout(glassPanel, BoxLayout.Y_AXIS));
        glassPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_GLASS, 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        glassPanel.add(buildSearchBar());

        // Filter chips + AI section
        filterChips = new FilterChips();
        aiSection = new AISection();
        glassPanel.add(filterChips);
        glassPanel.add(aiSection);

        glassPanel.add(buildResultsArea());
        glassPanel.add(buildStatusBar());

        setContentPane(glassPanel);

        // Setup keyboard handling
        setupKeyboardHandling();

        // Focus listener to close when losing focus (click outside)
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // Delay close to allow click events to process
                Timer closeTimer = new Timer(150, ev -> {
                    if (!isFocusOwner() && !isFloatingLauncherFocused()) {
                        close();
                    }
                });
                closeTimer.setRepeats(false);
                closeTimer.start();
            }
        });
    }

    // ── Floating Launcher Reference ────────────────────────────────────────
    public void setFloatingLauncher(FloatingLauncher launcher) {
        this.floatingLauncher = launcher;
    }

    public void setPreviewPanel(PreviewPanel panel) {
        this.previewPanel = panel;
    }

    private boolean isFloatingLauncherFocused() {
        return floatingLauncher != null && floatingLauncher.isVisible() && floatingLauncher.isFocusOwner();
    }

    // ── Custom Glass Panel ─────────────────────────────────────────────────
    private class GlassPanel extends JPanel {
        GlassPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int w = getWidth();
            int h = getHeight();

            // Draw rounded rectangle background (glass effect)
            g2.setColor(BACKDROP_GLASS);
            g2.fillRoundRect(0, 0, w, h, CORNER_RADIUS, CORNER_RADIUS);

            // Draw subtle border
            g2.setColor(BORDER_GLASS);
            g2.setStroke(new BasicStroke(1));
            g2.drawRoundRect(0, 0, w - 1, h - 1, CORNER_RADIUS, CORNER_RADIUS);
        }
    }

    // ── Build Search Bar ────────────────────────────────────────────────────
    private JPanel buildSearchBar() {
        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.setPreferredSize(new Dimension(PALETTE_WIDTH, SEARCH_BAR_HEIGHT));
        searchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, SEARCH_BAR_HEIGHT));
        searchBar.setOpaque(false);

        // Left: search icon (FlatSVGIcon via SvgIconProvider)
        JLabel searchIconLabel = SvgIconProvider.createLabel("SEARCH", DesignSystem.textTertiary, 20);
        searchIconLabel.setPreferredSize(new Dimension(36, SEARCH_BAR_HEIGHT));
        searchBar.add(searchIconLabel, BorderLayout.WEST);

        // Center: Search field
        searchField = new JTextField();
        searchField.setFont(DesignSystem.FONT_BODY.deriveFont(Font.PLAIN, 16f));
        searchField.setOpaque(false);
        searchField.setForeground(DesignSystem.textPrimary);
        searchField.setCaretColor(DesignSystem.surfaceAccent);
        searchField.setBorder(null);

        // Placeholder text
        searchField.putClientProperty("JTextField.placeholderText", "Search anything...");
        searchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }
        });

        // Live search with debounce
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
        });

        searchBar.add(searchField, BorderLayout.CENTER);

        // Right: icon row + hint
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        rightPanel.setOpaque(false);

        // Voice icon button
        JButton voiceBtn = new JButton(SvgIconProvider.getIcon("VOICE", DesignSystem.textTertiary));
        voiceBtn.setPreferredSize(new Dimension(28, 28));
        voiceBtn.setBorder(null);
        voiceBtn.setContentAreaFilled(false);
        voiceBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        voiceBtn.setToolTipText("Voice input (coming soon)");
        rightPanel.add(voiceBtn);

        // AI icon button
        JButton aiBtn = new JButton(SvgIconProvider.getIcon("AI", DesignSystem.textTertiary));
        aiBtn.setPreferredSize(new Dimension(28, 28));
        aiBtn.setBorder(null);
        aiBtn.setContentAreaFilled(false);
        aiBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        aiBtn.setToolTipText("AI suggestions");
        aiBtn.addActionListener(e -> toggleAISection());
        rightPanel.add(aiBtn);

        // Settings icon button
        JButton settingsBtn = new JButton(SvgIconProvider.getIcon("SETTINGS", DesignSystem.textTertiary));
        settingsBtn.setPreferredSize(new Dimension(28, 28));
        settingsBtn.setBorder(null);
        settingsBtn.setContentAreaFilled(false);
        settingsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsBtn.setToolTipText("Settings");
        settingsBtn.addActionListener(e -> showSettings());
        rightPanel.add(settingsBtn);

        // Keyboard shortcut hint
        JLabel shortcutHint = new JLabel("Ctrl+K");
        shortcutHint.setFont(DesignSystem.FONT_SMALL);
        shortcutHint.setForeground(DesignSystem.textTertiary);
        shortcutHint.setBorder(new EmptyBorder(0, 8, 0, 4));
        rightPanel.add(shortcutHint);

        // Esc hint
        JLabel escHint = new JLabel("esc");
        escHint.setFont(DesignSystem.FONT_SMALL);
        escHint.setForeground(DesignSystem.textTertiary);
        rightPanel.add(escHint);

        searchBar.add(rightPanel, BorderLayout.EAST);

        return searchBar;
    }

    // ── Build Chip Bar (placeholder for FilterChips) ───────────────────────
    private JPanel buildChipBar() {
        chipBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chipBar.setPreferredSize(new Dimension(PALETTE_WIDTH, 0)); // Hidden initially
        chipBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
        chipBar.setOpaque(false);
        chipBar.setVisible(false);
        return chipBar;
    }

    // ── Build Results Area ─────────────────────────────────────────────────
    private JScrollPane buildResultsArea() {
        resultsContainer = new JPanel();
        resultsContainer.setLayout(new BoxLayout(resultsContainer, BoxLayout.Y_AXIS));
        resultsContainer.setOpaque(false);

        resultsScroll = new JScrollPane(resultsContainer);
        resultsScroll.setPreferredSize(new Dimension(PALETTE_WIDTH, RESULTS_MAX_HEIGHT));
        resultsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, RESULTS_MAX_HEIGHT));
        resultsScroll.setOpaque(false);
        resultsScroll.getViewport().setOpaque(false);
        resultsScroll.setBorder(null);
        resultsScroll.getVerticalScrollBar().setUnitIncrement(24);
        resultsScroll.getVerticalScrollBar().setOpaque(false);

        return resultsScroll;
    }

    // ── Build Status Bar ───────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setPreferredSize(new Dimension(PALETTE_WIDTH, STATUS_HEIGHT));
        statusBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, STATUS_HEIGHT));
        statusBar.setOpaque(false);
        statusBar.setBorder(new EmptyBorder(2, 12, 2, 12));

        statusLabel = new JLabel("");
        statusLabel.setFont(DesignSystem.FONT_SMALL);
        statusLabel.setForeground(DesignSystem.textTertiary);
        statusBar.add(statusLabel, BorderLayout.WEST);

        countLabel = new JLabel("");
        countLabel.setFont(DesignSystem.FONT_SMALL);
        countLabel.setForeground(DesignSystem.textTertiary);
        statusBar.add(countLabel, BorderLayout.CENTER);

        JLabel hints = new JLabel("\u2191\u2193 navigate  \u23CE open  \u2423 preview  esc close");
        hints.setFont(DesignSystem.FONT_SMALL);
        hints.setForeground(DesignSystem.textTertiary);
        statusBar.add(hints, BorderLayout.EAST);

        return statusBar;
    }

    // ── Keyboard Handling ──────────────────────────────────────────────────
    private void setupKeyboardHandling() {
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int resultCount = countResultRows();
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> close();
                    case KeyEvent.VK_DOWN -> {
                        if (resultCount > 0) {
                            selectedResultIndex = Math.min(selectedResultIndex + 1, resultCount - 1);
                            highlightSelectedResult();
                            if (selectedResultIndex == 0) focusFirstResult();
                        }
                    }
                    case KeyEvent.VK_UP -> {
                        if (resultCount > 0 && selectedResultIndex > 0) {
                            selectedResultIndex = Math.max(selectedResultIndex - 1, 0);
                            highlightSelectedResult();
                        }
                    }
                    case KeyEvent.VK_ENTER -> {
                        SearchResult r = getSelectedResult();
                        if (r != null) openFile(r);
                    }
                    case KeyEvent.VK_SPACE -> {
                        SearchResult r = getSelectedResult();
                        if (r != null && previewPanel != null) {
                            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                            previewPanel.setLocation(
                                (screenSize.width - previewPanel.getWidth()) / 2,
                                (screenSize.height - previewPanel.getHeight()) / 2
                            );
                            previewPanel.showPreview(r, () -> searchField.requestFocus());
                        }
                    }
                    default -> {}
                }
            }
        });
    }

    private int countResultRows() {
        int count = 0;
        for (Component c : resultsContainer.getComponents()) {
            if (c instanceof JComponent jc && jc.getClientProperty("result") != null) {
                count++;
            }
        }
        return count;
    }

    private void highlightSelectedResult() {
        int idx = 0;
        for (Component c : resultsContainer.getComponents()) {
            if (c instanceof JComponent jc && jc.getClientProperty("result") != null) {
                if (idx == selectedResultIndex) {
                    jc.setBackground(DesignSystem.surfaceHighlight);
                    jc.setOpaque(true);
                } else {
                    jc.setOpaque(false);
                }
                jc.repaint();
                idx++;
            }
        }
    }

    private SearchResult getSelectedResult() {
        if (selectedResultIndex < 0) return null;
        int idx = 0;
        for (Component c : resultsContainer.getComponents()) {
            if (c instanceof JComponent jc && jc.getClientProperty("result") != null) {
                if (idx == selectedResultIndex) return (SearchResult) jc.getClientProperty("result");
                idx++;
            }
        }
        return null;
    }

    // ── Open / Close with Animation ────────────────────────────────────────
    public void openFromLauncher(Rectangle launcherBounds) {
        if (isOpen) return;
        isOpen = true;

        // Calculate target bounds (centered on screen, but could be near launcher)
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int paletteX = (screenSize.width - PALETTE_WIDTH) / 2;
        int paletteY = (int) (screenSize.height * 0.28);
        Rectangle endBounds = new Rectangle(paletteX, paletteY, PALETTE_WIDTH, MAX_HEIGHT);

        // Scale down from launcher size to full palette
        AnimationUtil.springAndFadeInBounds(this, launcherBounds, endBounds, 300, () -> {
            searchField.requestFocus();
            searchField.setText("");
            statusLabel.setText("Search anything...");
            showRecentFiles();
        });
    }

    public void open() {
        if (isOpen) return;
        isOpen = true;

        // Direct open without animation (from hotkey)
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screenSize.width - PALETTE_WIDTH) / 2;
        int y = (int) (screenSize.height * 0.28);
        setLocation(x, y);
        setVisible(true);
        searchField.requestFocus();
        searchField.setText("");
        statusLabel.setText("Search anything...");

        // Fade in
        AnimationUtil.fadeIn(this, 150, () -> {
            showRecentFiles();
        });
    }

    public void close() {
        if (!isOpen) return;
        isOpen = false;

        // Shrink animation back to launcher if available
        if (floatingLauncher != null && floatingLauncher.isVisible()) {
            Rectangle launcherBounds = floatingLauncher.getLauncherBounds();
            Rectangle currentBounds = getBounds();
            AnimationUtil.springAndFadeInBounds(this, currentBounds, launcherBounds, 200, () -> {
                setVisible(false);
            });
        } else {
            AnimationUtil.slideUpAndFadeOut(this, 20, 90, () -> {
                setVisible(false);
            });
        }
    }

    public boolean isOpen() {
        return isOpen;
    }

    // ── Search Logic (placeholder, reuses existing search logic) ────────────
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
            showRecentFiles();
            return;
        }

        statusLabel.setText("Searching...");
        countLabel.setText("");

        // Parse and search (reusing existing backend)
        NLQueryParser.ParsedQuery parsed = NLQueryParser.parse(query);

        if (currentWorker != null) {
            currentWorker.cancel(true);
        }
        currentWorker = new SwingWorker<>() {
            @Override
            protected List<SearchResult> doInBackground() {
                return LuceneIndexer.search(parsed, 100);
            }

            @Override
            protected void done() {
                try {
                    currentResults = get();
                    displayResults(currentResults, query);
                } catch (Exception e) {
                    currentResults = List.of();
                    displayResults(List.of(), query);
                }
            }
        };
        currentWorker.execute();
    }

    private void showRecentFiles() {
        resultsContainer.removeAll();
        selectedResultIndex = -1;
        // Show AI suggestions when search is empty
        if (aiSection != null) {
            aiSection.showSuggestions();
        }
        resultsContainer.revalidate();
        resultsContainer.repaint();
    }

    private void displayResults(List<SearchResult> results, String query) {
        resultsContainer.removeAll();
        selectedResultIndex = -1;
        // Hide AI suggestions when results are shown
        if (aiSection != null) {
            aiSection.hideSection();
        }

        if (results.isEmpty()) {
            showPlaceholder("No results for \"" + query + "\"");
            statusLabel.setText("No results");
            countLabel.setText("");
        } else {
            for (int i = 0; i < Math.min(results.size(), 50); i++) {
                SearchResult r = results.get(i);
                resultsContainer.add(createResultRow(r));
                resultsContainer.add(Box.createVerticalStrut(2));
            }
            statusLabel.setText(results.size() + " result" + (results.size() == 1 ? "" : "s"));
        }

        resultsContainer.revalidate();
        resultsContainer.repaint();
        resultsScroll.getVerticalScrollBar().setValue(0);
    }

    private JPanel createResultRow(SearchResult r) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Left: file type icon (FlatSVGIcon via SvgIconProvider)
        JLabel fileIconLabel = SvgIconProvider.createLabel("FILE_GENERIC", getFileTypeColor(r.ext()), 24);
        fileIconLabel.setPreferredSize(new Dimension(40, 56));
        row.add(fileIconLabel, BorderLayout.WEST);

        // Center: filename + path
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(r.filename());
        nameLabel.setFont(DesignSystem.FONT_SUBHEADING);
        nameLabel.setForeground(DesignSystem.textPrimary);
        centerPanel.add(nameLabel);

        JLabel pathLabel = new JLabel(r.parentFolder());
        pathLabel.setFont(DesignSystem.FONT_SMALL);
        pathLabel.setForeground(DesignSystem.textSecondary);
        centerPanel.add(pathLabel);

        row.add(centerPanel, BorderLayout.CENTER);

        // Right: size + date
        JPanel metaPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        metaPanel.setOpaque(false);

        JLabel sizeLabel = new JLabel(r.displaySize(), SwingConstants.RIGHT);
        sizeLabel.setFont(DesignSystem.FONT_SMALL);
        sizeLabel.setForeground(DesignSystem.textSecondary);
        metaPanel.add(sizeLabel);

        JLabel dateLabel = new JLabel(formatSmartDate(r.modifiedMs()), SwingConstants.RIGHT);
        dateLabel.setFont(DesignSystem.FONT_SMALL);
        dateLabel.setForeground(DesignSystem.textTertiary);
        metaPanel.add(dateLabel);

        row.add(metaPanel, BorderLayout.EAST);

        // Click handler
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openFile(r);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(DesignSystem.surfaceHighlight);
                row.setOpaque(true);
                row.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setOpaque(false);
                row.repaint();
            }
        });

        // Store result reference for keyboard navigation
        row.putClientProperty("result", r);
        return row;
    }

    private void showPlaceholder(String message) {
        JLabel lbl = new JLabel(message, SwingConstants.CENTER);
        lbl.setFont(DesignSystem.FONT_BODY);
        lbl.setForeground(DesignSystem.textTertiary);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(60, 0, 0, 0));
        wrap.add(lbl, BorderLayout.CENTER);
        resultsContainer.add(wrap);
    }

    private void focusFirstResult() {
        Component[] comps = resultsContainer.getComponents();
        for (Component c : comps) {
            if (c instanceof JPanel && c.isFocusable()) {
                c.requestFocusInWindow();
                return;
            }
        }
    }

    // ── File Actions ───────────────────────────────────────────────────────
    private void openFile(SearchResult r) {
        try {
            Desktop.getDesktop().open(new File(r.path()));
            close();
        } catch (IOException e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    private void toggleAISection() {
        if (aiSection != null) {
            aiSection.toggle();
        }
    }

    private void showSettings() {
        // Placeholder: open settings
        statusLabel.setText("Settings coming soon");
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private Color getFileTypeColor(String ext) {
        return ThemeManager.getFileTypeColor(ext != null ? ext : "");
    }

    private String formatSmartDate(long modifiedMs) {
        java.time.LocalDateTime dt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(modifiedMs),
                java.time.ZoneId.systemDefault()
        );
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate yesterday = today.minusDays(1);
        java.time.LocalDate fileDate = dt.toLocalDate();

        if (fileDate.equals(today)) {
            return String.format("Today %02d:%02d", dt.getHour(), dt.getMinute());
        } else if (fileDate.equals(yesterday)) {
            return String.format("Yesterday %02d:%02d", dt.getHour(), dt.getMinute());
        } else {
            return dt.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
        }
    }
}
