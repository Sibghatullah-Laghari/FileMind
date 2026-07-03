package com.recall.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import com.recall.ui.design.DesignSystem;

/**
 * Reusable animation utilities for fade-in/out and slide transitions.
 * Now includes support for reduced motion and a conceptual spring easing.
 *
 * FIXME: This utility assumes all animations are applied to JWindow components.
 *        For JPanel or other components, it's not directly usable.
 *        Should be more generic or split into different classes.
 *
 * FIXME: The springEaseOut function is clamped to [0,1] to avoid invalid opacity values,
 *        but this breaks the overshoot effect, making it a normal ease-out.
 *        Consider using a different approach for spring animations or allowing
 *        overshoot for bounds but not opacity.
 *
 * TODO: Add support for standard Swing components (JPanel) using alpha composite or
 *       custom painting instead of window opacity.
 */
public class AnimationUtil {

    // --- Reduced Motion Setting ---------------------------------------------------------
    // This can be set via a user preference in settings or system property.
    // For now, it's a simple flag.
    private static boolean isReducedMotion = false;

    /**
     * Returns whether the user prefers reduced motion.
     * Should be read from system preferences or user settings.
     *
     * FIXME: This hardcoded flag does not reflect actual system settings.
     *        Should use Toolkit.getDefaultToolkit().getDesktopProperty("awt.animation.auto")
     *        or a similar mechanism.
     *
     * @return true if reduced motion is enabled, false otherwise
     */
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
     *
     * FIXME: Clamping destroys the spring overshoot effect. For opacity, we need to
     *        either use a different approach (e.g., allow overshoot only for bounds)
     *        or accept that it's not a true spring for opacity.
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
     *
     * FIXME: On platforms that do not support translucency, setting opacity to 0
     *        will hide the window but it will not be faded; the change is abrupt.
     *
     * @param window the JWindow whose opacity to set
     * @param rawOpacity desired opacity (will be clamped to [0,1])
     */
    private static void safeSetOpacity(JWindow window, float rawOpacity) {
        float opacity = Math.max(0f, Math.min(1f, rawOpacity));
        if (GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().isWindowTranslucencySupported(
                        GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
            window.setOpacity(opacity);
        } else if (opacity < 0.5f) {
            // FIXME: If translucency not supported, we cannot smoothly fade out.
            //        This fallback simply hides the window when opacity would be <0.5,
            //        causing a sudden disappearance.
            window.setVisible(false);
        }
    }

    /**
     * Fade in a component over the specified duration.
     * @param component The component to fade in (typically a JWindow)
     * @param durationMs Total duration in milliseconds
     * @param onComplete Callback to run when animation finishes
     *
     * FIXME: The animation uses a fixed number of steps (15) and computes step duration
     *        as durationMs / steps. This can lead to non‑smooth animations if the
     *        step duration is not a multiple of the timer interval (currently using
     *        DesignSystem.FPS_INTERVAL_MS but not used here). The timer is created
     *        with a fixed delay per step, not per frame.
     *
     * FIXME: The timer interval is hardcoded to durationMs/steps, which may be too short
     *        (e.g., 1ms) and cause high CPU usage. Should use a fixed frame rate (e.g., 16ms)
     *        and interpolate based on elapsed time.
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
     * FIXME: Same issues as fadeIn: fixed steps, no frame‑based interpolation.
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
     * @param panel Panel to animate (must be a JWindow)
     * @param startY Starting Y position
     * @param endY Ending Y position
     * @param durationMs Total animation duration
     * @param onComplete Callback when animation finishes
     *
     * FIXME: The animation uses the timer's step duration equal to FPS_INTERVAL_MS,
     *        which is likely 16ms, but the total steps are not fixed; it stops based on time.
     *        That's better than the fade methods, but still uses a fixed frame interval.
     *        Also, it assumes the component's X coordinate remains constant.
     *
     * FIXME: No check for translucency support is performed; safeSetOpacity handles it.
     *        But if translucency is not supported, the fade effect will be abrupt.
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
     * FIXME: Same issues as slideAndFadeIn, plus it slides up by a fixed number of pixels
     *        rather than to a target coordinate.
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
     *
     * FIXME: This method uses springEaseOut which is clamped, so the spring effect is lost.
     *        For bounds, we could allow overshoot and then snap back, but the current
     *        implementation clamps the eased value, making it a normal ease‑out.
     *
     * FIXME: The component is made fully opaque at the end; but if the animation is
     *        interrupted, the opacity may remain partially set.
     *
     * FIXME: No guard against negative width/height if the interpolated value goes negative
     *        (though we clamp width/height to 1).
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
                    float easedT = springEaseOut(t); // Clamped, so no overshoot

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