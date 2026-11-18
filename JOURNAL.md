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
