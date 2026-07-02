package com.recall.ui.design;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stable icon provider backed by FlatLaf FlatSVGIcon.
 * Embeds minimal SVG XML strings — no SVG parser, no path tokenization, zero EDT exceptions.
 *
 * Each icon is a 24×24 viewBox SVG rendered via FlatLaf's proven SVG engine.
 * Icons are cached after first load.
 */
public final class SvgIconProvider {

    // ── 24×24 SVG templates ────────────────────────────────────────────────
    private static final String SVG_HEADER =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" " +
            "fill=\"none\" stroke=\"#000000\" stroke-width=\"2\" " +
            "stroke-linecap=\"round\" stroke-linejoin=\"round\">";
    private static final String SVG_FOOTER = "</svg>";

    // ── Cache ──────────────────────────────────────────────────────────────
    private static final ConcurrentHashMap<String, FlatSVGIcon> cache = new ConcurrentHashMap<>();

    // ── Public API ─────────────────────────────────────────────────────────
    /**
     * Returns a FlatSVGIcon for the given key, colorized with {@code color}.
     */
    public static FlatSVGIcon getIcon(String key, Color color) {
        String cacheKey = key + ":" + color.getRGB();
        FlatSVGIcon icon = cache.get(cacheKey);
        if (icon != null) return icon;
        icon = loadIcon(key);
        if (color != null) {
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
        }
        cache.put(cacheKey, icon);
        return icon;
    }

    /**
     * Returns a JLabel with the given icon at the given size, centered.
     */
    public static JLabel createLabel(String key, Color color, int size) {
        FlatSVGIcon svg = getIcon(key, color);
        JLabel label = new JLabel(svg);
        label.setPreferredSize(new Dimension(size, size));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    // ── Icon definitions ───────────────────────────────────────────────────
    private static FlatSVGIcon loadIcon(String key) {
        return switch (key) {
            case "SEARCH"       -> fromPath("M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.35-4.35");
            case "SETTINGS"     -> fromPath("M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2" +
                    " 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2" +
                    " 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2" +
                    " 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0" +
                    " 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08" +
                    "a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74" +
                    "v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73" +
                    "l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" +
                    "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z");
            case "VOICE"        -> fromPath("M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" +
                    "M19 10v2a7 7 0 0 1-14 0v-2M12 19v4M8 23h8");
            case "AI"           -> fromPath("M12 2l1.5 3.5L17 7l-3.5 1.5L12 12l-1.5-3.5L7 7l3.5-1.5z" +
                    "M18 15l.5 1.5L20 17l-1.5.5-.5 1.5-.5-1.5-1.5-.5 1.5-.5z" +
                    "M6 15l.3 1 .7.3-1 .3-.3.7-.3-.7-1-.3 1-.3z");
            case "FILE_GENERIC" -> fromPath("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0" +
                    " 2-2V8zM14 2v6h6M16 13H8M16 17H8M10 9H8");
            case "FOLDER"       -> fromPath("M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1" +
                    " 2-2h5l2 3h9a2 2 0 0 1 2 2z");
            default -> fromPath("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0" +
                    " 2-2V8zM14 2v6h6M16 13H8M16 17H8M10 9H8");
        };
    }

    private static FlatSVGIcon fromPath(String pathData) {
        String svg = SVG_HEADER + "<path d=\"" + pathData + "\"/>" + SVG_FOOTER;
        try {
            return new FlatSVGIcon(
                    new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load embedded SVG icon", e);
        }
    }
}
