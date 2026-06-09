# Roadmap

This document describes where the project is headed after its initial
public release. It lists **directions, not commitments**: items are
ordered by rough priority within each horizon, carry no dates, and may be
reshaped or dropped as reality intervenes. The roadmap is updated as work
lands — if something here has already shipped, check
[CHANGELOG.md](../CHANGELOG.md) for the authoritative record.

Concrete, actionable work items live in the
[issue tracker](https://github.com/alexeyw/PersonalAndroidAIAgent/issues);
this document provides the connective tissue between them. GitHub
Milestones may be introduced later to group issues per release once the
release cadence settles.

## Where the project is today

The current pre-release line (`0.4.x`) already covers the core loop
end-to-end:

- On-device LLM inference through LiteRT-LM, with optional
  bring-your-own-key cloud providers (OpenAI, Anthropic, Google,
  DeepSeek, Ollama).
- Graph-driven pipeline execution with a full in-app editor, a standalone
  browser editor, and a pipeline library with per-chat binding.
- Tool calling through AppFunctions (local) and MCP (external servers),
  with a human-in-the-loop gate for sensitive and destructive actions.
- Long-term memory with semantic retrieval, extraction, compaction, and
  export / import.
- At-rest encryption (SQLCipher + Android Keystore) and explicit Room
  migrations that preserve local data across upgrades.

See [README.md](../README.md) for the full feature list and
[architecture.md](architecture.md) for how the pieces fit together.

## Near term

### Agent tool-set expansion

The single biggest gap between "the agent can reason" and "the agent is
useful" is the breadth of its built-in tool catalogue. A dedicated
workstream will grow the set of local tools the agent can call out of the
box — including evaluating file-oriented tools (reading, writing, and
organising on-device documents) and further system integrations. The
design space (which tools, which permission and HITL surfaces, which
backing APIs) is intentionally **not** fixed yet; proposals and use-case
reports in the issue tracker are welcome input while this is being
scoped.

### First release-signed build

Builds so far are debug-signed; the signing infrastructure for a real
release keystore is already in place (see [release.md](release.md)). The
remaining step is provisioning a production keystore and shipping the
first release-signed build — after which in-place updates carry forward
across releases. Note the one-time migration cost described in the
*Pre-release notice* of [README.md](../README.md): updating a debug-signed
install to a release-signed one requires a reinstall.

## Mid term

### On-device verification beyond the JVM gate

The CI gate is deliberately JVM-only today; everything that needs real
Android system services, native inference, or hardware is verified by a
manual smoke test (see
[testing.md](testing.md#what-the-automated-gate-does-not-cover)).
Narrowing that gap — running the instrumented test suite (Room
migrations, Compose UI flows, the AppFunctions end-to-end round-trip) on
an emulator or device farm as a scheduled or pre-release job — would turn
"green CI" into a much stronger signal.

### Pipeline editor refinement

The in-app editor and the standalone browser editor
(`pipeline-editor.html`) share the same pipeline JSON but evolve at
different speeds. Keeping the two surfaces at feature parity, smoothing
rough edges reported by early users, and improving validation feedback
are ongoing concerns rather than a single feature.

## Longer term

### Path to 1.0

`1.0.0` is the point where the project starts guaranteeing stability for
its public surfaces: the pipeline JSON schema, exported data formats
(pipelines, memory, presets), the settings layout, and upgrade paths.
Getting there is mostly a hardening exercise — schema versioning for
exports, deprecation policies, and a longer track record of explicit Room
migrations.

### Localization

All user-visible strings are currently English-only. Once the UI surface
stabilises, externalising strings for translation and accepting
community-contributed locales is a natural, well-bounded direction — and
a good area for first-time contributors.

## How to get involved

- Issues labelled
  [`good first issue`](https://github.com/alexeyw/PersonalAndroidAIAgent/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
  are scoped to be approachable without deep knowledge of the codebase;
  [`help wanted`](https://github.com/alexeyw/PersonalAndroidAIAgent/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22)
  marks items where outside contributions are especially welcome.
- [CONTRIBUTING.md](../CONTRIBUTING.md) covers dev setup, the branch
  model, and the PR checklist.
- [extending.md](extending.md) has step-by-step recipes for the
  cheapest high-value contributions: new node types, tools, cloud
  providers, and prompt variables.
- For new capabilities, open a
  [feature request](https://github.com/alexeyw/PersonalAndroidAIAgent/issues/new/choose)
  first — many ideas fit an existing extension point and can land without
  core changes.

## How this roadmap is maintained

The roadmap is revised when a direction ships, gets re-scoped, or stops
making sense — typically alongside the release that affects it. Major
changes go through a PR like any other documentation change, so the
history of this file *is* the history of the project's intent.
