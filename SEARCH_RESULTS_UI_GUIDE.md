# FileMind – Dense IntelliJ Shift+Shift Style Search Results

## Overview

This implementation replaces the simple card-based result layout with a dense, high-capacity search results UI inspired by IntelliJ's "Shift+Shift" search. Results are rendered using a custom `JList` with variable-height rows for efficient space utilization and quick scanning.

## Architecture

### Components

#### 1. **ResultItem.java**
Wrapper class that encapsulates both result rows and section headers:
- **SearchResult items**: Wrapped by `ResultItem.fromResult(SearchResult)`
- **Section headers**: Created with `ResultItem.fromHeader(String)`
- **Properties**:
  - `isHeader()` - Boolean flag
  - `getHeight()` - Returns 42px for results, 22px for headers
  - `getResult()` / `getHeaderText()` - Type-safe accessors

#### 2. **ResultListModel.java**
Custom `AbstractListModel<ResultItem>` that manages the list of results and headers:
- **Features**:
  - Automatic grouping of results by category (FILES, CODE, RECENT)
  - Section header insertion
  - `getNextSelectableIndex()` - Keyboard navigation skips headers
  - `getFirstSelectableIndex()` - Selects first result on empty search
- **Grouping Logic**:
  - Java/Python/JS/etc. → CODE
  - PDF/Doc/Image → FILES
  - Default → FILES

#### 3. **ResultRenderer.java**
Custom `ListCellRenderer<ResultItem>` that paints both results and headers:
- **Result Rows (42px)**:
  - Left (28px): Badge with rounded rectangle and 2-letter code
  - Center (grow): Filename (bold 13pt) with query highlighting
  - Right sections:
    - Parent path (180px): Last two path segments, left-truncated with "..."
    - Size (50px): Right-aligned, formatted (12 B, 450 KB, 2.3 MB)
    - Date (80px): Right-aligned, smart formatting (Today HH:mm, Yesterday, dd MMM yyyy)
- **Section Headers (22px)**:
  - Text: "FILES", "CODE", "RECENT" in bold 9pt
  - Background: #162032 (dark) or #e2e8f0 (light)
  - Non-interactive
- **Badge Colors**:
  - PDF → #ef4444 (red, "PD")
  - Java → #f97316 (orange, "JV")
  - Python/JS/TS/etc → #3b82f6 (blue, extension code)
  - Image → #a855f7 (purple, "IM")
  - Doc → #2563eb (dark blue, "DC")
  - Excel → #16a34a (green, "XL")
  - Video → #ec4899 (pink, "VD")
  - Folder → #f59e0b (amber, "FD")
  - Other → #64748b (gray, first 2 chars)
- **Highlighting**:
  - Query matches are wrapped in `<b>` tags for HTML rendering
  - Case-insensitive matching
- **Selection States**:
  - Default: Transparent background
  - Hover: #1a2744 (dark) background
  - Selected: #1e3a5f (dark) background + 3px left blue accent border
  - Hover and selection are independent

#### 4. **Updated SearchPanel.java**
Refactored to use `JList<ResultItem>` instead of manual JPanel-based layout:
- **Results Display**:
  - `buildResultsArea()` creates `JList` with `ResultListModel` and `ResultRenderer`
  - `displayResults()` populates the model and auto-selects first result
  - Category filtering works in-memory on already-fetched results
- **Keyboard Navigation**:
  - ↑/↓ Skip section headers automatically
  - Enter opens selected file
  - Ctrl+C copies selected path
  - Ctrl+K shows context menu (✓)
  - Tab cycles to next category
  - Escape closes panel
- **Mouse Interaction**:
  - Double-click opens file
  - Single hover selects result
  - Right-click shows context menu (✗ not yet implemented, uses Ctrl+K)
- **Context Menu** (Ctrl+K):
  - Open file
  - Open folder
  - Copy path
  - Close

### Data Flow

```
User types query
    ↓
scheduleSearch() [300ms debounce]
    ↓
performSearch() [Background thread]
    ↓
LuceneIndexer.search() returns List<SearchResult>
    ↓
displayResults(results)
    ↓
filterByCategory(results) → List<SearchResult>
    ↓
resultsModel.setResults(results)
    ↓
ResultListModel groups by category, creates ResultItem wrappers
    ↓
ResultRenderer paints each row with badge, filename, path, size, date
    ↓
JList displays all items with proper layout and selection handling
```

## Visual Design

### Result Row Layout (42px)

```
[Badge] [Filename...........] [ParentPath....] [Size] [Date]
  28px        grow              180px R        50px R  80px R
```

**Padding**: 8px vertical, 12px horizontal borders

**Example Row**:
```
[JV] MyServiceImpl.java         /src/main/java    2.3 KB  Today 14:23
```

### Section Header (22px)

```
FILES
```

**Background**: Dark theme #162032, Light theme #e2e8f0  
**Text**: Bold 9pt, #475569, 12px left padding, 4px top padding

### Empty State

```
No results for "invalid query"

Hints:
spring boot
.pdf
files from yesterday
```

Centered, 13pt gray text

### Selection Indicators

- **Default**: Transparent
- **Hover**: Darker background (#1a2744)
- **Selected**: Dark background (#1e3a5f) + 3px left blue accent border
- **Multiple selections**: Only one at a time (ListSelectionModel.SINGLE_SELECTION)

## Features

### 1. Query Highlighting
- Extracts matched substring from filename
- Wraps in `<b>` HTML tags
- Case-insensitive matching
- Filename only (path is not highlighted)

### 2. Smart Date Formatting
- **Today**: "Today HH:mm"
- **Yesterday**: "Yesterday HH:mm"
- **Older**: "dd MMM yyyy"

### 3. Path Truncation
- Shows last two segments only
- Adds "..." prefix if truncated
- Example: `→.../main/java` (from full path `/home/user/project/src/main/java`)

### 4. Size Formatting
- Bytes: "12 B"
- Kilobytes: "450 KB"
- Megabytes: "2.3 MB"
- Gigabytes: "1.2 GB"

### 5. Category Grouping
Results are automatically grouped:
- **CODE**: Java, Python, JavaScript, TypeScript, C++, Go, Rust, Kotlin, Swift
- **FILES**: PDF, Doc, Image, Excel, Video, Folder
- **RECENT**: (future feature with ActivityHistory)

### 6. Keyboard Navigation
- ↑/↓ arrows skip section headers
- Auto-wrapping at top/bottom
- Tab cycles category tabs
- Ctrl+1..7 jump to specific category

## Theme Support

### Dark Mode (Default)
- Panel: #0f172a
- Row background: transparent
- Hover: #1a2744
- Selected: #1e3a5f
- Header background: #162032
- Text primary: #f1f5f9
- Text secondary: #94a3b8
- Text hint: #64748b

### Light Mode
- Panel: #ffffff
- Row background: transparent
- Hover: #f3f4f6
- Selected: #eff6ff
- Header background: #e2e8f0
- Text primary: #0f172a
- Text secondary: #6b7280
- Text hint: #9ca3af

Themes update instantly via `updateTheme()` and `dimLayer.updateTheme()`

## Performance

### Memory Efficiency
- Results are rendered on-demand by `ResultRenderer`
- No large component tree (JPanel per result is eliminated)
- `JList` uses a single renderer instance for all cells

### Rendering Speed
- Variable-height rows (`setFixedCellHeight(-1)`)
- Minimal layout calculations
- Direct Graphics2D painting in renderer

### Search Performance
- 300ms debounce prevents excessive searching
- Background thread (SwingWorker) keeps UI responsive
- Results cached in `currentResults` list
- Category filtering is in-memory only

## Interaction Flow

### Opening a File
1. User selects result (↑/↓ arrows or hover)
2. Presses Enter or double-clicks
3. `openFile(SearchResult)` called
4. Records in ActivityHistory
5. Opens file with `Desktop.getDesktop().open()`
6. Panel closes with animation

### Copying Path (Ctrl+C)
1. User selects result
2. Presses Ctrl+C
3. Full path copied to clipboard
4. User continues searching or closes panel

### Context Menu (Ctrl+K)
1. Popup appears below selected row
2. Options: Open, Open Folder, Copy Path, Close
3. Click action or Escape to dismiss

## Integration Points

### SearchPanel.java
```java
// Create results list
resultsList = new JList<>(resultsModel);
resultsList.setCellRenderer(new ResultRenderer());

// Display results
resultsModel.setResults(filteredResults);
resultRenderer.setSearchQuery(query);
statusLabel.setText(count + " results");

// Auto-select first
int firstIdx = resultsModel.getFirstSelectableIndex();
resultsList.setSelectedIndex(firstIdx);

// Keyboard navigation
int nextIdx = resultsModel.getNextSelectableIndex(current, direction);
resultsList.setSelectedIndex(nextIdx);
```

### KeyboardHandling
```java
case KeyEvent.VK_DOWN -> {
    int nextIdx = resultsModel.getNextSelectableIndex(currentIdx, 1);
    resultsList.setSelectedIndex(nextIdx);
}
case KeyEvent.VK_ENTER -> {
    ResultItem item = resultsModel.getElementAt(selectedIdx);
    if (!item.isHeader()) {
        openFile(item.getResult());
    }
}
```

## Future Enhancements

1. **Rename Suggestion**: Amber dot after filename if `suggestedName != null`
2. **Right-Click Menu**: Replace Ctrl+K with right-click context menu
3. **Hover Details**: Show tooltip with full path on hover
4. **Result Preview**: Side panel showing file preview
5. **Custom Badges**: User-configurable file type colors
6. **Sorting Options**: Sort by name, date, size, type
7. **Favorites**: Pin results to top
8. **History**: Show previous searches
9. **Advanced Filters**: UI controls for size, date, type filters

## Testing Checklist

- [x] Results display with badges and correct colors
- [x] Filename highlighting on query match
- [x] Path truncation to last two segments
- [x] Size formatting (B/KB/MB/GB)
- [x] Date formatting (Today/Yesterday/dd MMM yyyy)
- [x] Arrow keys skip headers
- [x] Enter opens file
- [x] Ctrl+C copies path
- [x] Ctrl+K shows context menu
- [x] Tab cycles categories
- [x] Empty state shows message
- [x] Section headers appear correctly
- [x] Theme toggle updates colors
- [x] Double-click opens file
- [x] Hover affects selection
- [x] First result auto-selected

## Files Created

- `src/main/java/com/recall/ui/ResultItem.java` - Wrapper for results and headers
- `src/main/java/com/recall/ui/ResultListModel.java` - Custom ListModel with grouping
- `src/main/java/com/recall/ui/ResultRenderer.java` - Custom ListCellRenderer

## Files Modified

- `src/main/java/com/recall/ui/SearchPanel.java` - Refactored to use JList

## Classes/Methods Added

### ResultItem
- `fromResult(SearchResult)` - Factory for result items
- `fromHeader(String)` - Factory for header items
- `isHeader()` - Check if item is a header
- `getHeight()` - Get row height (42 or 22px)
- `getResult()` / `getHeaderText()` - Type-safe accessors

### ResultListModel
- `setResults(List<SearchResult>)` - Populate model with results
- `clear()` - Clear all items
- `getNextSelectableIndex(int, int)` - Navigate skipping headers
- `getFirstSelectableIndex()` - Find first result

### ResultRenderer
- `setSearchQuery(String)` - Set query for highlighting
- `renderHeader()` - Paint section header
- `renderResult()` - Paint result row with badge and metadata
- `formatParentPath()` - Left-truncate path
- `formatDate()` - Smart date formatting
- `highlightMatch()` - HTML-tag query matches
- `getBadgeInfo()` - Map file type to color/text

### SearchPanel (Updated)
- `buildResultsArea()` - Create JList instead of JPanel
- `displayResults()` - Populate model and display
- `showEmptyState()` - Show "No results" message
- `showContextMenu()` - Ctrl+K menu with actions

