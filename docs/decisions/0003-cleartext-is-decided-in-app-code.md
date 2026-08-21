# 0003 — The manifest permits cleartext; the app decides

## Context

The network security config used to set `cleartextTrafficPermitted="false"` and
list fourteen private IP addresses one by one. Android supports neither masks
nor ranges in that file, and the file ships inside the APK, so a user of an
installed build cannot edit it. A local Ollama server or an MCP server over
plain HTTP therefore worked only for someone whose LAN address happened to be
one of the fourteen — while "run a model on your own machine" is a scenario the
product promises.

Anyone reading the manifest today sees cleartext permitted and reasonably reads
it as a weakened setting. It is the opposite: the rule moved to where it can
actually be expressed.

## Decision

The manifest permits cleartext. The rule lives in `CleartextPolicy` in the
domain layer:

- Plain HTTP is allowed **only** to a loopback or RFC-1918 address that the user
  has **explicitly approved**.
- A public host is refused always, and cannot be approved — by construction a
  public origin never reaches the state where approval is offered.
- Consent is taken **when the address is entered** (the Ollama base URL, an MCP
  server URL), not when a request is made. The network layer is three unrelated
  stacks — a shared OkHttp client, Ktor for cloud and Ollama, a separate Ktor
  client per MCP connection — and none of them is a place where a person can be
  asked a question.
- The approval key is the canonical `scheme://host[:port]`. A different port on
  the same machine is a different server.

The gate sits at three points: the raw Ollama client, the MCP connection pool
(the single place an MCP connection is opened), and an interceptor on the shared
OkHttp client. The interceptor runs on **every** request, which is what catches
a redirect from HTTPS to HTTP.

## Consequences

- **Do not "harden" the manifest back.** Restoring
  `cleartextTrafficPermitted="false"` breaks every self-hosted setup the product
  advertises, and removes nothing, because the app-level gate is what enforces
  the rule.
- A residual risk is accepted and named: the platform no longer blocks cleartext
  for the app as a whole, so a redirect to HTTP inside a third-party HTTP client
  that our gate is not installed in will not be stopped by the platform. This is
  a deliberate trade — a platform gate that did not work for the promised
  scenario, for an application gate that does. For public hosts the outcome is
  **stricter** than before: they were already refused, and now the refusal also
  carries a legible error.
- Resetting settings does not revoke approvals. A reset to recommended defaults
  must not silently break a working local installation.
- Consent is a banner, not a dialog: the Ollama base URL is persisted on every
  keystroke, so a dialog would open mid-typing.

## Status

Accepted, and current. The transport floor it implements is described in the
[security policy](../../SECURITY.md).
