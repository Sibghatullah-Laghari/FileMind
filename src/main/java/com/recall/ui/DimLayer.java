package com.recall.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Full-screen semi-transparent dim overlay (macOS Spotlight style).
 * Clicking anywhere on this layer closes the search panel.
 */
public class DimLayer extends JWindow {

    private SearchPanel searchPanel;

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

        setType(Type.UTILITY);
        setFocusableWindowState(false);
    }

    public void updateTheme() {
        repaint();
    }
}

