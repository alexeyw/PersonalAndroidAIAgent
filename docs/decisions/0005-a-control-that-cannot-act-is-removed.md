# 0005 — A control that cannot act is removed, not disabled

## Context

The cloud node's form offered Temperature, Max tokens and Timeout; the on-device
node's form offered temperature, topP, maxNewTokens and stopTokens. All seven
were **persisted** into the node's config and travelled into exported pipelines,
and none of them was read by anything: no executor parses that config, the node
model has no such fields, deadlines come from constants in the client factory,
and the on-device executor calls the engine without a sampler configuration.

Worst of the seven was Timeout. The person who reaches for it is precisely the
person whose provider has just hung — so the one control most likely to be tried
in a bad moment was guaranteed to do nothing.

## Decision

The controls were **removed from the forms**, not wired up and not shown
disabled. The underlying fields remain on the config objects, so existing
pipeline JSON still round-trips unchanged; those values simply are no longer
presented as adjustable.

## Consequences

- **Do not add a control before the thing behind it works.** A control that
  saves a value nobody reads is worse than an absent one: it looks like a
  setting, it survives export, and it quietly teaches the user that the app
  ignores them.
- Wiring these up was rejected as out of proportion rather than as unwanted.
  Doing it properly needs typed fields on the node model, which drags in a
  database migration, the content hash, the serializer, the node catalogue and
  the codec — a feature-sized change, not a fix. Per-node sampling and timeouts
  remain parked, on purpose and on record.
- A disabled control is not the compromise it looks like. It still occupies the
  place a working one would, and it still has to be explained.

## Status

Accepted, and current. Per-node sampling and timeout configuration remains
unimplemented.
