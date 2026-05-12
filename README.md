# On-Device AI Agent for Android

[![License: Apache 2.0](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-0.1.0-orange.svg)
![Android API](https://img.shields.io/badge/Android-API%2036%2B-3DDC84.svg?logo=android)
<!-- ![Build](https://img.shields.io/badge/build-pending-lightgrey.svg) — enabled once CI is wired up. -->

> An autonomous AI agent that runs on the device, understands natural language,
> plans tasks, and takes real actions across Android — without requiring an
> internet connection.

## Pre-release notice

This project is currently at **version 0.1.0** and is published for review and
experimentation. Expect rough edges:

- There are no stability guarantees for the public surface (Kotlin APIs,
  pipeline JSON schema, settings layout) between versions.
- On-device storage formats (Room schema, encrypted preferences, exported
  pipeline JSON) may change without a migration path.
- Documentation marked **TBD** below will be filled in by upcoming work in
  this same phase.

## Overview

The agent is an autonomous assistant for Android that takes a user request in
natural language, decides what to do, and carries the work out across the
device. Inference happens on-device via **LiteRT-LM** (the successor to
TensorFlow Lite, part of Google Edge AI), so a typical conversation —
including planning, tool invocations, and final replies — never leaves the
phone.

Pipelines are first-class. Every chat session is processed by a graph of
typed nodes (input, local LLM, optional cloud LLM, tool calls, routing,
decomposition, evaluation, clarifications, output) that the user can edit
either inside the app or in a standalone browser editor. Built-in prompt
variables let system prompts pull live values (current time, active model,
recent long-term memory) at render time without baking them into the
template.

Tools are wired through **AppFunctions Jetpack** for local actions and the
**Model Context Protocol (MCP)** for external servers. Destructive or
sensitive tool calls go through a human-in-the-loop gate so the agent
cannot, for example, send a message or delete a file without the user
seeing the request first. Cloud LLM providers are optional and bring-your-
own-key; nothing is sent off-device unless the user has explicitly
configured it.

## Key features

- Local LLM inference through LiteRT-LM with optional NPU/GPU acceleration.
- Optional cloud providers: OpenAI, Anthropic, Google (Gemini), DeepSeek,
  Ollama — all opt-in, bring-your-own-key.
- Model Context Protocol (MCP) client for connecting external tool servers.
- AppFunctions Jetpack integration for on-device tool calls.
- Visual pipeline orchestrator inside the app for editing node graphs.
- Pipeline library with per-chat binding, plus rename / duplicate / delete.
- Prompt variables (`$DATE`, `$TIME`, `$TOOLS`, `$MODEL`, `$MEMORY_SUMMARY`)
  rendered fresh on every execution.
- Agent-initiated clarifications: the agent can ask the user a question
  mid-pipeline and wait for the reply (human-in-the-loop).
- Multi-session chats with a priority task queue.
- Background execution as an Android Foreground Service with explicit idle
  and power-state management.
- Long-term memory with semantic retrieval (RAG) over past conversations.
- Standalone browser-based editor (`pipeline-editor.html`) for authoring
  and exporting pipelines without launching the app.
- Opt-in Firebase Crashlytics for anonymous crash reporting — off by
  default, never collects message content. See [SECURITY.md](SECURITY.md).
- At-rest encryption: Room database is SQLCipher-encrypted, API keys live
  in `EncryptedSharedPreferences` backed by the Android Keystore.

## Screenshots

> Placeholder images — real screenshots will land alongside the first
> tagged pre-release.

| Chat screen | Visual orchestrator |
|---|---|
| ![Screenshot TODO — chat](docs/screenshots/TODO.png) | ![Screenshot TODO — orchestrator](docs/screenshots/TODO.png) |

| Pipeline library | Browser pipeline editor |
|---|---|
| ![Screenshot TODO — pipeline library](docs/screenshots/TODO.png) | ![Screenshot TODO — browser editor](docs/screenshots/TODO.png) |

## Requirements

- Android 16 or newer (API level 36+).
- Approximately 2 GB of free RAM available for the LLM at runtime.
- Optional: hardware acceleration via NPU or GPU for noticeably faster
  inference. CPU-only operation is supported but slower.

## Quick start

```bash
git clone https://github.com/alexeyw/PersonalAndroidAIAgent.git
cd PersonalAndroidAIAgent
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

After installing:

1. Launch the app.
2. Open **Models** and download a LiteRT model through the built-in
   download manager. A small instruction-tuned model such as Gemma 2B from
   Hugging Face is a good starting point; you can also paste a custom URL.
3. Once the model finishes loading, send a first message from the chat
   screen to verify everything is wired up.

## Tech stack

| Layer            | Technology                                              |
|------------------|---------------------------------------------------------|
| Language         | Kotlin                                                  |
| UI               | Jetpack Compose + Material Design 3                     |
| LLM engine       | LiteRT-LM (Google Edge AI / ex-TensorFlow Lite)         |
| Tool calling     | AppFunctions Jetpack                                    |
| Agent framework  | Koog                                                    |
| Architecture     | Clean Architecture + MVVM                               |
| DI               | Hilt                                                    |
| Async            | Coroutines / Flow                                       |
| Network          | Retrofit, Coil                                          |
| Local storage    | Room + DataStore                                        |
| Testing          | JUnit + MockK                                           |

## Documentation

- Architecture overview — [docs/architecture.md](docs/architecture.md).
- User guide — [docs/user-guide.md](docs/user-guide.md).
- Extending the agent (new node types, tools, providers, prompt
  variables) — [docs/extending.md](docs/extending.md).
- Code style — [docs/code-style.md](docs/code-style.md).
- Testing strategy and coverage — [docs/testing.md](docs/testing.md).
- API & integration conventions — [docs/api-conventions.md](docs/api-conventions.md).
- Contributing guide — [CONTRIBUTING.md](CONTRIBUTING.md).
- Code of Conduct — [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
- Security policy and threat model — [SECURITY.md](SECURITY.md).
- Release notes and version history — [CHANGELOG.md](CHANGELOG.md).

## License

Released under the Apache License 2.0. See [LICENSE](LICENSE) for the full
text.
