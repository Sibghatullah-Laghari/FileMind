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
 *
 * FIXME: The "Pinned" and "Favorites" chips have no associated data model.
 *        Pinning/favoriting is not implemented in the core system, so these chips
 *        currently have no effect on search results.
 *
 * FIXME: The "All" chip is ambiguous – does it include folders? Files only? Both?
 *        The actual filtering logic is not implemented; this component only stores
 *        the active chip name but does not apply any filter. The parent component
 *        must listen to chip selection and perform filtering.
 *
 * FIXME: The chip selection is not backed by any search state; the active chip is
 *        stored here but the search service does not use it. The listener passes
 *        null ActionEvent, so the parent cannot differentiate which chip was clicked.
 *
 * FIXME: The chip names are hardcoded as strings and not localized. For i18n, they
 *        should be loaded from resource bundles.
 */
public class FilterChips extends JPanel {

    // ── Constants ──────────────────────────────────────────────────────────
    /** Horizontal gap between chips. */
    private static final int CHIP_GAP = 6;

    /** List of all chip buttons. */
    private List<ChipButton> chips = new ArrayList<>();

    /** The name of the currently selected chip. */
    private String activeChip = "All";

    /** Listener invoked when a chip is selected. */
    private ActionListener onChipSelected;

    // ─────────────────────────────────────────────────────────────────────
    /**
     * Constructs the filter chips panel with default set of chips.
     */
    public FilterChips() {
        setLayout(new FlowLayout(FlowLayout.LEFT, CHIP_GAP, 0));
        setOpaque(false);
        buildChips();
    }

    /**
     * Creates and adds all chip buttons.
     * FIXME: The chip list is static; should be configurable or dynamically built
     *        from available filters (e.g., from index statistics).
     */
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
    /**
     * Custom JToggleButton rendered as a pill-shaped chip.
     * Handles selection state, rollover, and painting.
     */
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

            // Rollover detection for visual feedback
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

        /**
         * Handles selection logic: deselect all other chips, update active chip,
         * and fire the listener if set.
         * FIXME: The logic forces at least one chip to be selected (it re‑selects itself
         *        if unselected). This might be confusing if a user tries to deselect all.
         *        Consider allowing no selection or a "clear all" chip.
         *
         * FIXME: The listener is fired with a null ActionEvent, so the parent cannot
         *        distinguish which chip was clicked. A better approach is to include
         *        the chip text in the ActionEvent's command string.
         */
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
                    onChipSelected.actionPerformed(null); // FIXME: Use event with command
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
            int w = textWidth + 24; // Add horizontal padding
            int h = 28; // Fixed height for all chips
            return new Dimension(w, h);
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────
    /**
     * Sets the listener to be invoked when a chip is selected.
     * The ActionEvent will have a null command; use getActiveChip() to know which.
     * FIXME: Consider modifying the listener to include the selected chip name.
     *
     * @param listener the ActionListener to call
     */
    public void setOnChipSelectedListener(ActionListener listener) {
        this.onChipSelected = listener;
    }

    /**
     * Returns the name of the currently active chip.
     *
     * @return the active chip text
     */
    public String getActiveChip() {
        return activeChip;
    }

    /**
     * Programmatically sets the active chip by name.
     * Updates all chip buttons' selection states accordingly.
     *
     * @param chipName the name of the chip to activate
     */
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
     * Repaints all chip buttons to reflect new design system colors.
     * FIXME: This repaints every chip individually; a more efficient approach
     *        would be to repaint the entire panel, but this works.
     */
    public void updateTheme() {
        for (ChipButton chip : chips) {
            chip.repaint();
        }
    }
}