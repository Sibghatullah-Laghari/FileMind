# FileMind Floating Icon & Global Hotkey Implementation

## Overview

FileMind now includes a **Grammarly-style floating icon** that stays on screen at all times, plus a **global hotkey system** for quick access to the search panel. These features provide a seamless experience for searching files from anywhere in your system.

## Features Implemented

### 1. Floating Icon (FloatingIcon.java)

A persistent 48×48px circular button that stays visible on top of all windows:

#### Visual Design
- **Default Color**: Deep navy blue (#1e293b)
- **Icon**: White magnifying glass emoji (🔍)
- **Position**: Bottom-right corner, 20px from edges
- **Transparency**: Fully transparent background (no window decoration)

#### Interactions

**Hover Effect**
- Smoothly transitions background color from navy (#1e293b) to blue (#3b82f6)
- Animation duration: 150ms across 10 interpolation steps
- Uses Graphics2D color interpolation for smooth transitions

**Click Actions**
- **Left-click**: Opens/focuses the FileMind search panel
- **Right-click**: Shows context menu with options:
  - "Open FileMind" - Opens the search panel
  - "Settings" - Opens settings dialog (placeholder for future features)
  - "Exit" - Cleanly exits the application (flushing Lucene index)

**Draggable**
- Click and drag the icon to any screen position
- Position is automatically saved to `~/.filemind/icon_pos.conf`
- Format: `x,y` coordinates
- On app restart, the icon appears in the last saved position

**Pulsing Animation**
- Every 4 seconds, the icon pulses smoothly (scales from 1.0x to 1.1x and back)
- Uses sine wave for smooth, continuous animation
- Runs via a 50ms Timer with phase calculation

#### Technical Details
- Uses **JWindow** (no title bar, not in taskbar, not in Alt+Tab)
- Custom `IconPanel` extends JPanel with custom painting
- Graphics2D antialiasing enabled for smooth rendering
- No window decorations (setType(Type.UTILITY))
- Always stays on top (setAlwaysOnTop(true))

### 2. Global Hotkey System (HotkeyManager.java)

Uses **JNativeHook** library for true OS-level global hotkey support:

#### Default Behavior
- **Hotkey**: Ctrl+Shift+F
- **Behavior**: Toggle - shows search panel if hidden, hides it if visible
- **Scope**: Works globally, even when FileMind is not focused

#### Configuration
- **Config File**: `~/.filemind/config.properties`
- **Format**: `hotkey=ctrl+shift+f` or `hotkey=alt+space`, etc.
- **Supported Modifiers**: ctrl, shift, alt
- **Format Examples**:
  - `hotkey=ctrl+shift+f` - Default
  - `hotkey=alt+space` - Alternative
  - `hotkey=ctrl+a` - Any valid single key with modifiers

#### Key Features
- **JNativeHook Integration**: Registers native keyboard hooks at OS level
- **Toggle Behavior**: Hotkey press toggles search UI visibility
- **Graceful Fallback**: If JNativeHook initialization fails (permission denied on Linux, etc.):
  - Prints warning message: `[HOTKEY] Failed to register global hotkey`
  - App continues normally without the global hotkey
  - **No crash** - application remains stable
- **Automatic Config Creation**: If `config.properties` doesn't exist, creates it with default values
- **Hotkey Parsing**: Intelligently parses hotkey strings from config file

### 3. Application Startup Changes (Main.java)

#### Modified Behavior
- **Search Panel**: Now starts **hidden** (not visible on app launch)
- **Floating Icon**: Automatically displayed on startup
- **System Tray**: Still available for accessing the app
- **Hotkey Manager**: Automatically initialized on startup

#### Shutdown Cleanup
- Unregisters JNativeHook global hotkey listener
- Flushes Lucene index cleanly
- Properly closes database connections

## Configuration

### Initial Setup

When FileMind starts for the first time:

1. **`~/.filemind/` directory** is created automatically
2. **`config.properties`** is created with default hotkey settings:
   ```properties
   # FileMind Configuration
   
   # Global hotkey for opening FileMind
   # Format: ctrl+shift+f, alt+space, etc.
   # Supported modifiers: ctrl, shift, alt
   hotkey=ctrl+shift+f
   ```
3. **`icon_pos.conf`** is created after first drag (stores floating icon position)

### Customizing the Hotkey

1. Edit `~/.filemind/config.properties`
2. Change the `hotkey=` line to your preferred hotkey:
   - `hotkey=ctrl+shift+f` (default)
   - `hotkey=alt+space`
   - `hotkey=ctrl+alt+s`
3. Restart FileMind for changes to take effect

## User Experience Flow

### On Application Start
1. Floating icon appears in bottom-right corner
2. Search panel remains hidden
3. System tray icon is available
4. Global hotkey (Ctrl+Shift+F) is registered and listening

### Using Ctrl+Shift+F
- **First Press**: Search panel opens and focuses
- **While Panel Visible**: Can type search immediately
- **Next Press of Ctrl+Shift+F**: Panel closes
- **Works Anywhere**: Even when FileMind window is not active

### Using Floating Icon
- **Hover**: Icon smoothly transitions to blue
- **Click**: Opens search panel (same as Ctrl+Shift+F)
- **Drag**: Move icon to preferred screen location
- **Right-Click**: Access context menu for additional options

### Exiting Application
- Click "Exit" in floating icon context menu, OR
- Click "Exit" in system tray menu
- Index is flushed cleanly, no data loss

## File Structure

```
src/main/java/com/recall/ui/
├── FloatingIcon.java      - Floating circular button implementation
├── HotkeyManager.java     - Global hotkey registration and handling
└── SearchUI.java          - Search panel (modified to work with new system)

~/.filemind/
├── config.properties      - Hotkey configuration
├── icon_pos.conf          - Floating icon position persistence
├── index/                 - Lucene search index
└── meta.db               - Metadata database
```

## Dependencies

- **JNativeHook** (com.github.kwhat:jnativehook:2.2.2)
  - Already added to pom.xml
  - Handles OS-level keyboard hooks
  - Graceful fallback if native library unavailable

## Troubleshooting

### Floating Icon Doesn't Appear
- Check that Java Swing is properly initialized
- Verify system supports JWindow windows
- Check console for error messages

### Global Hotkey Not Working
- Ensure `config.properties` is properly formatted
- Check permissions on Linux (JNativeHook may need elevated privileges)
- Verify hotkey syntax: `modifiers+key` format
- Check console for `[HOTKEY]` log messages

### Icon Position Reset
- This happens if `icon_pos.conf` is deleted or corrupted
- Simply drag the icon to desired position again to recreate the file
- Position will be remembered on next restart

### High CPU Usage (Pulsing Animation)
- The pulse animation uses a 50ms Timer (20 fps)
- This is by design for smooth animation
- Can be optimized by increasing timer interval if needed

## Future Enhancements

Potential improvements for future versions:
- User-configurable icon appearance (colors, size)
- Icon themes and customization
- Multi-monitor support for icon positioning
- Custom hotkey UI in Settings dialog
- Icon drag boundaries (stay within screen bounds)
- Animation speed customization

## Notes

- The floating icon is **not** in the taskbar or Alt+Tab switcher
- Both the floating icon and system tray can open the search panel
- The global hotkey works system-wide and doesn't interfere with other applications
- All configurations are persisted in `~/.filemind/` directory for cross-session consistency

