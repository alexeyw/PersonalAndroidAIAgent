/*
 * Build-logic module hosting the pure-Kotlin generators used by the app build.
 *
 * Currently exposes [app.knotwork.android.buildtools.BrowserEditorConstantsGenerator],
 * which derives the browser pipeline-editor's mirrored constants
 * (`NODE_TYPES`, `PROMPT_VARIABLES`, `AVAILABLE_TOOLS`, `DEFAULT_SYSTEM_PROMPTS`)
 * straight from the Android domain sources so `pipeline-editor.html` can no
 * longer silently drift from `domain/`. The two Gradle tasks that drive it
 * (`generateBrowserEditorConstants` / `verifyBrowserEditorConstants`) are
 * registered in `app/build.gradle.kts`.
 *
 * The `embedded-kotlin` plugin compiles this module with the Kotlin version
 * bundled inside Gradle — no external Kotlin plugin resolution, so the logic
 * here stays independent of the app's Kotlin/AGP toolchain.
 *
 * The generator logic is plain string-in / string-out, so it is unit-tested
 * with JUnit. Those tests are not part of the root `./gradlew check` graph
 * (buildSrc is a separate build); run them with `./gradlew -p buildSrc test`.
 */
plugins {
    `embedded-kotlin`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
