# Security Policy

This document describes the security posture of the **On-Device AI Agent for
Android** project: what data the app handles, how it is protected, what is sent
off-device when the user opts in to crash reporting, and how to report a
vulnerability you discover.

The project is currently a **pre-release (0.4.0)** and is published primarily
for review and experimentation. There are no stability guarantees for storage
formats, APIs, or persisted data across versions.

---

## Supported Versions

Only the latest release line is supported. As a solo pre-release project there
are no maintained back-release branches or long-term-support tags; fixes land on
the current `0.4.x` line and on the latest commit on `main`.

| Version            | Supported          |
|--------------------|--------------------|
| `0.4.x` (latest)   | :white_check_mark: |
| `< 0.4.0`          | :x:                |

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
  - `trace_steps` — the persistent pipeline-run trace: per-node inputs and
    outputs, console events, and resolved long-term-memory snapshots recorded
    while a run executes (this is what console replay and checkpoint resume
    read back).
  - `pipeline_runs` — persistent run records, including the original user
    prompt of each run and per-node progress markers.
  - `pending_interactions` — parked human-in-the-loop requests for runs that
    wait in the background: each record stores the staged **tool name and the
    exact arguments** awaiting approval (or the clarification question), so
    they are protected at rest like the conversation that produced them.
- The SQLCipher passphrase is a **32-byte random value** persisted in a
  Keystore-backed encrypted store: each value is encrypted with AES-256-GCM
  under a dedicated, non-exportable key held in the Android Keystore, and
  every ciphertext is authenticated against its storage slot so blobs cannot
  be swapped between entries. (Earlier releases used the now-deprecated
  `EncryptedSharedPreferences`; it was replaced — together with the
  `androidx.security:security-crypto` dependency — by this direct Keystore
  wrapper, removing the intermediate wrapped-keyset file and its corruption
  modes. As permitted by the pre-release storage policy, there is **no data
  migration**: an install upgraded across this change boots into the startup
  recovery screen, where the only path forward for the old database is the
  explicit wipe, and previously saved API keys must be re-entered.)
- **The passphrase is generated only while no database file exists yet, and
  is never regenerated once one does.** While a database is present, any
  failure to read the stored passphrase — unopenable preferences, a missing
  or malformed entry, or a key/file mismatch after the database was restored
  from another install — raises a typed error that routes to a dedicated
  startup recovery screen. That screen offers **Retry** (keystore failures
  are often transient) and an explicit **Erase all data** action behind a
  typed confirmation; the app never wipes, re-keys, or silently recreates
  the passphrase store on its own while user data could be orphaned by it.
  The passphrase is read lazily at the first real database open — never
  during dependency injection — so a failure always surfaces where the UI
  can handle it.
- The store holding **cloud API keys** intentionally keeps the opposite,
  availability-first recovery: a key value that can no longer be decrypted is
  treated as unset and dropped. Unlike the database passphrase, keys can
  simply be re-entered by the user, so availability wins over preservation
  there.
- The app does not retain any plaintext copy of the passphrase.
- **Schema migrations preserve data on upgrade.** Every schema-version bump is
  backed by an explicit Room `Migration` registered through `addMigrations(...)`;
  the destructive-recreation fallback on upgrade has been removed. An in-place
  upgrade therefore keeps all local data — chats and metadata (`chat_messages`,
  `chat_sessions`), long-term memory (`memory_chunks`), pipeline run traces
  (`trace_steps`), **custom pipelines** (`pipelines`, `pipeline_nodes`,
  `pipeline_connections`), and **saved presets and prompt templates**
  (`pipeline_presets`, `prompt_presets`, `prompt_templates`). The migrations
  across the exported-schema baseline range are covered by a `MigrationTestHelper`
  regression suite that validates the resulting schema and data preservation.
- **Residual pre-1.0 caveats.**
  - *Downgrade.* Installing an **older** build over a newer database recreates
    it empty (`fallbackToDestructiveMigrationOnDowngrade`), since forward
    migrations cannot reverse a schema. Avoid downgrading if you want to keep
    local data.
  - *Legacy plaintext dev databases.* Unencrypted databases from pre-SQLCipher
    development builds (which predate the public release) are not supported and
    cannot be opened. This affects only such dev installs, never a released
    version.

  Both are data-loss / availability concerns, not confidentiality ones —
  discarded rows are destroyed, never exposed. If you must downgrade, export
  anything you want to keep first: chats and long-term memory through their
  in-app export actions, and any custom pipelines / saved presets via the
  pipeline-library and preset JSON-export actions.

### Agent file workspace (at-rest)

The agent has a small private **workspace** — a single jailed directory
(`files/agent_workspace/` inside the app's private `filesDir`) that the file
tools (`read_file`, `write_file`, `edit_file`, `delete_file`, `list_files`,
`find_files`) read from and write to, and that the **Files** screen surfaces
to the user. Its at-rest posture is deliberately **weaker than the
database's**, and this is the honest statement of that trade-off:

- The workspace lives in app-private internal storage, so it is protected by
  the device's **file-based encryption (FBE)** — the OS-level encryption that
  covers every app's private data while the device is locked and the user has
  not yet authenticated after boot. It is sandboxed from other apps by the
  standard Android app-data permission boundary.
- It is **not** additionally encrypted with SQLCipher the way the Room
  database is. The database holds structured rows behind a single open
  helper, which makes a transparent cipher layer cheap and natural; the
  workspace holds arbitrary user/agent files streamed through file tools and
  exported to other apps, where a second application-level cipher would add
  cost and friction (every import/export/share would have to encrypt and
  decrypt) for protection that overlaps what FBE already provides. So
  workspace contents are protected by FBE and the app sandbox, **but not by
  the app's own SQLCipher key** — a meaningful difference if your threat model
  assumes an attacker who can read app-private storage on an unlocked,
  post-authentication device (which is already out of scope below, but is
  called out here so the asymmetry is not a surprise).
- The single canonicalisation gate (`AgentWorkspace.resolve`, which every
  other workspace operation funnels through) is the integrity boundary: every
  relative path a tool supplies is resolved and checked for containment, so a
  `../` traversal, an absolute path, or a symlink pointing out of the
  directory is refused with a typed `WorkspaceError.PathOutsideWorkspace`
  before any file is touched. A tool can therefore only ever read or write
  **inside** the workspace, never the rest of the app's private storage.

### Workspace quotas (availability control)

Two size quotas and a per-read budget bound how much an autonomous — possibly
injected or looping — agent can consume; they protect **availability**, not
confidentiality:

- A **per-file limit** (default 5 MB, `WorkspaceError.TooLarge`) and a
  **workspace-wide total limit** (default 100 MB, `WorkspaceError.QuotaExceeded`,
  pre-checked before any bytes are committed by the atomic stage-and-rename
  write) keep a runaway `write_file` loop from exhausting device storage. User
  imports through the Files screen are charged against the same limits.
- A **per-read token budget** (default 2000 tokens) truncates `read_file`
  output so a single large file cannot blow out the local model's context
  window, and the `http_request` response is capped (1 MB default) so untrusted
  remote content cannot do the same. Both limits are user-tunable.

### Run-history retention (mitigating control)

Pipeline runs and their traces accumulate content **derived from user
input** — prompts, per-node inputs/outputs, tool observations — for as long
as the rows exist. Encryption protects them at rest; retention bounds how
much of that derived content exists at all:

- A daily maintenance pass (WorkManager, charging + idle) deletes finished
  runs that fall outside the **last N runs per chat** window or exceed the
  **maximum age**, together with their traces. Both limits are user-tunable
  in **Settings → Privacy** (defaults: 20 runs per chat, 30 days).
- Only runs in a settled, terminal state are eligible. A run parked on a
  background approval or clarification is never removed by retention while
  it waits; its lifetime is bounded separately by the **approval window**
  (default 24 hours), after which it is failed and becomes a regular
  retention candidate.
- Deleting a chat session removes its runs and traces immediately,
  independent of the retention schedule.

### API keys for cloud providers

- Keys for optional cloud LLM providers (OpenAI, Anthropic, Google, DeepSeek,
  Ollama) are stored exclusively in the same kind of Keystore-backed
  encrypted store as the database passphrase (AES-256-GCM under its own
  dedicated Android Keystore key).
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
  - The `http_request` tool reaching a host the user has explicitly added
    to the **allowed-domains allowlist** (empty by default; see *Outbound
    HTTP and the exfiltration chain* below).
  - Anonymous crash reporting **after** the user has opted in (see below).

### Prompt injection via tool content (accepted risk)

Content returned by tools is **untrusted model input**, and the agent does
not attempt to sanitize it. This is a deliberate, accepted trade-off — not an
oversight — and it works as follows:

- Text returned by any tool — Wikipedia extracts from the built-in
  `search_tool`, results from user-configured **MCP servers**, responses
  from **AppFunctions** exposed by other installed apps, the body of an
  `http_request` response, and **the contents of a file the agent reads from
  its workspace** — is fed back into the context of subsequent pipeline
  nodes. A file the user imported through the Files screen (or that an
  earlier `write_file` produced from untrusted material) is therefore
  **untrusted model input**, exactly like a network tool result: it may
  contain text that reads as instructions to the model. That content reaches
  planning and routing nodes (`DECOMPOSITION`, `INTENT_ROUTER`), so a crafted
  tool result or file can steer which branch a pipeline takes and
  **influence the arguments of later tool calls** in the same run.
- The backstop is the **human-in-the-loop gate**: before any `SENSITIVE` or
  `DESTRUCTIVE` tool executes, the chat surfaces a confirmation card showing
  the **tool name and the exact arguments** the model produced, and the run
  suspends until the user approves or denies. An injected instruction can
  therefore *propose* a harmful call, but cannot *execute* it unreviewed.
- `READ_ONLY` tools are **not gated by design** — prompting on every lookup
  would make the agent unusable. The residual exposure is that injected
  content can shape further read-only queries and the text of the final
  answer.
- Tools without a known risk level (all MCP-provided tools included) default
  to `SENSITIVE`, the conservative fallback, so they always hit the gate.

**Recommendation:** when connecting an MCP server you do not fully trust —
or one that serves content from the open web — set the tool-approval policy
in **Settings → Restrictions** to require approval for **every** tool call,
regardless of risk level. That closes the ungated read-only path for the
price of one extra tap per call.

### Outbound HTTP and the exfiltration chain

The file tools and the `http_request` tool together create a concrete
**data-exfiltration** shape that did not exist when the agent could only read
the web and talk to a local model: an injected instruction (planted in a file
the agent reads, or in any tool result — see above) tells the model to
`read_file` something private and then `http_request` it to an
attacker-controlled URL. `http_request` is the most security-sensitive tool in
the workspace set and is designed conservatively around exactly this chain.
The defences are layered so that no single one has to be perfect:

- **Empty allowlist by default, tool hidden until opt-in.** `http_request`
  can only reach a host the user has explicitly added to the allowed-domains
  list (Settings → Tools → Allowed domains, persisted in DataStore). While the
  list is empty the tool is **not published to the agent at all** — it never
  appears in the tool catalogue — and a direct invocation is refused. There is
  no default destination an injection could reach.
- **Exact-host matching, no implied sub-domains.** Matching is exact and
  case-insensitive: adding `example.com` does not authorise `api.example.com`.
  An injection cannot widen the user's grant by guessing a neighbouring host.
- **Human-in-the-loop on every call, by method.** Risk is resolved per
  request through `HttpRequestPolicy`: a `GET` is `SENSITIVE` and a
  `POST`/`PUT`/`DELETE` is `DESTRUCTIVE`, so every `http_request` passes the
  HITL gate. The confirmation card shows the model-produced **URL and
  arguments**, so a user who is paying attention sees the destination before
  the data leaves the device. An unparsable call falls back to the strictest
  risk.
- **Stored-credential filter.** Before a request is sent, its URL, headers,
  and body are scanned for any saved cloud-provider API key (OpenAI,
  Anthropic, Google, DeepSeek). If a request would carry one, it is refused
  outright — a saved key can never be exfiltrated through this tool, even with
  user approval.
- **Redirect re-validation.** Automatic redirects are disabled; each hop is
  re-validated against the same allowlist (a redirect that points outside it
  aborts the request), the chain is capped, and credential headers are
  stripped when a redirect crosses to a different host. A redirect cannot be
  used to slip past the allowlist.
- **Transport floor.** Public hosts must use `https`; cleartext `http` is
  permitted only for loopback / private-LAN addresses (the same exception the
  network-security config makes for a local Ollama server).

The residual risk is the honest one: a user who has **deliberately added a
host to the allowlist** and then **approves** a `SENSITIVE`/`DESTRUCTIVE`
`http_request` to it can still send workspace data to that host — the tool is
doing exactly what the user authorised. The allowlist and the HITL gate make
that an explicit, reviewable decision rather than a silent capability, which
is the design goal; they do not (and cannot) override a user who chooses to
trust a destination. The Files screen warns about this when adding a domain.

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
  producing untrusted output. Injection through **tool-returned** content and
  through **files the agent reads** is documented separately above
  (*Prompt injection via tool content*), as is the read-then-exfiltrate chain
  it can drive (*Outbound HTTP and the exfiltration chain*) — same
  conclusion, same backstop: the human-in-the-loop gate and the conservative
  `http_request` allowlist bound what an injection can *do*, not what the
  model can *be told*.
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
- API keys, passphrases, or any value stored in the Keystore-backed
  encrypted stores.
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
