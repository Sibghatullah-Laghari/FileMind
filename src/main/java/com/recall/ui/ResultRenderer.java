package com.recall.ui;

import com.recall.core.SearchResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Custom ListCellRenderer for ResultItem in JList.
 * Renders both result rows (42px) and section headers (22px) with proper styling.
 */
public class ResultRenderer extends JPanel implements ListCellRenderer<ResultItem> {

    private JLabel badgeLabel;
    private JLabel filenameLabel;
    private JLabel snippetLabel;
    private JLabel pathLabel;
    private JLabel sizeLabel;
    private JLabel dateLabel;
    private JLabel headerLabel;

    private boolean isSelected;
    private boolean isHovered;
    private String currentQuery = "";

    public ResultRenderer() {
        setLayout(null);
        setOpaque(true);

        // Result row components
        badgeLabel = new JLabel();
        badgeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        badgeLabel.setVerticalAlignment(SwingConstants.CENTER);
        badgeLabel.setOpaque(true);
        add(badgeLabel);

        filenameLabel = new JLabel();
        filenameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        add(filenameLabel);

        snippetLabel = new JLabel();
        snippetLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        snippetLabel.setForeground(new Color(0x64748b));
        add(snippetLabel);

        pathLabel = new JLabel();
        pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        pathLabel.setForeground(ThemeManager.getTextHint());
        pathLabel.setHorizontalAlignment(SwingConstants.LEFT);
        add(pathLabel);

        sizeLabel = new JLabel();
        sizeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sizeLabel.setForeground(ThemeManager.getTextSecondary());
        sizeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(sizeLabel);

        dateLabel = new JLabel();
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        dateLabel.setForeground(ThemeManager.getTextHint());
        dateLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(dateLabel);

        // Header label
        headerLabel = new JLabel();
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 9));
        headerLabel.setForeground(new Color(0x475569));
        add(headerLabel);
    }

    public void setSearchQuery(String query) {
        this.currentQuery = query;
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends ResultItem> list,
            ResultItem value,
            int index,
            boolean selected,
            boolean cellHasFocus
    ) {
        this.isSelected = selected;

        if (value.isHeader()) {
            renderHeader(value);
        } else {
            renderResult(value, list, index);
        }

        return this;
    }

    private void renderHeader(ResultItem item) {
        // Hide all result components
        badgeLabel.setVisible(false);
        filenameLabel.setVisible(false);
        snippetLabel.setVisible(false);
        pathLabel.setVisible(false);
        sizeLabel.setVisible(false);
        dateLabel.setVisible(false);

        // Show header label
        headerLabel.setVisible(true);
        headerLabel.setText(item.getHeaderText());
        headerLabel.setBounds(12, 4, 200, 18);

        // Header background
        Color headerBg = ThemeManager.isDark() ? new Color(0x162032) : new Color(0xe2e8f0);
        setBackground(headerBg);
        setPreferredSize(new Dimension(0, 22));
    }

    private void renderResult(ResultItem item, JList<? extends ResultItem> list, int index) {
        // Hide header label
        headerLabel.setVisible(false);

        // Show result components
        badgeLabel.setVisible(true);
        filenameLabel.setVisible(true);
        pathLabel.setVisible(true);
        sizeLabel.setVisible(true);
        dateLabel.setVisible(true);

        SearchResult r = item.getResult();

        // Determine background color
        Color bgColor;
        if (isSelected) {
            bgColor = ThemeManager.getResultHover();
        } else {
            bgColor = ThemeManager.getPanelBg();
        }
        setBackground(bgColor);

        // Render badge
        renderBadge(r, 8, 7, 28, 28);

        boolean hasSnippet = r.snippet() != null && !r.snippet().isBlank() && !currentQuery.isEmpty();
        int topMargin = 7;
        int rowHeight = 42;

        if (hasSnippet) {
            // Taller row for snippet
            rowHeight = 60;
            topMargin = 5;

            snippetLabel.setVisible(true);
            String snippetHtml = highlightSnippet(r.snippet(), currentQuery);
            snippetLabel.setText(snippetHtml);
            snippetLabel.setBounds(42, 26, getWidth() - 60, 28);
        } else {
            snippetLabel.setVisible(false);
        }

        // Filename with highlight
        String filename = r.filename();
        String displayName = highlightMatch(filename);
        filenameLabel.setText(displayName);
        filenameLabel.setForeground(ThemeManager.getTextPrimary());
        filenameLabel.setBounds(42, topMargin, Integer.MAX_VALUE, hasSnippet ? 20 : 28);

        // Parent path (right-aligned, truncated)
        String parentPath = formatParentPath(r.parentFolder());
        pathLabel.setText(parentPath);
        pathLabel.setToolTipText(r.parentFolder());
        pathLabel.setBounds(getWidth() - 312, topMargin, 180, 28);

        // Size (right-aligned)
        sizeLabel.setText(r.displaySize());
        sizeLabel.setBounds(getWidth() - 132, topMargin, 50, 28);

        // Date (right-aligned)
        dateLabel.setText(formatDate(r.modifiedMs()));
        dateLabel.setBounds(getWidth() - 82, topMargin, 80, 28);

        setPreferredSize(new Dimension(0, rowHeight));
    }

    private void renderBadge(SearchResult r, int x, int y, int w, int h) {
        badgeLabel.setBounds(x, y, w, h);

        String ext = (r.ext() != null) ? r.ext().toLowerCase() : "?";
        String[] badgeInfo = getBadgeInfo(r, ext);
        String badgeText = badgeInfo[0];
        Color badgeColor = Color.decode(badgeInfo[1]);

        badgeLabel.setText(badgeText);
        badgeLabel.setBackground(badgeColor);
        badgeLabel.setForeground(Color.WHITE);
        badgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 9));
        badgeLabel.setBorder(new EmptyBorder(0, 0, 0, 0));
    }

    private String[] getBadgeInfo(SearchResult r, String ext) {
        // Check if it's a directory
        try {
            if (Files.isDirectory(Paths.get(r.path()))) {
                return new String[]{"FD", "#f59e0b"};  // Folder
            }
        } catch (Exception ignored) {}

        return switch (ext) {
            case "pdf" -> new String[]{"PD", "#ef4444"};      // PDF - red
            case "java" -> new String[]{"JV", "#f97316"};     // Java - orange
            case "py" -> new String[]{"PY", "#f97316"};       // Python - orange
            case "js", "ts" -> new String[]{ext.toUpperCase(), "#3b82f6"};  // JS/TS - blue
            case "cpp", "c", "h" -> new String[]{"C++", "#3b82f6"};        // C++ - blue
            case "go", "rs" -> new String[]{ext.toUpperCase(), "#3b82f6"}; // Go/Rust - blue
            case "png", "jpg", "jpeg", "gif", "bmp", "webp" -> new String[]{"IM", "#a855f7"};  // Image - purple
            case "docx", "doc" -> new String[]{"DC", "#2563eb"};           // Doc - dark blue
            case "xlsx", "xls", "csv" -> new String[]{"XL", "#16a34a"};    // Excel - green
            case "mp4", "avi", "mkv", "mov" -> new String[]{"VD", "#ec4899"};  // Video - pink
            default -> new String[]{ext.length() >= 2 ? ext.substring(0, 2).toUpperCase() : ext.toUpperCase(), "#64748b"};  // Other - gray
        };
    }

    private String formatParentPath(String fullPath) {
        // Show last two path segments only, truncate left
        String[] parts = fullPath.replace("\\", "/").split("/");
        if (parts.length <= 2) {
            return fullPath;
        }
        // Return last two segments
        return ".../" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }

    private String formatDate(long modifiedMs) {
        LocalDateTime dt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(modifiedMs),
                ZoneId.systemDefault()
        );
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate fileDate = dt.toLocalDate();

        if (fileDate.equals(today)) {
            return String.format("Today %02d:%02d", dt.getHour(), dt.getMinute());
        } else if (fileDate.equals(yesterday)) {
            return String.format("Yesterday %02d:%02d", dt.getHour(), dt.getMinute());
        } else {
            return dt.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
        }
    }

    private String highlightMatch(String filename) {
        if (currentQuery.isEmpty()) {
            return filename;
        }

        // Simple case-insensitive highlight
        String lower = filename.toLowerCase();
        String queryLower = currentQuery.toLowerCase();
        int idx = lower.indexOf(queryLower);

        if (idx >= 0) {
            String before = filename.substring(0, idx);
            String match = filename.substring(idx, idx + currentQuery.length());
            String after = filename.substring(idx + currentQuery.length());
            return before + "<b>" + match + "</b>" + after;
        }

        return filename;
    }

    private String highlightSnippet(String snippet, String query) {
        String lower = snippet.toLowerCase();
        String queryLower = query.toLowerCase();
        int idx = lower.indexOf(queryLower);

        if (idx < 0) return escapeHtml(snippet);

        String before = escapeHtml(snippet.substring(0, idx));
        String match = escapeHtml(snippet.substring(idx, idx + query.length()));
        String after = escapeHtml(snippet.substring(idx + query.length()));

        return "<html><body style='margin:0;padding:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;'>"
                + before + "<span style='color:#d97706;font-weight:bold;'>" + match + "</span>" + after
                + "</body></html>";
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&").replace("<", "<").replace(">", ">")
                .replace("\"", "&" + "quot;").replace("'", "'");
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Draw left accent border for selected items
        if (isSelected) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(ThemeManager.getAccent());
            g2.fillRect(0, 0, 3, getHeight());
        }
    }
}

