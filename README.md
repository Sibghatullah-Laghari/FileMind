# 🧠 FileMind

> Your computer deserves a photographic memory.

[![Java](https://img.shields.io/badge/Java-21-red?logo=openjdk)](https://adoptium.net/)
[![Memory](https://img.shields.io/badge/RAM-<100MB-brightgreen)](https://github.com/)
[![OS](https://img.shields.io/badge/OS-Windows_|_Linux-lightgrey)](https://github.com/)
[![Search](https://img.shields.io/badge/Search-Lucene-blue)](https://lucene.apache.org/)
[![Parsing](https://img.shields.io/badge/Parsing-Tika-orange)](https://tika.apache.org/)
[![Status](https://img.shields.io/badge/Status-Building...-yellow)](https://github.com/)

**FileMind** is a desktop application that turns your entire computer into a searchable knowledge base. Think *Google*, but for your own files, PDFs, code, screenshots, and notes. Type a word, hit `Ctrl+Space`, and get results in milliseconds—**no cloud, no boot lag, no heavy RAM**.

It runs silently in your system tray, indexes files in the background, and reads *inside* your documents. Built for developers, writers, and digital hoarders who are tired of digging through folders.

---

## ✨ Key Features

- ⚡ **Blazing Fast Search** – Apache Lucene returns results in milliseconds, even on old laptops.
- 📂 **Reads Inside Files** – Extracts text from 1000+ formats: PDF, Word, Excel, PowerPoint, HTML, ZIP, code (`.java`, `.py`, `.xml`).
- 🖥️ **Always On** – System tray icon + global shortcut (`Ctrl+Space`) brings up search from anywhere.
- 🪶 **Lightweight** – Pure Java, no Spring Boot overhead. Sits at ~**70–90 MB RAM** during normal use.
- 🔄 **Real-Time Indexing** – Java WatchService detects new, modified, or deleted files instantly. No manual re-scans.
- 🔒 **100% Offline** – Your data never leaves your machine. Privacy-first.
- 🖼️ **OCR Ready** (Phase 4) – Extracts text from screenshots and images via Tesseract.
- 🧠 **AI Powered** (Future) – Local LLMs (Ollama) for natural language queries like *"find that JWT project from last week."*

---

## 🧩 Tech Stack

| Technology | Role | What it does |
| :--- | :--- | :--- |
| **Apache Lucene** | Search Engine | The core brain. Indexes text and returns results instantly. Used by Elasticsearch internally. Tiny footprint (~5 MB jar). |
| **Apache Tika** | Content Extractor | Reads inside 1000+ file types (PDF, DOCX, XLSX, PPTX, HTML, ZIP) and extracts pure text for indexing. |
| **Java WatchService** | File Watcher | Built into Java. Detects file system changes (create/modify/delete) in real-time. Triggers incremental indexing. |
| **SQLite** | Metadata DB | Ultra-lightweight single-file database. Stores file paths, metadata, tags, and last-indexed timestamps. |
| **Java Swing** | Desktop GUI | Clean, native-looking interface for the search bar, results panel, and settings. |
| **Java SystemTray** | Tray Icon | Places the app icon in the system taskbar (like Grammarly or Dropbox). |
| **Tess4J (Tesseract)** | OCR Engine | (Phase 4) Converts images/screenshots to searchable text. Runs as a background job to keep the UI snappy. |
| **Ollama** | Local AI | (Phase 5) Runs small LLMs (Gemma 2B, Phi-3 Mini) offline for semantic/natural language search. |

---

## 🏗️ Architecture & Project Structure

No Spring Boot. No web server. Just **pure Java** with modular packages to keep it lean and fast.
