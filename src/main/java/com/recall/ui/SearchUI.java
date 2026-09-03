package com.recall.ui;

import com.recall.core.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/**
 * Complete FileMind search UI.
 *
 * Features:
 *  - Instant search as you type (300ms debounce — doesn't hammer Lucene on every keystroke)
 *  - Filter bar: All / PDF / Code / Image / Doc / Video
 *  - Result cards: icon + filename + path + size + date
 *  - Name suggestion tooltip on hover
 *  - Recent files section (shown when search is empty)
 *  - Status bar with index count + indexing progress
 *  - Keyboard nav: arrow keys move through results, Enter opens file
 *  - Right-click context menu: Open / Open folder / Copy path / Rename to suggested
 *  - History
 */
public class SearchUI extends JFrame {

    // ── colours (flat, easy on eyes) ─────────────────────────────────────────
    private static final Color BG_MAIN   = new Color(0xF9F9F9);
    private static final Color BG_CARD   = Color.WHITE;
    private static final Color BG_HOVER  = new Color(0xEEF4FF);
    private static final Color BG_FILTER = new Color(0xEFEFF4);
    private static final Color ACCENT    = new Color(0x3B82F6);  // blue
    private static final Color TEXT_PRI  = new Color(0x1A1A1A);
    private static final Color TEXT_SEC  = new Color(0x6B7280);
    private static final Color TEXT_HINT = new Color(0x9CA3AF);
    private static final Color BORDER    = new Color(0xE5E7EB);
    private static final Color SUGGEST   = new Color(0xFFFBEB); // amber tint for suggestion
    private static final Color GREEN     = new Color(0x16A34A);

    // ── fonts ──────────────────────────────────────────────────────────────────
    private static final Font FONT_SEARCH  = new Font("Segoe UI", Font.PLAIN, 16);
    private static final Font FONT_RESULT  = new Font("Segoe UI", Font.BOLD,  13);
    private static final Font FONT_PATH    = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_META    = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_STATUS  = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_FILTER  = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_SUGGEST = new Font("Segoe UI", Font.ITALIC, 11);
    private static final Font FONT_SECTION = new Font("Segoe UI", Font.BOLD,  11);

    // ── state ──────────────────────────────────────────────────────────────────
    private Timer  debounceTimer;         // Delays search until typing pauses
    private String activeFilter = "All";  // Current filter button selection
    private List<SearchResult> currentResults = List.of();

    // ── widgets ────────────────────────────────────────────────────────────────
    private JTextField  searchField;
    private JPanel      filterBar;
    private JPanel      resultsPanel;     // Container for result cards
    private JScrollPane resultsScroll;
    private JLabel      statusLabel;
    private JLabel      countLabel;       // Shows result count or index size

    // ─────────────────────────────────────────────────────────────────────────
    public SearchUI() {
        super("FileMind — Personal Search");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);  // hide to tray, don't exit
        setSize(760, 560);
        setMinimumSize(new Dimension(600, 400));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_MAIN);
        setLayout(new BorderLayout(0, 0));

        buildSearchBar();
        buildFilterBar();
        buildResultsArea();
        buildStatusBar();

        // Global keyboard shortcuts
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                    // Escape: clear search or hide window
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        if (!searchField.getText().isEmpty()) {
                            searchField.setText("");
                        } else {
                            setVisible(false);
                        }
                        return true;
                    }
                    // Arrow down from search field → move to results
                    if (e.getKeyCode() == KeyEvent.VK_DOWN && searchField.isFocusOwner()) {
                        focusFirstResult();
                        return true;
                    }
                    return false;
                });

        // Show recent files on startup
        SwingUtilities.invokeLater(this::showRecentFiles);
    }

    // ── search bar ────────────────────────────────────────────────────────────
    private void buildSearchBar() {
        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.setBackground(BG_MAIN);
        searchBar.setBorder(new EmptyBorder(14, 16, 8, 16));

        // Icon (magnifying glass)
        JLabel icon = new JLabel("🔍");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        icon.setPreferredSize(new Dimension(30, 40));
        icon.setHorizontalAlignment(SwingConstants.CENTER);

        // Search field
        searchField = new JTextField();
        searchField.setFont(FONT_SEARCH);
        searchField.setForeground(TEXT_PRI);
        searchField.setBackground(BG_CARD);
        searchField.setCaretColor(ACCENT);
        searchField.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        // Placeholder: show recent when empty
        searchField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { repaint(); }
            @Override public void focusLost(FocusEvent e) {
                if (searchField.getText().isEmpty()) showRecentFiles();
            }
        });

        // Debounced live search — wait 300ms after last keystroke before querying
        debounceTimer = new Timer(300, e -> performSearch());
        debounceTimer.setRepeats(false);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { scheduleSearch(); }
            @Override public void removeUpdate(DocumentEvent e)  { scheduleSearch(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleSearch(); }
        });

        // Clear button (×)
        JButton clearBtn = new JButton("✕");
        clearBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        clearBtn.setForeground(TEXT_SEC);
        clearBtn.setBackground(BG_MAIN);
        clearBtn.setBorder(new EmptyBorder(4, 8, 4, 8));
        clearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearBtn.setFocusable(false);
        clearBtn.addActionListener(e -> searchField.setText(""));

        searchBar.add(icon, BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(clearBtn, BorderLayout.EAST);
        add(searchBar, BorderLayout.NORTH);
    }

    // ── filter bar ────────────────────────────────────────────────────────────
    private void buildFilterBar() {
        filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filterBar.setBackground(BG_MAIN);
        filterBar.setBorder(new EmptyBorder(0, 16, 6, 16));

        String[] filters = {"All", "📕 PDF", "☕ Code", "🖼️ Image",
                "📝 Doc", "📊 Excel", "🎬 Video", "📦 Archive"};
        for (String f : filters) {
            JButton btn = makeFilterButton(f);
            filterBar.add(btn);
        }
        add(filterBar, BorderLayout.CENTER); // temp — will be replaced
    }

    private JButton makeFilterButton(String label) {
        JButton btn = new JButton(label);
        btn.setFont(FONT_FILTER);
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Initially set "All" as active
        updateFilterStyle(btn, label.equals("All") && activeFilter.equals("All"));

        btn.addActionListener(e -> {
            activeFilter = label.replaceAll("[^a-zA-Z ]", "").trim(); // strip emoji
            // Re-style all filter buttons
            for (Component c : filterBar.getComponents()) {
                if (c instanceof JButton fb) {
                    String fl = fb.getText().replaceAll("[^a-zA-Z ]", "").trim();
                    updateFilterStyle(fb, fl.equals(activeFilter));
                }
            }
            performSearch();
        });
        return btn;
    }

    // Sets active (blue) vs inactive (grey) style for filter buttons
    private void updateFilterStyle(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(ACCENT);
            btn.setForeground(Color.WHITE);
            btn.setBorder(new CompoundBorder(
                    new LineBorder(ACCENT, 1, true),
                    new EmptyBorder(4, 12, 4, 12)
            ));
        } else {
            btn.setBackground(BG_FILTER);
            btn.setForeground(TEXT_PRI);
            btn.setBorder(new CompoundBorder(
                    new LineBorder(BORDER, 1, true),
                    new EmptyBorder(4, 12, 4, 12)
            ));
        }
    }

    // ── results area ──────────────────────────────────────────────────────────
    private void buildResultsArea() {
        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBackground(BG_MAIN);
        resultsPanel.setBorder(new EmptyBorder(4, 16, 8, 16));

        resultsScroll = new JScrollPane(resultsPanel);
        resultsScroll.setBorder(null);
        resultsScroll.setBackground(BG_MAIN);
        resultsScroll.getViewport().setBackground(BG_MAIN);
        resultsScroll.getVerticalScrollBar().setUnitIncrement(24);

        // Replace CENTER with a panel that holds both filter bar and results
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(BG_MAIN);
        centerPanel.add(filterBar, BorderLayout.NORTH);
        centerPanel.add(resultsScroll, BorderLayout.CENTER);

        // Fix: remove filterBar from earlier wrong placement
        remove(filterBar);
        add(centerPanel, BorderLayout.CENTER);
    }

    // ── status bar ────────────────────────────────────────────────────────────
    private void buildStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBackground(new Color(0xF3F4F6));
        statusBar.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, BORDER),
                new EmptyBorder(5, 16, 5, 16)
        ));

        statusLabel = new JLabel("Initializing...");
        statusLabel.setFont(FONT_STATUS);
        statusLabel.setForeground(TEXT_SEC);

        countLabel = new JLabel("");
        countLabel.setFont(FONT_STATUS);
        countLabel.setForeground(TEXT_HINT);

        JLabel shortcut = new JLabel("Ctrl+Space to open  |  ↑↓ navigate  |  Enter to open  |  Esc to close");
        shortcut.setFont(FONT_STATUS);
        shortcut.setForeground(TEXT_HINT);

        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(shortcut, BorderLayout.CENTER);
        statusBar.add(countLabel, BorderLayout.EAST);

        add(statusBar, BorderLayout.SOUTH);
    }

    // ── search ────────────────────────────────────────────────────────────────
    private void scheduleSearch() {
        debounceTimer.restart(); // resets 300ms timer on every keystroke
    }

    private void performSearch() {
        String rawQuery = searchField.getText().trim();

        if (rawQuery.isEmpty()) {
            showRecentFiles();
            return;
        }

        // Map active filter label → type hint for NLQueryParser
        String typeHint = switch (activeFilter) {
            case "PDF"     -> "pdf";
            case "Code"    -> "code";
            case "Image"   -> "image";
            case "Doc"     -> "word";
            case "Excel"   -> "excel";
            case "Video"   -> "video";
            case "Archive" -> "zip";
            default        -> null;
        };

        // Build parsed query — NLQueryParser handles natural language
        NLQueryParser.ParsedQuery parsed = NLQueryParser.parse(rawQuery);

        // Override file type from filter bar if user explicitly clicked one
        if (typeHint != null) {
            parsed = new NLQueryParser.ParsedQuery(
                    parsed.luceneQuery(), typeHint,
                    parsed.afterMs(), parsed.beforeMs(),
                    parsed.minSizeBytes(), parsed.maxSizeBytes(),
                    parsed.historyOnly(), parsed.timeOfDayAfterHour(),
                    parsed.timeOfDayBeforeHour(), parsed.folderSearch()
            );
        }

        final NLQueryParser.ParsedQuery finalParsed = parsed;

        // Run search on background thread — never block EDT
        SwingWorker<List<SearchResult>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SearchResult> doInBackground() {
                if (finalParsed.historyOnly()) {
                    // History query — return files from ActivityHistory
                    long afterMs  = finalParsed.afterMs()  != null ? finalParsed.afterMs()  : 0;
                    long beforeMs = finalParsed.beforeMs() != null ? finalParsed.beforeMs() : Long.MAX_VALUE;
                    List<String> paths = ActivityHistory.query(
                            MetadataDB.getConnection(), afterMs, beforeMs,
                            finalParsed.timeOfDayAfterHour(), finalParsed.timeOfDayBeforeHour()
                    );
                    // Convert paths → SearchResult objects (metadata from SQLite)
                    return paths.stream()
                            .map(p -> {
                                Path path = Paths.get(p);
                                try {
                                    long size = Files.exists(path) ? Files.size(path) : 0;
                                    long mod  = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0;
                                    String ext = LuceneIndexer.getExtension(path.getFileName().toString());
                                    return new SearchResult(p, path.getFileName().toString(),
                                            ext, size, mod, null, 1.0f);
                                } catch (IOException e) {
                                    return null;
                                }
                            })
                            .filter(r -> r != null)
                            .toList();
                }
                return LuceneIndexer.search(finalParsed, 100);
            }

            @Override
            protected void done() {
                try {
                    currentResults = get();
                    displayResults(currentResults, rawQuery);
                } catch (Exception e) {
                    setStatus("Search error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void showRecentFiles() {
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                return ActivityHistory.recent(MetadataDB.getConnection(), 10);
            }
            @Override
            protected void done() {
                try {
                    List<String> recent = get();
                    resultsPanel.removeAll();
                    if (recent.isEmpty()) {
                        showPlaceholder("Start typing to search your files...");
                    } else {
                        addSectionHeader("Recently opened");
                        for (String path : recent) {
                            Path p = Paths.get(path);
                            if (!Files.exists(p)) continue;
                            try {
                                String fn  = p.getFileName().toString();
                                String ext = LuceneIndexer.getExtension(fn);
                                long size  = Files.size(p);
                                long mod   = Files.getLastModifiedTime(p).toMillis();
                                SearchResult r = new SearchResult(path, fn, ext, size, mod, null, 0);
                                resultsPanel.add(makeResultCard(r));
                                resultsPanel.add(Box.createVerticalStrut(4));
                            } catch (IOException ignored) {}
                        }
                    }
                    resultsPanel.revalidate();
                    resultsPanel.repaint();
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    // ── result rendering ──────────────────────────────────────────────────────
    private void displayResults(List<SearchResult> results, String query) {
        resultsPanel.removeAll();

        if (results.isEmpty()) {
            showPlaceholder("No results for \"" + query + "\"");
        } else {
            countLabel.setText(results.size() + " result" + (results.size() == 1 ? "" : "s"));
            for (SearchResult r : results) {
                resultsPanel.add(makeResultCard(r));
                resultsPanel.add(Box.createVerticalStrut(4));
            }
        }

        resultsPanel.revalidate();
        resultsPanel.repaint();
        resultsScroll.getVerticalScrollBar().setValue(0);
    }

    private JPanel makeResultCard(SearchResult r) {
        JPanel card = new JPanel(new BorderLayout(10, 0));
        card.setBackground(BG_CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(10, 14, 10, 14)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // ── left: type icon ────────────────────────────────────────
        JLabel iconLabel = new JLabel(r.typeIcon());
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        iconLabel.setPreferredSize(new Dimension(36, 36));
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        card.add(iconLabel, BorderLayout.WEST);

        // ── center: filename + path + suggestion ───────────────────
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(BG_CARD);

        JLabel nameLabel = new JLabel(r.filename());
        nameLabel.setFont(FONT_RESULT);
        nameLabel.setForeground(TEXT_PRI);

        JLabel pathLabel = new JLabel(r.parentFolder());
        pathLabel.setFont(FONT_PATH);
        pathLabel.setForeground(TEXT_SEC);

        centerPanel.add(nameLabel);
        centerPanel.add(Box.createVerticalStrut(2));
        centerPanel.add(pathLabel);

        // Name suggestion row (only if suggestion exists)
        if (r.suggestedName() != null) {
            JPanel suggestRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            suggestRow.setBackground(BG_CARD);
            JLabel suggestLabel = new JLabel("💡 Suggested: " + r.suggestedName());
            suggestLabel.setFont(FONT_SUGGEST);
            suggestLabel.setForeground(new Color(0x92400E)); // amber text
            JButton renameBtn = new JButton("Rename");
            renameBtn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            renameBtn.setForeground(ACCENT);
            renameBtn.setBackground(BG_CARD);
            renameBtn.setBorder(new LineBorder(ACCENT, 1, true));
            renameBtn.setFocusable(false);
            renameBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            renameBtn.addActionListener(e -> renameFile(r, nameLabel));
            suggestRow.add(suggestLabel);
            suggestRow.add(renameBtn);
            centerPanel.add(Box.createVerticalStrut(2));
            centerPanel.add(suggestRow);
        }

        card.add(centerPanel, BorderLayout.CENTER);

        // ── right: size + date ─────────────────────────────────────
        JPanel metaPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        metaPanel.setBackground(BG_CARD);

        JLabel sizeLabel = new JLabel(r.displaySize(), SwingConstants.RIGHT);
        sizeLabel.setFont(FONT_META);
        sizeLabel.setForeground(TEXT_SEC);

        JLabel dateLabel = new JLabel(r.displayDate(), SwingConstants.RIGHT);
        dateLabel.setFont(FONT_META);
        dateLabel.setForeground(TEXT_HINT);

        metaPanel.add(sizeLabel);
        metaPanel.add(dateLabel);
        card.add(metaPanel, BorderLayout.EAST);

        // ── hover effect + click handling ──────────────────────────
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                card.setBackground(BG_HOVER);
                centerPanel.setBackground(BG_HOVER);
                metaPanel.setBackground(BG_HOVER);
            }
            @Override public void mouseExited(MouseEvent e) {
                card.setBackground(BG_CARD);
                centerPanel.setBackground(BG_CARD);
                metaPanel.setBackground(BG_CARD);
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                    openFile(r);
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    showContextMenu(card, e.getX(), e.getY(), r, nameLabel);
                }
            }
        });

        return card;
    }

    private void addSectionHeader(String title) {
        JLabel header = new JLabel(title.toUpperCase());
        header.setFont(FONT_SECTION);
        header.setForeground(TEXT_HINT);
        header.setBorder(new EmptyBorder(4, 0, 6, 0));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(BG_MAIN);
        wrap.add(header, BorderLayout.WEST);
        resultsPanel.add(wrap);
    }

    private void showPlaceholder(String message) {
        JLabel lbl = new JLabel(message, SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lbl.setForeground(TEXT_HINT);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(BG_MAIN);
        wrap.setBorder(new EmptyBorder(60, 0, 0, 0));
        wrap.add(lbl, BorderLayout.CENTER);
        resultsPanel.add(wrap);
        countLabel.setText("");
    }

    // ── file actions ──────────────────────────────────────────────────────────
    private void openFile(SearchResult r) {
        // Record in history (for recent files / activity queries)
        ActivityHistory.recordOpen(MetadataDB.getConnection(), r.path());

        try {
            Desktop.getDesktop().open(new File(r.path()));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Cannot open file:\n" + e.getMessage(),
                    "Open failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showContextMenu(JPanel card, int x, int y, SearchResult r, JLabel nameLabel) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_CARD);

        JMenuItem openItem = new JMenuItem("Open file");
        openItem.addActionListener(e -> openFile(r));

        JMenuItem folderItem = new JMenuItem("Open containing folder");
        folderItem.addActionListener(e -> openContainingFolder(r));

        JMenuItem copyItem = new JMenuItem("Copy path");
        copyItem.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(r.path()), null);
        });

        menu.add(openItem);
        menu.add(folderItem);
        menu.addSeparator();
        menu.add(copyItem);

        if (r.suggestedName() != null) {
            menu.addSeparator();
            JMenuItem renameItem = new JMenuItem("Rename → " + r.suggestedName());
            renameItem.setForeground(new Color(0x92400E));
            renameItem.addActionListener(e -> renameFile(r, nameLabel));
            menu.add(renameItem);
        }

        menu.show(card, x, y);
    }

    private void openContainingFolder(SearchResult r) {
        try {
            File parent = new File(r.path()).getParentFile();
            if (parent != null && parent.exists())
                Desktop.getDesktop().open(parent);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot open folder:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renameFile(SearchResult r, JLabel nameLabel) {
        if (r.suggestedName() == null) return;
        File original = new File(r.path());
        File renamed  = new File(original.getParent(), r.suggestedName());

        int choice = JOptionPane.showConfirmDialog(this,
                "Rename:\n  " + r.filename() + "\nTo:\n  " + r.suggestedName() + "\n\nIn folder: " + original.getParent(),
                "Confirm rename", JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            if (original.renameTo(renamed)) {
                nameLabel.setText(r.suggestedName()); // Update UI
                JOptionPane.showMessageDialog(this, "Renamed successfully.", "Done", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Rename failed — file may be in use.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void focusFirstResult() {
        Component[] comps = resultsPanel.getComponents();
        for (Component c : comps) {
            if (c instanceof JPanel card && !(c instanceof Box.Filler)) {
                card.requestFocusInWindow();
                return;
            }
        }
    }

    // ── public API (called from Main) ─────────────────────────────────────────
    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    public void setIndexCount(int count) {
        SwingUtilities.invokeLater(() ->
                countLabel.setText(count + " files indexed"));
    }
}
