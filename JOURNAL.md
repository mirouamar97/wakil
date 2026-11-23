# JOURNAL

Append-only log of what was merged or rejected, and why. The agent reads this before choosing its next task.

## W0 — repository skeleton
- Created the public repository, folder layout, `guard.yml` (gitleaks + forbidden files/patterns + red paths) and `build.yml`.
- Nothing runs autonomously yet.

## W1 — chat + model router (PR #2, release v0.1.5)
- Done: Compose chat screen, Settings (language, API keys in EncryptedSharedPreferences, model names), `ModelRouter` (Groq -> Gemini -> OpenRouter -> Mistral, failover on 429/401/5xx/network, quota counter from `x-ratelimit-*` headers) with JUnit tests, ar/fr/en strings with a CI check. CI green; not yet tried on a real phone.
- Not done: permanent release signing key (APK is signed with a throwaway debug key), Cloudflare and Ollama providers, streaming replies, saved conversations.

## W1 fixes
- Whole `.github/workflows/` folder is now a red path (agents may not edit it), because build.yml uses signing secrets.

## Security Hardening (PR #4)
- Fixed guard script to fail-closed on git fetch / diff failures.
- Expanded red path patterns to encompass all of `.github/`, `scripts/guard.sh`, `.gitleaks.toml`, and keystores.
- Expanded branch checks to `evolve/*|agent/*|claude/*|opencode/*|gemini/*|rebuild`.
- Ensured `guard.yml` checks out trusted base branch version of `guard.sh` during PR validation.
- Restricted `build.yml` default permissions to `contents: read`.
- Added strict `.gitleaks.toml`.

## R0-R3 Complete Rebuild (branch rebuild)
- Integrated personal life OS into Wakil: Room database with entities for Goals, Projects, Milestones, Tasks, Notes, NoteLinks, Conversations, Messages, ActionLogs, and UserMemories.
- Added pure Kotlin deterministic `LifeRules`: Top 3 priorities with reason codes, active projects limit warning and pause suggestion (max 3), next action verification for active projects, and overdue task detection.
- Added streaming chat support (`chatStream`) to `ModelRouter` and `HttpChatTransport` with live chunk emission, cancellation, and provider attribution.
- Added local AI Agent tools (L0/L1) with mandatory approval dialogs for `DELETE` and deadline changes.
- Added emergency Kill Switch in Settings to halt tool execution immediately.
- Added JSON export and import with preview using `kotlinx.serialization`.
- Expanded trilingual resources (`ar`, `fr`, `en`) to 111 keys with 100% parity and verified with `check_i18n.py`.
- Pinned all dependencies in `gradle/libs.versions.toml` with Version Catalog.
