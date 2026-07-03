package com.recall.ui;

import com.recall.ui.design.DesignSystem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Dedicated AI suggestion section for the search palette.
 * Shows contextual suggestion cards when search is empty or when NL is detected.
 *
 * Cards:
 *  - "Find the PDF edited yesterday"
 *  - "Open my Java project"
 *  - "Show invoices from last month"
 *  - "Summarize this folder"
 *
 * This is UI only — no backend AI implementation.
 *
 * FIXME: This component does not integrate with the actual search logic.
 *        Clicking a suggestion prints to console but does not trigger a search.
 *        Needs a callback or listener to update the search field or execute search.
 */
public class AISection extends JPanel {

    private static final int SECTION_HEIGHT = 90;
    private boolean isExpanded = false; // FIXME: Unused field – remove or implement toggle logic.

    /**
     * Creates the AI section (initially collapsed).
     */
    public AISection() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setVisible(false); // Hidden by default
    }

    /**
     * Shows the AI suggestion section with default cards.
     * Rebuilds the UI each time – could be optimized with a single build and show/hide.
     */
    public void showSuggestions() {
        removeAll();
        setVisible(true);

        // Header
        JLabel header = new JLabel("\u2728 AI Suggestions");
        header.setFont(DesignSystem.FONT_SMALL.deriveFont(Font.BOLD, 11f));
        header.setForeground(DesignSystem.surfaceAccent);
        header.setBorder(new EmptyBorder(4, 12, 4, 12));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(header);

        // Suggestions cards row
        JPanel cardsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        cardsRow.setOpaque(false);

        String[][] suggestions = {
                {"Find the PDF edited yesterday", "\uD83D\uDCC4"},
                {"Open my Java project", "\u2615"},
                {"Show invoices", "\uD83D\uDCCB"},
                {"Summarize this folder", "\uD83D\uDCC2"}
        };

        for (String[] suggestion : suggestions) {
            cardsRow.add(createSuggestionCard(suggestion[1] + "  " + suggestion[0], suggestion[0]));
        }

        add(cardsRow);

        revalidate();
        repaint();
    }

    /**
     * Shows a natural language interpretation during a search.
     * @param interpretation The interpreted query text.
     */
    public void showInterpretation(String interpretation) {
        removeAll();
        setVisible(true);

        JLabel label = new JLabel("\u2728 " + interpretation);
        label.setFont(DesignSystem.FONT_SMALL.deriveFont(Font.ITALIC, 11f));
        label.setForeground(DesignSystem.textTertiary);
        label.setBorder(new EmptyBorder(4, 12, 4, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(label);

        revalidate();
        repaint();
    }

    /**
     * Hides the AI section.
     */
    public void hideSection() {
        setVisible(false);
        removeAll();
        revalidate();
        repaint();
    }

    /**
     * Toggles the AI section visibility.
     * FIXME: Toggle does not actually expand/collapse; just shows/hides.
     *        The `isExpanded` field is unused and should control a smooth animation.
     */
    public void toggle() {
        if (isVisible()) {
            hideSection();
        } else {
            showSuggestions();
        }
    }

    /**
     * Creates a pill-shaped suggestion button.
     * FIXME: The button's action command is set but never used to trigger search.
     *        Need to wire this to the parent search field or a controller.
     */
    private JButton createSuggestionCard(String displayText, String actionCommand) {
        JButton card = new JButton(displayText);
        card.setFont(DesignSystem.FONT_SMALL);
        card.setForeground(DesignSystem.textSecondary);
        card.setBackground(DesignSystem.surfaceSecondary);
        card.setBorder(new EmptyBorder(6, 12, 6, 12));
        card.setContentAreaFilled(false);
        card.setOpaque(false);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setFocusPainted(false);
        card.setActionCommand(actionCommand);

        // Custom pill-shaped painting
        card.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = c.getWidth();
                int h = c.getHeight();

                // Background
                ButtonModel model = ((AbstractButton) c).getModel();
                if (model.isRollover()) {
                    g2.setColor(DesignSystem.surfaceHighlight);
                } else {
                    g2.setColor(DesignSystem.surfaceSecondary);
                }
                g2.fillRoundRect(0, 0, w, h, h, h);

                // Text
                g2.setColor(DesignSystem.textSecondary);
                g2.setFont(c.getFont());
                FontMetrics fm = g2.getFontMetrics();
                String text = ((AbstractButton) c).getText();
                int textX = 12;
                int textY = (h + fm.getAscent()) / 2 - 2;
                g2.drawString(text, textX, textY);
            }
        });

        card.addActionListener(e -> {
            // Placeholder: when clicked, set the search field text
            // This should be connected to the parent SearchPalette
            System.out.println("AI suggestion clicked: " + e.getActionCommand());
            // FIXME: This does nothing useful. Should either set the search text or execute a search.
        });

        // Rollover listener for repaint
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                card.repaint();
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                card.repaint();
            }
        });

        return card;
    }

    @Override
    public Dimension getPreferredSize() {
        if (isVisible()) {
            return new Dimension(super.getPreferredSize().width, SECTION_HEIGHT);
        }
        return new Dimension(0, 0);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}