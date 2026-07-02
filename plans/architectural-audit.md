# FileMind — Production Architecture Audit

## Current State Assessment

### 1. Dependency Audit

| Dependency | Current | Required | Status |
|-----------|---------|----------|--------|
| Java | 17 (pom.xml) | 21 | ⚠️ Update |
| FlatLaf | Not present | Required | ❌ Missing |
| FlatLaf SVG | Not present | Required | ❌ Missing |
| MigLayout | Not present | Required | ❌ Missing |
| Lucene | 9.10.0 | 9.10.0 | ✅ |
| JNativeHook | 2.2.2 | 2.2.2 | ✅ (with caveats) |
| Tika | 2.9.0 | 2.9.0+ | ✅ |
| SQLite | 3.44.1 | 3.44.1+ | ✅ |

### 2. Package Structure Audit

```
Current:
  com.recall                 — Main (entry point, tray, startup, all responsibilities)
  com.recall.core            — SearchResult, LuceneIndexer, NLQueryParser, ContentExtractor, NameSuggester, MetadataDB, ActivityHistory, FileWatcher
  com.recall.ui              — 15 UI classes all flat in one package

Required:
  com.recall                 — Bootstrap only
  com.recall.ui              — Window management, orchestration
  com.recall.ui.components   — Reusable UI widgets
  com.recall.ui.theme        — Theme + design system
  com.recall.search          — Search service, ranking, caching
  com.recall.indexing        — Indexing service, watchers
  com.recall.ai              — AI extension interface (plugin)
  com.recall.config          — Configuration management
  com.recall.services        — Background services
  com.recall.util            — Utility classes
```

### 3. SOLID Violations

**Single Responsibility Principle** — Multiple classes do too much:

| Class | Violation | Impact |
|-------|-----------|--------|
| `SearchResult` | Record + UI formatting (typeIcon, displaySize, ...) | Cannot reuse data without UI coupling |
| `SearchPalette` | Search bar + results + keyboard + AI + settings + file actions | ~450 lines, 6+ responsibilities |
| `Main` | Entry + tray + thread pool + indexing orchestration + shutdown | Brittle, hard to test |
| `ThemeManager` | Colors + persistence + component tree traversal | Static state, no DI |
| `LuceneIndexer` | Indexing + searching + file filtering + content extraction | God class, ~361 lines |

**Dependency Inversion Principle** — UI depends directly on search:

- `SearchPalette` instantiates `LuceneIndexer.search()`, `NLQueryParser.parse()`
- No abstraction between UI and search engine
- Cannot swap Lucene with another engine without rewriting UI

**Open/Closed Principle** — Hard to extend:

- Adding a new file type requires modifying `SearchResult.typeIcon()`, `ResultRenderer.getBadgeInfo()`, filter logic
- No extension point for AI integration — it's baked into UI

### 4. Performance Audit

| Metric | Current | Target | Gap |
|--------|---------|--------|-----|
| Search latency | ~50-150ms (varies) | <30ms | 🔴 High — no caching layer |
| Autocomplete | ~300ms debounce only | <20ms | 🔴 No autocomplete cache |
| Animations | Fixed 15-step timer | 60 FPS | 🟡 Time-based needed |
| EDT blocking | SwingWorker (good) but... | Never block | 🟡 Some inline ops |
| RAM at idle | Unknown | <50MB | ⚪ Need measurement |
| Startup | ~2-5s (indexing) | <500ms | 🟡 Lazy indexing needed |
| Index size | Full file content | <2GB | ⚪ Configurable depth |

### 5. Architecture Violations

**No service layer:**
- `SearchPalette` → direct `LuceneIndexer.search()` call
- No search result cache
- No query pipeline (tokenize → parse → search → rank → format)

**No configuration abstraction:**
- `ThemeManager` reads/writes `~/.filemind/config.properties` directly
- `HotkeyManager` reads/writes same file
- No validation, no schema, no change notification

**Tightly coupled AI:**
- `AISection` is hardcoded with suggestions in the constructor
- No `AISuggestionProvider` interface
- Cannot disable AI without code changes

**No event bus:**
- Theme changes require manual `updateTheme()` calls everywhere
- No listener pattern for search completion, indexing progress

## Targeted Remediation Plan

Rather than rewriting everything, I will make minimal, compatible improvements:

### Phase A: Infrastructure (pom.xml + packages)

1. **pom.xml**: Add FlatLaf 3.x, FlatLaf SVG, MigLayout, upgrade to Java 21
2. **Package move**: Shift classes into proper packages with deprecation wrappers for backward compatibility

### Phase B: FlatLaf Integration

1. Replace `UIManager.setSystemLookAndFeel()` with `FlatLaf.setup()`
2. Replace custom `SvgIconRenderer` with `SVGIcon` / `SVGLoader` from FlatLaf
3. Replace all hardcoded colors with FlatLaf theme properties
4. Use FlatLaf's built-in dark/light theme switching

### Phase C: Layout Migration

1. Replace `BoxLayout`/`FlowLayout`/`GridBagLayout` with `MigLayout` in all new components
2. Keep old components working (don't change old SearchPanel/SearchUI layouts)

### Phase D: Search Abstraction

1. Create `SearchService` interface
2. Create `SearchCache` for autocomplete (<20ms)
3. Keep `LuceneIndexer` as default implementation
4. Pure data `SearchResult` — move UI methods to `ResultFormatter` utility

### Phase E: Event Bus

1. Minimal `EventBus` with typed listeners
2. `ThemeChangeEvent`, `SearchCompleteEvent`, `IndexProgressEvent`
3. Eliminates manual `updateTheme()` calls

## Files to Modify (Minimal Set)

```
MODIFY:
  pom.xml                          — Add FlatLaf, MigLayout, Java 21
  src/main/java/.../Main.java      — FlatLaf init, service wiring
  src/main/java/.../SearchPalette.java — MigLayout, SearchService usage
  src/main/java/.../ThemeManager.java  — FlatLaf theme integration

CREATE:
  src/main/java/.../search/SearchService.java        — Search abstraction
  src/main/java/.../search/SearchCache.java           — Autocomplete cache
  src/main/java/.../search/ResultFormatter.java       — UI formatting from SearchResult
  src/main/java/.../util/EventBus.java                — Lightweight event system
  src/main/java/.../config/AppConfig.java             — Configuration abstraction

REMOVE (no longer needed):
  src/main/java/.../ui/design/SvgIconRenderer.java    — Replaced by FlatLaf SVG
  src/main/java/.../ui/DimLayer.java                  — No dimming model

KEEP (working, no changes):
  LuceneIndexer, NLQueryParser, ContentExtractor, NameSuggester,
  MetadataDB, ActivityHistory, FileWatcher, SearchResult (data),
  FloatingIcon (until fully replaced), SearchPanel (until fully replaced)
```
