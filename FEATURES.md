# Features – FileMind

> 📅 **Last updated:** 2026-07-16  
> ✅ **Status:** Active development – core features are production-ready.

---

## 🔍 Smart Search

Search files instantly with sub‑millisecond response times..
.
- **Supported formats:** PDF, DOCX, DOC, XLSX, CSV, Java, Python, JavaScript, and many more via Apache Tika..
- **Fuzzy matching:** Handles typos and partial matches automatically.
- **Wildcard support:** Use `*` and `?` for flexible search patterns.
- **Result highlighting:** Matching terms are highlighted in the preview snippet.

> 💡 *Tip:* Try typing partial file names – the search engine suggests completions as you type.

---

## 📄 Content Search

Search inside the full text of your documents, not just filenames.

| Format | Content Extracted |
| :--- | :--- |
| **PDF** | Text, metadata (author, title) |
| **DOCX / DOC** | Paragraphs, headings, comments |
| **XLSX / CSV** | Cell values, sheet names |
| **Java / Python / JS** | Source code, comments, strings |
| **HTML / XML** | Visible text, attributes |
| **ZIP / Archives** | File names and contents of nested files |

> 🔍 *How it works:* Apache Tika extracts textual content during indexing. The raw text is tokenised, analysed (stemming, stop‑word removal), and stored in the Lucene index for fast retrieval.

---

## 🧠 Name Suggestions

Real‑time filename prediction as you type.

- **Autocomplete:** Suggests matching filenames from your indexed corpus.
- **History‑aware:** Frequently accessed files appear higher in suggestions.
- **Keyboard‑friendly:** Use arrow keys to navigate suggestions and `Enter` to open.

> ⚡ *Performance:* Suggestions appear in under 100ms – even on large directories (tested with 50,000+ files).

---

## 🗣️ Natural Language Search

Describe what you're looking for in plain English.

### Examples

| Query | Interpretation |
| :--- | :--- |
| `invoice from last week` | Finds invoices modified or created in the past 7 days. |
| `java project` | Returns Java source files, ideally from project directories. |
| `presentation about AI` | Finds PowerPoint/PDF files containing "AI" or "artificial intelligence". |
| `budget 2025` | Searches for spreadsheets or documents with "budget" and "2025". |

> 🧪 *Current status:* Natural language queries are parsed into Lucene boolean queries using a custom query rewriter. Semantic search (via Ollama) is planned for Phase 5.

---

## 🚀 Upcoming Features (Roadmap):-

| Phase | Feature | Status | ETA |
| :--- | :--- | :--- | :--- |
| 4 | **OCR for Images** – Extract text from screenshots, photos, and scanned PDFs using Tesseract. | 🚧 In development | Q3 2026 |
| 5 | **Semantic AI Search** – Local LLM integration (Ollama) for intent‑based search. | 📝 Planned | Q4 2026 |
| 6 | **Tagging & Folders** – Organise files with custom tags and virtual folders. | 📝 Planned | Q1 2027 |
| 7 | **Cross‑device Sync** – Optional encrypted sync across multiple machines. | 📝 Under consideration | TBD |

---

## 📌 Developer Notes (added 2026-07-16).

- **Extensibility:** The `Parser` interface in `com.filemind.parser` allows you to add custom parsers for new file types – simply implement the interface and register it in `ParserRegistry`.
- **Index location:** The Lucene index is stored in `~/.filemind/index/`. You can safely delete it to force a full re‑index (the application will rebuild it on next startup).
- **Logging:** The search query log is written to `~/.filemind/logs/queries.log` – useful for debugging or analysing popular search terms.
- **Configuration:** All settings (index paths, excluded folders, hotkey) are stored in `~/.filemind/config.properties`.

---

*This document is maintained alongside the code. For feature requests, please open an issue in the repository..*
