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

### The chat home screen

The chat home is the first surface that opens after onboarding. It is
now built on the Knotwork design system and shows, top to bottom:

- A **top app bar** with the current thread title, the active model
  name beneath it (e.g. *Gemma 2 · 2B*), a menu icon on the left that
  opens the thread drawer, and a model picker plus overflow menu on
  the right.
- A **message list** with user and assistant bubbles, inline tool
  invocations, and any clarification or HITL confirmation card the
  agent surfaced for the current run.
- A pinned **composer** at the bottom — type a message, attach a file
  with the paperclip, or dictate with the microphone. The send button
  morphs into a stop button while the agent is generating.

The surface adapts to nine deterministic visual states, all reachable
from the same screen:

| State            | When it appears                                                 |
|------------------|-----------------------------------------------------------------|
| Empty            | A brand-new thread with no messages — shows sample prompts.     |
| Idle             | History present, no in-flight request. The default.             |
| Generating       | The assistant is producing tokens.                              |
| HITL Confirm     | A tool call awaits your approval (read-only / sensitive /       |
|                  | destructive — each tier surfaces a different confirmation UI).  |
| Clarification    | The assistant asks you for more details before continuing.      |
| Error            | The model or network failed; an inline tile + retry appears.    |
| Drawer Open      | The thread list slides in as an alt-nav drawer over the chat.   |
| Console Expanded | The agent console rises from the bottom over the chat surface.  |
| Dark             | A cross-cutting variant — every state respects the system theme. |

**Debug builds** expose a state picker reachable by triple-tapping the
title row, which flips between every documented state for visual QA.
The picker is not present in release builds.

### Switching, creating, and renaming chats

Open the side drawer from the chat screen (tap the menu icon, or
swipe from the left edge). The drawer lists every chat under
**SESSIONS**. The currently active session is highlighted; favorited
chats are sorted to the top with a small leading star glyph.

- **New chat** — tap **New chat** at the top of the drawer. A bottom
  sheet opens with the available pipelines (pre-selected to the one
  currently bound to your active chat); confirm to create a fresh
  session. If no pipelines exist, the new chat inherits the default
  pipeline automatically.
- **Switch chat** — tap any row in the list. The chat screen updates
  immediately.
- **Rename chat** — tap the pencil icon next to a session row. A
  bottom sheet titled **Rename chat** opens with the current name
  pre-filled; **Save** persists the new name.
- **Favorite chat** — tap the star icon in the chat top bar to favorite
  the active chat. Favorited chats persist across restarts and sort to
  the top of the drawer.

### Switching models from the chat top bar

Tap the model label under the chat title to open a model-picker bottom
sheet listing every locally installed LiteRT model. Picking a model
activates it and reloads the inference engine. If no models are
installed, the sheet shows **Open Models** that takes you directly to
the Models screen.

### Settings shortcut

The drawer footer carries a **Settings** entry that deep-links to the
Settings screen. Use it whenever the chat surface needs a quick jump
to API keys, sampling parameters, or appearance toggles.

### Exporting and importing chat history

The app uses standard Android share and file-picker intents, so chat
history is portable to any app that handles JSON or plain text.

- **Export** — from the chat screen's top-bar overflow menu (`⋮`)
  choose **Export chat**. The Android **Share Sheet** opens with a
  JSON payload containing the full message history. Choose any
  destination that accepts JSON (Files, email, a messenger, a
  cloud-drive app, and so on).
- **Import** — open the drawer and tap **Import chat**. The system
  file picker opens, filtered to `application/json`. Selecting a
  previously exported file creates a new chat session with the
  imported messages and switches to it immediately. Malformed files
  surface an inline error via the chat snackbar.
- **Delete chat** — from the same overflow menu choose **Delete chat**.
  A destructive confirmation dialog appears; once confirmed the
  conversation (and every message in it) is removed. The next
  available chat is auto-selected, or a fresh unbound chat is created
  if none remain.

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

Loading a pipeline opens the **Pipeline editor**. The editor surface
is an infinite pan / zoom canvas with the following gestures:

- **One-finger drag on empty canvas** — pan the viewport.
- **Two-finger pinch** — zoom (`0.4×–2.0×`).
- **Drag a node card** — move the node; it snaps to a 24 dp grid on
  release with a soft spring settle.
- **Add a connection** — press and hold one of the node's **bottom
  port dots**, then drag toward another node and release when the
  finger is over its **top port dot**. For multi-output nodes
  (`If` → True / False, `Queue` → Item / Done, `Eval` → Pass / Retry /
  Fail, `Router` → one port per declared class) the dot you grabbed
  determines which branch the edge represents.
- **Delete a connection** — two paths:
  1. **Single-tap** the edge → it highlights in accent colour and the
     toolbar 🗑 Delete becomes active. Press 🗑 to remove. Or
  2. **Long-press** the edge → confirmation dialog "Remove
     connection?" opens; tap Remove.
  Both paths are undoable from the toolbar Undo button.
- **Tap a node** — select it (single-select mode).
- **Tap a selected node** — opens its **configuration sheet**
  (`NodeConfigSheet`) so you can edit the per-type properties.
  Equivalent to "double-tap the node".
- **Long-press a node** — enter multi-select. Subsequent taps toggle
  membership; the top bar swaps for a count + Cancel / Delete cluster.
- **Long-press the empty canvas** — opens a **radial quick-add menu**
  with one labelled tile per node type. Picking a tile spawns the node
  at the long-press point and immediately opens its configuration sheet.
- **Toolbar** — inline-editable pipeline name on the left; Undo /
  Redo / Delete (selection-aware: edge if one is selected, otherwise
  selected nodes) / Auto-layout / Run / overflow on the right.
  Auto-layout re-arranges nodes via a Sugiyama-style hierarchy
  (longest-path layering + median crossing reduction) so the graph
  reads top-to-bottom.

The bottom of the screen alternates between two bars:

- **Validation bar** — lists pipeline errors (missing input, dangling
  output, cycles, empty context, …). Tapping a row centres the canvas
  on the offending node and selects it. The bar collapses to a
  single-line "Pipeline is valid" when there are no errors.
- **Run-trace bar** — replaces the validation bar while a pipeline run
  is in progress; the active node header pulses and the connecting
  edges show a traveling-dot animation. Reduced-motion is respected.

The editor also has an **Import JSON** button that lets you load a
pipeline exported from the standalone browser editor (see
[Browser pipeline editor](#browser-pipeline-editor)).

### Node configuration sheets

Reach a node's per-type configuration by either:

- **Tapping a node you've already selected** (single-tap → select,
  tap again → open the sheet); or
- **Picking the node from the radial quick-add menu** — newly added
  nodes open the sheet immediately.

The sheet is a modal bottom-sheet whose body is documented in
`node-specs.md`. Every node type — Input, Output, LiteRt, Cloud,
IntentRouter, IfCondition, Clarification, Tool, Decomposition,
QueueProcessor, Evaluation, Summary — has its own form, with inline
validation that disables Save until every required field is filled.

For the **IntentRouter** node, the Classes section in its config
sheet lets you grow / shrink the class list: each row has a small
**−** button to remove it (disabled below the 2-class minimum), and a
**+ Add class** button under the list creates a new empty class row
(disabled above the 6-class maximum). The new class shows up as an
additional outbound port on the node card immediately on Save.

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
| `$MEMORY_SUMMARY`  | A numbered list of recent long-term memory entries. The default upper bound is configurable from **Settings → Memory → Memory summary default limit** (1–50). |

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

The **Settings** screen groups every user-tunable knob into nine
cards. The top bar carries the app version, channel and build date
plus a magnifying-glass action for in-settings search.

### Identity

An avatar + label confirm the device identity is anonymous and
local. The meta line shows the truncated device id and whether your
API keys live in the Android Keystore (hardware-backed) or — on
constrained devices — in encrypted preferences.

### System Instructions

A monospaced multi-line field whose content is prepended to every
system prompt the agent sends. Tap a chip to insert one of the
built-in variables (`$DATE`, `$TIME`, `$LANG`, `$LOCATION`,
`$USER`, `$DEVICE`) — they expand fresh on every prompt render.
The counter shows live character usage against the 4 000-character
limit.

### Restrictions · Human-in-the-loop

- **Approve tool calls** — segmented control switching between
  `All` (prompt for every call), `Sensitive +` (only sensitive or
  destructive tools — recommended default), and `Never` (no
  prompts at all; reserved for known-safe pipelines).
- **Block destructive tools** — when on, destructive tools are
  refused outright rather than going through the HITL prompt.
  Useful when the agent runs unattended.
- **Block network from local model** — when on, every cloud
  provider returns `null` to the inference pipeline and only the
  on-device LiteRT engine plus LAN-local Ollama remain reachable.
- **Cap autonomous steps** — upper bound on planner iterations
  per user message; the agent pauses for guidance when the cap is
  hit.

### LLM Parameters

The "Reset to defaults" action restores every slider in this card.

- **Temperature** (0.0 – 2.0) — higher values produce more varied
  output.
- **Top-K** (1 – 100) — keeps only the K most likely tokens.
- **Top-P** (0.0 – 1.0) — nucleus sampling threshold.
- **Repetition penalty** (1.0 – 2.0) — `1.0` is neutral; higher
  values discourage the model from repeating recent tokens.
- **Max context** (512 – 8 192) — working window in tokens.
- **Max steps** (5 – 100) — pipeline-iteration cap (same value
  as Restrictions → Cap autonomous steps; the two surfaces share
  the underlying preference).

### Local Model

- Active-model card showing name, file size, context window,
  quantization and download date. Tap **Change** to pick a
  different model; **Manage** opens the full Models browser.
- **Inference backend** — drop-down picking the engine (NPU
  preferred, falls back to GPU then CPU).
- **Test backend** — runs a fixed prompt-probe and persists the
  measurement (`Last probe · N tok in T s · K tok/s`) so the row
  keeps the metric across navigation. Changing the backend
  surfaces a restart banner — tap **Restart** to apply the
  change immediately.

### External Providers

Each provider — **OpenAI**, **Anthropic**, **Google**,
**DeepSeek**, **Ollama** — collapses to a single row showing the
masked key fingerprint and selected model. Tap the row to open
the standalone provider editor. The Ollama row additionally
carries a **LAN** pill plus the base URL. Use **+ Add provider**
to surface an unconfigured provider's editor without scrolling.
Leaving every cloud row blank keeps the agent fully offline.

### Memory

Four-cell stat grid — **Chunks / Size / Threads / Avg score** —
plus an **Auto-summarize threshold** slider (`%` of the memory
context budget) and an **Embedding model** row identifying the
on-device encoder. The action trio:

- **Export base** — opens a SAF picker; saves the entire memory
  table as a JSON blob.
- **Re-embed** — re-runs the embedder over every chunk; an inline
  progress bar tracks completion.
- **Clear** — opens a typed-confirm dialog ("type `yes` to
  confirm"); on confirm wipes every chunk, pinned included.

### Notifications

- **Long-running tasks** — when on, a low-importance system
  notification fires when a backgrounded pipeline run exceeds the
  long-running threshold.

### Privacy

- **Send anonymous crash reports** — forwards stack traces +
  device meta + active pipeline / model identifiers to Firebase
  Crashlytics. Off by default; debug builds never report. Full
  policy in [SECURITY.md](../SECURITY.md).
- **Reset all settings** — typed-confirm dialog that restores
  every preference to defaults (API keys, downloaded models, and
  memory are untouched).

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
