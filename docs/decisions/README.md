# Decisions

Short records of decisions that **constrain what a contributor may do** — the
ones where the obvious change is the wrong one, and where a reviewer would
otherwise have to explain the same reasoning again.

This is deliberately not a complete history of the project's decisions. It is a
curated set, and it is small on purpose.

## What earns a record

A decision belongs here only if all three hold:

1. **It still binds.** Superseded reasoning is history, not guidance.
2. **It constrains what a contributor may do** — not merely what the project
   happens to do. A record answers "why can't I just…?".
3. **No other document already owns it.** Each topic is written out in exactly
   one place; everywhere else links to it. If the architecture overview, the
   security policy, the testing strategy or the API conventions already explain
   a decision, that document keeps it and no record is written.

The third criterion does most of the work. When this catalogue was assembled,
nine strong candidates were dropped because an existing document already
covered them — including the single MCP connection pool, why tool-risk
resolution ignores a server's own hints, why deleting a memory requires proof,
and why usage statistics never leave the device. Look for the topic in the
[documentation index](../../README.md#documentation) before writing a record.

## When a new record is written

When a decision would otherwise be re-litigated in review. Not once per change,
not once per release, and not for a decision a reviewer would never have to
defend twice.

## About drift

Prose cannot be checked by a build gate the way generated tables can, so nothing
here is guarded against going stale. The mitigation is the size: a handful of
records that people actually read stays truer than a hundred that nobody does.
If a record no longer describes the code, correcting it is part of the change
that made it wrong.

## The records

| # | Decision |
|---|----------|
| [0001](0001-koog-is-not-the-agent-runtime.md) | Koog is a transport and a client, not the agent runtime |
| [0002](0002-machine-facing-prompt-text-is-english.md) | Machine-facing text in a pipeline is English |
| [0003](0003-cleartext-is-decided-in-app-code.md) | The manifest permits cleartext; the app decides |
| [0004](0004-branch-on-the-input-to-the-fork.md) | Branch on the input to the fork, not on a node's answer |
| [0005](0005-a-control-that-cannot-act-is-removed.md) | A control that cannot act is removed, not disabled |

## Format

One file per decision, `NNNN-kebab-title.md`, with **Context**, **Decision**,
**Consequences** and **Status**. Where a decision rests on a measurement, the
record gives the number. Where something in the repository enforces it, the
record names that gate or test — a decision with a guard is worth more than a
decision with a paragraph.
