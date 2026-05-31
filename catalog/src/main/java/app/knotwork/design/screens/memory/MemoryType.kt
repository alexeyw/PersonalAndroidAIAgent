@file:Suppress("MagicNumber") // Type-scale token file: every literal is a spec value.

package app.knotwork.design.screens.memory

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Per-element typography for the Memory Manager screen, transcribed verbatim
 * from the designer's "Memory Manager — Typography & Sizing Spec".
 *
 * The screen intentionally undershoots the Material 3 scale by 1–2 steps for
 * density, so these styles override sizes/weights/tracking on the two brand
 * font families: [KnotworkTextStyles.BodyBase] carries Inter (sans) and
 * [KnotworkTextStyles.MonoSm] carries JetBrains Mono (mono). Sizes are in `sp`
 * (= the spec's `px` at 1×); tracking values are likewise `sp`.
 */
internal object MemoryType {

    private val sans get() = KnotworkTextStyles.BodyBase
    private val mono get() = KnotworkTextStyles.MonoSm

    // App bar
    val appBarTitle: TextStyle get() = sans.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
    val appBarSubtitle: TextStyle
        get() = mono.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp, lineHeight = 16.sp)

    // Overview / stats header
    val overviewCount: TextStyle
        get() = sans.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, lineHeight = 26.sp)
    val overviewUnit: TextStyle get() = sans.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    val overviewMeta: TextStyle get() = mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp)
    val overviewLegend: TextStyle get() = mono.copy(
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 14.sp,
    )

    // Sort / range row
    val sortLabel: TextStyle
        get() = mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.6.sp, lineHeight = 14.sp)
    val control: TextStyle get() = sans.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)

    // Group headers
    val groupHeader: TextStyle
        get() = mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, lineHeight = 14.sp)
    val groupCount: TextStyle get() = mono.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp)

    // Card
    val cardTitle: TextStyle get() = sans.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    val cardBody: TextStyle get() = sans.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    val sourceTag: TextStyle
        get() = mono.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, lineHeight = 12.sp)
    val cardTags: TextStyle get() = mono.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
    val score: TextStyle get() = mono.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
    val timestamp: TextStyle get() = mono.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp)

    // Detail sheet
    val detailTitle: TextStyle
        get() = sans.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, lineHeight = 26.sp)
    val detailBody: TextStyle get() = sans.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp)
    val provKey: TextStyle
        get() = mono.copy(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.4.sp,
            lineHeight = 14.sp,
        )
    val provValue: TextStyle get() = sans.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp)

    // Compaction dialog
    val compactTitle: TextStyle get() = sans.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
    val compactBody: TextStyle get() = sans.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp)
    val statValue: TextStyle get() = mono.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
    val statLabel: TextStyle
        get() = mono.copy(fontSize = 10.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp, lineHeight = 14.sp)
}
