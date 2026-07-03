package com.recall.ui;

import com.recall.core.SearchResult;
import com.recall.ui.design.DesignSystem;
import com.recall.ui.design.SvgIconProvider;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * macOS Finder-style Quick Look preview panel.
 * Activated by pressing Space on a selected search result.
 *
 * Shows: large file icon, filename, path, size, modified date, and file type info.
 * For images: shows a scaled preview.
 * For text/code: shows first lines (future enhancement).
 *
 * Dismiss: Space again, Escape, or click outside.
 *
 * FIXME: Uses hardcoded colors and sizes; does not respect theme changes.
 * FIXME: Duplicates formatting logic from ResultFormatter (describeFileType, smartDate).
 * FIXME: SvgIconProvider.getIcon() may not be defined; likely should use createLabel or another method.
 * FIXME: ThemeManager class is not imported, so getFileTypeColor() may not compile.
 * FIXME: Key listener on JWindow may not receive events if window loses focus; should add to content pane.
 * FIXME: Does not show actual content preview for text/code files (future enhancement, but noted).
 * FIXME: The infoGrid labels are accessed by hardcoded indices; fragile if layout changes.
 * FIXME: Positioning is not managed; panel appears at default location, not centered relative to parent.
 * FIXME: Does not handle window focus lost to auto-dismiss.
 * FIXME: setAlwaysOnTop(true) may conflict with other always-on-top windows.
 */
public class PreviewPanel extends JWindow {

    private static final int PREVIEW_WIDTH = 520;
    private static final int PREVIEW_HEIGHT = 360;
    private static final int CORNER_RADIUS = 12;

    private SearchResult currentResult;
    private Runnable onDismiss;

    /**
     * Creates a preview panel. Must be positioned before showing.
     */
    public PreviewPanel() {
        setAlwaysOnTop(true);
        setType(Type.UTILITY);
        setSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        setBackground(new Color(0, 0, 0, 0));

        buildUI();

        // Global key listener for dismiss
        // FIXME: Key events may not be received if the window doesn't have focus;
        //        should add listener to the content pane and request focus on show.
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_SPACE) {
                    dismiss();
                }
            }
        });
    }

    private void buildUI() {
        JPanel content = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                int w = getWidth();
                int h = getHeight();

                // Background (dark card)
                // FIXME: Hardcoded color; should use DesignSystem or ThemeManager.
                g2.setColor(new Color(15, 23, 42, 250));
                g2.fillRoundRect(0, 0, w, h, CORNER_RADIUS, CORNER_RADIUS);

                // Border
                g2.setColor(new Color(255, 255, 255, 20));
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(0, 0, w - 1, h - 1, CORNER_RADIUS, CORNER_RADIUS);
            }
        };
        content.setOpaque(false);

        // Top: large icon area
        JPanel iconArea = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (currentResult == null) return;

                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                // Draw large file type icon (64x64)
                int iconSize = 64;
                int cx = (getWidth() - iconSize) / 2;
                int cy = (getHeight() - iconSize) / 2;

                // FIXME: ThemeManager is not imported; this will cause compilation error.
                Color iconColor = ThemeManager.getFileTypeColor(currentResult.ext() != null ? currentResult.ext() : "");

                // Background circle for icon
                g2.setColor(new Color(iconColor.getRed(), iconColor.getGreen(), iconColor.getBlue(), 30));
                g2.fillOval(cx - 8, cy - 8, iconSize + 16, iconSize + 16);

                // Icon (FlatSVGIcon via SvgIconProvider)
                // FIXME: SvgIconProvider.getIcon() may not exist; likely should be createLabel or another factory.
                SvgIconProvider.getIcon("FILE_GENERIC", iconColor).paintIcon(this, g, cx, cy);
            }
        };
        iconArea.setPreferredSize(new Dimension(PREVIEW_WIDTH, 120));
        iconArea.setOpaque(false);
        content.add(iconArea, BorderLayout.NORTH);

        // Center: metadata
        JPanel metaPanel = new JPanel();
        metaPanel.setLayout(new BoxLayout(metaPanel, BoxLayout.Y_AXIS));
        metaPanel.setOpaque(false);
        metaPanel.setBorder(new EmptyBorder(0, 32, 16, 32));

        // Filename
        JLabel nameLabel = new JLabel(" ");
        nameLabel.setFont(DesignSystem.FONT_HEADING);
        nameLabel.setForeground(DesignSystem.textPrimary);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        metaPanel.add(nameLabel);

        metaPanel.add(Box.createVerticalStrut(8));

        // Info rows
        JPanel infoGrid = new JPanel(new GridLayout(0, 2, 8, 4));
        infoGrid.setOpaque(false);

        String[][] infoItems = {
                {"Kind", " "},
                {"Size", " "},
                {"Modified", " "},
                {"Path", " "}
        };

        for (String[] item : infoItems) {
            JLabel keyLabel = new JLabel(item[0]);
            keyLabel.setFont(DesignSystem.FONT_SMALL);
            keyLabel.setForeground(DesignSystem.textTertiary);
            infoGrid.add(keyLabel);

            JLabel valLabel = new JLabel(item[1]);
            valLabel.setFont(DesignSystem.FONT_SMALL);
            valLabel.setForeground(DesignSystem.textSecondary);
            infoGrid.add(valLabel);
        }

        metaPanel.add(infoGrid);
        content.add(metaPanel, BorderLayout.CENTER);

        // Store labels for later update
        content.putClientProperty("nameLabel", nameLabel);
        content.putClientProperty("infoGrid", infoGrid);

        setContentPane(content);
    }

    /**
     * Shows the preview for a given search result.
     * @param result The file to preview.
     * @param onDismiss Callback when preview is dismissed.
     */
    public void showPreview(SearchResult result, Runnable onDismiss) {
        this.currentResult = result;
        this.onDismiss = onDismiss;

        // Update UI with result data
        JPanel content = (JPanel) getContentPane();
        JLabel nameLabel = (JLabel) content.getClientProperty("nameLabel");
        JPanel infoGrid = (JPanel) content.getClientProperty("infoGrid");

        if (nameLabel != null) {
            nameLabel.setText(result.filename());
        }

        if (infoGrid != null) {
            Component[] comps = infoGrid.getComponents();
            // FIXME: Hardcoded indices; if grid layout changes, this breaks.
            if (comps.length >= 8) {
                // Kind
                String kind = describeFileType(result.ext());
                ((JLabel) comps[1]).setText(kind);

                // Size
                ((JLabel) comps[3]).setText(result.displaySize());

                // Modified
                ((JLabel) comps[5]).setText(formatDate(result.modifiedMs()));

                // Path
                ((JLabel) comps[7]).setText(result.parentFolder());
            }
        }

        revalidate();
        repaint();
        setVisible(true);
        requestFocus(); // FIXME: This may not give focus to the window properly; should request focus on the content pane.
    }

    /**
     * Dismisses the preview panel.
     */
    public void dismiss() {
        setVisible(false);
        currentResult = null;
        if (onDismiss != null) {
            onDismiss.run();
            onDismiss = null;
        }
    }

    /**
     * Checks if the preview is currently showing.
     */
    public boolean isShowing() {
        return isVisible();
    }

    // FIXME: Duplicates ResultFormatter.describeFileType() – should reuse to avoid inconsistency.
    private String describeFileType(String ext) {
        if (ext == null || ext.isEmpty()) return "Unknown";
        return switch (ext.toLowerCase()) {
            case "pdf" -> "PDF Document";
            case "doc", "docx" -> "Word Document";
            case "xls", "xlsx", "csv" -> "Excel Spreadsheet";
            case "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg" -> "Image";
            case "mp4", "avi", "mkv", "mov" -> "Video";
            case "mp3", "wav", "flac" -> "Audio";
            case "java" -> "Java Source";
            case "py" -> "Python Script";
            case "js", "ts" -> "JavaScript/TypeScript";
            case "cpp", "c", "h" -> "C/C++ Source";
            case "html", "htm" -> "HTML Document";
            case "md" -> "Markdown Document";
            case "txt" -> "Text Document";
            case "zip", "rar", "7z" -> "Archive";
            case "exe", "app" -> "Executable";
            default -> ext.toUpperCase() + " File";
        };
    }

    // FIXME: Duplicates ResultFormatter.smartDate() – should reuse.
    private String formatDate(long modifiedMs) {
        java.time.LocalDateTime dt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(modifiedMs),
                java.time.ZoneId.systemDefault()
        );
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate fileDate = dt.toLocalDate();

        if (fileDate.equals(today)) {
            return String.format("Today at %02d:%02d", dt.getHour(), dt.getMinute());
        } else if (fileDate.equals(today.minusDays(1))) {
            return String.format("Yesterday at %02d:%02d", dt.getHour(), dt.getMinute());
        } else {
            return dt.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm"));
        }
    }
}