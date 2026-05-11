# Android AI Agent

On-device autonomous AI agent for Android. Full project description and
architecture are documented in `project_docs/DESCRIPTION.md`.

## Firebase Crashlytics setup (opt-in)

Crash reporting is **off by default**. The user enables it from
`Settings → Privacy → Send anonymous crash reports`. Firebase
auto-collection is suppressed via manifest meta-data
(`firebase_crashlytics_collection_enabled = false`,
`firebase_analytics_collection_enabled = false`) and the runtime
gate inside `CrashReportingRepository` short-circuits every call until
the user opts in.

### Replacing the placeholder `google-services.json`

A placeholder `app/google-services.json` is checked into the repository
so fresh clones and CI builds succeed out of the box. It contains a
synthetic project id and API key — no real Firebase project is wired
behind it.

To send crash reports to a real Firebase project:

1. Create a Firebase project at <https://console.firebase.google.com/>.
2. Register an Android app with `applicationId = ai.agent.android`.
3. Download the generated `google-services.json` from the Firebase
   console.
4. Overwrite `app/google-services.json` with the downloaded file.

Do **not** commit a real `google-services.json` into a public fork —
treat it as a deployment artefact. The placeholder file is safe to
keep committed because its credentials do not authenticate to any
project.

### What gets uploaded (only when opt-in is ON)

- Stack traces of fatal and non-fatal exceptions.
- Device model and Android version.
- App version.
- Custom keys set by the agent: `active_pipeline_id`, `active_model`.

No chat messages, prompts, long-term memory, API keys, or other
personal content are ever uploaded — the on-device-first privacy
posture is preserved.
