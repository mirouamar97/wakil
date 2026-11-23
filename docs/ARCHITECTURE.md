# Wakil Architecture & System Design

Wakil (وكيل) is a local-first native Android personal AI assistant with an integrated personal life-management layer (formerly AMAR OS).

## 1. High-Level Architecture

The system is strictly divided into two cooperating layers:

```
+--------------------------------------------------------------+
|                     Presentation Layer                       |
|   Jetpack Compose (Material 3) · Cairo Font · RTL/LTR        |
|   Navigation: Home (Command Center) · Projects · Chat · Settings |
+--------------------------------------------------------------+
                              |
+--------------------------------------------------------------+
|                       Domain Layer                           |
|   LifeRules: Deterministic Top 3, Active Projects, Overdue   |
|   SecurityPolicy: L0 (Read), L1 (Local Write), Approvals     |
|   DailyBriefing: Grounded factual status generation          |
+--------------------------------------------------------------+
         |                                           |
+----------------------+                   +-------------------+
|      Data Layer      |                   |   AI / Network    |
| Room SQLite DB       |                   | ModelRouter       |
| Goals, Projects,     |                   | Groq, Gemini,     |
| Tasks, Notes,        |                   | Cloudflare,       |
| ActionLogs, Memories |                   | OpenRouter,       |
| BackupManager (JSON) |                   | Mistral           |
+----------------------+                   +-------------------+
```

## 2. Core Modules

### 2.1 Domain & Rules (`app.wakil.agent.domain.rules`)
- **`LifeRules`**: Pure Kotlin business logic.
  - **Today's Top 3 Priorities**: Scored deterministically based on overdue status (+1000), due today (+500), priority (+300/+150/+50), and active project association (+200).
  - **Active Projects Limit**: Default limit of 3 active projects. Warns and recommends which project to pause (oldest updated), but never blocks.
  - **Next Action Enforcement**: Verifies each active project has a designated next action.
  - **Overdue Task Detection**: Identifies incomplete tasks past their due date.
- **`SecurityPolicy`**:
  - Requires user approval dialog for any `DELETE` operation or deadline modification.
  - Restricts operations to `L0` (Read) and `L1` (Local Data). Disables `L2` (Messaging/Phone control) and `L3` (Code execution).

### 2.2 Local Storage (`app.wakil.agent.data.local`)
- **Room Database (`WakilDatabase`)**:
  - Tables: `goals`, `projects`, `milestones`, `tasks`, `notes`, `note_links`, `conversations`, `messages`, `action_logs`, `user_memories`.
  - Type-safe converters for enums and timestamps.
- **JSON Backup (`BackupManager`)**:
  - Structured JSON export/import via Android Storage Access Framework (SAF).
  - Schema versioned, preview before write, with validation.

### 2.3 Model Routing (`app.wakil.agent.router`)
- **`ModelRouter`**:
  - Dispatches chat requests to free OpenAI-compatible endpoints: Groq (default) -> Gemini Flash -> Cloudflare Workers AI -> OpenRouter -> Mistral.
  - Real-time streaming (`chatStream`) with cancellation support.
  - Automatic fallback on HTTP 429, 401/403, 5xx, or network errors.
  - Rate-limit header parsing (`x-ratelimit-*`, `Retry-After`) with cooldowns.
  - Keys encrypted using Android Keystore-backed `EncryptedSharedPreferences`.

### 2.4 User Interface (`app.wakil.agent.ui`)
- Material 3 single-activity architecture with Navigation Compose.
- Typography styled with the Cairo variable font (SIL Open Font License).
- 100% localization across Arabic (default, RTL), French, and English without hardcoded strings.
