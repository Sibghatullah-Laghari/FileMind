package com.recall.ui;

import com.recall.ui.design.DesignSystem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.ArrayList;

/**
 * Pill-shaped filter chips for search filtering.
 * Replaces the old category tab bar with modern, tactile chip buttons.
 *
 * Chips: All, Files, Folders, Images, Videos, Code, PDF, Office, Recent, Pinned, Favorites
 */
public class FilterChips extends JPanel {

    // ── Constants ──────────────────────────────────────────────────────────
    private static final int CHIP_GAP = 6;

    private List<ChipButton> chips = new ArrayList<>();
    private String activeChip = "All";
    private ActionListener onChipSelected;

    // ─────────────────────────────────────────────────────────────────────
    public FilterChips() {
        setLayout(new FlowLayout(FlowLayout.LEFT, CHIP_GAP, 0));
        setOpaque(false);
        buildChips();
    }

    private void buildChips() {
        String[] chipNames = {
                "All", "Files", "Folders", "Images", "Videos",
                "Code", "PDF", "Office", "Recent", "Pinned", "Favorites"
        };
        for (String name : chipNames) {
            ChipButton chip = new ChipButton(name);
            chips.add(chip);
            add(chip);
        }
    }

    // ── Custom Chip Button ─────────────────────────────────────────────────
    private class ChipButton extends JToggleButton {
        private final String chipText;
        private boolean isRollover = false;

        ChipButton(String text) {
            super(text);
            this.chipText = text;
            setFont(DesignSystem.FONT_SMALL.deriveFont(Font.PLAIN, 12f));
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(4, 12, 4, 12));
            setContentAreaFilled(false);
            setOpaque(false);

            boolean isActive = text.equals(activeChip);
            setSelected(isActive);

            addActionListener(e -> handleSelection());

            // Rollover detection
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    isRollover = true;
                    repaint();
                }
                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    isRollover = false;
                    repaint();
                }
            });
        }

        private void handleSelection() {
            if (isSelected()) {
                for (ChipButton other : chips) {
                    if (other != this) {
                        other.setSelected(false);
                    }
                }
                setSelected(true);
                activeChip = chipText;
                if (onChipSelected != null) {
                    onChipSelected.actionPerformed(null);
                }
            } else {
                setSelected(true); // Re-select (at least one must stay selected)
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Determine background color
            Color bg;
            if (isSelected()) {
                bg = DesignSystem.surfaceAccent;
            } else if (isRollover) {
                bg = DesignSystem.surfaceHighlight;
            } else {
                bg = DesignSystem.surfaceSecondary;
            }

            // Draw pill background
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w, h, h, h);

            // Draw text
            g2.setColor(isSelected() ? DesignSystem.textOnAccent : DesignSystem.textSecondary);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String text = getText();
            int textX = (w - fm.stringWidth(text)) / 2;
            int textY = (h + fm.getAscent()) / 2 - 2;
            g2.drawString(text, textX, textY);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int textWidth = fm.stringWidth(chipText);
            int w = textWidth + 24;
            int h = 28;
            return new Dimension(w, h);
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────
    public void setOnChipSelectedListener(ActionListener listener) {
        this.onChipSelected = listener;
    }

    public String getActiveChip() {
        return activeChip;
    }

    public void setActiveChip(String chipName) {
        for (ChipButton chip : chips) {
            boolean isActive = chip.chipText.equals(chipName);
            chip.setSelected(isActive);
        }
        activeChip = chipName;
        repaint();
    }

    /**
     * Updates chip colors when theme changes.
     */
    public void updateTheme() {
        for (ChipButton chip : chips) {
            chip.repaint();
        }
    }
}
