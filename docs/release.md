# Release engineering

This document captures how the on-device agent is built, signed, and shipped
for distribution. It is intentionally a single-page playbook — every step
should be runnable verbatim from a clean checkout.

## 1. Variants at a glance

The app has two build types (`debug` / `release`) and a **`distribution`
product-flavour dimension** with two flavours, so a full build variant is
`<flavour><BuildType>` (e.g. `fullRelease`, `fossDebug`):

| Flavour | Crash reporting          | Google/Firebase dependency | Intended channel        |
|---------|--------------------------|----------------------------|-------------------------|
| `full`  | Firebase Crashlytics (opt-in) | yes (`fullImplementation`) | Play Store / direct APK |
| `foss`  | none (no-op)             | **none** — provably absent | **F-Droid** (§8)        |

`full` is the default flavour (`isDefault = true`), so plain commands and
Android Studio's variant selector resolve to it.

| Build type | Minified | Resource-shrunk | Signing                             |
|------------|----------|------------------|-------------------------------------|
| `debug`    | no       | no               | Android debug key                   |
| `release`  | yes (R8) | yes              | Release keystore (debug fallback) * |

\* See §3 below. The `release` build type uses a dedicated `signingConfigs.release`
when its credentials are provisioned via `local.properties` or environment
variables; when they are absent it **falls back to the debug keystore** so a
clean checkout still builds. A debug-signed `release` artefact is suitable for
sideloading on a developer device but **not** acceptable for Play Store upload.

## 2. Building locally

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Universal arm64-v8a APK (used for sideload smoke-tests).
./gradlew :app:assembleFullRelease   # Play / direct-APK build (Firebase)
./gradlew :app:assembleFossRelease   # F-Droid build (no Google/Firebase) — see §8

# Android App Bundle (the Play Store upload format; full flavour only).
./gradlew :app:bundleFullRelease
```

Outputs (the flavour name is part of the path):

- `app/build/outputs/apk/full/release/app-full-release.apk`
- `app/build/outputs/apk/foss/release/app-foss-release.apk`
- `app/build/outputs/bundle/fullRelease/app-full-release.aab`

The release variant strips every ABI except `arm64-v8a`
(`build.gradle.kts → buildTypes.release.ndk.abiFilters`). With
`minSdk = 36` (Android 16) every supported device is 64-bit; shipping
`armeabi-v7a` / `x86` / `x86_64` would add ~65 MB for zero benefit.
Emulator-driven smoke-tests should use the `debug` variant instead, which
keeps every ABI.

## 3. Signing

The `release` buildType is wired to a dedicated `signingConfigs.release` whose
credentials are resolved at configuration time from `local.properties` first
and environment variables second (`build.gradle.kts → resolveReleaseSigning()`).
The recognised keys are:

| Key                         | Meaning                                   |
|-----------------------------|-------------------------------------------|
| `RELEASE_KEYSTORE_PATH`     | Path to the keystore, relative to repo root. |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore (store) password.                |
| `RELEASE_KEY_ALIAS`         | Alias of the signing key in the keystore. |
| `RELEASE_KEY_PASSWORD`      | Password protecting the signing key.      |

If **any** key is missing/blank, or the resolved keystore file does not exist,
`resolveReleaseSigning()` returns `null` and the `release` buildType falls back
to the debug keystore:

```kotlin
signingConfig = signingConfigs.findByName("release")
    ?: signingConfigs.getByName("debug")
```

So a clean checkout without a provisioned key still produces a (debug-signed)
release artefact — configuration never fails for the lack of a key. The
credential values are never committed: `.gitignore` blocks every keystore
extension (`*.jks` / `*.keystore` / `*.p12` …) plus `local.properties`,
`keystore.properties`, and `secrets.properties`.

### Current distribution state

Until a maintainer provisions a real release keystore, GitHub-Release builds
are debug-signed via the fallback above. This is acceptable for the pre-release
sideload channel because:

- the debug signing identity is well-known and not a secret;
- sideloading does not require a stable signing identity across releases
  (Android only enforces that the *signer* matches the previously-installed
  copy, and a clean install is acceptable until v1.0);
- a leaked debug-keystore signature cannot impersonate a Play Store
  upload — Play Store rejects debug-signed AABs.

The first release-signed build will use a different signer than the historical
debug-signed builds, so an in-place upgrade from a debug-signed install will be
rejected with a signature mismatch — see the *Pre-release notice* in
[README.md](../README.md). Plan for a clean install at that transition.

### Generating the release keystore

The keystore is created **outside VCS** on the maintainer's machine:

```bash
keytool \
    -genkeypair \
    -v \
    -keystore release.keystore \
    -keyalg RSA \
    -keysize 4096 \
    -validity 36500 \
    -alias agent-release
# Distinguished Name prompt:
#   CN: Knotwork
#   OU: Releases
#   O: <your org or personal name>
#   L / ST / C: as appropriate
```

Move the resulting `release.keystore` into `app/` (covered by `.gitignore`)
and record the path + passwords + alias in `local.properties`:

```properties
RELEASE_KEYSTORE_PATH=app/release.keystore
RELEASE_KEYSTORE_PASSWORD=••••••
RELEASE_KEY_ALIAS=agent-release
RELEASE_KEY_PASSWORD=••••••
```

The Play Store also requires App Signing by Google Play — upload the keystore
once during the first release, then Play Store rotates the in-app signing
certificate on every subsequent release.

### Provisioning the keystore in CI

CI builds read the same four keys from the environment, so they are injected
as **repository secrets** rather than checked-in files. Store the keystore
itself as a base64-encoded secret and materialise it before the build:

```yaml
# GitHub Actions — release-signing step (illustrative).
- name: Decode release keystore
  run: echo "${{ secrets.RELEASE_KEYSTORE_BASE64 }}" | base64 --decode > app/release.keystore
- name: Assemble signed release
  env:
    RELEASE_KEYSTORE_PATH: app/release.keystore
    RELEASE_KEYSTORE_PASSWORD: ${{ secrets.RELEASE_KEYSTORE_PASSWORD }}
    RELEASE_KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
    RELEASE_KEY_PASSWORD: ${{ secrets.RELEASE_KEY_PASSWORD }}
  run: ./gradlew :app:bundleRelease
```

The default `check.yml` gate does **not** build release artefacts, so it needs
no signing secrets; wire the snippet above only into a dedicated release/publish
workflow.

### Verifying the signature

After a signed build, confirm the artefact carries the expected certificate
(not the debug one) with `apksigner` from the Android SDK build-tools:

```bash
apksigner verify --print-certs --verbose \
    app/build/outputs/apk/release/app-release.apk
```

Check the printed `Signer #1 certificate DN` matches the keystore's
Distinguished Name (e.g. `CN=Knotwork`) and that the SHA-256
fingerprint matches the one Play Console registered for the app. The debug
keystore prints `CN=Android Debug`, so this is the quickest way to catch an
accidental fallback to debug signing.

## 4. R8 minification + resource shrinking

Enabled on `release` (`isMinifyEnabled = true`, `isShrinkResources = true`).
Keep-rules live in `app/proguard-rules.pro`, organised by subsystem with a
KDoc-style banner per section explaining *why* the rule is needed. The
short version:

| Subsystem                | Why it needs explicit keeps                                  |
|--------------------------|---------------------------------------------------------------|
| `kotlin.Metadata`        | Used by every reflection-driven library (Koog, serialization). |
| `kotlinx.serialization`  | `$$serializer` synthetic + `serializer(...)` lookup.          |
| Gson                     | Round-trip of `app_functions_*.xml` + chat-export payloads.   |
| MediaPipe / LiteRT       | JNI bindings reach Java classes by name — R8 has no AST view. |
| SQLCipher                | `net.zetetic:sqlcipher-android` loads its `.so` by reflection. |
| Koog                     | Heavy reflection over node / tool / pipeline graph definitions.|
| Ktor                     | Transitive HTTP layer underneath every Koog cloud client.     |
| AppFunctions             | `*_AppFunctionInventory` / `*_AppFunctionInvoker` KSP outputs are loaded by `androidx.appfunctions` via reflection. |
| Hilt                     | Aggregated component classes occasionally over-shrunk on full mode. |
| Room                     | `*_Impl` DAOs / database instantiated reflectively.           |
| OpenTelemetry incubator  | Optional symbols referenced from Koog's OTel logging plumbing — kept under `-dontwarn` since the runtime path is never hit. |

If R8 starts stripping something at runtime, drop a new section into
`proguard-rules.pro` rather than scattering rules across the file, and
include a one-line comment on the symptom that triggered the keep.

## 5. APK size breakdown — v0.4.0

> The numbers below are a **point-in-time snapshot measured on v0.4.0** and
> have not been re-measured since; treat them as indicative of the size
> profile rather than the current byte counts.

`app-release.apk` measures **59.6 MiB on disk** (62,465,437 bytes;
~59.9 MiB uncompressed inside the APK container). The 30 MB target from
the original phase plan is **not achievable** with the current dependency
set: native libraries + the bundled universal-sentence-encoder embedding
model already account for ~40 MB before a single line of agent code is
included.

Top contributors (uncompressed bytes inside the APK, arm64-v8a only):

| Entry                                          | Size   | Notes                                     |
|------------------------------------------------|--------|-------------------------------------------|
| `lib/arm64-v8a/liblitertlm_jni.so`             | 14.2 MB | LiteRT-LM tokenizer + runtime JNI.        |
| `classes.dex`                                  | 11.1 MB | App + Koog agents (post-R8).              |
| `lib/arm64-v8a/libmediapipe_tasks_jni.so`      | 10.0 MB | MediaPipe Tasks (text embedding host).    |
| `classes2.dex`                                 |  7.9 MB | App + Koog agents (overflow DEX).         |
| `assets/universal_sentence_encoder.tflite`     |  5.8 MB | Bundled embedding model (long-term memory). |
| `lib/arm64-v8a/libLiteRt.so`                   |  4.8 MB | LiteRT base runtime.                      |
| `lib/arm64-v8a/libLiteRtClGlAccelerator.so`    |  2.6 MB | LiteRT GPU delegate.                      |
| `lib/arm64-v8a/libsqlcipher.so`                |  2.0 MB | SQLCipher engine.                         |
| Everything else combined                       |  ~1.4 MB | DataStore native, baseline profiles, fonts, resources, AndroidManifest. |

What we already did to keep this in check:

- **arm64-v8a only.** Other ABIs would more than double the artefact.
- **R8 full mode + resource shrinking.** Saves ~2 MB on DEX vs. unminified.
- **Strip Jansi non-Android natives.** `org/fusesource/jansi/internal/native/{Windows,Mac,Linux,FreeBSD}/**` and `META-INF/native-image/jansi/**` are dropped via the `android.packaging.resources` exclude list — Jansi ships through Koog's logger and only its ANSI-escape rendering runs on JVM hosts.

Future wins (left out of scope for now):

- **Move the universal-sentence-encoder model to a first-run download.** Wins ~6 MB; complicates first-run UX. Tracked separately.
- **Per-ABI dynamic feature module for LiteRT GPU.** Only devices that actually use the GPU delegate would download `libLiteRtClGlAccelerator.so`. Wins ~2.6 MB; requires App Bundle delivery (already in place) plus split-install plumbing.
- **Promote Koog clients to optional dynamic features.** Cloud LLM clients are bundled today; ~1 MB per provider could move out for users who only use the local model.

## 6. App Bundle build (Play Store upload)

```bash
./gradlew :app:bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab  (~37 MiB)
```

The AAB is the format Play Store wants; per-device APKs delivered through
Play Store are smaller than `app-release.apk` because they only ship the
ABI + density resources the target device needs.

To inspect what Play Store would deliver to a specific device:

```bash
# bundletool is in the Android SDK cmdline-tools.
bundletool build-apks \
    --bundle=app-release.aab \
    --output=app.apks \
    --mode=universal
bundletool get-size total --apks=app.apks
```

## 7. Quality gate before release

```bash
./gradlew check :app:lintFullRelease :app:bundleFullRelease
```

- `check` aggregates `detekt`, `ktlintCheck`, lint, the unit-test suite for
  **both** flavours (`testFullDebugUnitTest` + `testFossDebugUnitTest`), and
  `koverVerifyFullDebug` (coverage is measured on the representative `full`
  variant; the flavours share every measured source).
- `lintFullRelease` re-runs lint on the release configuration (catches issues
  hidden by debug-only resources).
- `bundleFullRelease` confirms R8 + resource shrinking still produce a valid AAB.

The integration PR gates on the same three commands in CI, plus the
manual smoke test on the reference device described in
[`testing.md`](testing.md) § *What the automated gate does NOT cover*.

## 8. FOSS / F-Droid build

F-Droid requires a build that is **free of proprietary dependencies and
build-time tooling**. The `foss` flavour is exactly that build, and the
`full` flavour is everything else:

```bash
# F-Droid-compatible release APK — no Google/Firebase anywhere in the graph.
./gradlew :app:assembleFossRelease
```

How the freedom is guaranteed:

- **No crash-reporting / telemetry SDK.** Firebase Crashlytics + Analytics are
  declared as `fullImplementation(...)` in `app/build.gradle.kts`, so the
  Crashlytics/Analytics SDKs (and their `firebase-common` /
  `firebase-installations` / `play-services` transitive graph) are on the `full`
  classpath only. Verify the `foss` classpath carries none of them:

  ```bash
  ./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath \
      | grep -iE 'firebase-crashlytics|firebase-analytics|firebase-common|firebase-installations|play-services'
  # expected: no output
  ```

- **No proprietary build plugin.** The `com.google.gms.google-services` and
  `com.google.firebase.crashlytics` Gradle plugins are applied *conditionally* —
  skipped whenever **every** requested Gradle task is `Foss`-scoped (the
  `fossOnlyBuild` guard in `app/build.gradle.kts`). An `assembleFossRelease`
  invocation therefore never loads them and never reads `google-services.json`.
  (Mixed invocations such as `./gradlew check`, which also build/test the `full`
  variant, do apply the plugins — the `full` variant needs the generated
  `google_app_id` for its Robolectric tests. The `foss` artifact stays clean
  because the F-Droid build is `foss`-only.)

- **No crash collector in the app.** The `foss` flavour binds a no-op
  `CrashReportingRepository` (`app/src/foss/.../NoOpCrashReportingRepository.kt`)
  that records and transmits nothing, and the in-app crash-reporting consent
  toggle is hidden (driven by `BuildConfig.CRASH_REPORTING_AVAILABLE = false`).
  The `full` flavour keeps the Firebase-backed implementation under
  `app/src/full/`.

The `foss` and `full` flavours share **all** `main` sources; only the
crash-reporting implementation, its Hilt module, and the Crashlytics/Analytics
manifest meta-data differ between the two source sets. This keeps the two builds
behaviourally identical apart from crash reporting.

### Known residuals (out of scope for the crash-reporting split)

Removing Crashlytics/Analytics is the blocker this flavour split targets. Two
larger items remain before the `foss` build is fully F-Droid-*eligible* — they
are inherent to the product, not to crash reporting, and are tracked separately:

- **`com.google.firebase:firebase-encoders{,-json,-proto}`** still appear on the
  `foss` classpath. They are **not** the telemetry SDK — they are Apache-2.0
  serialization utilities pulled transitively by MediaPipe
  (`com.google.mediapipe:tasks-text` → `com.google.android.datatransport` →
  `firebase-encoders`), present in **both** flavours. They could be excluded from
  the `foss` configuration, but MediaPipe declares `datatransport` for its own
  logging, so an exclusion needs an on-device smoke of the embeddings / text
  tasks before it can be trusted.
- **Proprietary on-device inference binaries.** `com.google.ai.edge.litertlm`
  and `com.google.mediapipe:tasks-text` ship prebuilt, non-free native
  libraries. The official F-Droid repo cannot build these from source, so true
  inclusion there requires either a free inference backend or F-Droid's
  prebuilt-binary allowance. This is a product-architecture decision well beyond
  the crash-reporting split.

### Reproducible builds

F-Droid prefers builds it can reproduce bit-for-bit from source. Two known
sources of non-determinism in this project:

- `BuildConfig.GIT_COMMIT_DATE_EPOCH_MS` is stamped from
  `System.currentTimeMillis()` at configuration time (see `defaultConfig` in
  `app/build.gradle.kts`). For a reproducible F-Droid build this should be
  pinned to the commit timestamp (e.g. `SOURCE_DATE_EPOCH`) by the F-Droid
  recipe rather than read from the wall clock.
- `BuildConfig.GIT_SHA` resolves the short commit SHA, which is deterministic
  for a given checkout.

The `foss` release is otherwise a standard R8-minified arm64-v8a build (§4); the
F-Droid build recipe should disable any signing config so F-Droid applies its
own signature.
