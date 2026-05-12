<!--
Thanks for opening a pull request!

Please fill in the sections below. The checklist mirrors the local quality
gate enforced by `./gradlew check` and the conventions described in
CONTRIBUTING.md.
-->

## Summary

<!-- A short description of what changed and why. -->

## Type of change

<!-- Tick all that apply. Mirrors the Conventional Commits prefixes documented in CONTRIBUTING.md. -->

- [ ] `feat` — user-facing feature
- [ ] `fix` — bug fix
- [ ] `refactor` — internal restructuring with no behaviour change
- [ ] `docs` — documentation-only change
- [ ] `test` — tests added or corrected, no production change
- [ ] `chore` — maintenance work, neither feature nor fix
- [ ] `build` — build system, CI config, dependency or toolchain change

## Linked issue

<!-- Use `Closes #N` to auto-close on merge, or `Refs #N` to reference without closing. -->

Closes #

## Checklist

- [ ] `./gradlew check` passes locally (detekt, ktlint, lintDebug,
      testDebugUnitTest, koverVerifyDebug).
- [ ] Tests added or updated for the changed code (target 100% logic
      coverage in new `domain` / `data` code; see `docs/testing.md`).
- [ ] Public documentation updated where relevant (see the *Pull
      requests* section in
      [`CONTRIBUTING.md`](../blob/main/CONTRIBUTING.md)). Typical
      triggers:
      user-visible feature → `README.md` + `docs/user-guide.md`; new
      `NodeType` / `Tool` / prompt variable → `docs/extending.md` and
      `pipeline-editor.html`; architecture change →
      `docs/architecture.md`; any merge to `main` → `CHANGELOG.md`
      `[Unreleased]`.
- [ ] `FILE_MAP.md` updated when files or directories were added, moved,
      or removed (package map under
      `app/src/main/java/ai/agent/android/FILE_MAP.md` and/or the root
      `FILE_MAP.md` for top-level changes).
- [ ] Commit messages follow
      [Conventional Commits](https://www.conventionalcommits.org/) (see
      `CONTRIBUTING.md` for the accepted prefixes).
