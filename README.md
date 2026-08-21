# 🧠 FileMind

> Give your computer a memory that never forgets.  
> 📅 **README Updated:** 2026-07-16 – added build instructions and development notes.

[![Java](https://img.shields.io/badge/Java-21-red?logo=openjdk)](https://adoptium.net/)
[![Memory](https://img.shields.io/badge/RAM-<100MB-brightgreen)](https://github.com/)
[![OS](https://img.shields.io/badge/OS-Windows_|_Linux-lightgrey)](https://github.com/)
[![Search](https://img.shields.io/badge/Search-Lucene-blue)](https://lucene.apache.org/)
[![Parsing](https://img.shields.io/badge/Parsing-Tika-orange)](https://tika.apache.org/)
[![Status](https://img.shields.io/badge/Status-v1.0--Beta-yellowgreen)](https://github.com/)

**FileMind** is a desktop search application that transforms your computer into a searchable personal knowledge library. Similar to *Google* for your own files, it lets you search PDFs, source code, documents, screenshots, and notes in milliseconds using a simple `Ctrl+Space` shortcut—**without relying on the cloud or consuming excessive system resources.**

The application operates quietly from the system tray, continuously indexes files in the background, and searches the contents of documents instead of only their names. It is designed for developers, students, writers, and anyone who manages a large collection of digital files.

---

## ✨ Key Features.

- ⚡ **High-Speed Search** – Apache Lucene delivers search results in milliseconds, even on modest hardware.
- 📂 **Content-Aware Search** – Extracts text from over 1,000 file formats including PDF, Word, Excel, PowerPoint, HTML, ZIP archives, and source code (`.java`, `.py`, `.xml`)..
- 🖥️ **Background Operation** – Runs from the system tray with a global `Ctrl+Space` shortcut for instant access.
- 🪶 **Resource Efficient** – Built with pure Java and avoids unnecessary framework overhead, typically using **70–90 MB of RAM**.
- 🔄 **Automatic File Monitoring** – Java WatchService detects newly created, updated, and removed files, keeping the search index synchronized automatically.
- 🔒 **Privacy Focused** – All indexing and searching are performed locally. Your files remain on your own computer.
- 🖼️ **OCR Support** *(Phase 4)* – Planned image and screenshot text extraction using Tesseract OCR.
- 🧠 **AI Search** *(Future)* – Planned integration with local LLMs through Ollama for natural language search queries such as *"find the JWT project I worked on last week."*

> 💡 *Tip:* The search index is stored in `~/.filemind/index` – you can safely delete it to trigger a full re-index if needed.

---

## 🧩 Technology Stack.

| Technology | Role | Description |
| :--- | :--- | :--- |
| **Apache Lucene** | Search Engine | Provides high-performance indexing and full-text search with a minimal memory footprint. |
| **Apache Tika** | Content Extraction | Extracts searchable text from more than 1,000 supported document formats, including PDF, Office documents, HTML, ZIP archives, and more. |
| **Java WatchService** | File Monitoring | Watches the file system for create, modify, and delete events to enable automatic incremental indexing. |
| **SQLite** | Metadata Storage | Lightweight embedded database used to store file metadata, indexing information, and tags. |
| **Java Swing** | Desktop Interface | Powers the application's graphical interface, including the search window, results list, and settings screens. |
| **Java SystemTray** | System Integration | Displays the application in the operating system tray for quick access and background execution. |
| **Tess4J (Tesseract)** | OCR Engine | *(Phase 4)* Extracts searchable text from images and screenshots while running in the background. |
| **Ollama** | Local AI Integration | *(Phase 5)* Enables offline semantic search using lightweight local language models such as Gemma 2B and Phi-3 Mini. |

> 📌 *Why pure Java?* – We chose vanilla Java (without Spring Boot) to keep the application lightweight (~15MB JAR), reduce startup time, and avoid memory overhead from dependency injection containers.

---

## 🏗️ Architecture & Project Structure.

FileMind is built entirely with **pure Java** and follows a modular package structure instead of relying on Spring Boot or a web server. This approach keeps the application lightweight, responsive, and easy to maintain while allowing individual components to evolve independently.

```text
filemind/
├── src/main/java/com/filemind/
│   ├── core/          # Indexing, searching, and query logic
│   ├── monitor/       # WatchService integration for file changes
│   ├── ui/            # Swing-based windows and system tray
│   ├── parser/        # Tika wrapper and custom parsers
│   └── store/         # SQLite metadata handling
├── src/main/resources/
├── pom.xml            # Maven build configuration
└── README.md          # This file..
.

.....
