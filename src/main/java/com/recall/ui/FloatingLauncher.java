package com.recall.ui;

import com.recall.ui.design.DesignSystem;
import com.recall.ui.design.SvgIconProvider;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.IOException;
import java.nio.file.*;

/**
 * Premium floating launcher (32x32px) that stays on top of all windows.
 *
 * Design:
 *  - 32px circular button with custom Graphics2D-drawn magnifying glass (no emoji)
 *  - Animated glowing border (gradient rotation over 6s)
 *  - Soft breathing opacity animation (oscillates 0.85-1.0 over 3s)
 *  - Hover: glow intensity increases, subtle scale (1.05x)
 *  - Click: executes an OnExpandCallback to animate the search palette open
 *  - Draggable with position persistence
 *  - Right-click context menu
 */
public class FloatingLauncher extends JWindow {

    // ── Constants ──────────────────────────────────────────────────────────
    private static final int LAUNCHER_SIZE = 32;
    private static final int GLOW_BORDER_WIDTH = 3;

    // Breathing animation
    private static final int BREATH_INTERVAL_MS = 3000;
    private static final float BREATH_MIN_OPACITY = 0.85f;
    private static final float BREATH_MAX_OPACITY = 1.0f;

    // Glow rotation animation
    private static final int GLOW_ROTATION_INTERVAL_MS = 6000;

    // Hover animation
    private static final int HOVER_DURATION_MS = 150;

    // ── Colors ─────────────────────────────────────────────────────────────
    private static final Color ICON_BG_DEFAULT = new Color(0x1e293b);
    private static final Color ICON_BG_HOVER = new Color(0x3b82f6);
    private static final Color GLOW_COLOR_START = new Color(0x3b82f6);
    private static final Color GLOW_COLOR_END = new Color(0x8b5cf6);
    private static final Color ICON_COLOR = Color.WHITE;

    // ── State ──────────────────────────────────────────────────────────────
    private float currentBgR, currentBgG, currentBgB;
    private float glowAngle = 0.0f;
    private float hoverScale = 1.0f;
    private float targetHoverScale = 1.0f;
    private boolean isHovering = false;
    private int dragOffsetX, dragOffsetY;

    // Timers
    private Timer breathingTimer;
    private Timer glowTimer;
    private Timer hoverTimer;

    // Callback for expanding into the search palette
    private Runnable onExpandCallback;

    // ─────────────────────────────────────────────────────────────────────
    public FloatingLauncher() {
        setAlwaysOnTop(true);
        setType(Type.UTILITY);
        setSize(LAUNCHER_SIZE + GLOW_BORDER_WIDTH * 4, LAUNCHER_SIZE + GLOW_BORDER_WIDTH * 4);
        setBackground(new Color(0, 0, 0, 0));

        // Custom panel for drawing backgrounds
        LauncherPanel panel = new LauncherPanel();
        panel.setOpaque(false);
        panel.setLayout(new GridBagLayout());

        // Add FlatSVGIcon-based search icon centered on panel
        JLabel iconLabel = SvgIconProvider.createLabel("SEARCH", ICON_COLOR, 16);
        iconLabel.setOpaque(false);
        panel.add(iconLabel);
        // Store reference for internal use (optional)
        panel.putClientProperty("iconLabel", iconLabel);

        setContentPane(panel);

        // Initialize background color interpolation
        currentBgR = ICON_BG_DEFAULT.getRed() / 255f;
        currentBgG = ICON_BG_DEFAULT.getGreen() / 255f;
        currentBgB = ICON_BG_DEFAULT.getBlue() / 255f;

        setupMouseHandling();
        restorePosition();

        // Start animations
        startBreathingAnimation();
        startGlowAnimation();
    }

    // ── Callback Setter ───────────────────────────────────────────────────
    public void setOnExpandCallback(Runnable callback) {
        this.onExpandCallback = callback;
    }

    // ── Custom Panel ──────────────────────────────────────────────────────
    private class LauncherPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth();
            int h = getHeight();
            int cx = w / 2;
            int cy = h / 2;
            int radius = LAUNCHER_SIZE / 2;

            // Apply hover scale transform
            g2.translate(cx, cy);
            g2.scale(hoverScale, hoverScale);
            g2.translate(-cx, -cy);

            // ── Draw glowing border ──────────────────────────────────
            // Simulate a gradient glow by drawing concentric arcs at different angles
            int glowRadius = radius + GLOW_BORDER_WIDTH;
            for (int i = 0; i < 12; i++) {
                float arcAngle = (float) Math.toDegrees(glowAngle + (i * Math.PI * 2 / 12));
                float alpha = 0.1f + (isHovering ? 0.4f : 0.2f) * (1.0f - (float) i / 12);
                Color glowColor = interpolateColor(GLOW_COLOR_START, GLOW_COLOR_END, (float) i / 12);
                g2.setColor(new Color(glowColor.getRed() / 255f, glowColor.getGreen() / 255f,
                        glowColor.getBlue() / 255f, alpha));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawArc(cx - glowRadius, cy - glowRadius, glowRadius * 2, glowRadius * 2,
                        (int) arcAngle, 30);
            }

            // ── Draw circular background ─────────────────────────────
            g2.setColor(new Color(
                    Math.round(currentBgR * 255),
                    Math.round(currentBgG * 255),
                    Math.round(currentBgB * 255),
                    255
            ));
            g2.fillOval(cx - radius, cy - radius, LAUNCHER_SIZE, LAUNCHER_SIZE);

            // ── Draw subtle inner shadow (radial gradient) ───────────
            g2.setPaint(new RadialGradientPaint(
                    cx - radius / 3, cy - radius / 3, radius,
                    new float[]{0.0f, 0.8f, 1.0f},
                    new Color[]{new Color(255, 255, 255, 30), new Color(0, 0, 0, 0), new Color(0, 0, 0, 60)}
            ));
            g2.fillOval(cx - radius, cy - radius, LAUNCHER_SIZE, LAUNCHER_SIZE);
        }

        private Color interpolateColor(Color c1, Color c2, float t) {
            int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * t);
            int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t);
            int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * t);
            return new Color(r, g, b);
        }
    }

    // ── Mouse Handling ────────────────────────────────────────────────────
    private void setupMouseHandling() {
        JPanel panel = (JPanel) getContentPane();

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovering = true;
                targetHoverScale = 1.05f;
                startHoverColorTransition(ICON_BG_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovering = false;
                targetHoverScale = 1.0f;
                startHoverColorTransition(ICON_BG_DEFAULT);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                dragOffsetX = e.getXOnScreen() - getX();
                dragOffsetY = e.getYOnScreen() - getY();

                if (e.getButton() == MouseEvent.BUTTON3) {
                    showContextMenu(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    // Trigger expand callback instead of opening panel directly
                    if (onExpandCallback != null) {
                        onExpandCallback.run();
                    }
                }
            }
        });

        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int newX = e.getXOnScreen() - dragOffsetX;
                int newY = e.getYOnScreen() - dragOffsetY;
                setLocation(newX, newY);
                savePosition(newX, newY);
            }
        });
    }

    // ── Hover Color Transition ────────────────────────────────────────────
    private void startHoverColorTransition(Color targetColor) {
        if (hoverTimer != null && hoverTimer.isRunning()) {
            hoverTimer.stop();
        }

        final float startR = currentBgR;
        final float startG = currentBgG;
        final float startB = currentBgB;
        final float endR = targetColor.getRed() / 255f;
        final float endG = targetColor.getGreen() / 255f;
        final float endB = targetColor.getBlue() / 255f;

        final int steps = 15;
        final int[] step = {0};

        hoverTimer = new Timer(HOVER_DURATION_MS / steps, e -> {
            step[0]++;
            float t = Math.min(1.0f, (float) step[0] / steps);
            // Ease-out quadratic
            float easedT = -t * (t - 2);

            currentBgR = startR + (endR - startR) * easedT;
            currentBgG = startG + (endG - startG) * easedT;
            currentBgB = startB + (endB - startB) * easedT;

            if (step[0] >= steps) {
                currentBgR = endR;
                currentBgG = endG;
                currentBgB = endB;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        hoverTimer.start();
    }

    // ── Breathing Animation ───────────────────────────────────────────────
    private void startBreathingAnimation() {
        breathingTimer = new Timer(50, e -> {
            long elapsed = System.currentTimeMillis() % BREATH_INTERVAL_MS;
            double phase = (elapsed / (double) BREATH_INTERVAL_MS) * 2 * Math.PI;
            float breathOpacity = BREATH_MIN_OPACITY +
                    (BREATH_MAX_OPACITY - BREATH_MIN_OPACITY) * (0.5f + 0.5f * (float) Math.sin(phase));
            setOpacity(breathOpacity);
        });
        breathingTimer.start();
    }

    // ── Glow Rotation Animation ───────────────────────────────────────────
    private void startGlowAnimation() {
        glowTimer = new Timer(50, e -> {
            long elapsed = System.currentTimeMillis() % GLOW_ROTATION_INTERVAL_MS;
            glowAngle = (float) (elapsed / (double) GLOW_ROTATION_INTERVAL_MS) * (float) (2 * Math.PI);
            repaint();
        });
        glowTimer.start();
    }

    // ── Hover Scale Animation ─────────────────────────────────────────────
    private void updateHoverScale() {
        // Smoothed scale interpolation (runs in glow timer repaint)
        if (hoverScale < targetHoverScale) {
            hoverScale = Math.min(hoverScale + 0.02f, targetHoverScale);
        } else if (hoverScale > targetHoverScale) {
            hoverScale = Math.max(hoverScale - 0.02f, targetHoverScale);
        }
    }

    // ── Position Persistence ──────────────────────────────────────────────
    private void restorePosition() {
        Path configDir = Paths.get(System.getProperty("user.home"), ".filemind");
        Path posFile = configDir.resolve("launcher_pos.conf");

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
        int defaultX = screenSize.width - LAUNCHER_SIZE - GLOW_BORDER_WIDTH * 4 - 20;
        int defaultY = screenSize.height - LAUNCHER_SIZE - GLOW_BORDER_WIDTH * 4 - 20;
        setLocation(defaultX, defaultY);
        savePosition(defaultX, defaultY);
    }

    private void savePosition(int x, int y) {
        try {
            Path configDir = Paths.get(System.getProperty("user.home"), ".filemind");
            Files.createDirectories(configDir);
            Path posFile = configDir.resolve("launcher_pos.conf");
            Files.writeString(posFile, x + "," + y);
        } catch (IOException ignored) {}
    }

    // ── Context Menu ──────────────────────────────────────────────────────
    private void showContextMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem openItem = new JMenuItem("Open FileMind");
        openItem.addActionListener(e -> {
            if (onExpandCallback != null) onExpandCallback.run();
        });
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

    // ── Actions ───────────────────────────────────────────────────────────
    private void showSettings() {
        // Future: integrate new SettingsDialog
        // SettingsDialog dialog = new SettingsDialog(this);
        // dialog.setVisible(true);
    }

    public void stopTimers() {
        if (breathingTimer != null) breathingTimer.stop();
        if (glowTimer != null) glowTimer.stop();
        if (hoverTimer != null) hoverTimer.stop();
    }

    private void exitApp() {
        stopTimers();
        System.exit(0);
    }

    // ── Public API ────────────────────────────────────────────────────────
    public static FloatingLauncher createAndShow() {
        FloatingLauncher launcher = new FloatingLauncher();
        launcher.setVisible(true);
        return launcher;
    }

    /**
     * Returns a Rectangle representing the launcher's current screen position and size.
     * Used for calculating the expand animation into the search palette.
     */
    public Rectangle getLauncherBounds() {
        return new Rectangle(getX(), getY(), LAUNCHER_SIZE + GLOW_BORDER_WIDTH * 4, LAUNCHER_SIZE + GLOW_BORDER_WIDTH * 4);
    }

    public void setHovering(boolean hovering) {
        this.isHovering = hovering;
        if (hovering) {
            targetHoverScale = 1.05f;
            startHoverColorTransition(ICON_BG_HOVER);
        } else {
            targetHoverScale = 1.0f;
            startHoverColorTransition(ICON_BG_DEFAULT);
        }
    }
}
