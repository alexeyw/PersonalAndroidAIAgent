package ai.agent.android.presentation.theme

import ai.agent.android.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource

/**
 * A composable function that applies the custom theme to the application.
 *
 * The theme reads its primary/secondary/tertiary colors from
 * `res/values/colors.xml` so designers can tweak the palette without touching
 * Kotlin. Dynamic color (Android 12+) is preferred when available; the
 * resource-backed scheme is a fallback for older devices and `dynamicColor =
 * false`.
 *
 * @param darkTheme Whether to use the dark theme.
 * @param dynamicColor Whether to use dynamic color on supported devices.
 * @param content The composable content to apply the theme to.
 */
@Composable
fun AndroidAIAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val staticDarkScheme = darkColorScheme(
        primary = colorResource(R.color.theme_primary_dark),
        secondary = colorResource(R.color.theme_secondary_dark),
        tertiary = colorResource(R.color.theme_tertiary_dark),
    )
    val staticLightScheme = lightColorScheme(
        primary = colorResource(R.color.theme_primary_light),
        secondary = colorResource(R.color.theme_secondary_light),
        tertiary = colorResource(R.color.theme_tertiary_light),
    )

    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> staticDarkScheme
        else -> staticLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
