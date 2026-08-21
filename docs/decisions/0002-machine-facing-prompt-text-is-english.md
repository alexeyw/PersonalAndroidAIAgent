# 0002 — Machine-facing text in a pipeline is English

## Context

A model given a Russian question answers in Russian, and every downstream step
continues in that language. For prose that is correct. For everything else it is
not: a router's keyword, a subtask that becomes a tool call, a tool name, an
argument, a file path — all of these are compared against English literals in
code. The match disappears, the router falls through to its default branch, the
tool is never called.

**The failure is silent.** The pipeline completes. It simply did something other
than what was asked. This arrived as a user's bug report, not as a test failure.

Several bundled presets were making it worse by instructing intake and planning
nodes to "work in the user's language", although their output is read by routers
and tool executors rather than by a person.

## Decision

A node's language is decided by its **consumer**, never by its type:

| Consumer | Language |
|---|---|
| Machine — routing, parsing, subtasks that become tool calls, file paths | **English, always** |
| Human — the final answer, a clarifying question, an in-character reply | **The user's language** (`$LANG`) |
| The language is part of the data — a search term paired with a `lang` argument, an explicit translation target | **As the data requires** |

## Consequences

- Do not add `$LANG` to a node because it produces text. Ask who reads that
  text.
- The "final node" is not necessarily the `OUTPUT` node. An `OUTPUT` with an
  empty prompt is a pass-through and rephrases nothing, so the last node that
  *produces* text is the one that must answer in the user's language. Making
  `OUTPUT` itself translate was rejected: it would add an on-device inference
  pass to every run and let a formatter distort an answer that was already
  correct.
- The guard is a per-node table, not a rule about node types:
  `promptLanguageExpectations` in `PipelinePresetCatalogValidationTest`, whose
  values are `ENGLISH`, `USER` or `DATA`. A machine-facing prompt that does not
  tell the model to work in English fails; a user-facing one that does not
  reference `$LANG` fails; and a table entry for a node that no longer carries a
  prompt fails too, so the table cannot rot quietly.
- The previous guard was type-based — it required `$LANG` from every
  `LITE_RT` / `CLOUD` / `SUMMARY` / `CLARIFICATION` / `OUTPUT` node — and so
  **enforced the defect**, since it demanded the user's language from intake and
  planning nodes whose output is read by a router. It was replaced, not
  extended.
- A preset node carrying a prompt that the table does not mention fails the
  test, so adding a node means deciding who reads it.

## Status

Accepted, and enforced by the bundled-preset validation test.
