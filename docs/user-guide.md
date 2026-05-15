# User Guide

This guide is for people who installed the **On-Device AI Agent for
Android** and want to learn how to use it. It walks through every
screen the app provides and the user-facing flows that connect them.

It is not a developer document. If you are looking for the internal
architecture, see [`architecture.md`](architecture.md); for recipes on
extending the agent with new node types, tools, or cloud providers,
see [`extending.md`](extending.md).

Screenshots in this guide are placeholders until the first tagged
pre-release ships with real captures.

---

## Table of contents

1. [Getting started](#getting-started)
2. [Chats](#chats)
3. [Console](#console)
4. [Pipelines](#pipelines)
5. [Browser pipeline editor](#browser-pipeline-editor)
6. [Tools and MCP](#tools-and-mcp)
7. [Memory](#memory)
8. [Settings](#settings)
9. [Troubleshooting](#troubleshooting)

---

## Getting started

The first time you launch the app you go through a brief onboarding
screen, then land on the Chat tab.

The bottom of the screen always shows the four navigation tabs:

- **Chat** — talk to the agent (the default tab the app opens on).
- **Pipelines** — browse and edit the agent's reasoning pipelines.
- **Tools** — manage AppFunctions and connected MCP servers.
- **More** — secondary screens (Memory, Models, Prompt library,
  Active tasks, Live metrics, Settings, About).

The Back gesture returns you up the inner stack of the current tab.
While you are on the start screen of a tab, Back closes the app — it
does not switch between tabs.

The first time you open the app, there is no model loaded yet — you
need to download one before you can talk to the agent.

### 1. Open the Models screen

Open the **More** tab and tap **Models**. The screen has two areas:

- A list of **presets** — curated LiteRT models that are known to work
  with the agent. Each preset shows the model name and a **Download**
  button (or a **Downloaded** label if it is already on the device).
- A **Custom Model URL** field for paste-in downloads (for example, a
  direct file URL from Hugging Face).

If the source you are downloading from requires authentication, paste
your token into the **HuggingFace Auth Token** field above the URL
input. The field is masked and is used only for the download request.

![Screenshot TODO — models screen](screenshots/TODO.png)

### 2. Download a model

- To use a preset, tap **Download** next to it.
- To download a custom file, paste the URL into **Custom Model URL**
  and tap **Download Custom Model**.

A progress bar appears with a percentage. You can leave the screen
while the download runs in the background.

### 3. Activate the model

After the download finishes, the model shows up in the **Downloaded**
list below the presets. Tap **Make Active** on the row you want to
use. The active model is highlighted and labelled **Active**. Only
one model can be active at a time.

### 4. Send a first message

Open the **Chat** screen and type a message. The agent should reply
within a few seconds on a device with hardware acceleration, and
within a longer wait on CPU-only devices. If the reply never arrives,
jump to [Troubleshooting](#troubleshooting).

---

## Chats

Every conversation lives in its own chat session. You can keep many
sessions in parallel — each one has its own message history and can
be bound to its own pipeline.

### Switching, creating, and renaming chats

Open the side drawer from the chat screen (tap the menu icon, or
swipe from the left edge). The drawer lists every chat under
**Chat Sessions**. The currently active session is highlighted.

- **New chat** — tap **New Chat** at the top of the drawer. The new
  session uses the default pipeline unless you explicitly attach a
  different one (see [Pipelines](#pipelines)).
- **Switch chat** — tap any row in the list. The chat screen updates
  immediately.
- **Rename chat** — tap the pencil icon next to a session row. A
  dialog titled **Rename Chat** opens with a **Chat Name** field.

![Screenshot TODO — chat drawer](screenshots/TODO.png)

### Exporting and importing chat history

The app uses standard Android share and file-picker intents, so chat
history is portable to any app that handles JSON or plain text.

- **Export** — from the chat screen's top-bar overflow menu (`⋮`)
  choose **Export Chat**. The Android **Share Sheet** opens with a
  JSON payload containing the full message history. Choose any
  destination that accepts JSON (Files, email, a messenger, a
  cloud-drive app, and so on).
- **Import** — open the drawer and tap **Import Chat**. The system
  file picker opens, filtered to `application/json` and `text/plain`.
  Selecting a previously exported file creates a new chat session
  with the imported messages.

### Saving and filtering messages

You can mark individual messages as favourites so they stay easy to
find later.

- **Save / Unsave** — long-press a message bubble and choose **Save**
  (or **Unsave** to remove the mark). A small star appears on saved
  bubbles.
- **Show only saved messages** — toggle the star icon in the chat
  screen's top bar. While the filter is on, only saved messages are
  rendered; toggle it again to see the full conversation.

You can also long-press a message to copy its text to the clipboard.

---

## Console

The console shows what the agent is actually doing while it processes
your request — which pipeline node is running, which tools were
called, which memory chunks were retrieved, and any errors that
occurred.

### Mini-console (collapsed)

A small strip sits just above the input field on the chat screen and
always shows the most recent agent events:

- The last three events on a typical screen, or the last event on a
  compact viewport.
- Each line is formatted as `[TAG] message`, where the tag indicates
  the event type (for example, `[NODE]`, `[TOOL]`, `[MEMORY]`,
  `[ERROR]`).
- When the agent needs your approval to run a sensitive or
  destructive tool, the mini-console shows inline **Approve** and
  **Deny** buttons in place of the latest event.

Tap anywhere on the strip to open the full log.

![Screenshot TODO — mini console](screenshots/TODO.png)

### Full log (expanded)

The full log opens as a bottom sheet covering most of the screen. It
shows every event from the current session in chronological order,
with millisecond timestamps.

At the top of the sheet is a row of filter chips:

| Chip      | Shows                                                 |
|-----------|-------------------------------------------------------|
| **All**     | Every event in the session.                         |
| **Nodes**   | Pipeline-node execution events only.                |
| **Tools**   | Tool invocations and their results only.            |
| **Memory**  | Long-term memory reads and writes only.             |
| **Errors**  | Error events only.                                  |

Two actions are available in the sheet:

- **Clear** — wipes the on-screen log for the current session after
  a confirmation dialog. The underlying chat messages are not
  affected.
- **Copy all** — copies the full plain-text dump of the log to the
  clipboard, regardless of which filter chip is active.

The sheet auto-scrolls to the latest event as long as you are pinned
to the bottom; if you scroll up to read older events, auto-scroll
pauses so you do not lose your place.

---

## Pipelines

A **pipeline** is the recipe the agent follows when it processes a
message. It is a graph of typed nodes (for example, a local-LLM call,
a cloud-LLM call, a tool invocation, an output formatter) connected
by arrows that describe the flow of data.

You do not need to design a pipeline yourself — the app ships with a
sensible default — but the orchestrator lets you tweak how the agent
thinks, what tools it can use, and what the final answer looks like.

### Library and active pipeline

Open the **Pipelines** screen to see every pipeline saved on the
device. The active pipeline is highlighted; sending a message uses
whichever pipeline is bound to the current chat (or the default
pipeline if the chat has no explicit binding).

Tap the `⋮` button on a row, or long-press the row, to see the
per-pipeline menu:

- **Load** — open this pipeline in the visual editor.
- **Rename** — open a dialog titled **Rename pipeline** with a
  **Name** field.
- **Duplicate** — create a copy with `(copy)` appended to the name.
- **Delete** — remove the pipeline after a confirmation dialog. The
  currently active pipeline cannot be deleted; switch to another one
  first.
- **Set as default** — make this pipeline the fallback for any chat
  that has no explicit binding.

Tap the **+** button (floating action button) to create a new
pipeline. New pipelines start as a minimal `INPUT → OUTPUT` graph so
that they are valid immediately.

![Screenshot TODO — pipeline library](screenshots/TODO.png)

### Binding a pipeline to a chat

When you create a new chat from the drawer, you can attach a specific
pipeline to it. Chats without an explicit binding fall back to the
default pipeline marked in the library.

### Visual editor

Loading a pipeline opens the **Visual Orchestrator**. You can drag
nodes around the canvas, draw connections between them, and tap a
node to open its configuration dialog.

The editor also has an **Import JSON** button that lets you load a
pipeline exported from the standalone browser editor (see
[Browser pipeline editor](#browser-pipeline-editor)).

### Variables in system prompts

Nodes that drive a language model (local or cloud) carry their own
system prompt. Instead of baking dynamic values into the prompt
text, the prompt can reference **variables** with a `$NAME` syntax —
the app substitutes the current value every time the node runs.

Built-in variables:

| Placeholder        | Resolves to                                              |
|--------------------|----------------------------------------------------------|
| `$DATE`            | Current device-local date (`dd MMMM yyyy`).              |
| `$TIME`            | Current device-local time (`HH:mm`, 24-hour).            |
| `$TOOLS`           | The active tools list, one `name — description` per line.|
| `$MODEL`           | The display name of the currently active local model.    |
| `$MEMORY_SUMMARY`  | A numbered list of recent long-term memory entries.      |

When you edit a system prompt in a node's configuration dialog, a
row of chips beneath the prompt field shows every available variable.
Tap a chip to insert the token at the cursor. Unknown placeholders
are kept verbatim and reported in the console as a warning, so a
typo never crashes the run.

Example system prompt:

```
You are a helpful assistant running on $MODEL.
Today is $DATE, local time $TIME.
You have access to the following tools:
$TOOLS

Recent memory:
$MEMORY_SUMMARY
```

To emit a literal `$KEY` (for example, when you want to write
documentation inside the prompt), escape the dollar sign as `\$KEY`.

---

## Browser pipeline editor

The repository ships a standalone HTML editor at
`pipeline-editor.html`. It is a regular single-file web page — no
build step, no server, no extension required.

### Running the editor

1. Clone or download the repository.
2. Open `pipeline-editor.html` in any modern desktop browser
   (Chrome, Firefox, Safari, or Edge).

The page mirrors the in-app visual orchestrator. You can drop nodes
onto a canvas, draw connections, edit each node's parameters, and
review the prompt-variable list — all locally in your browser.

### Exporting to JSON

The editor's top bar has an **Export JSON** action. It produces a
single file describing the entire graph (nodes, connections,
configuration, and the schema version). The same format is used by
the app's import flow, so there is no manual conversion step.

### Importing into the app

1. Move the exported JSON file onto the Android device (USB, cloud
   drive, email — anything that puts it in a place the system file
   picker can reach).
2. Open the app's **Pipelines** screen and load any pipeline to open
   the **Visual Orchestrator**.
3. Tap **Import JSON**. The system file picker opens; pick the file
   you exported from the browser editor.
4. The imported pipeline is saved into the library and becomes the
   one currently open in the editor.

---

## Tools and MCP

Tools are how the agent takes real action — looking something up,
scheduling future work, or delegating a hard subtask to a more
capable model. They are managed from the **Tools** screen.

### Built-in tools

The app ships with the following tools:

| Tool             | What it does                                                                          |
|------------------|---------------------------------------------------------------------------------------|
| **search_tool**    | Looks up a topic on Wikipedia and returns a concise summary.                        |
| **schedule_task**  | Schedules a task to run later in the background (one-off or recurring).             |
| **delegate_task**  | Hands a hard subtask to a configured cloud LLM and stores the result in memory. Only appears when at least one cloud provider has an API key configured. |

Each tool has a switch on the Tools screen. Turn a tool off to hide
it from the agent for the next run; turn it on to make it available
again.

![Screenshot TODO — tools screen](screenshots/TODO.png)

### Risk levels and human-in-the-loop

Every tool declares a **risk level** that controls whether the agent
can run it on its own:

- **READ_ONLY** — runs without prompting (for example, looking up a
  fact).
- **SENSITIVE** — surfaces an **Approve / Deny** prompt in the chat
  before the call happens.
- **DESTRUCTIVE** — same approval gate as **SENSITIVE**, used for
  actions that cannot be undone (for example, sending a message or
  deleting data).

When approval is required, the mini-console shows inline
**Approve** and **Deny** buttons. The agent waits for your response
before continuing the pipeline; deny cleanly stops the call without
killing the run.

The **Human-in-the-loop** toggle in **Settings → Restrictions** lets
you require approval for **every** tool call, regardless of its risk
level. Turn it on if you want to confirm even read-only lookups.

### Adding an MCP server

The **Tools** screen has a second section called **MCP Servers** —
external **Model Context Protocol** endpoints that publish their own
tools. To add one:

1. Scroll to **MCP Servers**.
2. Paste the server's URL into the **Add New MCP Server URL** field.
3. Tap **Add Server**.

The server's tools become available to the agent on the next run.
Remove a server by tapping the trash icon next to its row.

MCP connections open lazily — the app only contacts the server when
a tool from it is needed — and they are wrapped in error-handling
so an unreachable server does not crash the chat. If a tool that
relied on an MCP server stops responding, you will see an error
event in the console rather than a silent failure.

---

## Memory

The agent has two kinds of memory:

- **Short-term memory** — the rolling context window of the current
  conversation. This is what the model "remembers" from one message
  to the next.
- **Long-term memory** — a vector store of past conversations the
  agent can search semantically when a new question is similar to
  something you talked about before.

### Watching the context window

The chat screen's top bar shows the **current / maximum** token
count for the active conversation:

- Grey — normal usage.
- Amber — context window is more than 70% full.
- Red — context window is more than 90% full; older messages will
  start to fall out of scope on the next reply.

If you are seeing red and want to keep the conversation going,
either start a new chat (the old one keeps its history) or clear
the rolling context (described below).

### Browsing long-term memory

Open the **More** tab, tap **Memory**. The screen has two tabs:

- **Chat History** — every past session, grouped by chat. Expand a
  session to see its messages.
- **Vector Base** — the actual long-term memory chunks the agent
  searches over. Each chunk is a small fragment distilled from a
  past conversation.

### Compact Memory

The **Vector Base** tab has a **Compact Memory** button. Tap it to
let the agent re-summarize and consolidate memory chunks — useful
after a long string of conversations have produced overlapping
fragments. Compaction is a single one-shot action; you can leave the
screen while it runs.

### Clearing context

Starting a new chat from the drawer is the simplest way to drop the
short-term context — old sessions stay where they are, and the new
one begins with an empty window. Clearing the mini-console with
**Console → Clear** wipes the visible event log but does not touch
chat history or memory.

---

## Settings

The **Settings** screen groups every user-tunable knob into clearly
labelled sections.

### System Instructions

A multi-line **System Prompt Prefix** field. Anything you type here
is prepended to every system prompt the agent sends, regardless of
which pipeline runs.

### Restrictions

- **Human-in-the-loop** — when on, every tool call requires your
  approval, not just sensitive or destructive ones (see
  [Tools and MCP](#tools-and-mcp)).

### LLM Parameters

These knobs control how the on-device model generates text:

- **Temperature** (0.0 – 2.0) — higher values produce more varied,
  creative output; lower values stay closer to the most likely
  token.
- **Top-K** (1 – 100) — limits sampling to the K most likely tokens
  at each step.
- **Top-P** (0.0 – 1.0) — nucleus sampling threshold. The model
  considers the smallest set of tokens whose probabilities add up
  to P.
- **Max Context Length** (512 – 8192) — maximum number of tokens
  the model keeps in its working window.
- **Max Steps** (5 – 100) — upper bound on how many pipeline
  iterations the agent is allowed to run for a single message. Use
  it to cap runaway loops.

### Local Model Settings

- **Inference Backend** — drop-down that selects the engine used to
  run the local model.
- **Test Backend** — runs a tiny inference probe and shows a toast
  with the result. Use it after switching backends to make sure
  acceleration is wired up.

### Privacy

- **Opt-in Crash Reporting** — when on, anonymous crash reports are
  forwarded to Firebase Crashlytics so problems can be diagnosed.
  The first time you enable it, the app shows a dialog explaining
  exactly what is and is not collected; you must explicitly confirm
  to opt in.

  Crash reporting transmits stack traces, device model, Android
  version, app version/build, and the identifiers of the active
  pipeline and model. It **never** transmits the contents of chat
  messages, model prompts or replies, memory chunks, tool inputs or
  outputs, or any value stored in encrypted preferences. Debug
  builds disable crash reporting entirely regardless of the toggle.
  Full details are in [SECURITY.md](../SECURITY.md).

### External Providers

Each optional cloud provider (**OpenAI**, **Anthropic**, **Google**,
**DeepSeek**) has its own subsection with two fields:

- **API Key** — masked password field. Keys are stored in
  encrypted shared preferences and are never written to plain
  storage, logs, or chat exports.
- **Model** — drop-down that selects the default model identifier
  the agent uses when it dispatches a request to that provider.

A separate **Ollama (Local Network Models)** subsection adds three
fields:

- **Ollama Base URL** (for example, `http://localhost:11434`).
- **Ollama Model** (for example, `mistral`).
- **Ollama Context Window** (numeric, used to clamp the prompt
  size sent to Ollama).

Leaving every cloud provider blank is fine — the agent runs fully
offline on the local LiteRT model and hides any tools that require
cloud reasoning.

---

## Troubleshooting

### The model fails to load with "out of memory"

Local LLMs need a large block of contiguous memory. If loading a
model fails:

- Open **Models** and tap **Make Active** on a smaller model. Quad-
  or 4-bit-quantised variants tend to fit where full-precision ones
  do not.
- If you have multiple models on the device, the previously active
  one stays loaded until a new one is activated. Switching back and
  forth a few times can leave the device fragmented — closing the
  app entirely (swipe it away from the recents list) and reopening
  it frees the native handle reliably.
- Make sure other heavy apps are not running in the background.

### Inference is very slow

Without an NPU or a usable GPU, the local model runs on CPU only,
which is noticeably slower (especially for the first few tokens):

- Open **Settings → Local Model Settings** and tap **Test Backend**
  to confirm which backend the model is actually using.
- Try a smaller model from the **Models** screen — even a 1B-2B
  parameter model can be substantially faster than a 7B+ one on
  CPU.
- Lower **Max Context Length** in **Settings → LLM Parameters**.
  Shorter contexts mean less work per token.

### A tool says it is unavailable

Two common causes:

- A built-in tool that delegates to a cloud provider (for example,
  **delegate_task**) requires at least one cloud API key in
  **Settings → External Providers**. Without a key it is hidden
  from the agent.
- An MCP-server tool requires the server itself to be reachable.
  Open the **Tools** screen and confirm the server is still listed
  under **MCP Servers**; if the URL changed or the server is down,
  the tool will fail with an error event in the console.

### A pipeline went missing

If you delete or rename a pipeline that a chat was bound to, the
chat falls back to the **default** pipeline marked in the library
on its next message. There is no broken state — replies keep
working — but the conversation will start using whichever pipeline
is currently flagged as the default. Pick a new pipeline for the
chat by reopening the **Pipelines** screen and using **Set as
default**, or rebind the chat by creating a new one.

### The agent stopped mid-run

Long pipelines can hit the **Max Steps** ceiling. The console will
show a stop event with the step count. If you legitimately need
more iterations, raise the ceiling in **Settings → LLM Parameters →
Max Steps**. If the run is looping unproductively, lower it instead.

---

## See also

- [`architecture.md`](architecture.md) — internal design of the
  agent for contributors and reviewers.
- [`extending.md`](extending.md) — recipes for adding new node
  types, tools, cloud providers, and prompt variables.
- [`../SECURITY.md`](../SECURITY.md) — security policy, threat
  model, and what crash reporting collects.
- [`../README.md`](../README.md) — project overview and quick start.
