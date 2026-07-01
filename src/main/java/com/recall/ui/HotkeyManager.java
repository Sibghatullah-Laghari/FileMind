package com.recall.ui;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/**
 * Global hotkey manager using JNativeHook.
 *
 * Features:
 *  - Default hotkey: Ctrl+Shift+F
 *  - Configurable via ~/.filemind/config.properties (hotkey=...)
 *  - Toggle SearchUI visibility on trigger
 *  - Graceful fallback if JNativeHook fails (permission denied on Linux, etc.)
 *  - No crash on init failure — prints warning and continues
 */
public class HotkeyManager implements NativeKeyListener {

    // ── Configuration ──────────────────────────────────────────────────────
    private static final String DEFAULT_HOTKEY = "ctrl+shift+f";
    private static final String CONFIG_FILE = System.getProperty("user.home") + "/.filemind/config.properties";

    // ── State ──────────────────────────────────────────────────────────────
    private String hotkey = DEFAULT_HOTKEY;
    private int modifiers;  // bitmask of CTRL, SHIFT, ALT
    private int keyCode;    // VK_F, VK_G, etc.
    private boolean initialized = false;
    private static final int MOD_CTRL  = 1;
    private static final int MOD_SHIFT = 2;
    private static final int MOD_ALT   = 4;

    // ─────────────────────────────────────────────────────────────────────
    public HotkeyManager() {
        loadConfiguration();
        parseHotkey();
    }

    // ── Configuration Loading ──────────────────────────────────────────────
    private void loadConfiguration() {
        Path configPath = Paths.get(CONFIG_FILE);
        if (Files.exists(configPath)) {
            try {
                Properties props = new Properties();
                props.load(Files.newInputStream(configPath));
                if (props.containsKey("hotkey")) {
                    hotkey = props.getProperty("hotkey").toLowerCase().trim();
                    System.out.println("[HOTKEY] Loaded hotkey from config: " + hotkey);
                }
            } catch (IOException e) {
                System.err.println("[HOTKEY] Failed to read config: " + e.getMessage());
                System.out.println("[HOTKEY] Using default: " + DEFAULT_HOTKEY);
            }
        } else {
            System.out.println("[HOTKEY] No config file found, using default: " + DEFAULT_HOTKEY);
            saveDefaultConfiguration();
        }
    }

    private void saveDefaultConfiguration() {
        try {
            Path configDir = Paths.get(System.getProperty("user.home"), ".filemind");
            Files.createDirectories(configDir);
            Path configPath = Paths.get(CONFIG_FILE);
            String content = "# FileMind Configuration\nhotkey=" + DEFAULT_HOTKEY + "\n";
            Files.writeString(configPath, content);
        } catch (IOException e) {
            System.err.println("[HOTKEY] Failed to save default config: " + e.getMessage());
        }
    }

    // ── Hotkey Parsing ────────────────────────────────────────────────────
    private void parseHotkey() {
        // Parse "ctrl+shift+f" → modifiers bitmask + keyCode
        modifiers = 0;
        keyCode = 0;

        String[] parts = hotkey.split("\\+");
        for (String part : parts) {
            part = part.trim().toLowerCase();
            switch (part) {
                case "ctrl" -> modifiers |= MOD_CTRL;
                case "shift" -> modifiers |= MOD_SHIFT;
                case "alt" -> modifiers |= MOD_ALT;
                default -> {
                    // It's the actual key
                    keyCode = charToKeyCode(part.charAt(0));
                }
            }
        }
    }

    private int charToKeyCode(char c) {
        // Very simple: maps lowercase letter to its ASCII code
        // This works for JNativeHook's NativeKeyEvent
        return Character.toUpperCase(c);
    }

    // ── Registration ───────────────────────────────────────────────────────
    public void register() {
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            initialized = true;
            System.out.println("[HOTKEY] Global hotkey registered: " + hotkey);
        } catch (Exception e) {
            System.err.println("[HOTKEY] Failed to register global hotkey: " + e.getMessage());
            System.err.println("[HOTKEY] Continuing without global hotkey support");
            initialized = false;
        }
    }

    public void unregister() {
        if (initialized) {
            try {
                GlobalScreen.unregisterNativeHook();
                GlobalScreen.removeNativeKeyListener(this);
            } catch (Exception e) {
                System.err.println("[HOTKEY] Error during unregister: " + e.getMessage());
            }
        }
    }

    // ── JNativeHook Listener ───────────────────────────────────────────────
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        // Check if this is our hotkey combo
        if (matchesHotkey(e)) {
            // Hotkey matched, toggle the search UI
            toggleSearchUI();
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        // Not needed for hotkey detection
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // Not needed for hotkey detection
    }

    // ── Hotkey Matching ───────────────────────────────────────────────────
    private boolean matchesHotkey(NativeKeyEvent e) {
        // Check modifiers
        int pressedMods = 0;
        if ((e.getModifiers() & NativeKeyEvent.CTRL_MASK) != 0) pressedMods |= MOD_CTRL;
        if ((e.getModifiers() & NativeKeyEvent.SHIFT_MASK) != 0) pressedMods |= MOD_SHIFT;
        if ((e.getModifiers() & NativeKeyEvent.ALT_MASK) != 0) pressedMods |= MOD_ALT;

        // Check key code
        boolean modMatch = (pressedMods == modifiers);
        boolean keyMatch = (e.getKeyCode() == keyCode);

        return modMatch && keyMatch;
    }

    // ── Search UI Toggle ───────────────────────────────────────────────────
    private void toggleSearchUI() {
        SwingUtilities.invokeLater(() -> {
            SearchPanel panel = SearchPanel.getInstance();
            if (panel.isVisible()) {
                panel.close();
            } else {
                panel.open();
            }
        });
    }

    // ── Public API ─────────────────────────────────────────────────────────
    public static HotkeyManager init() {
        HotkeyManager manager = new HotkeyManager();
        manager.register();
        return manager;
    }

    public boolean isInitialized() {
        return initialized;
    }
}


