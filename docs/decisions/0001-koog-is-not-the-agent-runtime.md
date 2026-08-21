# 0001 — Koog is a transport and a client, not the agent runtime

## Context

The build declares the umbrella `koog-agents` dependency, which transitively
puts Koog's **entire** surface on the runtime classpath: its graph runtime,
parallel nodes, history compression, persistence and rollback tool registry are
all resolved into the APK today. Reading the dependency list, the natural
conclusion is that Koog runs the agent. It does not.

Pipeline execution is this project's own code: a graph walk over the user's
saved node graph, with the control constructs the product needs
(`QUEUE_PROCESSOR`, evaluation retry, conditional branching), human-in-the-loop
suspension, checkpointing and resume. That lives in the domain layer
(`GraphExecutionEngine`, `AgentOrchestratorUseCase`).

The distinction matters because the boundary is about **what we call**, not
about what is present. Adopting another Koog module costs zero new artifacts,
which makes "it's already on the classpath" an argument that proves nothing.

## Decision

Koog is used for exactly three things:

- **MCP transport and client** — connecting to external tool servers.
- **Cloud LLM client** — the provider-facing calls behind the unified `CLOUD`
  node, including retry wrapping and the native-JSON capability probe.
- **Cloud embeddings.**

Koog's agent runtime is **not** adopted. Neither are its file tools: the agent's
file operations are written in-house, against this project's own workspace and
permission model.

An addition to that list is a decision, not an implementation detail, and each
adopted module is recorded with the specific class actually invoked.

## Consequences

- A new capability is not "already available because Koog has it". Judge it on
  whether it fits the graph the user drew and the guarantees around it — the
  human-in-the-loop gate, the run ceilings, the resume path — none of which an
  external runtime knows about.
- Retry and cloud structured output *were* adopted from modules already on the
  classpath, because both extend the existing cloud-client usage rather than
  replacing the execution model. Structural validation still runs through this
  project's own gate, which stays the single source of truth for JSON shape.
- The autonomous ReAct loop is deliberately not part of the core. The agent
  executes the graph the user composed.

## Status

Accepted, and current. Extending the list of what Koog is used for requires the
same explicit reasoning as the original boundary.
