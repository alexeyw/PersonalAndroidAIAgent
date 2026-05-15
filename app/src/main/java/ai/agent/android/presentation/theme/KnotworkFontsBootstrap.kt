package ai.agent.android.presentation.theme

import ai.agent.android.R
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.knotwork.design.tokens.KnotworkFonts

/**
 * Builds the bundled Inter and JetBrains Mono [FontFamily] instances from the
 * `R.font.*` resources packaged inside `:app/src/main/res/font/` and pushes
 * them into [KnotworkFonts] so the design-system typography
 * ([app.knotwork.design.tokens.KnotworkTextStyles]) renders against the brand
 * fonts instead of the platform sans/mono fallbacks.
 *
 * Called once from [ai.agent.android.App.onCreate]. Idempotent — subsequent
 * invocations are harmless because [KnotworkFonts.install] is last-write-wins.
 *
 * Font asset inventory (subset to Latin-1 + a handful of punctuation glyphs,
 * total APK delta ≈ 152 KB, see `res/font/`):
 *
 *  | Resource id                   | Weight             |
 *  |-------------------------------|--------------------|
 *  | `R.font.inter_regular`        | [FontWeight.Normal]   |
 *  | `R.font.inter_medium`         | [FontWeight.Medium]   |
 *  | `R.font.inter_semibold`       | [FontWeight.SemiBold] |
 *  | `R.font.inter_bold`           | [FontWeight.Bold]     |
 *  | `R.font.jetbrains_mono_regular` | [FontWeight.Normal] |
 *  | `R.font.jetbrains_mono_medium`  | [FontWeight.Medium] |
 */
internal object KnotworkFontsBootstrap {

    /**
     * Constructs the bundled font families and installs them into
     * [KnotworkFonts]. Must run before the first Compose composition so the
     * typography sheet picks up the brand fonts on the very first frame.
     */
    fun install() {
        val inter = FontFamily(
            Font(R.font.inter_regular, FontWeight.Normal),
            Font(R.font.inter_medium, FontWeight.Medium),
            Font(R.font.inter_semibold, FontWeight.SemiBold),
            Font(R.font.inter_bold, FontWeight.Bold),
        )
        val mono = FontFamily(
            Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
            Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        )
        KnotworkFonts.install(inter = inter, mono = mono)
    }
}
