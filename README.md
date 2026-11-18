# Wakil (وكيل)

A free Android AI assistant that talks to several free LLM providers and moves to the next one when a quota runs out.

**Status: early. Only stage W1 exists** — a chat screen, a provider router, and Arabic / French / English UI. There are no tools, no memory, no self-development loop yet. Those are planned, not built.

## What works today (W1)

- Chat with an LLM through any OpenAI-compatible endpoint.
- `ModelRouter`: tries providers in order (Groq, then Gemini, then OpenRouter, then Mistral), switches on HTTP 429, reads `x-ratelimit-*` response headers and shows the remaining quota when the provider sends them.
- You enter your own free API keys in Settings. They are stored with `EncryptedSharedPreferences` and never leave the device except in requests to the provider you chose.
- UI in Arabic (RTL), French and English; no hard-coded UI strings (checked in CI).

## Not built yet

Tools, permissions, memory, skills, sub-agents, Ollama/Tailscale home server, on-device model, self-evolution loop. See `docs`-style plan in the owner's private notes; this README will only list what really exists.

## Public repository rules

This repo is public so GitHub Actions minutes are free. Therefore: no secrets, no personal data. `guard.yml` enforces it on every PR (gitleaks, forbidden files, e-mail/phone patterns, red paths).

## Build

CI builds everything (`.github/workflows/build.yml`): i18n check, unit tests, APK. Merged commits on `main` publish a signed APK in Releases.

To build locally you need JDK 17 and the Android SDK: `./gradlew testDebugUnitTest assembleDebug`.

## Layout

```
app/        Android app (Kotlin, Jetpack Compose)
skills/     agent skills, plain Markdown (empty for now)
agents/     sub-agent definitions, Markdown (empty for now)
server/     scripts for a home server (empty for now)
scripts/    CI helpers (guard, i18n check)
```
