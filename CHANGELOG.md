# Changelog

All notable changes to DocuMind will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Dockerfile with multi-stage build (JDK 17 Maven build → Alpine JRE runtime)
- GitHub Actions CI pipeline (test on push/PR, Docker build on main)
- `.dockerignore` for optimized image builds
- `docs/ENGINEERING_GAPS.md` — engineering maturity audit document
- `docs/DEPLOYMENT.md` §10 Docker deployment guide
- Vector store persistence: `InMemoryEmbeddingStore` serialized to `.documind-vectors.json`
- Index cache persistence: `.documind-index-cache.json` for incremental refresh after restart
- Atomic file write (temp + atomic move) for store and cache files
- Session memory bounded by Caffeine cache (`maximumSize` + `expireAfterAccess`)
- Configurable session limits: `app.chat.session.max`, `app.chat.session.ttl-minutes`
- `CHANGELOG.md` for release tracking
- GitHub Actions release workflow (tag → build JAR → push GHCR image → GitHub Release)
- File-backed H2 database for document metadata, knowledge gaps, audit records, and user accounts

### Changed
- Session memory storage changed from `ConcurrentHashMap` to Caffeine `Cache` (default: 1000 max sessions, 60 min TTL)
- `RagService.init()` now loads persisted vector store before running index refresh
- `AGENTS.md` updated to reflect vector store persistence and session memory bounds

## [1.0.0-SNAPSHOT] — 2026-06-24

### Features
- RAG-based document Q&A with DeepSeek API (OpenAI-compatible)
- Local embedding model (All-MiniLM-L6-V2, no external service required)
- Multi-format document parsing: PDF, TXT, DOC/DOCX, PPT/PPTX, XLS/XLSX
- Multi-knowledge-base support with per-user access control
- Streaming chat (SSE) with non-streaming fallback
- Keyword + vector hybrid retrieval with CJK bigram support
- Prompt injection defense in RAG context
- Basic Auth with ADMIN/USER roles
- Rate limiting (per-account, configurable)
- Audit logging for all operations
- Knowledge gap tracking and FAQ draft generation
- Document staleness detection
- Health check endpoints (liveness + readiness)
- Configurable via environment variables (20+ parameters)
- Original HTML/CSS/JS frontend with dark mode
