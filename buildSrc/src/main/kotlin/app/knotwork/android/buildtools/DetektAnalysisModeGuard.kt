package app.knotwork.android.buildtools

import java.io.File
import java.util.zip.ZipFile

/**
 * Guard that keeps a detekt rule from being configured into a run that cannot
 * execute it.
 *
 * Detekt 2.x splits its rules by what they need to answer. A rule that only
 * walks the syntax tree runs anywhere; a rule that needs resolved types
 * implements `dev.detekt.api.RequiresAnalysisApi` and can only run in the
 * `full` analysis mode. The failure this guard exists for is what happens in
 * between: when such a rule is configured into a `light`-mode run, detekt does
 * not refuse it, warn about it, or mention it in the report. It skips it. The
 * build stays green, the report stays empty, and the rule checks nothing —
 * indistinguishable, from the outside, from a clean codebase.
 *
 * This project paid for that distinction. Four rules sat in
 * `config/detekt/detekt.yml` — `LongParameterList`, `UnusedImport`,
 * `UnusedPrivateFunction`, `UnusedPrivateProperty` — each with a comment
 * explaining the threshold chosen for it, and none of them had ever run. The
 * config was correct, the plugin was wired, the gate was green, and an
 * 18-parameter constructor passed it. When the rules were finally executed
 * under type resolution they had 52 findings waiting, including 19 unused
 * imports and a triage the config comment described as already done.
 *
 * So the invariant is mechanical now: **a rule this project explicitly
 * activates in the light-mode config may not be one that requires the Analysis
 * API.** Such rules belong in `config/detekt/detekt-type-resolution.yml`, which
 * is run by the type-resolution tasks.
 *
 * **Scope, stated rather than implied.** The guard judges the rules the
 * repository *declares*, not the ones detekt's bundled defaults switch on
 * underneath them (`buildUponDefaultConfig = true`). Those defaults do include
 * rules requiring the Analysis API, and the light-mode task skips those in the
 * same silence — running the whole strict config under type resolution was
 * measured at 296 findings, 263 of them from rules this project never asked
 * for. Adopting them is a deliberate piece of work, not something a guard
 * should force by failing the build. What the guard protects is the narrower,
 * and the only enforceable, promise: a rule the project went to the trouble of
 * naming and tuning actually runs.
 *
 * The interesting half is a pure `String -> Set<String>` / `Set<String> ->
 * List<Violation>` transform with no file-system access, so it is unit-tested
 * directly. Only [rulesRequiringAnalysisApi] touches disk, and it reads
 * detekt's own jars — the same artefacts the analysis runs from — rather than a
 * list pinned in this file, so a detekt upgrade that moves a rule across the
 * boundary is caught by the next build instead of by nobody.
 */
object DetektAnalysisModeGuard {

    /**
     * Binary name of the marker interface a detekt rule implements when it can
     * only run under type resolution, in the internal form used by the class
     * file constant pool.
     */
    private const val ANALYSIS_API_MARKER = "dev/detekt/api/RequiresAnalysisApi"

    /**
     * Path prefix of detekt's own bundled rule implementations inside its jars.
     * Restricting the scan to it keeps the match away from the API and tooling
     * artefacts, which mention the marker interface without implementing it.
     */
    private const val RULE_CLASS_PREFIX = "dev/detekt/rules/"

    /**
     * A rule that is configured where it cannot run.
     *
     * @property ruleId Simple name of the detekt rule, as written in the YAML.
     * @property configPath Path of the config file that activates it, for the
     *   failure message.
     */
    data class Violation(val ruleId: String, val configPath: String) {

        /** One-line rendering for the aggregated build-failure message. */
        fun format(): String = "  - $ruleId (activated in $configPath) requires type resolution"
    }

    /**
     * Reports every rule that [lightModeRules] activates and
     * [analysisApiRules] marks as needing type resolution — i.e. every rule the
     * light-mode detekt run would silently skip.
     *
     * @param lightModeRules Rule ids explicitly activated by the light-mode
     *   config, as returned by [activeRuleIds].
     * @param analysisApiRules Rule ids that require the Analysis API, as
     *   returned by [rulesRequiringAnalysisApi].
     * @param configPath Path of the light-mode config, echoed into each
     *   violation so the message names the file to edit.
     * @return The offending rules, sorted for a stable message; empty when the
     *   configuration is sound.
     */
    fun scan(
        lightModeRules: Set<String>,
        analysisApiRules: Set<String>,
        configPath: String,
    ): List<Violation> = lightModeRules.intersect(analysisApiRules)
        .sorted()
        .map { Violation(ruleId = it, configPath = configPath) }

    /**
     * Extracts the rule ids a detekt YAML config explicitly activates.
     *
     * The config shape detekt uses is two levels deep and fixed: a rule-set key
     * at column 0, a PascalCase rule key indented two spaces under it, and the
     * rule's properties indented four. That regularity is why this reads the
     * file with a line scanner instead of adding a YAML parser to `buildSrc`,
     * which is otherwise dependency-free by design.
     *
     * A rule counts as activated unless it says `active: false`. Detekt applies
     * the same default — a rule named with no `active` key keeps whatever state
     * it inherits, which for a config layered on the bundled defaults is
     * usually on — and erring towards "activated" is also the safe direction
     * here: it can only make the guard notice more, never less.
     *
     * A rule set switched off wholesale (`active: false` directly under the
     * rule-set key) takes its rules with it, so nothing under it is reported.
     *
     * @param configYaml Full text of the config file.
     * @return Simple rule names, e.g. `LongParameterList`.
     */
    fun activeRuleIds(configYaml: String): Set<String> {
        val active = mutableSetOf<String>()
        var ruleSetEnabled = true
        var currentRule: String? = null
        for (rawLine in configYaml.lineSequence()) {
            val line = rawLine.substringBefore('#').trimEnd()
            if (line.isBlank()) continue
            when {
                RULE_SET_KEY.matches(line) -> {
                    ruleSetEnabled = true
                    currentRule = null
                }

                RULE_SET_DISABLED.matches(line) -> ruleSetEnabled = false

                else -> {
                    val ruleMatch = RULE_KEY.matchEntire(line)
                    if (ruleMatch != null) {
                        currentRule = ruleMatch.groupValues[1]
                        if (ruleSetEnabled) active += currentRule
                    } else if (currentRule != null && RULE_DISABLED.matches(line)) {
                        active -= currentRule
                    }
                }
            }
        }
        return active
    }

    /**
     * Collects the simple names of detekt rules that implement
     * `RequiresAnalysisApi`, by reading the rule classes out of detekt's own
     * jars.
     *
     * The membership test is the presence of the marker interface's binary name
     * in the class file, restricted to top-level classes under
     * [RULE_CLASS_PREFIX]. That is deliberately cruder than decoding the
     * constant pool and the interface table, and it is safe at this precision
     * because the result is only ever intersected with rule ids the config
     * names: for a false positive to matter, a class would have to be named
     * exactly like a configured rule, live among detekt's rule implementations,
     * and mention the interface without implementing it. The scan was checked
     * against `javap` output over the whole rule classpath and reproduced the
     * same set.
     *
     * @param jars The `detekt` configuration's resolved artefacts. Entries that
     *   are not jars, and jars that cannot be opened, are skipped — the caller
     *   guards against an empty result, which is the failure that matters.
     * @return Simple class names, e.g. `LongParameterList`.
     */
    fun rulesRequiringAnalysisApi(jars: Collection<File>): Set<String> {
        val marker = ANALYSIS_API_MARKER.toByteArray(Charsets.UTF_8)
        val found = mutableSetOf<String>()
        for (jar in jars) {
            if (!jar.isFile || !jar.name.endsWith(".jar")) continue
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .filter { entry ->
                        entry.name.startsWith(RULE_CLASS_PREFIX) &&
                            entry.name.endsWith(".class") &&
                            !entry.name.contains('$')
                    }
                    .forEach { entry ->
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        if (bytes.containsSequence(marker)) {
                            found += entry.name.substringAfterLast('/').removeSuffix(".class")
                        }
                    }
            }
        }
        return found
    }

    /** Rule-set key at column 0, e.g. `style:` or `potential-bugs:`. */
    private val RULE_SET_KEY = Regex("""^[a-z][a-zA-Z-]*:$""")

    /** A rule set switched off wholesale: `  active: false` under a rule-set key. */
    private val RULE_SET_DISABLED = Regex("""^ {2}active:\s*false$""")

    /** A PascalCase rule key nested under a rule set, e.g. `  LongParameterList:`. */
    private val RULE_KEY = Regex("""^ {2}([A-Z][A-Za-z0-9]*):$""")

    /** A rule switched off: `    active: false` under a rule key. */
    private val RULE_DISABLED = Regex("""^ {4}active:\s*false$""")

    /**
     * Naive substring search over raw bytes — the class files involved are a
     * few kilobytes each, so the simple scan is well inside the budget and
     * avoids decoding class bytes as text.
     */
    private fun ByteArray.containsSequence(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        outer@ for (start in 0..size - needle.size) {
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) continue@outer
            }
            return true
        }
        return false
    }
}
