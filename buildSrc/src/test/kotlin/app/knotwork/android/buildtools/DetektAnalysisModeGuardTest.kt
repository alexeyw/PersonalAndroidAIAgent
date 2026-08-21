package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests for [DetektAnalysisModeGuard].
 *
 * The regression pinned down here is the one the guard was written for: a rule
 * that requires type resolution, configured into the light-mode detekt run,
 * where detekt skips it without a word. The fixtures use the exact rules that
 * were affected — `LongParameterList` was in `config/detekt/detekt.yml` for the
 * whole life of the gate and never fired once.
 *
 * Run with `./gradlew -p buildSrc test`.
 */
class DetektAnalysisModeGuardTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    // ── activeRuleIds ────────────────────────────────────────────────────────

    @Test
    fun `given a rule with active true when parsing then it counts as activated`() {
        // Given
        val config = """
            |complexity:
            |  active: true
            |  LongParameterList:
            |    active: true
            |    allowedFunctionParameters: 10
        """.trimMargin()

        // When
        val active = DetektAnalysisModeGuard.activeRuleIds(config)

        // Then
        assertEquals(setOf("LongParameterList"), active)
    }

    @Test
    fun `given a rule with active false when parsing then it is not activated`() {
        // Given
        val config = """
            |exceptions:
            |  active: true
            |  TooGenericExceptionCaught:
            |    active: false
            |  SwallowedException:
            |    active: false
        """.trimMargin()

        // When
        val active = DetektAnalysisModeGuard.activeRuleIds(config)

        // Then
        assertTrue("no rule should be reported as active: $active", active.isEmpty())
    }

    @Test
    fun `given a rule set switched off when parsing then its rules are not activated`() {
        // Given — the shape `detekt-type-resolution.yml` uses for the sets it
        // does not need. A rule named under a dead rule set never runs, so
        // reporting it would be a false alarm.
        val config = """
            |style:
            |  active: false
            |  UnusedImport:
            |    active: true
        """.trimMargin()

        // When
        val active = DetektAnalysisModeGuard.activeRuleIds(config)

        // Then
        assertTrue("a disabled rule set must take its rules with it: $active", active.isEmpty())
    }

    @Test
    fun `given a rule named without an active key when parsing then it counts as activated`() {
        // Given — layered on the bundled defaults, a named rule with no `active`
        // key keeps whatever state it inherits, which is usually on. Erring
        // towards activated can only make the guard notice more, never less.
        val config = """
            |style:
            |  active: true
            |  MaxLineLength:
            |    maxLineLength: 120
        """.trimMargin()

        // When
        val active = DetektAnalysisModeGuard.activeRuleIds(config)

        // Then
        assertEquals(setOf("MaxLineLength"), active)
    }

    @Test
    fun `given commented-out rules and blank lines when parsing then they are ignored`() {
        // Given — the real config carries a rationale comment above most rules,
        // and this project's convention is to leave a note where a rule moved
        // out. A comment must never read as a declaration.
        val config = """
            |complexity:
            |  active: true
            |
            |  # `LongParameterList` requires the Analysis API — it lives in
            |  # `detekt-type-resolution.yml`, not here.
            |  TooManyFunctions:
            |    active: true # kept here: no type resolution needed
        """.trimMargin()

        // When
        val active = DetektAnalysisModeGuard.activeRuleIds(config)

        // Then
        assertEquals(setOf("TooManyFunctions"), active)
    }

    @Test
    fun `given the top-level config block when parsing then its keys are not read as rules`() {
        // Given — `config:` is a settings block, not a rule set, and its keys are
        // lowercase. The PascalCase rule-key shape is what keeps them apart.
        val config = """
            |config:
            |  validation: true
            |  warningsAsErrors: false
            |  checkExhaustiveness: false
        """.trimMargin()

        // When
        val active = DetektAnalysisModeGuard.activeRuleIds(config)

        // Then
        assertTrue("settings keys must not be read as rules: $active", active.isEmpty())
    }

    // ── scan ─────────────────────────────────────────────────────────────────

    @Test
    fun `given a type-resolution rule in the light config when scanning then it is reported`() {
        // Given — verbatim the defect: the rule was configured, tuned, commented,
        // and silently skipped on every build.
        val lightModeRules = setOf("LongParameterList", "TooManyFunctions", "MagicNumber")
        val analysisApiRules = setOf("LongParameterList", "UnusedImport")

        // When
        val violations = DetektAnalysisModeGuard.scan(
            lightModeRules = lightModeRules,
            analysisApiRules = analysisApiRules,
            configPath = "config/detekt/detekt.yml",
        )

        // Then
        assertEquals(1, violations.size)
        assertEquals("LongParameterList", violations.single().ruleId)
        assertEquals("config/detekt/detekt.yml", violations.single().configPath)
        assertTrue(violations.single().format().contains("requires type resolution"))
    }

    @Test
    fun `given several offenders when scanning then they are reported in a stable order`() {
        // Given
        val lightModeRules = setOf("UnusedPrivateProperty", "LongParameterList", "UnusedImport")
        val analysisApiRules = setOf("UnusedImport", "LongParameterList", "UnusedPrivateProperty")

        // When
        val violations = DetektAnalysisModeGuard.scan(
            lightModeRules = lightModeRules,
            analysisApiRules = analysisApiRules,
            configPath = "config/detekt/detekt.yml",
        )

        // Then — sorted, so a failure message does not reshuffle between runs
        assertEquals(
            listOf("LongParameterList", "UnusedImport", "UnusedPrivateProperty"),
            violations.map { it.ruleId },
        )
    }

    @Test
    fun `given only light-mode rules when scanning then nothing is reported`() {
        // Given
        val lightModeRules = setOf("TooManyFunctions", "MagicNumber", "UnusedPrivateClass")
        val analysisApiRules = setOf("LongParameterList", "UnusedImport", "UnusedPrivateProperty")

        // When
        val violations = DetektAnalysisModeGuard.scan(
            lightModeRules = lightModeRules,
            analysisApiRules = analysisApiRules,
            configPath = "config/detekt/detekt.yml",
        )

        // Then
        assertTrue("a sound configuration must produce no violations: $violations", violations.isEmpty())
    }

    // ── rulesRequiringAnalysisApi ────────────────────────────────────────────

    @Test
    fun `given rule classes in a jar when scanning then only those naming the marker are returned`() {
        // Given — a stand-in for detekt's rule jar. The marker interface appears
        // in the class file of a rule that implements it and nowhere else.
        val jar = writeJar(
            "dev/detekt/rules/complexity/LongParameterList.class" to classBytesReferencing(
                "dev/detekt/api/RequiresAnalysisApi",
            ),
            "dev/detekt/rules/complexity/LongMethod.class" to classBytesReferencing("dev/detekt/api/Rule"),
        )

        // When
        val requiresAnalysisApi = DetektAnalysisModeGuard.rulesRequiringAnalysisApi(listOf(jar))

        // Then
        assertEquals(setOf("LongParameterList"), requiresAnalysisApi)
    }

    @Test
    fun `given nested and non-rule classes when scanning then they are skipped`() {
        // Given — synthetic nested classes carry their outer class's constant
        // pool, so counting them would report `LongParameterList` several times
        // under mangled names; classes outside the rule packages mention the
        // marker interface as API surface without implementing it.
        val jar = writeJar(
            "dev/detekt/rules/complexity/LongParameterList\$Companion.class" to
                classBytesReferencing("dev/detekt/api/RequiresAnalysisApi"),
            "dev/detekt/api/RequiresAnalysisApi.class" to
                classBytesReferencing("dev/detekt/api/RequiresAnalysisApi"),
            "dev/detekt/rules/style/UnusedImport.class" to
                classBytesReferencing("dev/detekt/api/RequiresAnalysisApi"),
        )

        // When
        val requiresAnalysisApi = DetektAnalysisModeGuard.rulesRequiringAnalysisApi(listOf(jar))

        // Then
        assertEquals(setOf("UnusedImport"), requiresAnalysisApi)
    }

    @Test
    fun `given a non-jar file on the classpath when scanning then it is skipped`() {
        // Given — the `detekt` configuration resolves directories and metadata
        // alongside jars; opening one as a zip would abort the whole scan.
        val notAJar = tempFolder.newFile("detekt-config.yml").apply { writeText("style:\n  active: true\n") }
        val jar = writeJar(
            "dev/detekt/rules/style/UnusedImport.class" to
                classBytesReferencing("dev/detekt/api/RequiresAnalysisApi"),
        )

        // When
        val requiresAnalysisApi = DetektAnalysisModeGuard.rulesRequiringAnalysisApi(listOf(notAJar, jar))

        // Then
        assertEquals(setOf("UnusedImport"), requiresAnalysisApi)
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    /**
     * Writes a jar holding the given entries and returns it. Content is opaque
     * bytes — the guard searches raw bytes rather than decoding a class file, so
     * a fixture only has to carry the string at the right place.
     */
    private var jarCount = 0

    private fun writeJar(vararg entries: Pair<String, ByteArray>): File {
        val jar = tempFolder.newFile("fixture-${jarCount++}.jar")
        ZipOutputStream(jar.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return jar
    }

    /**
     * Class-file stand-in: a byte blob with a binary type name embedded in it,
     * the way a real constant pool carries the interfaces a class implements.
     */
    private fun classBytesReferencing(binaryName: String): ByteArray =
        byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()) +
            binaryName.toByteArray(Charsets.UTF_8) +
            byteArrayOf(0, 1, 2, 3)
}
