# Directory Map: app/src/androidTest/java/app/knotwork/android

This file maps the contents of the main application test package.

- `AppFunctionsEndToEndTest.kt` - End-to-end coverage of the AppFunctions IPC path through the production Hilt graph.
- `ExampleInstrumentedTest.kt` - Example instrumentation test.
- `data/`
  - `local/`
    - `AppDatabaseMigrationTest.kt` - Regression suite invoking every `MIGRATION_*` declared on `AppDatabase` against a real on-disk SQLite file (per-step + chained v17→v23 run).
    - `dao/`
      - `ChatDaoTest.kt` - Tests for `ChatDao` — chat_messages + chat_sessions tables.
      - `LocalModelDaoTest.kt` - Tests for `LocalModelDao`.
      - `MemoryDaoTest.kt` - Tests for `MemoryDao`.
      - `PipelineDaoTest.kt` - Tests for `PipelineDao` — pipelines / pipeline_nodes / pipeline_connections + `NodeContextConfig` TypeConverter round-trip + FK cascade.
      - `PromptTemplateDaoTest.kt` - Tests for `PromptTemplateDao`.
      - `TraceStepDaoTest.kt` - Tests for `TraceStepDao` — per-session ordering + FK cascade from `chat_sessions`.
- `presentation/`
  - `ui/`
    - `chat/`
      - `home/`
        - `ChatHomeOverflowMenuTest.kt` - Tests for the chat home overflow menu.
        - `ClarificationIntegrationTest.kt` - Tests for the Clarification HITL flow.
        - `HitlIntegrationTest.kt` - Tests for the HITL approval / deny flow.
    - `navigation/`
      - `AppShellNavigationTest.kt` - Tests for `AppShellScaffold` + nav-graph wiring.
    - `orchestrator/`
      - `presets/`
        - `PresetCategoryBadgeLayoutTest.kt` - Layout guard for the preset category badge on both preset rows: asserts the badge sits entirely inside its row when the preset name is long. Instrumented rather than JVM because the same assertions under a Robolectric-hosted composition reported nonsense (a 62 dp root for a full-width row) and stayed green with every fix reverted. Measures **position**, not height — a starved badge that has been pushed past the right edge is the same bug one constraint later, and height does not see it.
    - `tools/`
      - `ToolDetailScreenTest.kt` - Tests for `ToolDetailScreen`.
      - `ToolsScreenTest.kt` - Tests for `ToolsScreen`.
- `FILE_MAP.md` - This file mapping the current directory structure.
