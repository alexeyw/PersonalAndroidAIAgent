# Security Policy

This document describes the security posture of the **On-Device AI Agent for
Android** project: what data the app handles, how it is protected, what is sent
off-device when the user opts in to crash reporting, and how to report a
vulnerability you discover.

The project is currently a **pre-release (0.3.0)** and is published primarily
for review and experimentation. There are no stability guarantees for storage
formats, APIs, or persisted data across versions.

---

## Supported Versions

Only the latest commit on `main` is supported. There are no maintained release
branches or long-term-support tags at this time.

| Version             | Supported          |
|---------------------|--------------------|
| `main` (latest)     | :white_check_mark: |
| Older commits/tags  | :x:                |

---

## Threat Model

The agent is designed around the principle that **sensitive user data stays on
the device** unless the user has explicitly configured an outbound integration
(such as a cloud LLM provider). The following protections apply to local
storage and credentials:

### Local database (at-rest encryption)

- The Room database `agent_database.db` is encrypted with **SQLCipher**
  (`net.zetetic:sqlcipher-android`), wired through Room via
  `SupportOpenHelperFactory`.
- The encryption protects the contents of all tables that may hold material
  derived from user input or model output:
  - `chat_messages` — user messages and model replies.
  - `chat_sessions` — chat metadata and pipeline bindings.
  - `memory_chunks` — fragments of long-term agent memory extracted from
    prior conversations.
  - `trace_steps` — intermediate per-node outputs produced during pipeline
    execution.
- The SQLCipher passphrase is a **32-byte random value** generated on first
  launch and persisted in `EncryptedSharedPreferences`. The master key
  protecting `EncryptedSharedPreferences` is stored in the Android Keystore.
- The app does not retain any plaintext copy of the passphrase. Legacy
  unencrypted databases from earlier development builds are not migrated; if
  one is detected, Room recreates the database via destructive migration.
- **Pre-1.0 data-durability caveat.** The Room database is opened with
  `fallbackToDestructiveMigration(true)`. Until `1.0.0`, schema migrations are
  **not guaranteed**: any schema-version bump may drop **all** local tables and
  recreate them empty rather than migrate the data. This affects every
  user-authored surface, not just conversations — chats and metadata
  (`chat_messages`, `chat_sessions`), long-term memory (`memory_chunks`),
  pipeline run traces (`trace_steps`), **custom pipelines**
  (`pipelines`, `pipeline_nodes`, `pipeline_connections`), and **saved presets
  and prompt templates** (`pipeline_presets`, `prompt_presets`,
  `prompt_templates`). This is a data-loss / availability caveat, not a
  confidentiality one — discarded rows are destroyed, never exposed. Users who
  need to retain data across an upgrade should export it first: chats and
  long-term memory through their in-app export actions, and any custom
  pipelines / saved presets via the pipeline-library and preset JSON-export
  actions.

### API keys for cloud providers

- Keys for optional cloud LLM providers (OpenAI, Anthropic, Google, DeepSeek,
  Ollama) are stored exclusively in `EncryptedSharedPreferences`.
- Keys are never written to plain `SharedPreferences`, DataStore, log files,
  exported chat archives, or any artifact checked into the repository.

### On-device processing by default

- All inference performed through the on-device LiteRT-LM engine is local.
  No prompt, model output, memory chunk, tool input, or tool output leaves
  the device as part of normal operation.
- The app reaches the network only for explicitly user-initiated actions:
  - Sending a request to a cloud LLM provider that the user has configured
    with their own API key.
  - Downloading a model file from a URL the user supplied (for example,
    Hugging Face).
  - Anonymous crash reporting **after** the user has opted in (see below).

### Out of scope

The threat model does not attempt to defend against:

- A device that is rooted, jailbroken, or otherwise compromised at the OS
  level.
- An attacker with physical access to an unlocked device.
- Screen capture, accessibility services, or other apps with elevated
  privileges granted by the user.
- Prompt-injection attacks delivered through content the user feeds into the
  model. The agent confirms destructive or sensitive tool invocations with
  the user (human-in-the-loop), but it cannot prevent the model from
  producing untrusted output.
- Vulnerabilities in third-party dependencies. Those should be reported to
  the respective upstream projects.

---

## What Is Collected (Crash Reporting)

Crash reporting is **opt-in and disabled by default**. The following controls
apply:

- The `AndroidManifest.xml` sets both
  `firebase_crashlytics_collection_enabled` and
  `firebase_analytics_collection_enabled` to `false`, which disables Firebase
  auto-collection at process start.
- A runtime gate in `CrashReportingRepository` short-circuits every reporting
  call to a no-op until the user toggles
  **Settings → Privacy → Send anonymous crash reports** to on. The toggle is
  accompanied by an in-app description of what is collected.
- **Debug builds never enable crash reporting.** The opt-in observer that
  forwards events to Firebase is only installed in release builds; debug
  builds use a local Timber tree and do not touch Crashlytics regardless of
  the persisted preference.

When (and only when) a user has explicitly opted in on a release build, the
following information may be transmitted to Firebase Crashlytics:

- Stack traces for fatal crashes and non-fatal `Log.WARN` / `Log.ERROR`
  records captured by Timber.
- Device model and Android OS version.
- App version and build identifier.
- Two custom keys set by the pipeline engine: `active_pipeline_id` and
  `active_model` (the identifier of the pipeline and the model in use when
  the event occurred).

The following are **never** transmitted off-device, even with crash reporting
enabled:

- The contents of chat messages, model prompts, or model replies.
- Long-term memory chunks or any user-authored text.
- Tool inputs, tool outputs, or arguments produced by the agent.
- API keys, passphrases, or any value stored in
  `EncryptedSharedPreferences`.
- Personally identifying information beyond the device/app metadata listed
  above.

The user can revoke consent at any time from the same settings entry; the
runtime gate then returns every reporting call to a no-op.

---

## Reporting a Vulnerability

Please report suspected vulnerabilities **privately** through GitHub Security
Advisories, using the **Security** tab of this repository and the
"Report a vulnerability" action. This opens a private channel between you and
the maintainers; public issues should not be used for security reports.

When reporting, please include:

- The affected version (commit SHA or build identifier).
- A clear description of the issue and its security impact.
- Reproduction steps, proof-of-concept code, or sample data, if available.
- Expected versus actual behavior.
- Any suggested mitigation, if you have one.

Response expectations:

- **Acknowledgement:** best effort within 7 days.
- **Fix timeline:** no fixed SLA at the current pre-release stage. We will
  communicate a target timeline with you after triage.
- Please do not publicly disclose the issue until a fix has been released or
  we have agreed on a coordinated disclosure date.

Reports about vulnerabilities in third-party dependencies (LiteRT-LM, Koog,
Room, SQLCipher, Firebase, and so on) should be filed with the respective
upstream projects. We are happy to receive a courtesy heads-up if such an
issue materially affects this project.

---

## Scope

In scope for this policy: source code in this repository, build configuration,
and the runtime behavior of the resulting Android application.

Out of scope: vulnerabilities in third-party dependencies, model-quality
issues (hallucinations, refusals, biased output), and reports that require an
attacker to already control the device or its operating system.
