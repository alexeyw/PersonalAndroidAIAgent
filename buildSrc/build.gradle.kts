/*
 * Build-logic module hosting the pure-Kotlin generators used by the app build.
 *
 * Hosts the documentation generators and the build-time checkers. Each derives
 * a document (or a verdict about one) from the sources it describes, so the
 * document cannot silently drift from the code: the browser pipeline-editor's
 * mirrored constants, the settings reference table, the external-automation
 * reference, the node cookbook, and the `FILE_MAP.md` source trees. The full
 * roster and what each one gates lives in `docs/static-analysis.md`.
 *
 * The Gradle tasks that drive them are registered in `app/build.gradle.kts`.
 * Most are ad-hoc task blocks; the file-map pair is a pair of typed task
 * classes ([app.knotwork.android.buildtools.GenerateFileMapTask] /
 * [app.knotwork.android.buildtools.VerifyFileMapTask]) declared here, which is
 * why this module depends on `gradleApi()`.
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
    implementation(gradleApi())
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
