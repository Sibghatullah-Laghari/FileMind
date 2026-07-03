package com.recall.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Full-screen semi-transparent dim overlay (macOS Spotlight style).
 * Clicking anywhere on this layer closes the search panel.
 *
 * This overlay is a JWindow that covers the entire screen with a translucent
 * background, providing a focus‑dimming effect when the search palette is open.
 *
 * FIXME: The overlay is created with a reference to SearchPanel, but the close()
 *        method is called directly. This creates a tight coupling between the
 *        overlay and the search panel; a more decoupled approach via EventBus or
 *        a listener would be better.
 *
 * FIXME: The overlay uses JWindow.setOpacity(0.0f) initially, but opacity changes
 *        are not animated. It would be more polished to fade in the dim layer
 *        using AnimationUtil.
 *
 * FIXME: The overlay sets focusableWindowState(false), which prevents it from
 *        stealing focus, but it also means keyboard events (e.g., Escape) are not
 *        captured. The parent SearchPanel should handle Escape to close.
 *
 * TODO: Add support for multi‑monitor setups: the overlay should cover all screens,
 *       not just the default one. Currently uses the default screen's bounds.
 */
public class DimLayer extends JWindow {

    /** Reference to the search panel to close when the overlay is clicked. */
    private SearchPanel searchPanel;

    /**
     * Constructs the dim layer overlay.
     *
     * @param searchPanel the SearchPanel to be closed when the overlay is clicked
     */
    public DimLayer(SearchPanel searchPanel) {
        super();
        this.searchPanel = searchPanel;

        // Make it transparent and capture all screen space
        setOpacity(0.0f);
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle bounds = gc.getBounds();
        setBounds(bounds);

        // Setup background
        JPanel dimPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(ThemeManager.getOverlayBg());
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        dimPanel.setOpaque(false);
        dimPanel.setBackground(new Color(0, 0, 0, 0));
        setContentPane(dimPanel);

        // Click anywhere to dismiss
        dimPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                searchPanel.close();
            }
        });

        // Ensure the window doesn't interfere with normal window focus
        setType(Type.UTILITY);
        setFocusableWindowState(false);
    }

    /**
     * Repaint the overlay to reflect a theme change.
     * This method is called when the application theme changes.
     *
     * FIXME: The paintComponent uses ThemeManager.getOverlayBg() each time,
     *        which is fine, but the overlay may not be repainted if the theme
     *        changes while it is shown. Calling repaint() directly is correct.
     */
    public void updateTheme() {
        repaint();
    }
}