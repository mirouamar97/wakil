# Wakil (وكيل)

A free, local-first native Android personal AI assistant with an integrated personal life-management layer.

## Overview

Wakil combines two essential layers:
1. **The Intelligence Layer (Wakil):** Streaming chat with ModelRouter across free LLM providers (Groq, Gemini Flash, Cloudflare Workers AI, OpenRouter, Mistral), key rotation, quota tracking via rate-limit headers, and local AI agent tools (L0 read, L1 local write).
2. **The Personal Life Layer:** Grounded local database (Room) for Goals, Projects, Tasks, Notes, Action History, and Daily Command Center. Works 100% offline.

## Core Features

- **Command Center:** Deterministic Top 3 daily priorities, overdue task detection, active project tracking with next action verification, and one-line quick capture.
- **ModelRouter with Streaming:** Live streaming responses with failover on HTTP 429/5xx/network errors, header-based quota tracking, and EncryptedSharedPreferences (Android Keystore backed) storage for user keys.
- **Safety & Permissions:** Hard rule that record deletion and deadline changes require explicit user approval. Emergency Kill Switch immediately blocks assistant actions. Action log with full undo support.
- **Backup & Restore:** Fast, offline JSON export and import with preview using Android Storage Access Framework.
- **Trilingual & RTL-First:** Native support for Arabic (default, RTL with Cairo font), French, and English. Zero hard-coded UI strings.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for full system architecture, entity relationships, and permission policies.

## Repository Safety

This is a public repository:
- No secrets or personal data, ever. Enforced by `.github/workflows/guard.yml` and Gitleaks.
- Sensitive workflows and security files are strictly protected red paths.

## Build & Test

Built in CI via `.github/workflows/build.yml` on JDK 17:
```bash
./gradlew testDebugUnitTest assembleDebug
```
Check localization parity:
```bash
python scripts/check_i18n.py
```
Check repository security guard:
```bash
bash scripts/guard.sh
```
