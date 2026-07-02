package com.recall.ui;

import com.recall.ui.design.DesignSystem;

import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/**
 * Centralized theme management for dark/light/auto modes.
 * Stores preference in ~/.filemind/config.properties
 */
public class ThemeManager {

    // --- Theme Enum ----------------------------------------------------
    public enum Theme {
        DARK, LIGHT, AUTO
    }

    // ── State ──────────────────────────────────────────────────────────────
    private static Theme currentTheme = Theme.DARK; // Default theme
    private static boolean isDarkModeActive = true; // Actual active mode based on currentTheme or AUTO resolution
    private static final String CONFIG_FILE = System.getProperty("user.home") + "/.filemind/config.properties";

    static {
        loadThemePreference();
        applyDesignSystemColors(); // Apply colors on startup
    }

    // ── Load/Save Theme ───────────────────────────────────────────────────
    private static void loadThemePreference() {
        Path configPath = Paths.get(CONFIG_FILE);
        if (Files.exists(configPath)) {
            try {
                Properties props = new Properties();
                props.load(Files.newInputStream(configPath));
                String themeStr = props.getProperty("theme", "dark");
                try {
                    currentTheme = Theme.valueOf(themeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    currentTheme = Theme.DARK; // Fallback to dark if invalid value
                }
            } catch (IOException ignored) {
                currentTheme = Theme.DARK;
            }
        } else {
            // If no config file, save default (which will call saveThemePreference)
            saveThemePreference(currentTheme);
        }

        resolveAndApplyAutoTheme();
    }

    public static void saveThemePreference(Theme theme) {
        currentTheme = theme;
        resolveAndApplyAutoTheme(); // Resolve AUTO theme before saving
        try {
            Path configDir = Paths.get(System.getProperty("user.home"), ".filemind");
            Files.createDirectories(configDir);
            Path configPath = Paths.get(CONFIG_FILE);

            Properties props = new Properties();
            if (Files.exists(configPath)) {
                props.load(Files.newInputStream(configPath));
            }
            props.setProperty("theme", currentTheme.name().toLowerCase());
            props.store(Files.newOutputStream(configPath), "FileMind Configuration");
        } catch (IOException e) {
            System.err.println("[THEME] Failed to save theme preference: " + e.getMessage());
        }
    }

    private static void resolveAndApplyAutoTheme() {
        if (currentTheme == Theme.AUTO) {
            // Simple heuristic for OS dark mode detection (might not be accurate on all systems)
            // A more robust solution would involve platform-specific APIs (e.g., D-Bus on Linux, Win registry on Windows)
            // For now, we'll default to dark mode for AUTO on Linux, or check a property if available.
            boolean osIsDark = false;
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("windows")) {
                // Windows 10/11 dark mode check via registry (complex in Java, not doing here)
                // For simplicity, default to dark for AUTO on Windows for now.
                osIsDark = false; // Default for Windows auto to light until proper detection
            } else if (osName.contains("mac")) {
                // macOS dark mode check (requires native code, not doing here)
                // For simplicity, default to dark for AUTO on Mac for now.
                osIsDark = true;
            } else if (osName.contains("linux")) {
                // Linux desktop environment (Gnome, KDE) dark mode check (complex)
                // Default to dark for AUTO on Linux for now.
                osIsDark = true;
            }
            isDarkModeActive = osIsDark; // Set the actual active mode
        } else {
            isDarkModeActive = (currentTheme == Theme.DARK);
        }
        applyDesignSystemColors();
    }

    /**
     * Applies the current theme's colors to the DesignSystem static fields.
     * This method should be called whenever the theme changes.
     */
    private static void applyDesignSystemColors() {
        if (isDarkModeActive) {
            DesignSystem.surfacePrimary = Color.decode("#0f172a");
            DesignSystem.surfaceSecondary = Color.decode("#1e293b");
            DesignSystem.surfaceHighlight = Color.decode("#1e3a5f");
            DesignSystem.surfaceAccent = Color.decode("#3b82f6");
            DesignSystem.textPrimary = Color.decode("#f1f5f9");
            DesignSystem.textSecondary = Color.decode("#94a3b8");
            DesignSystem.textTertiary = Color.decode("#64748b");
            DesignSystem.textOnAccent = Color.WHITE;
            DesignSystem.borderPrimary = Color.decode("#334155");
            DesignSystem.borderSecondary = Color.decode("#3b82f6");
            DesignSystem.overlayDim = new Color(0, 0, 0, 140);
            DesignSystem.statusInfo = Color.decode("#16a34a");
            DesignSystem.statusError = Color.decode("#dc2626");
            DesignSystem.statusWarning = Color.decode("#d97706");
            // File type colors (remain the same for now, but could have dark/light variants)
            DesignSystem.fileTypePdf = Color.decode("#ef4444");
            DesignSystem.fileTypeWord = Color.decode("#2563eb");
            DesignSystem.fileTypeExcel = Color.decode("#16a34a");
            DesignSystem.fileTypeImage = Color.decode("#a855f7");
            DesignSystem.fileTypeVideo = Color.decode("#ec4899");
            DesignSystem.fileTypeFolder = Color.decode("#f59e0b");
            DesignSystem.fileTypeCode = Color.decode("#3b82f6");
            DesignSystem.fileTypeJava = Color.decode("#f97316");
            DesignSystem.fileTypePython = Color.decode("#3b82f6");
            DesignSystem.fileTypeZip = Color.decode("#64748b");
            DesignSystem.fileTypeAudio = Color.decode("#eab308");
            DesignSystem.fileTypeExecutable = Color.decode("#7c3aed");
            DesignSystem.fileTypeText = Color.decode("#cbd5e1");
            DesignSystem.fileTypeHtml = Color.decode("#0d9488");
            DesignSystem.fileTypeMarkdown = Color.decode("#4f46e5");
            DesignSystem.fileTypeDefault = Color.decode("#64748b");

        } else { // Light Mode
            DesignSystem.surfacePrimary = Color.WHITE;
            DesignSystem.surfaceSecondary = Color.decode("#f1f5f9");
            DesignSystem.surfaceHighlight = Color.decode("#eff6ff");
            DesignSystem.surfaceAccent = Color.decode("#3b82f6");
            DesignSystem.textPrimary = Color.decode("#0f172a");
            DesignSystem.textSecondary = Color.decode("#6b7280");
            DesignSystem.textTertiary = Color.decode("#9ca3af");
            DesignSystem.textOnAccent = Color.WHITE;
            DesignSystem.borderPrimary = Color.decode("#e5e7eb");
            DesignSystem.borderSecondary = Color.decode("#3b82f6");
            DesignSystem.overlayDim = new Color(0, 0, 0, 76);
            DesignSystem.statusInfo = Color.decode("#16a34a");
            DesignSystem.statusError = Color.decode("#dc2626");
            DesignSystem.statusWarning = Color.decode("#d97706");
            // File type colors
            DesignSystem.fileTypePdf = Color.decode("#ef4444");
            DesignSystem.fileTypeWord = Color.decode("#2563eb");
            DesignSystem.fileTypeExcel = Color.decode("#16a34a");
            DesignSystem.fileTypeImage = Color.decode("#a855f7");
            DesignSystem.fileTypeVideo = Color.decode("#ec4899");
            DesignSystem.fileTypeFolder = Color.decode("#f59e0b");
            DesignSystem.fileTypeCode = Color.decode("#3b82f6");
            DesignSystem.fileTypeJava = Color.decode("#f97316");
            DesignSystem.fileTypePython = Color.decode("#3b82f6");
            DesignSystem.fileTypeZip = Color.decode("#64748b");
            DesignSystem.fileTypeAudio = Color.decode("#eab308");
            DesignSystem.fileTypeExecutable = Color.decode("#7c3aed");
            DesignSystem.fileTypeText = Color.decode("#cbd5e1");
            DesignSystem.fileTypeHtml = Color.decode("#0d9488");
            DesignSystem.fileTypeMarkdown = Color.decode("#4f46e5");
            DesignSystem.fileTypeDefault = Color.decode("#64748b");
        }
    }

    // ── Color Getters ─────────────────────────────────────────────────────
    // These now return colors directly from DesignSystem
    public static Color getOverlayBg() {
        return DesignSystem.overlayDim;
    }

    public static Color getPanelBg() {
        return DesignSystem.surfacePrimary;
    }

    public static Color getPanelBorder() {
        return DesignSystem.borderPrimary;
    }

    public static Color getSearchBg() {
        return DesignSystem.surfaceSecondary;
    }

    public static Color getSearchText() {
        return DesignSystem.textPrimary;
    }

    public static Color getPlaceholder() {
        return DesignSystem.textTertiary;
    }

    public static Color getAccent() {
        return DesignSystem.surfaceAccent;
    }

    public static Color getResultHover() {
        return DesignSystem.surfaceHighlight;
    }

    public static Color getTextPrimary() {
        return DesignSystem.textPrimary;
    }

    public static Color getTextSecondary() {
        return DesignSystem.textSecondary;
    }

    public static Color getTextHint() {
        return DesignSystem.textTertiary;
    }

    public static Color getSeparator() {
        // DesignSystem currently has borderPrimary as separator, using that for now.
        // Could introduce a specific separator color if needed.
        return DesignSystem.borderPrimary;
    }

    public static boolean isDark() {
        return isDarkModeActive;
    }

    public static Theme getCurrentTheme() {
        return currentTheme;
    }

    public static void toggleTheme() {
        switch (currentTheme) {
            case DARK -> saveThemePreference(Theme.LIGHT);
            case LIGHT -> saveThemePreference(Theme.AUTO);
            case AUTO -> saveThemePreference(Theme.DARK);
        }
        // No need to call applyDesignSystemColors here, saveThemePreference does it.
    }

    /**
     * Helper to get a specific file type color from DesignSystem.
     * @param fileType A string identifier for the file type (e.g., "pdf", "java", "folder").
     * @return The corresponding color from DesignSystem, or DesignSystem.fileTypeDefault if not found.
     */
    public static Color getFileTypeColor(String fileType) {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> DesignSystem.fileTypePdf;
            case "doc", "docx" -> DesignSystem.fileTypeWord;
            case "xls", "xlsx", "csv" -> DesignSystem.fileTypeExcel;
            case "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp" -> DesignSystem.fileTypeImage;
            case "mp4", "avi", "mkv", "mov" -> DesignSystem.fileTypeVideo;
            case "folder" -> DesignSystem.fileTypeFolder; // Note: needs to be passed explicitly for folders
            case "java" -> DesignSystem.fileTypeJava;
            case "py" -> DesignSystem.fileTypePython;
            case "js", "ts", "cpp", "c", "h", "go", "rs", "kt", "swift" -> DesignSystem.fileTypeCode;
            case "zip", "rar", "7z" -> DesignSystem.fileTypeZip;
            case "mp3", "wav", "flac" -> DesignSystem.fileTypeAudio;
            case "exe", "app", "bat", "sh" -> DesignSystem.fileTypeExecutable;
            case "txt" -> DesignSystem.fileTypeText;
            case "html", "htm" -> DesignSystem.fileTypeHtml;
            case "md" -> DesignSystem.fileTypeMarkdown;
            default -> DesignSystem.fileTypeDefault;
        };
    }

    // ── Apply Theme to Component ───────────────────────────────────────────
    public static void applyTheme(Component comp) {
        if (comp instanceof javax.swing.JPanel) {
            ((javax.swing.JPanel) comp).setBackground(getPanelBg());
        } else if (comp instanceof javax.swing.JTextField) {
            ((javax.swing.JTextField) comp).setBackground(getSearchBg());
            ((javax.swing.JTextField) comp).setForeground(getSearchText());
        }
        // Extend to other component types as needed.
        // Ensure to handle borders and other styling as well.

        if (comp instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) comp).getComponents()) {
                applyTheme(child);
            }
        }
    }
}
