package com.recall.ui.design;

import java.awt.*;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralized design system tokens for FileMind UI.
 * This class defines reusable values for colors, typography, spacing,
 * corner radii, shadows, and animation durations.
 */
public class DesignSystem {

    // --- 1. Colors (Semantic Naming) ----------------------------------------------------
    // These abstract colors will be resolved by ThemeManager based on active theme.
    // Default values here are for Dark Mode. ThemeManager will override.

    public static Color surfacePrimary = Color.decode("#0f172a"); // Panel background
    public static Color surfaceSecondary = Color.decode("#1e293b"); // Search field, result card background
    public static Color surfaceHighlight = Color.decode("#1e3a5f"); // Hover/selected state for results
    public static Color surfaceAccent = Color.decode("#3b82f6"); // Accent color for buttons, borders
    public static Color textPrimary = Color.decode("#f1f5f9"); // Main text color
    public static Color textSecondary = Color.decode("#94a3b8"); // Secondary text, path
    public static Color textTertiary = Color.decode("#64748b"); // Hint text, disabled
    public static Color textOnAccent = Color.WHITE; // Text on accent backgrounds
    public static Color borderPrimary = Color.decode("#334155"); // Panel borders
    public static Color borderSecondary = Color.decode("#3b82f6"); // Accent borders (e.g., active tab)
    public static Color overlayDim = new Color(0, 0, 0, 140); // Dimming overlay (for modals, not search palette)
    public static Color statusInfo = Color.decode("#16a34a"); // Green for success
    public static Color statusError = Color.decode("#dc2626"); // Red for error
    public static Color statusWarning = Color.decode("#d97706"); // Amber for warning/suggestion

    // Specific colors for file type icons (could be mapped dynamically)
    public static Color fileTypePdf = Color.decode("#ef4444");
    public static Color fileTypeWord = Color.decode("#2563eb");
    public static Color fileTypeExcel = Color.decode("#16a34a");
    public static Color fileTypeImage = Color.decode("#a855f7");
    public static Color fileTypeVideo = Color.decode("#ec4899");
    public static Color fileTypeFolder = Color.decode("#f59e0b");
    public static Color fileTypeCode = Color.decode("#3b82f6"); // Generic code
    public static Color fileTypeJava = Color.decode("#f97316");
    public static Color fileTypePython = Color.decode("#3b82f6");
    public static Color fileTypeZip = Color.decode("#64748b");
    public static Color fileTypeAudio = Color.decode("#eab308");
    public static Color fileTypeExecutable = Color.decode("#7c3aed");
    public static Color fileTypeText = Color.decode("#cbd5e1");
    public static Color fileTypeHtml = Color.decode("#0d9488");
    public static Color fileTypeMarkdown = Color.decode("#4f46e5");
    public static Color fileTypeDefault = Color.decode("#64748b");


    // --- 2. Typography ------------------------------------------------------------------
    public static final String FONT_FAMILY_PRIMARY = "Segoe UI"; // Windows default
    public static final String FONT_FAMILY_MONO = "JetBrains Mono"; // For code snippets

    public static final Font FONT_TITLE = new Font(FONT_FAMILY_PRIMARY, Font.BOLD, 24);
    public static final Font FONT_HEADING = new Font(FONT_FAMILY_PRIMARY, Font.BOLD, 16);
    public static final Font FONT_SUBHEADING = new Font(FONT_FAMILY_PRIMARY, Font.BOLD, 13);
    public static final Font FONT_BODY = new Font(FONT_FAMILY_PRIMARY, Font.PLAIN, 13);
    public static final Font FONT_SMALL = new Font(FONT_FAMILY_PRIMARY, Font.PLAIN, 11);
    public static final Font FONT_TINY = new Font(FONT_FAMILY_PRIMARY, Font.PLAIN, 9);
    public static final Font FONT_CODE = new Font(FONT_FAMILY_MONO, Font.PLAIN, 12);

    // --- 3. Spacing (4px grid) ----------------------------------------------------------
    public static final int SPACING_0 = 0;
    public static final int SPACING_1 = 4;   // xs
    public static final int SPACING_2 = 8;   // sm
    public static final int SPACING_3 = 12;  // md
    public static final int SPACING_4 = 16;  // lg
    public static final int SPACING_5 = 20;  // xl
    public static final int SPACING_6 = 24;  // 2xl
    public static final int SPACING_7 = 32;  // 3xl
    public static final int SPACING_8 = 48;  // 4xl

    // --- 4. Corner Radii ----------------------------------------------------------------
    public static final int RADIUS_0 = 0;
    public static final int RADIUS_1 = 4;   // sm
    public static final int RADIUS_2 = 8;   // md
    public static final int RADIUS_3 = 12;  // lg
    public static final int RADIUS_4 = 16;  // xl
    public static final int RADIUS_FULL = 999; // For pill shapes

    // --- 5. Shadows (Custom painting might be needed for true shadows) -------------------
    public static final Insets SHADOW_NONE = new Insets(0, 0, 0, 0); // No shadow
    // Example shadow definitions (will require custom painting or library)
    // For now, these are conceptual. A simple border might suffice for elevation 1-2.
    public static final Color SHADOW_COLOR = new Color(0, 0, 0, 50); // Alpha for softness

    // Elevation 1 (light hover, subtle lift)
    public static final Insets SHADOW_1_INSETS = new Insets(0, 0, 2, 0); // Bottom shadow
    public static final int SHADOW_1_OFFSET_Y = 1;
    public static final int SHADOW_1_BLUR = 2;

    // Elevation 2 (button click, card lift)
    public static final Insets SHADOW_2_INSETS = new Insets(0, 0, 4, 0);
    public static final int SHADOW_2_OFFSET_Y = 2;
    public static final int SHADOW_2_BLUR = 4;

    // Elevation 3 (modal, floating panel)
    public static final Insets SHADOW_3_INSETS = new Insets(0, 0, 8, 0);
    public static final int SHADOW_3_OFFSET_Y = 4;
    public static final int SHADOW_3_BLUR = 8;

    // --- 6. Animation Durations (in milliseconds) ---------------------------------------
    public static final int DURATION_QUICK = 100;  // Fast transitions (hover, click)
    public static final int DURATION_NORMAL = 200; // Standard UI animations (panel open/close)
    public static final int DURATION_SLOW = 300;   // More deliberate animations
    public static final int DURATION_BREATHING = 3000; // Long, subtle animations (e.g., floating icon)

    // --- 7. Animation Easing (Conceptual, requires custom implementation or library) -----
    // For now, linear or simple ease-in-out from AnimationUtil.
    public static final String EASE_OUT_QUAD = "cubic-bezier(0.25, 0.46, 0.45, 0.94)";
    public static final String EASE_IN_OUT_CUBIC = "cubic-bezier(0.65, 0.05, 0.36, 1)";
    public static final String SPRING = "spring"; // Requires a spring animation library/custom impl

    // --- 8. High DPI Scaling (Conceptual, Java handles basic scaling) -------------------
    public static final int FPS_INTERVAL_MS = 1000 / 60; // ~60 FPS animation

    public static double getScaleFactor() {
        return Toolkit.getDefaultToolkit().getScreenResolution() / 96.0;
    }

    public static int scale(int value) {
        return (int) (value * getScaleFactor());
    }

    /**
     * Attempts to load a custom font, falling back to a system default if not found.
     * This is a utility for loading custom fonts like JetBrains Mono.
     * @param fontPath Path to the .ttf font file in resources.
     * @param defaultFont Fallback system font.
     * @param style Font style (e.g., Font.PLAIN).
     * @param size Font size.
     * @return Loaded font or fallback.
     */
    public static Font loadCustomFont(String fontPath, String defaultFont, int style, int size) {
        try (InputStream is = DesignSystem.class.getResourceAsStream(fontPath)) {
            if (is != null) {
                Font customFont = Font.createFont(Font.TRUETYPE_FONT, is);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(customFont);
                return customFont.deriveFont(style, (float) size);
            }
        } catch (FontFormatException | IOException e) {
            System.err.println("Failed to load custom font from " + fontPath + ": " + e.getMessage());
        }
        return new Font(defaultFont, style, size);
    }

    // Initialize custom fonts if available (requires font files in resources)
    static {
        // Example: If we had "JetBrainsMono-Regular.ttf" in /resources/fonts/
        // FONT_MONO = loadCustomFont("/fonts/JetBrainsMono-Regular.ttf", "Monospaced", Font.PLAIN, 12);
        // For now, stick to logical font names or system defaults for simplicity
    }
}
