# SearchPanel Implementation Guide

## Overview

The new `SearchPanel` replaces the old `SearchUI` JFrame with a modern, overlay-based search interface that combines:
- **macOS Spotlight** style (centered overlay, disappears after use)
- **IntelliJ Shift+Shift** style (category tabs, dense results, keyboard-first navigation)

The panel uses a JWindow with a full-screen dim layer for a focused, distraction-free search experience.

## Architecture

### Core Components

#### 1. **SearchPanel.java** (Main Panel)
The central search interface component:
- **Type**: JWindow (no title bar, system window)
- **Size**: 680px width, max 520px height
- **Position**: Centered horizontally, 28% from top vertically
- **Appearance**: Dark theme (deep navy #0f172a) or Light theme (white) depending on user preference

#### 2. **DimLayer.java** (Full-Screen Overlay)
Semi-transparent background layer that appears behind the panel:
- **Coverage**: Full screen (covers all monitors)
- **Click-to-dismiss**: Clicking anywhere on the dim layer closes the panel
- **Theme-aware**: Updates opacity and color based on current theme
- **Type**: JWindow.Type.UTILITY (doesn't appear in taskbar)

#### 3. **ThemeManager.java** (Theme Management)
Centralized color management and theme switching:
- **Persistence**: Stores theme preference in `~/.filemind/config.properties` as `theme=dark` or `theme=light`
- **Default**: Dark mode
- **Colors**: Separate constants for dark and light modes
- **Dynamic**: Can apply theme to entire component tree instantly

#### 4. **AnimationUtil.java** (Animations)
Reusable animation utilities:
- **Fade In/Out**: Smooth opacity transitions over specified duration
- **Slide & Fade**: Combined vertical slide and fade animation
- **Timing**: Uses javax.swing.Timer for frame-independent animation

### Layout Structure

```
┌─────────────────────────────────────────────┐
│  SearchPanel (680px wide)                   │
├─────────────────────────────────────────────┤
│ [🔍] [Search Field...........................] [☀️] esc │ (56px)
├─────────────────────────────────────────────┤
│ All │ Files │ Folders │ Code │ PDF │ ... │ (34px)
├─────────────────────────────────────────────┤
│ 🔤 Natural language detected...             │ (22px, optional)
├─────────────────────────────────────────────┤
│ [📕] PDF File                    Dec 1 2024 │ (56px each)
│ [☕] Java Source                 Dec 1 2024 │
│ [📁] Documents Folder            Nov 30 2024│
│ ... (scrollable)                            │
├─────────────────────────────────────────────┤
│ 3 results  ↑↓ nav  ↵ open  ⌘K copy  Esc    │ (26px)
└─────────────────────────────────────────────┘
```

## Visual Design

### Dark Mode (Default)
- **Panel BG**: #0f172a (deep navy)
- **Overlay**: rgba(0,0,0,0.55)
- **Search Field BG**: #1e293b
- **Search Text**: #f1f5f9 (off-white)
- **Accent**: #3b82f6 (bright blue)
- **Result Hover**: #1e3a5f (darker blue)
- **Text Primary**: #f1f5f9
- **Text Secondary**: #94a3b8 (gray)
- **Text Hint**: #64748b (lighter gray)

### Light Mode
- **Panel BG**: #ffffff (white)
- **Overlay**: rgba(0,0,0,0.30)
- **Search Field BG**: #f1f5f9 (off-white)
- **Search Text**: #0f172a (dark navy)
- **Accent**: #3b82f6 (same blue)
- **Result Hover**: #eff6ff (very light blue)
- **Text Primary**: #0f172a
- **Text Secondary**: #6b7280
- **Text Hint**: #9ca3af

## Features

### 1. Search Bar (56px height)
- **Left**: Magnifying glass emoji (🔍) in hint color
- **Center**: Transparent JTextField (no border) with 17pt font
- **Right**: Theme toggle button (☀️/🌙) + "esc" hint label

### 2. Category Tabs (34px height)
Available tabs: `All | Files | Folders | Code | PDF | Images | Recent`

**Active Tab**:
- Bottom border: 2px solid #3b82f6 (blue accent)
- Text color: #3b82f6 (blue)

**Inactive Tab**:
- No border
- Text color: #64748b (hint color)

**Keyboard Shortcuts**: Ctrl+1 through Ctrl+7 to switch tabs instantly

### 3. Natural Language Hint (22px height, optional)
Shows when query is detected as natural language:
- Message: "🔤 Natural language detected — searching by meaning"
- Font: Italic, 11pt
- Color: #64748b (hint)

**Detection triggers**:
- Contains: "yesterday", "ago", "between", "larger", "recently", "java files", "pdf documents"

### 4. Results Area (max 440px, scrollable)
Each result card (56px height):
- **Left**: Type emoji (📕 PDF, ☕ Code, 🖼️ Image, etc.)
- **Center**: Filename (13pt bold) + parent folder path (11pt gray)
- **Right**: Modified date (11pt hint color)
- **Hover**: Background changes to #1e3a5f (dark mode) or #eff6ff (light mode)
- **Click**: Opens the file with `Desktop.getDesktop().open()`

### 5. Status Footer (26px height)
- **Left**: Result count ("N results" or "No results")
- **Right**: Keyboard hints: "↑↓ navigate ↵ open ⌘K copy path Esc close"

## Keyboard Navigation

| Key | Action |
|-----|--------|
| Type | Instant search (300ms debounce) |
| ↑ Arrow Up | Move selection highlight up |
| ↓ Arrow Down | Move selection highlight down |
| ↵ Enter | Open selected file |
| Ctrl+Enter | Open containing folder |
| Ctrl+C / Cmd+C | Copy selected file path to clipboard |
| Esc | Close panel with animation |
| Tab | Move to next category tab |
| Ctrl+1..7 | Switch to specific category tab (1=All, 2=Files, etc.) |

## Theme Switching

### Storage
Theme preference is stored in `~/.filemind/config.properties`:
```properties
theme=dark
```
or
```properties
theme=light
```

### Toggle Behavior
- Click the ☀️ (light) or 🌙 (dark) button in top-right
- Theme updates instantly across all components
- Preference is saved automatically

### Implementation
- `ThemeManager.toggleTheme()` - switches mode and saves to config
- `ThemeManager.isDark()` - checks current mode
- Color getters return appropriate colors based on current theme

## Animation

### Open Animation (120ms)
1. Dim layer fades in: opacity 0 → 0.55
2. Panel slides down: y - 30 → y
3. Panel fades in: opacity 0 → 1.0
- All happen simultaneously
- Uses 15 animation steps over 120ms

### Close Animation (90ms)
1. Panel slides up: y → y - 20
2. Panel fades out: opacity 1 → 0
3. Dim layer fades out: opacity 0.55 → 0
- After completion: both windows set to invisible

## Search Behavior

### Instant Search
- Triggered on every keystroke
- 300ms debounce timer prevents excessive searching
- Runs on background SwingWorker thread (non-blocking UI)

### Natural Language Parsing
- Uses existing `NLQueryParser` to interpret natural language queries
- Hint appears when NL is detected
- Full query parsing: dates, sizes, file types, time ranges, etc.

### Category Filtering
- All categories shown immediately (no re-search needed)
- Filtered in-memory from current results
- Categories:
  - **All**: No filtering
  - **Files**: Only files (not directories)
  - **Folders**: Only directories
  - **Code**: Java, Python, JS, C++, Go, Rust, etc.
  - **PDF**: PDF documents only
  - **Images**: PNG, JPG, GIF, BMP, SVG
  - **Recent**: From ActivityHistory (future feature)

## Integration Points

### Main.java
```java
// On startup:
searchPanel = SearchPanel.getInstance();  // Creates singleton instance

// On shutdown:
// SearchPanel is properly closed with animations
```

### FloatingIcon.java
```java
// Click handler:
SearchPanel.getInstance().show();  // Opens search panel
```

### HotkeyManager.java
```java
// Ctrl+Shift+F hotkey toggle:
SearchPanel.getInstance().show();  // Opens if hidden
SearchPanel.getInstance().close(); // Closes if visible
```

### SearchUI.java
The old `SearchUI` JFrame is now deprecated but still exists in the codebase for backward compatibility. The new `SearchPanel` is the primary search interface.

## File Access from SearchResult

The panel uses the existing `SearchResult` record from the indexing system:
- **Fields accessed**:
  - `path()` - Full file path
  - `filename()` - Display name
  - `ext()` - File extension
  - `modifiedMs` - Last modified time
  - `typeIcon()` - Emoji for file type
  - `displayDate()` - Formatted date string
  - `parentFolder()` - Parent directory path

## Performance Considerations

1. **Debounced Search**: 300ms delay prevents searching on every keystroke
2. **Background Indexing**: Uses SwingWorker to keep UI responsive
3. **In-Memory Filtering**: Category filters work on cached results (no re-search)
4. **Lazy Drawing**: Components only repaint when necessary
5. **Animation Efficiency**: Uses low-resolution timers (50ms for smooth appearance)

## Troubleshooting

### Panel doesn't appear
- Check that `SearchPanel.getInstance()` is called before `show()`
- Verify `DimLayer` is created and visible
- Check console for exceptions

### Theme not persisting
- Verify `~/.filemind/config.properties` is writable
- Check for permission issues on the config file
- Ensure `ThemeManager.saveThemePreference()` is called

### Animations are choppy
- Animation timer interval is 8-10ms (15 steps over 120ms)
- Increase step count in `AnimationUtil` for smoother motion
- Decrease for faster animation

### Category filters not working
- Verify `activeCategory` is being set in `switchCategory()`
- Check that `filterByCategory()` logic matches expected file types
- Ensure results are properly updated after category switch

## Future Enhancements

1. **Folder browsing**: Add folder navigation support
2. **Recent files**: Populate from ActivityHistory
3. **Favorite shortcuts**: Quick access to common folders
4. **Custom hotkey UI**: Settings dialog to change hotkey
5. **Search history**: Remember previous searches
6. **Advanced filters**: Size, date range, etc. in UI
7. **Preview panel**: Show file preview on selection
8. **Custom colors**: User-configurable color schemes

## Files Modified

- `src/main/java/com/recall/ui/FloatingIcon.java` - Updated to use SearchPanel
- `src/main/java/com/recall/ui/HotkeyManager.java` - Updated to use SearchPanel
- `src/main/java/com/recall/Main.java` - Updated to init SearchPanel
- `pom.xml` - Already has required dependencies

## Files Created

- `src/main/java/com/recall/ui/SearchPanel.java` - Main search interface
- `src/main/java/com/recall/ui/DimLayer.java` - Full-screen overlay
- `src/main/java/com/recall/ui/ThemeManager.java` - Theme management
- `src/main/java/com/recall/ui/AnimationUtil.java` - Animation utilities

