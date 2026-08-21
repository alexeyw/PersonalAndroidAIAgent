# 0004 — Branch on the input to the fork, not on a node's answer

## Context

A pipeline that needs to choose between two paths can be wired two ways: put the
fork first and let it judge the request, or let a node produce "either X or Y"
and have the fork work out which one arrived.

The second shape fails, and it fails in a way no amount of rewording repairs. A
bundled preset was once wired as
`INPUT → model("either a search term or an answer") → IF("which one is this?")`.
Asked *"What is 17 times 23?"*, the model answered `391`; the fork saw a bare
number, read it as a search term, and looked up *the year 391*. As strings,
`391` and `France` are indistinguishable — the condition has no signal to work
with, because the information that would separate them was thrown away one step
earlier.

## Decision

Evaluate a conditional or a router on the **request entering the fork**, not on
the output of an upstream node that was told to produce one of several shapes.

```
INPUT → IF "needs a lookup?" ─true──→ query builder → TOOL → summary ─┐
                             └false─→ direct answer ─────────────────┴→ OUTPUT
```

## Consequences

- A condition should judge **natural language** — something a language model is
  good at — rather than the type or shape of an intermediate string, which it is
  not.
- **A node told to emit "either X or Y" is a smell.** Give it one job and put
  the choice in front of it.
- Bias a condition toward the safer branch, and say so in its prompt.
- A node on a terminal branch must be told to answer in prose. An `OUTPUT` with
  an empty prompt is a pass-through, so a bare value reaches the user unchanged
  — which is how `391` became a user-visible answer to a question about a year.

## Status

Accepted. This is a rule about composing pipelines rather than about the code:
it is not enforced by a build gate, and a graph that violates it is valid and
will run.
