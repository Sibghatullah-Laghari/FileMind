package com.recall.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Reusable animation utilities for fade-in/out and slide transitions.
 */
public class AnimationUtil {

    /**
     * Fade in a component over the specified duration.
     * @param component The component to fade in (typically a JWindow)
     * @param durationMs Total duration in milliseconds
     * @param onComplete Callback to run when animation finishes
     */
    public static void fadeIn(JWindow component, int durationMs, Runnable onComplete) {
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
                component.setOpacity(opacity[0]);
            });
            timer.start();
        });
    }

    /**
     * Fade out a component over the specified duration.
     */
    public static void fadeOut(JWindow component, int durationMs, Runnable onComplete) {
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
                component.setOpacity(opacity[0]);
            });
            timer.start();
        });
    }

    /**
     * Slide and fade in a panel simultaneously.
     * @param panel Panel to animate
     * @param startY Starting Y position
     * @param endY Ending Y position
     * @param durationMs Total animation duration
     * @param onComplete Callback when animation finishes
     */
    public static void slideAndFadeIn(JWindow panel, int startY, int endY, int durationMs, Runnable onComplete) {
        SwingUtilities.invokeLater(() -> {
            final int steps = 15;
            final int stepDuration = durationMs / steps;
            final float[] opacity = {0.0f};
            final int[] currentY = {startY};
            final float increment = 1.0f / steps;
            final int yStep = (endY - startY) / steps;

            panel.setLocation(panel.getX(), startY);
            panel.setOpacity(0.0f);

            Timer timer = new Timer(stepDuration, null);
            timer.addActionListener(e -> {
                opacity[0] += increment;
                currentY[0] += yStep;

                if (opacity[0] >= 1.0f) {
                    opacity[0] = 1.0f;
                    currentY[0] = endY;
                    ((Timer) e.getSource()).stop();
                    if (onComplete != null) onComplete.run();
                }

                panel.setOpacity(opacity[0]);
                panel.setLocation(panel.getX(), currentY[0]);
            });
            timer.start();
        });
    }

    /**
     * Slide up and fade out a panel simultaneously.
     */
    public static void slideUpAndFadeOut(JWindow panel, int slidePixels, int durationMs, Runnable onComplete) {
        SwingUtilities.invokeLater(() -> {
            final int steps = 15;
            final int stepDuration = durationMs / steps;
            final float[] opacity = {1.0f};
            final int[] currentY = {panel.getY()};
            final float decrement = 1.0f / steps;
            final int yStep = slidePixels / steps;
            final int startY = panel.getY();

            Timer timer = new Timer(stepDuration, null);
            timer.addActionListener(e -> {
                opacity[0] -= decrement;
                currentY[0] -= yStep;

                if (opacity[0] <= 0.0f) {
                    opacity[0] = 0.0f;
                    currentY[0] = startY - slidePixels;
                    ((Timer) e.getSource()).stop();
                    if (onComplete != null) onComplete.run();
                }

                panel.setOpacity(opacity[0]);
                panel.setLocation(panel.getX(), currentY[0]);
            });
            timer.start();
        });
    }
}

