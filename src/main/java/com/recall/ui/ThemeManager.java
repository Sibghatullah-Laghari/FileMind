package com.recall.ui;

import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/**
 * Centralized theme management for dark/light modes.
 * Stores preference in ~/.filemind/config.properties
 */
public class ThemeManager {

    // ── Color Palettes ────────────────────────────────────────────────────
    // Dark Mode
    public static final Color DARK_OVERLAY_BG = new Color(0, 0, 0, 140);  // rgba(0,0,0,0.55)
    public static final Color DARK_PANEL_BG = new Color(0x0f172a);
    public static final Color DARK_PANEL_BORDER = new Color(0x334155);
    public static final Color DARK_SEARCH_BG = new Color(0x1e293b);
    public static final Color DARK_SEARCH_TEXT = new Color(0xf1f5f9);
    public static final Color DARK_PLACEHOLDER = new Color(0x475569);
    public static final Color DARK_ACCENT = new Color(0x3b82f6);
    public static final Color DARK_RESULT_HOVER = new Color(0x1e3a5f);
    public static final Color DARK_TEXT_PRIMARY = new Color(0xf1f5f9);
    public static final Color DARK_TEXT_SECONDARY = new Color(0x94a3b8);
    public static final Color DARK_TEXT_HINT = new Color(0x64748b);
    public static final Color DARK_SEPARATOR = new Color(0x1e293b);

    // Light Mode
    public static final Color LIGHT_OVERLAY_BG = new Color(0, 0, 0, 76);  // rgba(0,0,0,0.30)
    public static final Color LIGHT_PANEL_BG = Color.WHITE;
    public static final Color LIGHT_PANEL_BORDER = new Color(0xe5e7eb);
    public static final Color LIGHT_SEARCH_BG = new Color(0xf1f5f9);
    public static final Color LIGHT_SEARCH_TEXT = new Color(0x0f172a);
    public static final Color LIGHT_PLACEHOLDER = new Color(0x9ca3af);
    public static final Color LIGHT_ACCENT = new Color(0x3b82f6);
    public static final Color LIGHT_RESULT_HOVER = new Color(0xeff6ff);
    public static final Color LIGHT_TEXT_PRIMARY = new Color(0x0f172a);
    public static final Color LIGHT_TEXT_SECONDARY = new Color(0x6b7280);
    public static final Color LIGHT_TEXT_HINT = new Color(0x9ca3af);
    public static final Color LIGHT_SEPARATOR = new Color(0xf3f4f6);

    // ── State ──────────────────────────────────────────────────────────────
    private static boolean isDarkMode = true;
    private static final String CONFIG_FILE = System.getProperty("user.home") + "/.filemind/config.properties";

    static {
        loadThemePreference();
    }

    // ── Load/Save Theme ───────────────────────────────────────────────────
    private static void loadThemePreference() {
        Path configPath = Paths.get(CONFIG_FILE);
        if (Files.exists(configPath)) {
            try {
                Properties props = new Properties();
                props.load(Files.newInputStream(configPath));
                String theme = props.getProperty("theme", "dark");
                isDarkMode = theme.equalsIgnoreCase("dark");
            } catch (IOException ignored) {
                isDarkMode = true;
            }
        } else {
            saveThemePreference(isDarkMode);
        }
    }

    public static void saveThemePreference(boolean darkMode) {
        isDarkMode = darkMode;
        try {
            Path configDir = Paths.get(System.getProperty("user.home"), ".filemind");
            Files.createDirectories(configDir);
            Path configPath = Paths.get(CONFIG_FILE);

            Properties props = new Properties();
            if (Files.exists(configPath)) {
                props.load(Files.newInputStream(configPath));
            }
            props.setProperty("theme", darkMode ? "dark" : "light");
            props.store(Files.newOutputStream(configPath), "FileMind Configuration");
        } catch (IOException e) {
            System.err.println("[THEME] Failed to save theme preference: " + e.getMessage());
        }
    }

    // ── Color Getters ─────────────────────────────────────────────────────
    public static Color getOverlayBg() {
        return isDarkMode ? DARK_OVERLAY_BG : LIGHT_OVERLAY_BG;
    }

    public static Color getPanelBg() {
        return isDarkMode ? DARK_PANEL_BG : LIGHT_PANEL_BG;
    }

    public static Color getPanelBorder() {
        return isDarkMode ? DARK_PANEL_BORDER : LIGHT_PANEL_BORDER;
    }

    public static Color getSearchBg() {
        return isDarkMode ? DARK_SEARCH_BG : LIGHT_SEARCH_BG;
    }

    public static Color getSearchText() {
        return isDarkMode ? DARK_SEARCH_TEXT : LIGHT_SEARCH_TEXT;
    }

    public static Color getPlaceholder() {
        return isDarkMode ? DARK_PLACEHOLDER : LIGHT_PLACEHOLDER;
    }

    public static Color getAccent() {
        return isDarkMode ? DARK_ACCENT : LIGHT_ACCENT;
    }

    public static Color getResultHover() {
        return isDarkMode ? DARK_RESULT_HOVER : LIGHT_RESULT_HOVER;
    }

    public static Color getTextPrimary() {
        return isDarkMode ? DARK_TEXT_PRIMARY : LIGHT_TEXT_PRIMARY;
    }

    public static Color getTextSecondary() {
        return isDarkMode ? DARK_TEXT_SECONDARY : LIGHT_TEXT_SECONDARY;
    }

    public static Color getTextHint() {
        return isDarkMode ? DARK_TEXT_HINT : LIGHT_TEXT_HINT;
    }

    public static Color getSeparator() {
        return isDarkMode ? DARK_SEPARATOR : LIGHT_SEPARATOR;
    }

    public static boolean isDark() {
        return isDarkMode;
    }

    public static void toggleTheme() {
        isDarkMode = !isDarkMode;
        saveThemePreference(isDarkMode);
    }

    // ── Apply Theme to Component ───────────────────────────────────────────
    public static void applyTheme(Component comp) {
        if (comp instanceof javax.swing.JPanel) {
            ((javax.swing.JPanel) comp).setBackground(getPanelBg());
        } else if (comp instanceof javax.swing.JTextField) {
            ((javax.swing.JTextField) comp).setBackground(getSearchBg());
            ((javax.swing.JTextField) comp).setForeground(getSearchText());
        }

        if (comp instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) comp).getComponents()) {
                applyTheme(child);
            }
        }
    }
}

