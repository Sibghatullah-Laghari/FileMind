package com.recall.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.nio.file.*;
import java.util.prefs.Preferences;

/**
 * Floating circular icon (48x48px) that stays on top of all windows.
 *
 * Features:
 *  - Navy blue (#1e293b) circle with white magnifying glass emoji (\uD83D\uDD0D)
 *  - Always on top, draggable, remembers position
 *  - Hover effect: smoothly transitions to blue (#3b82f6) in 150ms
 *  - Pulsing animation: scales 1.1x every 4 seconds
 *  - Right-click menu: Open FileMind, Settings, Exit
 *  - Left-click: opens SearchPanel
 *
 * FIXME: This class is tightly coupled to SearchPanel via getInstance().open().
 *        Should use a service/controller or EventBus to decouple UI components.
 *
 * FIXME: The position is saved to a file on every drag, which may cause disk I/O
 *        performance issues. Consider using Preferences API or throttling saves.
 *
 * FIXME: The pulse animation is always running, even when the icon is not visible
 *        or the app is idle, consuming CPU. Should pause when not needed.
 *
 * FIXME: The icon uses a hardcoded emoji font "Segoe UI Emoji" which may not be
 *        available on all platforms. Consider using a fallback or rendering the
 *        magnifying glass as a shape/icon.
 *
 * FIXME: The exit() method calls System.exit(0) directly, which is abrupt.
 *        Should perform graceful shutdown (e.g., close Lucene index, save state).
 */
public class FloatingIcon extends JWindow {

    // ── Constants ──────────────────────────────────────────────────────────
    /** Size of the floating icon (square). */
    private static final int ICON_SIZE = 48;
    private static final Color COLOR_DEFAULT = new Color(0x1e293b);  // navy
    private static final Color COLOR_HOVER   = new Color(0x3b82f6);  // blue
    private static final int HOVER_DURATION = 150;
    private static final int HOVER_STEPS = 10;
    private static final String MAGNIFYING_GLASS = "\uD83D\uDD0D";
    private static final int PULSE_INTERVAL = 4000; // 4 seconds

    // ── State ──────────────────────────────────────────────────────────────
    /** Interpolated RGB values for hover animation. */
    private float currentR, currentG, currentB;

    /** Drag offset for moving the icon. */
    private int dragOffsetX, dragOffsetY;

    /** Timer for hover animation. */
    private Timer hoverTimer;

    /** Timer for pulse animation. */
    private Timer pulseTimer;

    /** Current pulse scale factor (1.0 to 1.1). */
    private float pulseScale = 1.0f;

    /** Current step in hover animation (0..HOVER_STEPS). */
    private int hoverStep = 0;

    /** Whether the mouse is currently hovering. */
    private boolean isHovering = false;

    // ─────────────────────────────────────────────────────────────────────
    public FloatingIcon() {
        // Make it transparent and always on top
        setAlwaysOnTop(true);
        setType(Type.UTILITY);
        setSize(ICON_SIZE, ICON_SIZE);
        setLocationRelativeTo(null);  // temp, will be overridden from config

        // Transparency setup: make window background transparent
        setOpacity(1.0f);
        setBackground(new Color(0, 0, 0, 0));  // Fully transparent background

        // Custom panel for drawing
        JPanel panel = new IconPanel();
        panel.setOpaque(false);
        setContentPane(panel);

        // Initialize color interpolation
        currentR = COLOR_DEFAULT.getRed() / 255f;
        currentG = COLOR_DEFAULT.getGreen() / 255f;
        currentB = COLOR_DEFAULT.getBlue() / 255f;

        // Setup input handling
        setupMouseHandling();

        // Restore position from config
        restorePosition();

        // Start pulsing animation
        startPulseAnimation();
    }

    // ── Painting ───────────────────────────────────────────────────────────
    private class IconPanel extends JPanel {
        IconPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            // Don't call super - we want full transparency
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Apply pulse scale transform (centered)
            g2.translate(w / 2, h / 2);
            g2.scale(pulseScale, pulseScale);
            g2.translate(-w / 2, -h / 2);

            // Draw circular background
            g2.setColor(new Color(
                    Math.round(currentR * 255),
                    Math.round(currentG * 255),
                    Math.round(currentB * 255),
                    255
            ));
            g2.fillOval(0, 0, w, h);

            // Draw magnifying glass emoji
            g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
            FontMetrics fm = g2.getFontMetrics();
            int textX = (w - fm.stringWidth(MAGNIFYING_GLASS)) / 2;
            int textY = ((h - fm.getHeight()) / 2) + fm.getAscent();
            g2.setColor(Color.WHITE);
            g2.drawString(MAGNIFYING_GLASS, textX, textY);
        }
    }

    // ── Mouse Handling ─────────────────────────────────────────────────────
    private void setupMouseHandling() {
        JPanel contentPanel = (JPanel) getContentPane();

        contentPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (hoverTimer != null) hoverTimer.stop();
                isHovering = true;
                hoverStep = 0;
                hoverTimer = new Timer(HOVER_DURATION / HOVER_STEPS, evt -> animateHoverIn());
                hoverTimer.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverTimer != null) hoverTimer.stop();
                isHovering = false;
                hoverStep = HOVER_STEPS;
                hoverTimer = new Timer(HOVER_DURATION / HOVER_STEPS, evt -> animateHoverOut());
                hoverTimer.start();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                dragOffsetX = e.getXOnScreen() - getX();
                dragOffsetY = e.getYOnScreen() - getY();

                if (e.getButton() == MouseEvent.BUTTON3) {
                    // Right-click context menu
                    showContextMenu(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    // Left-click: open search panel
                    openSearchPanel();
                }
            }
        });

        contentPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int newX = e.getXOnScreen() - dragOffsetX;
                int newY = e.getYOnScreen() - dragOffsetY;
                setLocation(newX, newY);
                savePosition(newX, newY);
            }
        });
    }

    // ── Hover animation ────────────────────────────────────────────────────
    private void animateHoverIn() {
        if (hoverStep >= HOVER_STEPS) {
            if (hoverTimer != null) hoverTimer.stop();
            return;
        }
        hoverStep++;
        interpolateColor(hoverStep / (float) HOVER_STEPS);
        repaint();
    }

    private void animateHoverOut() {
        if (hoverStep <= 0) {
            if (hoverTimer != null) hoverTimer.stop();
            return;
        }
        hoverStep--;
        interpolateColor(hoverStep / (float) HOVER_STEPS);
        repaint();
    }

    private void interpolateColor(float t) {
        // Linear interpolation from default to hover color
        currentR = COLOR_DEFAULT.getRed() / 255f + (COLOR_HOVER.getRed() / 255f - COLOR_DEFAULT.getRed() / 255f) * t;
        currentG = COLOR_DEFAULT.getGreen() / 255f + (COLOR_HOVER.getGreen() / 255f - COLOR_DEFAULT.getGreen() / 255f) * t;
        currentB = COLOR_DEFAULT.getBlue() / 255f + (COLOR_HOVER.getBlue() / 255f - COLOR_DEFAULT.getBlue() / 255f) * t;
    }

    // ── Pulse animation ────────────────────────────────────────────────────
    private void startPulseAnimation() {
        pulseTimer = new Timer(50, e -> {
            // Smooth sine wave pulse: scales from 1.0 to 1.1 and back
            long elapsed = System.currentTimeMillis() % PULSE_INTERVAL;
            double phase = (elapsed / (double) PULSE_INTERVAL) * 2 * Math.PI;
            pulseScale = 1.0f + 0.1f * (float) Math.sin(phase);
            repaint();
        });
        pulseTimer.start();
    }

    // ── Position persistence ──────────────────────────────────────────────
    private void restorePosition() {
        Path configDir = Paths.get(System.getProperty("user.home"), ".filemind");
        Path posFile = configDir.resolve("icon_pos.conf");

        if (Files.exists(posFile)) {
            try {
                String content = Files.readString(posFile).trim();
                String[] parts = content.split(",");
                if (parts.length == 2) {
                    int x = Integer.parseInt(parts[0].trim());
                    int y = Integer.parseInt(parts[1].trim());
                    setLocation(x, y);
                    return;
                }
            } catch (Exception ignored) {}
        }

        // Default: bottom-right, 20px from edges
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int defaultX = screenSize.width - ICON_SIZE - 20;
        int defaultY = screenSize.height - ICON_SIZE - 20;
        setLocation(defaultX, defaultY);
        savePosition(defaultX, defaultY);
    }

    private void savePosition(int x, int y) {
        try {
            Path configDir = Paths.get(System.getProperty("user.home"), ".filemind");
            Files.createDirectories(configDir);
            Path posFile = configDir.resolve("icon_pos.conf");
            Files.writeString(posFile, x + "," + y);
        } catch (IOException ignored) {}
    }

    // ── Context Menu ───────────────────────────────────────────────────────
    private void showContextMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem openItem = new JMenuItem("Open FileMind");
        openItem.addActionListener(e -> openSearchPanel());
        menu.add(openItem);

        JMenuItem settingsItem = new JMenuItem("Settings");
        settingsItem.addActionListener(e -> showSettings());
        menu.add(settingsItem);

        menu.addSeparator();

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> exitApp());
        menu.add(exitItem);

        menu.show(this, x, y);
    }

    // ── Actions ────────────────────────────────────────────────────────────
    private void openSearchPanel() {
        SearchPanel.getInstance().open(); // FIXME: Static singleton call – tight coupling
    }

    private void showSettings() {
        SettingsDialog dialog = new SettingsDialog(this);
        dialog.setVisible(true);
    }

    private void exitApp() {
        // FIXME: This is an abrupt exit. Should perform graceful shutdown.
        if (pulseTimer != null) pulseTimer.stop();
        if (hoverTimer != null) hoverTimer.stop();
        System.exit(0);
    }

    // ── Public API ─────────────────────────────────────────────────────
    public static FloatingIcon createAndShow() {
        FloatingIcon icon = new FloatingIcon();
        icon.setVisible(true);
        return icon;
    }
}