package com.recall.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import com.recall.ui.design.DesignSystem;

/**
 * Reusable animation utilities for fade-in/out and slide transitions.
 * Now includes support for reduced motion and a conceptual spring easing.
 */
public class AnimationUtil {

    // --- Reduced Motion Setting ---------------------------------------------------------
    // This can be set via a user preference in settings or system property.
    // For now, it's a simple flag.
    private static boolean isReducedMotion = false;

    public static boolean prefersReducedMotion() {
        // In a real app, this might check system settings or user preferences.
        // For example: `Toolkit.getDefaultToolkit().getDesktopProperty("awt.animation.auto")` on macOS
        // or a custom user setting loaded from config.
        return isReducedMotion;
    }

    public static void setReducedMotion(boolean reducedMotion) {
        isReducedMotion = reducedMotion;
    }

    // --- Easing Functions (Conceptual) --------------------------------------------------
    // These are mathematical functions that describe the acceleration and deceleration
    // of an animation. 't' is the normalized time (0.0 to 1.0).

    /**
     * Easing function: Ease-Out Quad.
     * Starts fast, ends slow.
     * @param t Normalized time (0.0 to 1.0)
     * @return Eased value
     */
    private static float easeOutQuad(float t) {
        return -t * (t - 2);
    }

    /**
     * Easing function: Ease-In-Out Cubic.
     * Starts slow, speeds up, ends slow.
     * @param t Normalized time (0.0 to 1.0)
     * @return Eased value
     */
    private static float easeInOutCubic(float t) {
        return (t < 0.5f) ? (4 * t * t * t) : (1 - (float) Math.pow(-2 * t + 2, 3) / 2);
    }

    /**
     * Easing function: Spring Ease-Out (overshoot).
     * Clamped to [0, 1] because Window.setOpacity rejects values outside that range.
     * @param t Normalized time (0.0 to 1.0)
     * @return Eased value clamped to [0, 1]
     */
    private static float springEaseOut(float t) {
        float c1 = 1.70158f;
        float c2 = c1 * 1.525f;
        float v = (float) (1 + c1 * Math.pow(t - 1, 3) + c2 * Math.pow(t - 1, 2));
        return Math.max(0f, Math.min(1f, v));
    }


    /**
     * Sets window opacity with clamping and platform support check.
     * Falls back to visibility-only if per-pixel translucency is unsupported.
     */
    private static void safeSetOpacity(JWindow window, float rawOpacity) {
        float opacity = Math.max(0f, Math.min(1f, rawOpacity));
        if (GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().isWindowTranslucencySupported(
                        GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
            window.setOpacity(opacity);
        } else if (opacity < 0.5f) {
            window.setVisible(false);
        }
    }

    /**
     * Fade in a component over the specified duration.
     * @param component The component to fade in (typically a JWindow)
     * @param durationMs Total duration in milliseconds
     * @param onComplete Callback to run when animation finishes
     */
    public static void fadeIn(JWindow component, int durationMs, Runnable onComplete) {
        if (prefersReducedMotion()) {
            safeSetOpacity(component, 1.0f);
            if (onComplete != null) onComplete.run();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            final int steps = 15;
            final int stepDuration = durationMs / steps;
            final float[] opacity = {0.0f};
            final float increment = 1.0f / steps;

            Timer timer = new Timer(stepDuration, null);
            timer.addActionListener(e -> {
                opacity[0] += increment;
                if (opacity[0] >= 1.0f) {
                    opacity[0] = 1.0f;
                    ((Timer) e.getSource()).stop();
                    if (onComplete != null) onComplete.run();
                }
                safeSetOpacity(component, opacity[0]);
            });
            timer.start();
        });
    }

    /**
     * Fade out a component over the specified duration.
     */
    public static void fadeOut(JWindow component, int durationMs, Runnable onComplete) {
        if (prefersReducedMotion()) {
            safeSetOpacity(component, 0.0f);
            if (onComplete != null) onComplete.run();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            final int steps = 15;
            final int stepDuration = durationMs / steps;
            final float[] opacity = {1.0f};
            final float decrement = 1.0f / steps;

            Timer timer = new Timer(stepDuration, null);
            timer.addActionListener(e -> {
                opacity[0] -= decrement;
                if (opacity[0] <= 0.0f) {
                    opacity[0] = 0.0f;
                    ((Timer) e.getSource()).stop();
                    if (onComplete != null) onComplete.run();
                }
                safeSetOpacity(component, opacity[0]);
            });
            timer.start();
        });
    }

    /**
     * Slide and fade in a panel simultaneously with an ease-out-quad easing.
     * @param panel Panel to animate
     * @param startY Starting Y position
     * @param endY Ending Y position
     * @param durationMs Total animation duration
     * @param onComplete Callback when animation finishes
     */
    public static void slideAndFadeIn(JWindow panel, int startY, int endY, int durationMs, Runnable onComplete) {
        if (prefersReducedMotion()) {
            panel.setLocation(panel.getX(), endY);
            safeSetOpacity(panel, 1.0f);
            if (onComplete != null) onComplete.run();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            final long startTime = System.currentTimeMillis();
            final Timer timer = new Timer(DesignSystem.FPS_INTERVAL_MS, null);

            ActionListener listener = new ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float t = Math.min(1.0f, (float) elapsed / durationMs);
                    float easedT = easeOutQuad(t);

                    safeSetOpacity(panel, easedT);
                    int currentY = startY + (int) ((endY - startY) * easedT);
                    panel.setLocation(panel.getX(), currentY);

                    if (t >= 1.0f) {
                        timer.stop();
                        if (onComplete != null) onComplete.run();
                    }
                }
            };
            timer.addActionListener(listener);
            timer.start();
        });
    }

    /**
     * Slide up and fade out a panel simultaneously with an ease-out-quad easing.
     */
    public static void slideUpAndFadeOut(JWindow panel, int slidePixels, int durationMs, Runnable onComplete) {
        if (prefersReducedMotion()) {
            safeSetOpacity(panel, 0.0f);
            if (onComplete != null) onComplete.run();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            final int startY = panel.getY();
            final int endY = startY - slidePixels;
            final long startTime = System.currentTimeMillis();
            final Timer timer = new Timer(DesignSystem.FPS_INTERVAL_MS, null);

            ActionListener listener = new ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float t = Math.min(1.0f, (float) elapsed / durationMs);
                    float easedT = easeOutQuad(t);

                    safeSetOpacity(panel, 1.0f - easedT);
                    int currentY = startY + (int) ((endY - startY) * easedT);
                    panel.setLocation(panel.getX(), currentY);

                    if (t >= 1.0f) {
                        timer.stop();
                        if (onComplete != null) onComplete.run();
                    }
                }
            };
            timer.addActionListener(listener);
            timer.start();
        });
    }

    /**
     * Animates a JWindow's bounds (position and size) from start to end with spring easing.
     * This is designed for the FloatingLauncher expanding into the SearchPalette.
     *
     * @param component The JWindow to animate.
     * @param startBounds Initial bounds (x, y, width, height).
     * @param endBounds Final bounds (x, y, width, height).
     * @param durationMs Duration of the animation in milliseconds.
     * @param onComplete Callback to execute when animation finishes.
     */
    public static void springAndFadeInBounds(JWindow component, Rectangle startBounds, Rectangle endBounds, int durationMs, Runnable onComplete) {
        if (prefersReducedMotion()) {
            component.setBounds(endBounds);
            safeSetOpacity(component, 1.0f);
            if (onComplete != null) onComplete.run();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            final long startTime = System.currentTimeMillis();
            final Timer timer = new Timer(DesignSystem.FPS_INTERVAL_MS, null);

            // Set initial state
            component.setBounds(startBounds);
            safeSetOpacity(component, 0.0f);

            ActionListener listener = new ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float t = Math.min(1.0f, (float) elapsed / durationMs);
                    float easedT = springEaseOut(t);

                    // Interpolate bounds (clamped to prevent invalid coordinates)
                    int x = (int) (startBounds.x + (endBounds.x - startBounds.x) * easedT);
                    int y = (int) (startBounds.y + (endBounds.y - startBounds.y) * easedT);
                    int width = Math.max(1, (int) (startBounds.width + (endBounds.width - startBounds.width) * easedT));
                    int height = Math.max(1, (int) (startBounds.height + (endBounds.height - startBounds.height) * easedT));

                    component.setBounds(x, y, width, height);
                    safeSetOpacity(component, easedT);

                    if (t >= 1.0f) {
                        timer.stop();
                        if (onComplete != null) onComplete.run();
                    }
                }
            };
            timer.addActionListener(listener);
            timer.start();
        });
    }
}
