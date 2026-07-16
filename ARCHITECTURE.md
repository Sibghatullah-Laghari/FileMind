# System Architecture – Document Search Engine

> **📅 Updated:** 2026-07-16  
> **📌 Purpose:** This document describes the high‑level architecture of the document search engine, from user interface down to the underlying file system.

---

## 🏗️ Architecture Overview (Layered View)

```text
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                            │
│   (Web interface / REST API endpoints)                     │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       Controllers                          │
│   (Request routing, input validation, response formatting) │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      Search Engine                         │
│   (Query parsing, ranking, scoring, result aggregation)    │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      Lucene Index                          │
│   (Inverted index, term dictionaries, stored fields)       │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      Apache Tika                           │
│   (Content extraction, metadata parsing, text detection)   │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       File System                          │
│   (Physical storage – documents, PDFs, Word files, etc.)   │
└─────────────────────────────────────────────────────────────┘
