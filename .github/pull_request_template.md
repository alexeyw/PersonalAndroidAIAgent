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

- [ ] `./gradlew check` passes locally (detekt, ktlint, lintFullDebug +
      lintFossDebug, testFullDebugUnitTest + testFossDebugUnitTest,
      koverVerifyFullDebug).
- [ ] If `app/src/androidTest/` changed: the instrumented sources compile
      (`./gradlew :app:compileFullDebugAndroidTestKotlin` — its own Gradle
      invocation, not appended to `check`) and the suite was run on a
      device or emulator (`./gradlew connectedFullDebugAndroidTest`).
- [ ] Tests added or updated for the changed code (target 100% logic
      coverage in new `domain` / `data` code; see `docs/testing.md`).
- [ ] Public documentation updated where relevant (see the *Pull
      requests* section in
      [`CONTRIBUTING.md`](https://github.com/alexeyw/knotwork/blob/main/CONTRIBUTING.md)). Typical
      triggers:
      user-visible feature → `README.md` + `docs/user-guide.md`; new
      `NodeType` / `Tool` / prompt variable → `docs/extending.md` and
      `pipeline-editor.html`; architecture change →
      `docs/architecture.md`; any merge to `main` → `CHANGELOG.md`
      `[Unreleased]`.
- [ ] `./gradlew :app:generateFileMap` run and its result committed when
      Kotlin files or directories were added, moved, or removed. It owns
      the `app/src/main`, `app/src/test`, `app/src/androidTest` and
      `catalog/` maps; the root `FILE_MAP.md` is hand-written and covers
      top-level changes.
- [ ] Commit messages follow
      [Conventional Commits](https://www.conventionalcommits.org/) (see
      `CONTRIBUTING.md` for the accepted prefixes).
