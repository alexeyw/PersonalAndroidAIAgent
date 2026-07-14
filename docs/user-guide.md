# User Guide

This guide is for people who installed the **On-Device AI Agent for
Android** and want to learn how to use it. It walks through every
screen the app provides and the user-facing flows that connect them.

It is not a developer document. If you are looking for the internal
architecture, see [`architecture.md`](architecture.md); for recipes on
extending the agent with new node types, tools, or cloud providers,
see [`extending.md`](extending.md).

Marketing-style hero shots of the main surfaces (chat home, pipeline
editor, pipeline library, tools, settings) live under
[`docs/images/`](images/) and are rendered in the project README.
Inline per-flow screenshots in this guide are placeholders pending a
device-capture pass — the agent's surfaces are evolving fast enough
between phases that hand-curated guide screenshots tend to go stale
before the next release ships.

---

## Table of contents

1. [Getting started](#getting-started)
2. [Chats](#chats)
3. [Console](#console)
4. [Background tasks](#background-tasks)
5. [Entry surfaces](#entry-surfaces)
6. [Triggers](#triggers)
7. [Pipelines](#pipelines)
8. [Browser pipeline editor](#browser-pipeline-editor)
9. [Tools and MCP](#tools-and-mcp)
10. [Files](#files)
11. [Memory](#memory)
12. [Settings](#settings)
13. [Troubleshooting](#troubleshooting)

---

## Getting started

The first time you launch the app you go through a short onboarding
flow, then land on the Chat tab. Instead of asking you to assemble a
pipeline up front, onboarding leads with **what the agent should do for
you**: you pick one of a few ready-made scenarios — a styled translator,
a share-to-capture handler, or a mood-aware companion — and the app sets
up everything that scenario needs. It materialises the matching pipeline
as your default, wires any surface it uses (the share handler starts
listening on the system Share sheet), and downloads exactly the model
that scenario requires, showing the size and live progress so the wait
is a known one. When the model has finished warming, the final step
opens your scenario straight into a working chat. Prefer to build your
own? The gallery's **Start from scratch** card skips the scenario and
drops you into the app to wire a pipeline node by node.

The bottom of the screen always shows the four navigation tabs:

- **Chat** — talk to the agent (the default tab the app opens on).
- **Pipelines** — browse and edit the agent's reasoning pipelines.
- **Tools** — manage AppFunctions and connected MCP servers.
- **More** — secondary screens (Memory, Models, Prompt library,
  Skill library, Active tasks, Live metrics, Settings, About).

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

<!-- TODO: device capture of the Models screen. The hero shots under
     `docs/images/` cover chat / pipeline-editor / pipeline-library /
     tools / settings; Models / Memory / Onboarding still need a
     dedicated pass. -->


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

### Discovering models from Hugging Face

You don't have to know a URL. Tap the **Discover** action in the Models
screen top bar to browse the curated
[`litert-community`](https://huggingface.co/litert-community) collection
on Hugging Face — ready-to-run on-device models — without leaving the
app:

- **Browse and search.** Each card shows the model name, its downloads
  and likes, and its licence. Type in the search box to filter, or pull
  down to refresh. The app only contacts Hugging Face when you open the
  screen, search, or open a card — never in the background.
- **Open a model.** Tapping a card shows the repository's installable
  `.litertlm` files with their sizes, the licence, and a **View on
  Hugging Face** link to the full model card. Files you already have on
  disk are marked **Installed**.
- **Install.** Tap **Install** next to a file. You'll be asked to
  **review and accept the licence** before the download starts; it then
  streams through the same downloader as the rest of the screen and the
  model appears in your **Downloaded** list, ready to activate.
- **Gated models.** Some repositories require you to accept their
  licence on the Hugging Face website and use a personal access token.
  These show a **Gated** badge and an inline token field — paste your
  token there (it is stored in the same encrypted slot as the download
  token). If a download is refused (HTTP 401/403), the app tells you to
  accept the licence on Hugging Face and add a token.
- **Offline / errors.** If the network is unavailable, the screen shows
  a clear message with a **Retry** button rather than failing silently.

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
- A pinned **composer** at the bottom — type a message or dictate with
  the microphone. The send button morphs into a **stop** button while
  the agent is generating, and into a **retry** button after an error.
  Anything you type but do not send is **kept per chat**: switch to
  another conversation and back and your half-written message is still
  there (drafts live for the session and are not kept across an app
  restart). If you send before the on-device model has loaded, the app
  **loads it and then sends your message automatically** — you no longer
  have to load the model and press send separately.

The surface adapts to eight deterministic visual states, all reachable
from the same screen:

| State            | When it appears                                                 |
|------------------|-----------------------------------------------------------------|
| Empty            | A brand-new thread with no messages — shows the active pipeline's starter prompts. |
| Idle             | History present, no in-flight request. The default.             |
| Generating       | The assistant is producing tokens.                              |
| HITL Confirm     | A tool call awaits your approval (read-only / sensitive /       |
|                  | destructive — each tier surfaces a different confirmation UI).  |
| Clarification    | The assistant asks you for more details before continuing.      |
| Error            | The model or network failed; an inline tile + retry appears.    |
| Drawer Open      | The thread list slides in as an alt-nav drawer over the chat.   |
| Dark             | A cross-cutting variant — every state respects the system theme. |

The **console pane** (see the [Console](#console) section) is rendered
as a bottom-sheet *overlay* on top of any of the above states — it is
not a chat state in its own right and stays mounted across state
transitions while it is open.

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

### Copying a message

Long-press any message bubble to open its context menu. From there you
can **Copy** the text to the clipboard, **Re-run** it (drop the text back
into the composer), **Rate** the reply, or **Save to memory** — which
stores the message verbatim in long-term memory as a manual entry and
confirms with a *Saved to memory* snackbar. Saved entries show up under
**More → Memory** with the **Manual** source.

### Attaching an image

Tap the **image** button in the composer to attach a picture to your next
message. A small sheet offers two sources:

- **Photo library** — the system photo picker (your gallery and
  screenshots). No storage permission is requested; the picker runs in its
  own process and hands back only the image you choose.
- **Camera** — take a photo now.

The chosen image is **downscaled on the device** (its aspect ratio is kept —
never cropped to a square) and re-encoded to JPEG before it is stored; the
original file is never copied into the app. A removable preview appears above
the input row while you finish typing — tap the **✕** to drop it. You can send
an image on its own, without any caption.

Once sent, the message bubble shows a thumbnail; tap it to view the image
full-screen, and use back, the close button, or a tap outside the image to
dismiss. If an image was later cleared to save space (see *Run history and
retention*), the thumbnail and viewer say so plainly — the message text stays
in the chat.

### Image understanding (vision)

For the agent to actually *read* the picture, the active local model must be
**vision-capable** (for example a Gemma 4 model). Because the on-device runtime
does not advertise this, you tell the app which models can see: open
**Models**, and on the active model's card flip the **Image support** toggle on.
The setting is per model and off by default.

When you send an image, the picture is handed to the **first on-device step of
the pipeline** together with your message; from there the rest of the pipeline
works on text as usual. The agent console shows an `Image input: W×H, N KB`
line at the start of the run so you can see the image was taken in.

Safety checks run **before** a run starts, so you get a clear message instead of
a failure mid-answer (your draft and the attached image are always kept):

- **Cloud-first pipeline.** Images **never leave your device** — they are not
  sent to cloud models. If the pipeline bound to the chat begins with a cloud
  step, sending an image is blocked with a note to use an on-device pipeline.
- **Text-only model.** If the active model is not marked vision-capable, the app
  explains that it can't read images and points you to the Models toggle (or to
  switch models).
- **No on-device image step.** If the bound pipeline has no on-device model step
  that could read the picture, the send is blocked rather than quietly ignoring
  the image.

For a pipeline that branches, the image goes to the first on-device model step
on the path the run actually takes. If a branch is taken that has no such step,
the agent says so on the console (`Image not used …`) instead of pretending the
model saw the picture.

**Branching on whether a picture was sent.** Two node types can react to the
presence of an image even though the picture itself only ever reaches the vision
step:

- An **If Condition** node has a *“Branch True when input has an image”* toggle.
  Turn it on and the node takes the **True** branch whenever the message carries
  an image and **False** otherwise — a plain check with no model call, evaluated
  before the text expression (which is ignored while the toggle is on). This is
  the simplest way to fork a pipeline into an “image” path and a “text” path.
- An **Intent Router** is told when the message includes an image, so you can add
  a route (an outgoing edge labelled e.g. `image`) and the router can pick it.

Only the *fact* that an image is attached is shared with these steps — never the
picture's contents, which stay with the on-device vision model.

### Voice input

Tap the **microphone** button in the composer to add voice. A small sheet
offers two ways in:

- **Record voice** — the app asks for microphone permission the first time,
  then records. While recording, the input row becomes a bar with a live timer
  (`0:07 / 0:30`), a discard ✕, and a Stop button. Recording **auto-stops** at
  the limit (set by *Audio recording length*, default 30 seconds) — the timer
  turns amber in the last few seconds to warn you.
- **Choose audio file** — pick an existing clip from your device.

Either way, the clip is **transcribed to text by your active model before
anything runs** — the audio never travels the pipeline. You'll see
*Transcribing…* in the composer; when it finishes, the transcript drops into
the input field as **ordinary editable text**. Read it, fix anything, and send
it like any other message.

Transcription needs an **audio-capable** model:

- **Text-only model.** A calm note ("This model can't transcribe audio") points
  you to the **Audio support** toggle on the Models screen (next to *Image
  support*) — turn it on for a multimodal model such as Gemma 4.
- **Microphone off.** If you declined the permission, a note offers to open
  system settings so you can grant it.
- **A task is running.** Transcription shares the single on-device engine with
  the agent, so it waits until the current run finishes rather than interrupting
  it; a note tells you to try again in a moment.

The recorded or picked clip is **temporary** — it is deleted as soon as it has
been transcribed (only the text remains).

---

## Console

The console shows what the agent is actually doing while it processes
your request — which pipeline node is running, which tools were
called, which memory chunks were retrieved, and any errors that
occurred.

### Agent-status pill

Just above the message composer sits a single-line **status pill**
formatted as `[TAG]  body`. It reflects the agent's current activity
without flooding the chat — for example:

| Pill body                          | When you see it                     |
|------------------------------------|-------------------------------------|
| `[NODE]  idle · ready`             | The agent is waiting for input.     |
| `[NODE]  generating · streaming`   | A response is being produced.       |
| `[TOOL]  awaiting approval`        | The HITL card is on screen.         |
| `[NODE]  waiting on clarification` | A clarification card is on screen.  |
| `[NODE]  error · see message`      | The latest run failed (see banner). |

Tapping the pill opens the **console pane** — a bottom sheet that
covers most of the screen with the full chronological event log.

### Console pane

The console pane is independent of the chat state — it stays open
across `Generating → HitlConfirm → Clarification` transitions, so you
can watch the agent while it works without losing your place.

The pane has three tabs:

- **Console log** — every `[TAG] message` event from the current
  session in chronological order with millisecond timestamps. A row
  of source filter chips at the top lets you narrow by origin —
  **NODE / TOOL / MEMORY / RUNTIME / USER** — each toggling
  independently. The **MEMORY** chip isolates long-term-memory
  retrievals: each one is logged as
  `Memory: query='…' → N hits (score, …)`, echoing the query, how many
  chunks were surfaced, and their similarity scores. Turn on
  **Settings → Memory → (Advanced) Verbose memory logging** to expand each line
  with a per-hit text snippet and score.
- **Pipeline trace** — a structured view of the pipeline run as a tree
  of node spans (name, duration, status). Useful for understanding
  *why* a particular branch fired or a node was skipped.
- **Node I/O** — per-node input / output text plus the values of every
  `$VARIABLE` resolved during the run, so you can diff what the agent
  actually saw. In a long conversation the input may open with an
  **`--- Earlier conversation (summarized) ---`** block: that is the
  model-written stand-in for older turns that no longer fit verbatim (see
  **Settings → Memory → Compress long chat history**). The recent turns
  still appear underneath it under `--- Chat History ---`.

A few events are worth recognising while you read the **Console log**:

- **Reliability warnings (RUNTIME chip).** When the agent has to nudge a
  node into producing well-formed output, you'll see a muted yellow line
  like `Output repair 1/2 for node Intent Router`. When a cloud call hits a
  transient failure and is retried, you'll see `Cloud retry 1/2 for openai`.
  Both are normal self-healing, not errors — they only mean the agent had to
  try again. A genuine give-up is logged as a red **Error** line instead
  (for example a router that exhausted its repairs and fell back to its
  default branch).
- **History compression (MEMORY chip).** Once per run, if older turns were
  summarised to fit the context window, a line such as `Chat history
  compressed: summarized older turns, kept the last 10 messages` appears.
  If the summary was not ready in time, you'll see `Chat history over
  budget; summary not ready, kept the last 10 messages` instead.

Three actions are available at the pane footer:

- **Copy line** — long-press a single console row.
- **Copy all** — copies the full plain-text dump of the active tab to
  the clipboard, regardless of which filter chip is active.
- **Clear console** — wipes the on-screen log baseline for the current
  session after a destructive confirmation dialog. The underlying chat
  messages and the saved pipeline trace are not affected.

The active tab is persisted between runs, so the pane re-opens to the
tab you used last. The pane auto-scrolls to the latest event as long as
you are pinned to the bottom; if you scroll up to read older events,
auto-scroll pauses so you do not lose your place.

### Trace replay after reopening a chat

The console is no longer wiped when the app restarts. The execution
trace of every run — the console log plus each node's input/output —
is saved to the encrypted local database as the run executes, and
opening a chat replays the trace of its active (or most recent) run
into all three tabs. A run that executed while the app was closed is
therefore fully inspectable afterwards: what the agent did, which
tools it called, and what every node received and produced. If the run
is still executing, live events continue seamlessly below the replayed
history. **Clear console** keeps working on top of a replayed trace —
cleared rows stay hidden until you switch sessions or send a new
message.

### Sub-pipelines in the console

When a pipeline calls another pipeline (a **Pipeline** node), the
sub-pipeline's work is no longer hidden inside a single "Pipeline: 40 s"
line. Its console log, variables and trace spans appear **indented**
under the calling node and prefixed with the sub-pipeline's name (for
example `[Translator] ▶ LITE_RT`), so you can read a nested run as a
hierarchy — both live and when replaying a finished run. Each level of
nesting adds one step of indentation.

Two run-wide limits also span the whole call tree:

- **Step budget.** The maximum-steps limit is shared across the parent
  and every sub-pipeline it calls, so a composition cannot loop forever
  by nesting. If the budget runs out deep inside a sub-pipeline, the
  whole run stops with a clear "exceeded the maximum … steps shared
  across the pipeline tree" message.
- **Approvals and questions.** A tool approval or clarification raised
  *inside* a sub-pipeline surfaces its card in the chat exactly like a
  top-level one, and answering it continues the nested run in place.

If the app is killed (or you step away from a background approval) while
a sub-pipeline is mid-run, **Resume** restores the entire stack: the
parent pipeline fast-forwards through the work it already finished, then
continues the sub-pipeline from where it stopped — neither the parent
nor the child re-runs a node that already completed.

### Reopening a chat while a run is in flight

Closing the chat — or the whole app UI — no longer disconnects you from
a run that is still working. When you open a session, the app checks
the persistent run record and reattaches accordingly:

- **Run still executing** — the chat reconnects to the live run
  without restarting it: the generating indicator returns, streaming
  continues, and the console picks up where the replayed trace ends.
- **Run waiting on you** — a pending tool approval or clarification
  question is restored as its card in the message stream, so a request
  raised while you were away is never lost behind a spinner.
- **Run finished in the background** — the final answer is already in
  the conversation and the console holds the full trace; nothing
  restarts.
- **Run interrupted** — if the process died mid-run (battery
  optimisation, memory pressure, swiping the app away), the chat shows
  a **Run interrupted** card naming the node the run stopped at, with
  **Resume** and **Discard** buttons. *Discard* dismisses the run for
  good. *Resume* continues from the checkpoint: nodes that already
  finished replay their recorded results instantly (no re-inference),
  and execution restarts at the first unfinished node. A tool call the
  run died on is never replayed — the tool may or may not have acted —
  so it runs again from scratch, asking for your approval as usual.
  Resume is offered while the interruption is younger than the
  **Resume window** setting (default 48 hours) and the pipeline has not
  been edited since the run started; an edited or deleted pipeline
  means the task can only be restarted from the beginning.

Conversations with a run still working in the background are easy to
spot: their row in the thread drawer shows a small in-progress
indicator next to the title.

### Approving a tool call

If the agent needs your approval to run a sensitive or destructive
tool, the chat shifts into the **HitlConfirm** state — a card appears
inline in the message stream with the tool name, the typed arguments,
a colour-coded risk pill (`READ` / `SENS` / `DEST`), and **Approve** /
**Deny** buttons. Destructive tools also require typing the literal
tool name as a typed-confirm gate before the **Approve** button is
enabled.

An unanswered request does not fail the run. When the live waiting
window elapses — say a scheduled run hits a sensitive tool at 6 a.m. —
the run parks in a persistent waiting state and an ongoing notification
becomes your way back to it; swiping the notification away simply
re-posts it. For read-only and sensitive tools, **Approve** and
**Deny** work straight from the notification, even if the app process
has since been killed: the run resumes from its checkpoint and the
tool call is re-validated before your stored decision is applied.
Destructive tools never execute from a notification — it offers
**Deny** and a **Review in chat** link to the regular typed-confirm
card. Clarifying questions park the same way under an **Agent needs
your input** notification that deep-links back to the chat. Parked
requests expire after the **Settings → Background & triggers → Approval window**
period (default 24 hours); an expired run fails with *Approval window
expired*.

---

## Background tasks

A pipeline run does not need the screen. This section is the
one-stop summary of what happens when the app goes away mid-run; the
chat-level details live in [Chats](#chats).

### What happens when you close the app during a run

- **Backgrounding the app** keeps the run alive: a foreground service
  (or, for scheduled tasks, the worker itself) holds the process while
  inference continues, with a status notification. That notification is
  shown **only while the agent is actively working** and is removed on
  its own once the run settles — it does not linger in the shade after
  the agent has finished. Tap it to jump back into the app.
- **Process death** (battery optimisation, memory pressure, swiping
  the app away) cannot be survived in place — but every run writes its
  progress to the encrypted database as it executes. On the next app
  start the run is detected and marked **interrupted**, and its chat
  shows a **Run interrupted** card with **Resume** / **Discard**.
  *Resume* continues from the checkpoint — finished nodes replay their
  recorded results instantly, only unfinished work re-executes (see
  [Reopening a chat while a run is in flight](#reopening-a-chat-while-a-run-is-in-flight)).
- **A run that finishes in the background** lands its answer in the
  conversation as usual; open the chat and the full console trace is
  there to replay.

### Notifications

| Notification | When | Actions |
|---|---|---|
| *Agent is working* (only while working) | While a run executes in the background; auto-removed when it finishes | Opens the app |
| *Approval required* | A sensitive/destructive tool call awaits your decision | **Approve** / **Deny** (destructive: **Deny** / **Review in chat**) |
| *Agent needs your input* | A clarification question is waiting | Deep-links into the chat |
| *Task completed* / *Task failed* | A scheduled task finished | Opens the conversation the result landed in |
| *Still running* (optional ping) | A long backgrounded run is still going | Opens the app |

Approval and clarification requests survive process death: the staged
request is stored persistently, and acting on the notification resumes
the run from its checkpoint even if the app was killed in between. An
unanswered request waits for the **Approval window** setting (default
24 hours, Settings → Background & triggers → Advanced) and then fails the run with
*Approval window expired*.

### Run history and retention

Every run's record and trace are kept in the encrypted local database
so chats can reattach, traces can replay, and interrupted runs can
resume. To keep that history from growing forever, a daily maintenance
pass (it runs while the device is charging and idle) deletes old
finished runs and their traces:

- **Keep run history per chat** (Settings → Privacy, default 20) — each
  conversation keeps its most recent runs; older finished ones are
  removed.
- **Run history max age** (Settings → Privacy, default 30 days) —
  finished runs older than this are removed regardless of count.

Runs still waiting on an approval or clarification are never removed by
retention — they stay until you respond or their approval window
expires. Deleting a conversation removes its runs and traces
immediately.

---

## Entry surfaces

Beyond opening the app, three OS surfaces let you reach the agent from
elsewhere on the device. Every surface is **off by default**: it does
nothing until you point it at a pipeline, so the agent never acts on
shared content or a tap until you have opted in.

### Sharing into the agent

The app appears in the Android share sheet for **text and images**.
Share something and pick the app: it runs your chosen *share pipeline*
over the shared content and opens the chat so you can watch the run.
A shared image is attached exactly like a composer attachment (the local
model reads it; it never leaves the device). If you have not bound a
share pipeline yet, the app opens with a reminder instead of running
anything.

By default every share lands in one running **Shared** chat, so
everything you send accumulates in one place — new shares are appended to
it rather than starting a fresh chat each time. Turn this off with
**Settings → Background & triggers → Keep shares in one chat** to get a
new, auto-named chat per share instead. Either way, shares never touch
the chat you were already in.

### Launcher shortcuts

Long-press the app icon for quick actions:

- **New chat** — opens a fresh conversation.
- **Pipelines** — opens the pipeline library.
- **Recent chats** — the app also keeps a couple of dynamic shortcuts to
  your most recently used conversations, so you can jump straight back in.

### Quick Settings tile

Add the **Run pipeline** tile to your Quick Settings (notification
shade). One tap runs your chosen *tile pipeline* in the background — even
when the app is closed — and posts a notification when it finishes, with
a tap-through into the resulting chat. While no pipeline is bound the tile
is inactive and a tap opens the app so you can set one up. Right after you
bind a tile pipeline in Settings, Android offers to add the tile for you.

### Choosing a pipeline per surface

Bind a pipeline to a surface in either place:

- **Settings → Background & triggers** — the *Pipeline for sharing* and
  *Quick Settings pipeline* rows open a picker (choose a pipeline, or
  **None** to switch the surface back off).
- **Pipeline library** — a pipeline's row menu (⋮) has **Use for sharing**
  and **Use for Quick Settings tile**. The bound pipelines are easy to spot
  at a glance: the library row carries an outlined **SHARE** pill when it is
  the share-target pipeline and an outlined **TILE** pill when it is the
  Quick Settings tile pipeline (next to the filled **DEFAULT** pill).

If you delete a pipeline that a surface was using, that surface simply
turns off again until you bind another.

---

## Triggers

A **trigger** runs a pipeline on its own when a condition is met — a time
of day, a repeating interval, the device starting to charge, or a network
connection — and drops the result into a chat in the background. Open
**More → Triggers** to manage them.

The list shows each trigger with a plain-language condition line ("Every
day at 08:00", "When charging connected", "When Wi-Fi connects"), its
bound pipeline, and a switch to enable or disable it on the spot. The
first time you open the screen, an empty state explains the model —
*trigger → background run → result in chat* — with a button to create your
first one.

### Creating or editing a trigger

Tap **New trigger** (or a row to edit one). The editor has:

- **Name** — a label for the list.
- **When** — the condition type:
  - **Interval** — repeat every 15 min, 30 min, 1 h, 6 h or 24 h, or a
    **Custom** value in minutes or hours. The minimum is 15 minutes:
    background runs are batched, so the system may defer a run by a few
    minutes.
  - **Daily** — a time of day (24-hour).
  - **Charging** — fires once **the moment you plug in** (and re-arms when
    you unplug). This one is event-driven, so it runs right away rather
    than waiting for a poll.
  - **Network** — fires on connecting; flip **Wi-Fi only** to ignore
    mobile data. Under **Only on these Wi-Fi networks** you can add one or
    more network names (SSIDs) so the trigger fires *only* on those — e.g.
    just your home or office Wi-Fi. Leave it empty to fire on any Wi-Fi.
    Adding a name asks for **location permission** the first time (Android
    ties the Wi-Fi name to location); if you decline, the trigger stays
    saved but won't fire until you grant it. The names are used only for
    the match on your device — they are never uploaded.
- **Run this pipeline** — bind the pipeline to run. Choosing **None**
  leaves the trigger inert (saved but it fires nothing).
- **Input prompt** — the message handed to the pipeline each time it
  fires.
- **Enabled** — on/off. Off means saved but it won't fire; you can also
  toggle this from the list.

An **unbound** trigger (no pipeline) is always inert and shows "No
pipeline — tap to bind" with a disabled switch — a trigger fires nothing
without a pipeline. Saving, enabling, disabling or deleting a trigger
takes effect immediately, without waiting for the next app launch. If you
delete the bound pipeline, the trigger is disabled automatically.

### How soon a trigger fires

**Charging** triggers are event-driven — plugging in fires the run within
seconds, even if the app is closed. **Interval**, **Daily** and **Network**
triggers are checked on a background poll the system runs roughly every
**15 minutes** (the platform minimum), so they fire at the next check after
their condition is met, not the instant it changes. When the device is idle
or under aggressive battery optimisation the system may defer that poll
further; keeping the app excluded from battery optimisation makes background
runs more punctual.

### Results and notifications

Each trigger owns its **own chat session**, named after the trigger. The
first time it fires it creates that chat, and every later run lands in the
**same** conversation, so a recurring trigger keeps one running log instead
of scattering a new chat each time. (If you delete that chat, the next fire
quietly starts a fresh one.) The run streams its work and final answer into
the session exactly as if you had typed the prompt yourself.

While a run happens in the background you get up to three notifications,
all on the **"Scheduled task results"** channel and gated by the same
**Settings → Background & triggers** notifications toggle:

- **Trigger fired** — when an automation starts a background run.
- **Task completed** — with a preview of the final answer.
- **Task failed** — with the reason.

Tapping any of them deep-links straight into the trigger's chat. If a run
pauses for approval of a sensitive or destructive tool, you get the usual
**approval notification** with **Approve / Deny** actions, so you can let a
background trigger run proceed (or stop it) without opening the app.

This first wave covers only **low-sensitivity** conditions (time,
charging, network) that need no dangerous permission; notification,
location and SMS triggers are intentionally deferred.

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

On the very first launch the app seeds one pipeline for you — a
**showcase** graph materialised from the bundled `showcase_full_agent`
preset and marked as the default. It triages each message into
**chat**, **factual** or **task** and runs a tailored branch: a quick
on-device reply for chat; a Wikipedia-grounded lookup for factual
questions (with a complexity gate that can break hard questions into a
small research loop); and a plan → subtask-loop → synthesis flow for
actionable tasks, including a human-in-the-loop clarification step when
a subtask needs your input. The task loop is **composed**: each subtask
runs as one of four bundled sub-pipelines (Clarify / Lookup / Act /
Process) called through Pipeline nodes, so the showcase seeds a parent
pipeline plus those four sub-pipelines into your library. It runs
entirely on-device. Everything is an ordinary pipeline: edit, duplicate,
rename or delete any of them like any other (see
[Composing pipelines](#composing-pipelines-the-pipeline-node)).

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
- **Save as preset** — package the pipeline as a reusable template
  with a name, description, category and tags. Saved presets show up
  under **More → Library** and in the **+ From preset** picker.

Tap the **+** button (floating action button) to expand the two-way
speed-dial:

- **+ New pipeline** — create a blank pipeline. New pipelines start as
  a minimal `INPUT → OUTPUT` graph so that they are valid immediately.
- **+ From preset** — opens the **Pipeline presets** picker with a
  **Bundled** tab (curated starter presets that ship with the app) and
  a **Mine** tab (presets you have saved yourself). Use the category
  chips to narrow the list, tap a card to see its graph preview, then
  hit **Use this preset** to spawn a fresh pipeline from the template
  and jump into the editor.

### Pipeline presets

A **pipeline preset** is a reusable template of a whole graph. Two kinds
exist:

- **Bundled** — a handful of curated starter presets that ship with the
  app (local-only Q&A, cloud assist, tool-using agent, multi-step
  research, clarify-then-act, routed local/cloud, and **research to
  file** — a question turned into a Wikipedia lookup, distilled, written
  out as a Markdown report under `reports/` in the agent workspace, with
  the saved path returned). They are read-only.
- **Mine** — presets you create yourself with **Save as preset** (from a
  pipeline's `⋮` menu). These live in the app's local database.

Spawn a pipeline from a preset with the FAB's **+ From preset** option
(see above), or — when you open the editor on an empty pipeline — tap the
canvas's **From template** button to pick a preset without leaving the
editor. Either way, loading a preset always creates a *fresh* pipeline
with new ids, so the template is never modified. Presets are interchangeable with
the [browser pipeline editor](#browser-pipeline-editor): a bundled preset
can be exported as a `*.preset.json` file (see below) and imported into
the editor, and the editor can export its own `*.preset.json` for import
back into the app.

### Managing presets

Open **More → Library** to manage every pipeline preset. Bundled
presets are read-only — they can be exported as JSON (for example to
import them in the browser pipeline editor) but not renamed or
deleted. Your own presets expose a `⋮` overflow with **Rename**,
**Export JSON** (writes a `*.preset.json` file via the system file
picker), and **Delete** (asks to confirm).

For a marketing-style preview of this screen see
[`docs/images/hero-pipeline-library.png`](images/hero-pipeline-library.png)
(dark variant: [`hero-pipeline-library-dark.png`](images/hero-pipeline-library-dark.png)).

### Binding a pipeline to a chat

When you create a new chat from the drawer, you can attach a specific
pipeline to it. Chats without an explicit binding fall back to the
default pipeline marked in the library.

If no pipeline is marked as default (for example after deleting the one
that was), an unbound chat refuses to run and shows an explicit error
instead of silently picking an arbitrary pipeline from the library. To
fix it, mark a default via the pipeline card's `⋮` menu in the library,
or bind a pipeline to the chat directly. If a chat's bound pipeline has
been deleted, the chat is rebound to the default and a brief
notification tells you the selection moved.

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
QueueProcessor, Evaluation, Summary, Pipeline, Skill — has its own
form, with inline validation that disables Save until every required
field is filled.

By default an **Output** node has **no system prompt** and simply forwards
the previous node's text to you *verbatim* — what the last model or tool
produced is exactly what you see, with no extra processing. This keeps simple
pipelines clean. If you *do* want the Output node to re-format the reply, give
it a system prompt (there is a ready-made "Formatter" template you can pick in
its config sheet): it then runs one more model pass over the incoming text and
returns the formatted result instead. Even then, its default context is just the
**previous node's output** — not the chat history, the original task, memory or
tool results — so it re-formats only that reply. Enable the other context blocks
in the node's config if a formatter genuinely needs them.

For the **IntentRouter** node, the Classes section in its config
sheet lets you grow / shrink the class list: each row has a small
**−** button to remove it (disabled below the 2-class minimum), and a
**+ Add class** button under the list creates a new empty class row
(disabled above the 6-class maximum). The new class shows up as an
additional outbound port on the node card immediately on Save.

The nodes that produce a structured result — **IntentRouter**,
**IfCondition**, **Evaluation**, **Decomposition** and **Tool** — run their
output through a validate-and-repair step: if the model's first reply is
malformed, the agent hands it back the bad output and asks it to fix itself
(up to a small repair budget, 2 attempts by default) before falling back.
Each repair attempt shows on the console as `Output repair 1/2 for node …`,
and a node that exhausts its budget logs an Error and takes its default
path (or, for Decomposition, fails the run rather than continue on a
corrupt subtask list).

Each of these nodes also carries an **Engine** selector — choose whether it
runs **On-device** (the default) or against a configured cloud provider. A
cloud provider that natively guarantees JSON output lets a JSON-producing
node skip the repair loop; if the chosen provider is unavailable at run time
the node falls back to on-device and notes it on the console.

### Composing pipelines (the Pipeline node)

A pipeline can call **another pipeline** as a single step. Add a
**Pipeline** node, open its configuration sheet, and pick the
sub-pipeline to run from the **Target pipeline** picker — the list is
every other saved pipeline. When the node runs, its input becomes the
sub-pipeline's message, and the sub-pipeline's final answer becomes the
node's output, so a composition reads like a function call between
pipelines. This is the building block for reuse: describe a reusable
sub-flow once and call it from several pipelines instead of copying
nodes around. The bundled **Showcase — full agent** is the worked
example — its task loop routes each subtask to one of four bundled
sub-pipelines (Clarify / Lookup / Act / Process) through Pipeline nodes.

A few rules keep compositions sound, all enforced before a pipeline can
be saved:

- **No target, no save.** A Pipeline node with no target selected is a
  validation error, exactly like a dangling connection.
- **No cycles.** A pipeline cannot call itself, directly or through a
  chain that loops back to it. The target picker greys out any choice
  that would close a cycle and tells you which pipeline causes it.
- **Depth limit.** Compositions can only nest so deep (the picker greys
  out a target that would exceed the limit). The same ceiling is
  re-checked while the pipeline runs, so a graph edited after it was
  validated can never recurse without bound.
- **Editing a sub-pipeline.** A sub-pipeline is an ordinary pipeline —
  edit, rename or duplicate it like any other. If you delete one that
  another pipeline still calls, the deletion dialog lists the dependent
  pipelines so you can repoint or remove the reference first.

While a composed run executes, each sub-pipeline appears as its own
**indented span** in the console — see
[Sub-pipelines in the console](#sub-pipelines-in-the-console) for how
nested traces, the shared step budget, approvals raised inside a
sub-pipeline, and resuming across a sub-pipeline boundary all behave.

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
| `$LANG`            | The device locale as a BCP-47 language tag (e.g. `en-US`). |
| `$LOCATION`        | The device's coarse region — the locale country code (e.g. `US`). |
| `$USER`            | The display name from your identity card (currently the literal `Anonymous`). |
| `$DEVICE`          | A short `manufacturer model · Android <version>` descriptor. |

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

### Prompt presets

Every prompt-bearing field in a node's configuration sheet has two
small icons next to its label:

- **📚 Library** — opens a picker scoped to the current node's type.
  The picker has two tabs:
  - **Bundled** — curated, read-only prompt templates that ship with
    the app (e.g. *Concise assistant*, *Step-by-step reasoner*,
    *JSON structured output*, *Keyword classifier*, *Dependency-aware
    decomposition*).
  - **Mine** — prompt templates you've saved yourself (see 💾 below).
  Use the search box to filter by name, or tap the tag chips to narrow
  the list further. Every row exposes two actions:
  - **Preview** — renders the prompt with `$VARIABLE` placeholders
    substituted at the current moment so you can see the final text
    before applying.
  - **Apply** — replaces the field's current value with the preset's
    body and closes the picker.
- **💾 Save as preset** — captures the current draft as a new entry in
  the **Mine** tab. You enter a name (max 60 chars), an optional
  description, and optional comma-separated tags. The preset's node
  type is inferred from the field you saved from, so it'll only show
  up in the picker when you open it on a matching node type later.

User presets live in the app's local database; bundled presets ship
with the APK and are never modified.

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
2. Open the app's **Pipelines** screen — the **Import JSON** affordance
   sits in the *From browser editor* footer at the bottom of the
   library list.
3. Tap **Import JSON**. The system file picker opens; pick the file
   you exported from the browser editor.
4. The imported pipeline is saved into the library and becomes the
   one currently open in the editor.

### Moving a whole composition — bundles

A single **Export JSON** / **Import JSON** moves *one* pipeline. If your
pipeline calls other pipelines (through **Pipeline** nodes), those
sub-pipelines are separate files — moving the parent alone would leave
dangling references. A **bundle** solves this: it packs the pipeline
*and* every sub-pipeline it depends on into one file.

- **Export a bundle.** In the pipeline library, open a pipeline's
  overflow menu and choose **Export bundle (with dependencies)**. The
  app walks the pipeline's dependencies, gathers the whole set, and
  writes a single `knotwork-bundle-YYYY-MM-DD.json`. If a dependency
  can't be found the export stops and tells you which one — a bundle is
  only useful if it's complete. In the browser editor the matching
  button is **📦 Export bundle**.
- **Import a bundle.** Use the same **Import JSON** affordance — it
  recognises a bundle automatically and imports every pipeline in it at
  once, reporting how many landed. In the browser editor, **📦 Import
  bundle** adds them to your *Mine* presets.
- **When something already exists.** If a pipeline you're importing has
  the same identity as one already in your library, the app asks what to
  do: **Replace** it (update in place, keeping anything bound to it) or
  **import as a copy** (leave the existing one untouched and add a fresh
  duplicate). This choice now appears for ordinary single-pipeline
  imports too, so an import never silently overwrites your work.

Bundles carry pipelines only — not triggers, tool/MCP settings, prompt
presets, or chat history. Those stay on the device they were set up on.

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
| **read_file**      | Reads a text file from the agent's private workspace, truncated to a token budget so a long file never overflows the model's context; supports byte `offset`/`limit` paging. |
| **list_files**     | Lists files in the workspace (optionally under a sub-directory) with their size and last-modified time. |
| **find_files**     | Finds workspace files whose path matches a glob pattern (`*.md`, `reports/**`).      |
| **write_file**     | Writes a text file to the workspace. Creating a new file is the default; replacing an existing one needs an explicit overwrite flag, so content is never clobbered by accident. Asks for confirmation before running. |
| **edit_file**      | Makes a targeted change to an existing workspace file by replacing a unique snippet of text. The snippet must match exactly once, so an edit never lands in the wrong place. Asks for confirmation before running. |
| **append_file**    | Adds text to the **end** of a workspace file, creating it on the first call. Existing content is always kept (there is no overwrite), so it is the natural fit for accumulating entries in a daily log or report. Asks for confirmation before running. |
| **delete_file**    | Deletes a file from the workspace. This is irreversible and always asks you to confirm before it runs. |
| **http_request**   | Calls a remote HTTP(S) API (GET/POST/PUT/DELETE). It can only reach domains you have explicitly added to the **Allowed domains** list — until you add one, the tool is hidden from the agent entirely. A GET asks for confirmation; a POST/PUT/DELETE asks for the stronger destructive-action confirmation. See the warning below before adding a domain. |

Each tool has a switch on the Tools screen. Turn a tool off to hide
it from the agent for the next run; turn it on to make it available
again.

#### Allowed domains for http_request

The **http_request** tool is off by default: it stays invisible to the
agent until you opt a specific destination in. Because a file the agent
reads could contain instructions planted by someone else (a *prompt
injection*), an HTTP tool that could reach any address would be a way to
quietly send your data off the device. The allowlist is the safeguard:

- The agent can only contact a host you have added. Matching is exact —
  sub-domains are not implied, so adding `example.com` does not allow
  `api.example.com`; add each host you need. Any other host — including
  one reached through a redirect — is refused before the request leaves
  the device.
- Public domains must use `https`. Plain `http` is allowed only for
  local addresses (for example a home Ollama server).
- A request is refused outright if it would carry one of your stored
  provider API keys, so a saved key can't be leaked to a remote host.

To manage the list, open the **Tools** screen and tap **Allowed domains**
under the http_request row. The editor shows the current hosts, lets you
remove any of them, and previews how a typed entry will be stored before
you add it (it normalises `HTTPS://Api.Example.com/v1` to
`api.example.com`, and warns when an entry is invalid or already on the
list). Only add a domain you trust. Adding one lets the agent send data to
it, so treat the list the way you would treat granting an app network
access to a specific site.

For a marketing-style preview of this screen see
[`docs/images/hero-tools.png`](images/hero-tools.png)
(dark variant: [`hero-tools-dark.png`](images/hero-tools-dark.png)).

### Where scheduled task results land

A task created with **schedule_task** executes through the same
pipeline as an interactive message, and its result lands in the
conversation that scheduled it: when the task fires, the prompt
appears as a user message, intermediate steps go to the console, and
the final answer arrives as a regular agent reply. Opening the chat
later shows the exchange as if the run had happened on screen — and if
the chat is already open when the task fires, the run attaches live.

If the original conversation was deleted before the task fired (or the
task predates session binding), the result is delivered to a fresh
conversation named after the task, e.g. *Scheduled: check the news*.

When a scheduled run finishes, the app posts a **Task completed**
notification with the first line of the answer — or **Task failed**
with the reason — and tapping it opens the conversation. The
announcement can be turned off with **Settings → Background & triggers →
Scheduled task results** (on by default).

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

The **Approve tool calls** control in **Settings → Tools & workspace** lets
you require approval for **every** tool call (`All`), regardless of its risk
level. Choose it if you want to confirm even read-only lookups.

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

## Files

The agent has a small private **workspace** — a sandboxed directory it
can read from and write to via the file tools (`read_file`,
`write_file`, `list_files`, …). The **Files** screen, reached from
**More → Files**, is your window into it: it is where the reports and
exports the agent produces show up, and where you can hand it a file to
read.

### The listing and quota

Files are shown in a flat, path-sorted list — a file saved under
`reports/` reads as `reports/name.md`, with the directory dimmed and the
name emphasised, so the layout is legible without a folder tree. Each
row shows the file's size and when it was last modified; text files and
binary files carry different icons.

The header shows a **quota indicator** — how much of the workspace
budget is used out of its limit, with a fill bar. As the workspace fills
it ramps from neutral to amber (near the limit) to red. If it is full,
a banner explains that the agent's writes are being refused until space
is freed; delete files or raise the limit (Settings → Tools & workspace →
Advanced → Workspace max total size) to recover.

Pull down to refresh the listing.

### Previewing a file

Tap a text file to open a read-only, monospace **preview** in a bottom
sheet. Very large files are shown truncated (the first part only) with a
banner telling you so — use **Save as…** to get the whole file. Binary
files are not previewable; use the row's overflow menu to share, save,
or delete them.

### Taking a file out

From the preview sheet (or a row's overflow menu) you can:

- **Share** — opens the system share sheet. The app stages a temporary
  copy for sharing, so the workspace directory itself is never exposed
  to other apps; the receiving app gets read access to that one copy
  only.
- **Save as…** — opens the system "create document" picker so you can
  write the file out to a location of your choice (Downloads, Drive,
  etc.).

In multi-select mode you can share several files at once.

### Putting a file in

Tap **Import** (the button in the quota header, the floating action
button, or the empty-state call to action) to pick a file with the
system file picker and copy it into the workspace for the agent to read.
Imports are subject to the same per-file and total-size limits as the
agent's own writes. If a file with the same name already exists, you are
asked whether to **keep both** (the import is saved under a numbered
name like `report (1).md`) or **replace** the existing file.

### Cleaning up

Delete a single file from its preview or overflow menu, or long-press a
row to enter multi-select and delete several at once. Deletion asks for
confirmation and is **permanent** — files removed here cannot be
recovered.

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

Open the **More** tab, tap **Memory**. A stats header sits at the top:
the total number of stored memories, the on-disk size, when memory was
last compacted, and a coloured bar breaking entries down by provenance
(**Auto** / **Compaction** / **Manual**). Below it, a chip row narrows
the list — **All**, **Pinned**, or one provenance at a time, each chip
showing its count — and a **Sort** + date-range pair of dropdowns
re-orders and time-bounds the list. Entries are grouped into **Pinned**,
**Today**, **This week**, and **Earlier**, each row carrying a coloured
provenance accent, a source badge, and its tags.

### Auto-extract from conversations

When **Settings → Memory → Auto-extract from conversations** is on
(the default), the agent automatically tops up long-term memory for
you. Shortly after a reply finishes (a ~30-second quiet period, so a
fast back-and-forth is processed only once), it re-reads the recent
conversation and distils the durable facts you stated — preferences,
events, and relationships — into new memory chunks. Small talk, the
assistant's own wording, and anything not explicitly stated are
ignored, and a fact that closely matches one you already have is
skipped rather than duplicated.

Each new chunk is tagged with the fact type it represents (`fact`,
`preference`, `project`, …) and the chat it came from, so you can tell
auto-saved memories apart from ones you saved by hand. You can watch
this happen in the **Console** pane (the **Memory** filter) and review
or delete the results on the Memory screen.

Turn the toggle off if you would rather curate memory entirely by
hand; extraction then stops and existing memories are left untouched.

### Saving a memory by hand

You don't have to wait for auto-extract. Two ways to add a **Manual**
entry: long-press any chat message and choose **Save to memory** (see
[Copying a message](#copying-a-message)), or tap the **Add memory** FAB
on the Memory screen and type the text. Manual entries are embedded with
whichever embedding provider is active, so they are searchable straight
away.

### Searching memory

Tap the search icon to open semantic search. Your query is embedded and
the list is re-ranked by relevance — each result shows a 0–1 score, so a
search for "berlin" surfaces the timezone note even though it never
contains that word.

### Viewing and editing an entry

Tap a row to open its detail sheet. It shows the full text, an
approximate token count, the source ("Auto-extracted" / "Saved
manually" / "Compacted"), which chat it was learned from, when it was
captured, and how often it has been used in replies. From here you can
**pin**, **edit** the text and tags, or **delete** the entry. Pinned
entries float to the top and are never touched by compaction.

### Compact Memory

Tap **Compact** in the stats header to consolidate memory. A dialog
previews the estimated number of chunks removed, bytes freed, and
runtime before you confirm. Compaction merges near-duplicate chunks and
re-summarises the oldest entries; pinned memories are never touched. The
"compacted N ago" line in the header reflects the last run.

### Exporting memory

The overflow menu (⋮) on the Memory screen offers **Export memory**,
which writes every chunk — text, embedding, tags, and metadata — to a
JSON file via the system file picker, for backup or migration. The same
**Export** action lives under **Settings → Memory**, and the Memory
screen's multi-select mode can export only the chosen entries.

### Moving memory to another device (export / import)

To carry an agent's memory to a new phone, export it on the old device
and import the file on the new one:

1. On the source device, open **Settings → Memory → Export** and pick a
   location. The file is a self-describing JSON document (it records
   which embedding provider produced the vectors and when it was
   written).
2. Copy the file to the new device (any transfer works — cloud drive,
   cable, messaging app).
3. On the new device, open **Settings → Memory → Import**, select the
   file, then choose a strategy:
   - **Merge** — add the imported chunks to whatever is already there,
     skipping any with an id that already exists. Nothing is deleted.
   - **Replace all** — wipe the current memory (pinned entries included)
     and load the file's chunks exactly. Use this for a clean transfer.

If the file was exported with a **different embedding provider** than the
one the new device is using, the app shows a notice and re-computes the
affected embeddings automatically in the background after import — so the
transferred entries become findable without any manual step (give it a
moment to finish before searching). You can also force an immediate pass
with **Settings → Memory → Re-embed**.

### Clearing context

Starting a new chat from the drawer is the simplest way to drop the
short-term context — old sessions stay where they are, and the new
one begins with an empty window. Clearing the mini-console with
**Console → Clear** wipes the visible event log but does not touch
chat history or memory.

---

## Settings

Settings open on a **hub**: a search field, a short **Basic** block of the
handful of knobs most people touch, and a list of eight categories. Tapping a
category opens a focused sub-screen that shows its Basic settings immediately
and tucks the rest behind an in-category **"Advanced — change deliberately"**
disclosure. The redesign changed only *where* each setting lives, not *what* it
does — every existing control survives, just grouped by topic instead of one
long scroll. The top bar carries the app version, channel and build date.

For a marketing-style preview of the hub see
[`docs/images/hero-settings.png`](images/hero-settings.png)
(dark variant: [`hero-settings-dark.png`](images/hero-settings-dark.png)).

### Basic vs Advanced

Every category leads with its **Basic** settings — the ones that change everyday
behaviour and are safe to adjust. The **Advanced** disclosure holds tuning
parameters (sampling internals, retrieval thresholds, workspace and HTTP limits,
retention windows) that have sensible defaults and rarely need changing; the
"change deliberately" label is a reminder, not a lock. Six cross-category Basic
controls are also surfaced inline on the hub so you never have to open a
sub-screen for them: **System instructions**, **Inference backend**, **Approve
tool calls**, **Block destructive tools**, **Long-running tasks** notifications
and **Send anonymous crash reports**.

### Search the settings

The magnifying-glass field at the top of the hub searches every setting by name,
description, owning category and a set of synonyms — so typing `max` surfaces
*Cap autonomous steps* (via the synonym *max steps*), *Max context length*,
*Max memory chunks* and more. The matched text is highlighted in each result, and
a result row shows its category **breadcrumb** and **Basic/Advanced** tier (plus
a `≈ "synonym"` chip when a synonym is what matched). Tapping a result opens the
owning category, expands its Advanced section when the target lives there, and
scrolls to and briefly flashes the row (a static accent under reduced motion). A
calm *"no settings match"* state offers a one-tap **Clear**. The index is built
from the settings registry, so any setting added to the app becomes searchable
automatically.

See [`docs/images/settings-search.png`](images/settings-search.png)
(dark variant: [`settings-search-dark.png`](images/settings-search-dark.png))
for the search results in action.

### Generation

System-prompt and sampling controls.

- **System instructions** *(Basic)* — a monospaced multi-line field whose content
  is prepended to every system prompt the agent sends. Tap a chip to insert one
  of the built-in variables (`$DATE`, `$TIME`, `$LANG`, `$LOCATION`, `$USER`,
  `$DEVICE`) — they expand fresh on every prompt render. The counter shows live
  character usage against the 4 000-character limit.
- **Tool-usage instruction** *(Advanced)* — extra free-text guidance on when and
  how the agent should call tools, appended to the tool-calling prompt.
- **Temperature** (0.0 – 2.0) — higher values produce more varied output.
- **Top-K** (1 – 100) — keeps only the K most likely tokens.
- **Top-P** (0.0 – 1.0) — nucleus sampling threshold.
- **Repetition penalty** (1.0 – 2.0) — `1.0` is neutral; higher values discourage
  the model from repeating recent tokens.
- **Max context length** (512 – 8 192) — working window in tokens.
- **Voice-input length** (seconds, default 30) — the auto-stop limit for voice
  capture before transcription.

### Models

The active on-device model, its backend, and external cloud providers.

- **Active-model card** — name, file size, context window, quantization and
  download date, with an **Active** badge.
- **Inference backend** *(Basic)* — drop-down picking the engine (NPU preferred,
  falling back to GPU then CPU). Changing it surfaces a restart banner — tap
  **Restart** to apply immediately.
- **Test backend** — runs a fixed prompt-probe and persists the measurement
  (`Last probe · N tok in T s · K tok/s`) so the row keeps the metric across
  navigation.
- **Manage** — opens the full Models browser to discover and install on-device
  models.
- **External providers** *(Basic link)* — each provider (**OpenAI**,
  **Anthropic**, **Google**, **DeepSeek**, **Ollama**) collapses to a row showing
  the masked key fingerprint and selected model; tap to open the provider editor.
  The Ollama row carries a **LAN** pill and base URL. **+ Add provider** surfaces
  an unconfigured provider without scrolling. Leaving every cloud row blank keeps
  the agent fully offline.
- **Default pipeline** *(Advanced link)* — picks which pipeline new chats use by
  default.

The provider editor's **Retry policy** applies to every cloud provider (chat and
cloud embeddings alike). Transient failures — rate-limits (HTTP 429), server
errors (5xx) and connection/read timeouts — are retried with exponential
backoff; authentication errors are not retried, and stopping a run cancels
cleanly. **Max attempts** (1–5, default 3; set to **1** to disable retries) and
**Base delay** (100–10 000 ms, default 1 000) tune it. Each retry shows on the
agent console as a muted line such as `Cloud retry 1/2 for openai`.

### Memory

Long-term memory extraction, chat-history compression, retrieval tuning and data
actions (behaviour unchanged — see [Long-term memory](#long-term-memory) for the
full feature).

Basic:

- **Auto-extract from conversations** (default on) — distils durable facts from
  finished chats into memory.
- **Background compaction** (default on) — the daily charging-and-idle worker
  consolidates stale clusters.
- **Compress long chat history** (default on) — older turns of a long session are
  summarised by the local model in the background and shown to the agent as an
  *"Earlier conversation (summarized)"* block ahead of the recent, verbatim
  turns.

Advanced:

- **Auto-summarize threshold** — `%` of the memory context budget.
- **Search results (top-K)** (1–20, default 5), **Similarity threshold**
  (0.30–0.90, default 0.55), **Recency half-life** (7–180 days, default 30),
  **Compaction age** (7–90 days, default 30) and **Max stored chunks**
  (1 000–20 000, default 5 000) — the retrieval and housekeeping parameters.
- **Compression threshold** (500–32 000 tokens, default 3 500) and **Live
  window** (2–50 messages, default 10) — tune chat-history compression. Keep the
  threshold comfortably below **Max context** so the summary plus the live window
  still fit.
- **Memory summary default limit** (1–50) — how many recent chunks the
  `$MEMORY_SUMMARY` prompt variable injects.
- **Embedding model** — on-device Universal Sentence Encoder, OpenAI or Ollama.
  Switching applies on the next embed/retrieval; a persistent
  *"re-embed recommended"* banner appears (with an inline **Re-embed** button)
  until a full re-embed or wipe re-aligns the store, or you switch back.
- **Verbose memory logging** (off by default) — expands every memory-retrieval
  console line with a per-hit snippet and similarity score, and logs which chunks
  background compaction merged. A local diagnostic only — nothing leaves the
  device.
- **Data actions** — **Export base** (SAF picker → JSON blob), **Re-embed**
  (re-runs the embedder over every chunk, with a progress bar), and **Clear** (a
  typed-confirm dialog that wipes every chunk, pinned included).

Each slider only offers in-range values; a rejected value shows an inline message
and is discarded rather than saved.

### Pipelines & structured output

- **Cap autonomous steps** *(Basic)* (5 – 100) — upper bound on planner
  iterations per user message; the agent pauses for guidance when the cap is hit.
- **Max nesting depth** *(Advanced)* — how deep `PIPELINE` nodes may recurse.
- **Structured-output repairs** *(Advanced)* — how many times the
  structured-output gate re-asks the model to fix malformed JSON before falling
  back to the per-node failure policy.
- **Retry policy** *(Advanced link)* — opens the provider detail screen (the same
  retry sliders described under Models).

### Tools & workspace

Tool approval, safety guardrails, and the agent workspace / HTTP limits.

Basic:

- **Approve tool calls** — segmented control: `All` (prompt for every call),
  `Sensitive +` (only sensitive/destructive — recommended), `Never` (no prompts;
  reserved for known-safe pipelines).
- **Block destructive tools** — when on, destructive tools are refused outright
  rather than going through the HITL prompt. Useful when the agent runs
  unattended.
- **Block network from local model** — when on, every cloud provider returns
  `null` to the inference pipeline and only the on-device LiteRT engine plus
  LAN-local Ollama remain reachable.
- **Manage tools / MCP servers** *(link)* — enable tools, set per-tool risk
  overrides, add MCP servers.

Advanced:

- **Tool-call timeout**, **Workspace max file size**, **Workspace max total
  size**, **Workspace read token budget** and **HTTP response cap** — the
  workspace and `http_request` limits.
- **Files / allowed domains** *(link)* — the `http_request` domain allowlist and
  the workspace file browser.

### Background & triggers

Notifications and the windows that govern parked / resumable runs.

Basic:

- **Long-running tasks** — when on, a low-importance system notification fires
  when a backgrounded pipeline run exceeds the long-running threshold.
- **Scheduled task results** — when on, finishing a scheduled background task
  posts a **Task completed** notification with the first line of the answer (or
  **Task failed** with the reason); tapping it opens the conversation the result
  landed in. On by default.

Advanced:

- **Resume window** (1 – 168 hours, default 48) — how long an interrupted run
  stays resumable from its checkpoint. Older interrupted runs only offer
  **Discard** — their recorded context grows stale with time.
- **Approval window** (1 – 168 hours, default 24) — how long a run parked on an
  unanswered tool approval or clarifying question waits before failing with
  *Approval window expired*.

### Privacy

- **Send anonymous crash reports** *(Basic)* — forwards stack traces + device
  meta + active pipeline / model identifiers to Firebase Crashlytics. Off by
  default; debug builds never report. Full policy in
  [SECURITY.md](../SECURITY.md). **This toggle exists only on the standard
  build.** The **F-Droid / FOSS build** ships with no crash-reporting
  dependency at all — it collects and transmits nothing, and the toggle is
  absent — so which build you installed decides whether this control is even
  present. The rest of the app is identical between builds.
- **Keep run history per chat** *(Advanced)* (5–100, default 20) — how many
  most-recent pipeline runs each conversation keeps. Older finished runs and
  their traces are deleted by the daily maintenance pass (see
  [Background tasks](#background-tasks)).
- **Run history max age** *(Advanced)* (7–180 days, default 30) — finished runs
  older than this are deleted regardless of the per-chat count. Runs still
  waiting on an approval or clarification are never removed by retention.
- **Usage statistics** *(link)* — opens a fully on-device usage dashboard (see
  below).

#### Usage statistics

A privacy-preserving picture of how you use the app, computed entirely on the
device. **Nothing on this screen is ever transmitted** — the counts live in the
same encrypted database as the rest of your data, and there is no network call
anywhere on this path (a build-time guard enforces that). The screen shows:

- **Runs** — total finished runs and the share that **completed**, **failed**,
  were **cancelled**, or were **interrupted** (a run cut short by the app being
  killed). Nested sub-pipeline runs are not double-counted.
- **Runs by pipeline** — how many runs each pipeline accounted for.
- **Trigger firings** — how many times each kind of automation trigger
  (schedule / daily / charging / network) fired.
- **Active days** — the number of distinct days with any activity, plus the
  first and last.

Controls:

- **Record usage on this device** — the opt-in toggle. On by default and
  local-only; turn it off to stop the counters advancing (already-recorded
  figures are untouched).
- **Share as text** / **Export JSON** — take a voluntary snapshot for your own
  analysis. The text goes through the system share sheet; the JSON is written to
  a file you pick. Neither happens automatically.
- **Reset statistics** — permanently clears every recorded count (it does not
  change the recording toggle).

### About

- **Identity card** — an avatar + label confirm the device identity is anonymous
  and local. The meta line shows the truncated device id and whether your API
  keys live in the Android Keystore (hardware-backed) or — on constrained
  devices — in encrypted preferences.
- **App version & licenses** *(link)* — build info and the open-source license
  list.
- **Reset all settings** *(Advanced)* — a confirm dialog that restores every
  tunable setting to its recommended default in one step. It touches *settings
  only*: your chats, long-term memory, pipelines, presets, skills, MCP/cloud
  connections, the `http_request` allowlist, custom prompts, the active embedding
  provider, and API keys are all left untouched. The defaults are sensible
  starting points rather than tuned optimums — they are refined as the app sees
  real-world use, so resetting is a safe way to get back to a known-good
  baseline.

---

## More tab

The **More** tab is the landing page for every secondary surface.
Each row carries a live counter (memory chunks, active model name,
prompt categories, active-task count + a numeric badge, app
version), and a footer pill summarises the privacy state — when the
agent has not made any outbound LLM or MCP call for a minute, the
pill reads `on-device · no network calls in last N m`; an in-flight
cloud call flips the indicator to `online · cloud enabled`. The
window resets when the process is recreated.

## Managing local models

Open **More → Models** to install, activate, or remove on-device
LLMs.

- The top **Active** card highlights the model currently loaded into
  inference memory. Its mono subtitle shows size, accelerator
  backend, and execution backend.
- The **HuggingFace** section lets you paste a personal access token
  (stored encrypted, in the same Keystore-backed store as cloud API
  keys) so gated repositories can be downloaded. The `+ Paste` button
  reads the system clipboard.
- The **Custom model URL** field accepts a direct link to any
  `.litertlm`, `.task`, or `.gguf` file. Tap `Get` to start
  downloading.
- The **Available presets** list shows curated models, each row in
  one of three states: `Get` (not downloaded), progress bar with
  cancel-X (downloading), or `✓ ON DISK` (ready to activate).

### Model performance & benchmark

Below the Active card, the **Performance** card shows how the active
model actually performs *on your device* — picking a model is a
speed/quality/memory trade-off, and the numbers make it concrete instead
of a guess:

- **Time to first token** — how long after a run starts the model emits
  its first token (model-load time excluded). Shown in milliseconds, or
  seconds once it passes one second.
- **Decode speed** — sustained generation throughput in tokens per second.
- **Peak memory** — the highest process-wide native-heap usage seen during
  a run. This is deliberately labelled **approximate**: it covers the whole
  app process (not just the model), excludes the model file's
  memory-mapped pages, and can read low on devices that compress memory. It
  is a useful relative indicator, **not** the model's exact footprint.

These figures are **averaged over the model's most recent runs** and update
automatically — every on-device generation you trigger (chatting, running a
pipeline) records a sample. A freshly installed model that hasn't run yet
shows a calm **"No runs yet"** state.

Tap **Run benchmark** to measure the model on demand. It runs a fixed
prompt twice — a **warm-up** run (not counted) followed by a **measured**
run — then shows a one-shot report (TTFT, decode speed, total time, peak
memory) with a **BENCHMARK** badge. **Share** hands a plain-text summary to
any app (messages, notes, a bug report); **Done** returns to the rolling
averages. You can **Cancel** a benchmark while it runs. The benchmark only
runs in the foreground and **waits its turn** if a pipeline is currently
using the engine — it never interrupts an active run (you'll see a calm
"Busy with a task" notice in that case).

## Prompt library

**More → Prompt library** stores reusable system prompts grouped by
node type. The screen opens on the first category tab; tap any tab
to switch. Each card has:

- A category chip on the left and the prompt name in bold.
- A multi-line preview with `$VARIABLE` tokens highlighted inline so
  you can see at a glance which runtime values the prompt depends
  on.
- Edit (pencil) and Delete (trash) icons in the row header.
- A footer with `used by N pipelines` and a `Duplicate` action.

The FAB at the bottom-right opens the editor sheet. Inside, you can
edit the name and category and tap any chip in the `INSERT` row to
append the matching `$VARIABLE` to the prompt body. Save persists
the change immediately; the next pipeline run picks it up.

## Skill library

**More → Skill library** stores **skills** — reusable bundles of
*instruction + tool restriction + context configuration*. Instead of
copying a system prompt between nodes and pipelines, you describe a
capability once and reuse it.

The screen has two tabs:

- **Bundled** — read-only skills that ship with the app (Summarizer,
  Translator, Report Writer). Their row menu offers only **Duplicate**,
  which drops an editable copy named `… (copy)` into your **Mine** tab.
- **Mine** — your own skills, with full **Edit / Duplicate / Delete**.

Each row shows the skill name, a one-line description, and a
**tool-restriction** pill that is always one of three visually distinct
states: **All tools** (unrestricted — every tool, including ones added
later), **N tools** (an explicit subset), or **No tools** (an explicit
empty allowlist — *not* the same as unrestricted).

Tap the FAB (**New skill**) or **Edit** to open the full-screen editor:

- **Name** and **Description**.
- **Instruction** — a monospace field. Use `$VARIABLE` placeholders
  (e.g. `$DATE`, `$LANG`) for runtime values; they're substituted when
  the skill runs. Tap a chip in the **INSERT** row to append one.
- **Tools** — a master control selects **All tools**, **Restrict**, or
  **No tools**. In **Restrict** mode a checklist of the available tools
  appears, each with its risk pill, so you can pick exactly which tools
  the skill may use.
- **Context the skill sees** — five toggles (chat history, original
  task, node input, long-term memory, tool results) that become the
  skill's default context.

Deleting a user skill asks for confirmation; if any pipelines reference
it the dialog lists them so you can repoint or remove the reference
first.

### Using a skill in a pipeline (the SKILL node)

In the pipeline editor, add a **Skill** node to run a skill as one step
of a pipeline. The node configuration sheet offers:

- **Skill** — a picker over your whole library (bundled + your own).
  Until you choose one, the node is flagged invalid and the pipeline
  won't save.
- **Instruction (read-only)** — a preview of the chosen skill's
  instruction. It's edited in the Skill library, not here.
- **Tool allowlist** — an indicator showing the skill's restriction
  (All tools / N tools / No tools).
- **Inference engine** — run the skill **on-device** or in the **cloud**.
- **Context toggles** — these start **inherited** from the skill's own
  default context; change any one and it's marked **overridden** so you
  can see at a glance where the node diverges from the skill.

The tool allowlist is a real boundary, not a hint: when the skill runs,
`$TOOLS` in its instruction lists only the allowed tools, and if the
model still tries to call a tool outside the allowlist the node refuses
it and reports the refusal instead of running it. Allowed tool calls go
through the same confirmation prompts as any other tool — a skill never
lowers a tool's risk or skips a confirmation.

## Active tasks

**More → Active tasks** lists everything the agent is running right
now plus completed history. Filter chips at the top scope the list
to `All`, `Active`, `Background`, or `Completed`. Each row shows
the task title, a mono subtitle with the pipeline stage, a status
pill (Queued / Running / Success / Failed / Cancelled), and an
inline cancel button on running background work. Tap any row to
open a bottom sheet with the task details and an `Open chat` shortcut
for session-bound tasks.

## Live metrics

**More → Live metrics** surfaces the orchestrator's performance
counters and the most recent system log lines. The header three-cell
grid shows last inference time (ms), tokens-per-second, and the
total tokens processed since process start. Under it sit the
session-wide totals and a per-node-type breakdown. When the device
enters power-saving mode, a warning banner appears above the grid
to flag that the agent has paused background work.

## About

**More → About** shows the app's brand mark, version / build /
commit, the open-source license name (Apache 2.0), a hand-curated
acknowledgments list of the libraries that ship inside the app, and
a short privacy summary. Tap `Open license text` to load the
license verbatim in your browser, or `Read privacy policy` for the
detailed privacy stance.

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

- Open **Settings → Models** and tap **Test backend** to confirm
  which backend the model is actually using.
- Try a smaller model from the **Models** screen — even a 1B-2B
  parameter model can be substantially faster than a 7B+ one on
  CPU.
- Lower **Max context length** in **Settings → Generation → Advanced**.
  Shorter contexts mean less work per token.

### A tool says it is unavailable

Two common causes:

- A built-in tool that delegates to a cloud provider (for example,
  **delegate_task**) requires at least one cloud API key in
  **Settings → Models → External providers**. Without a key it is hidden
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

Long pipelines can hit the **Cap autonomous steps** ceiling. The console will
show a stop event with the step count. If you legitimately need
more iterations, raise the ceiling in **Settings → Pipelines & structured
output → Cap autonomous steps**. If the run is looping unproductively, lower it
instead.

### Memory search isn't finding an obvious entry

If a memory you know exists never shows up in a reply (or in the Memory
screen's search), work down this list:

- **The node isn't reading memory.** Only nodes with the **Long-term
  memory** input flag pull from the store. Open the pipeline, check the
  node's *Input Data* section, and watch the **Console → Memory** filter:
  if there is no `Memory: query=… → N hits` line for the run, the active
  node never queried memory.
- **The similarity threshold is too high.** A high **Settings → Memory →
  Similarity threshold** drops loosely-related chunks before they reach
  the prompt. Lower it (or pin the entry — pinned chunks bypass the
  threshold entirely and always sort to the top).
- **Recency decay buried it.** Old, non-pinned chunks lose score with
  age. If a months-old fact stops surfacing, raise **Recency half-life**
  or pin it.
- **The entry is queued for re-embedding.** A chunk imported under a
  different embedding provider can't be matched until the background
  re-embed finishes (it scores ~0 in the meantime). Give it a moment, or
  force it with **Settings → Memory → Re-embed**.
- **The provider changed.** Switching the **Embedding model** leaves
  existing chunks in the old vector space; run **Re-embed** so the whole
  store shares the active provider's space again. The Memory card shows
  a persistent *re-embed recommended* banner while this mismatch holds.
- **It was never extracted.** Auto-extract only keeps durable facts
  (preferences, events, relationships) and skips small talk and
  near-duplicates. If a fact didn't make the cut, add it by hand with
  **Save to memory** or the **Add memory** FAB.

### "Your data can't be unlocked" appears at startup

All local data is stored in an encrypted database whose key lives in
Android's hardware keystore. In rare situations — typically right after
restoring the app from a backup, after an OS update, or due to a
transient keystore glitch — that key can become temporarily unreadable,
and the app shows a dedicated recovery screen instead of starting:

- **Tap Retry first — possibly more than once.** Keystore failures are
  often transient; if the key becomes readable again, the app opens your
  existing data untouched. Rebooting the device before another retry
  helps in some cases.
- **Erase all data is the last resort.** If retrying never gets past the
  screen, the key is gone for good and the encrypted database can no
  longer be opened by anyone — including the app itself. **Erase all
  data** deletes the database and generates a fresh key so you can start
  over. The action is irreversible and guarded by a typed confirmation.

The app never deletes or re-keys your data automatically in this state:
without the original key the database contents cannot be recovered, so
the decision to wipe is always yours.

---

## See also

- [`architecture.md`](architecture.md) — internal design of the
  agent for contributors and reviewers.
- [`extending.md`](extending.md) — recipes for adding new node
  types, tools, cloud providers, and prompt variables.
- [`../SECURITY.md`](../SECURITY.md) — security policy, threat
  model, and what crash reporting collects.
- [`../README.md`](../README.md) — project overview and quick start.
