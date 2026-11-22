package dev.yxuan.commit.widget

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontFamily
import androidx.glance.unit.ColorProvider
import dev.yxuan.commit.domain.theme.ActiveTheme
import dev.yxuan.commit.domain.theme.ThemeColors
import dev.yxuan.commit.domain.theme.ThemeDefaults
import dev.yxuan.commit.domain.theme.ThemeMode

/**
 * The design tokens as Glance colours: day/night pairs, so the widget follows the system's dark
 * mode with the theme's dark set (Automatic and imported themes), or stays fixed (Light / Dark).
 */
data class WidgetColors(
    val ink: ColorProvider,
    val onInk: ColorProvider,
    val textStrong: ColorProvider,
    val muted: ColorProvider,
    val faint: ColorProvider,
    val track: ColorProvider,
    val fill: ColorProvider,
    val surface: ColorProvider,
    val danger: ColorProvider,
    /** Unselected tabs: halfway between muted and faint, so the selected tab stands out. */
    val tabIdle: ColorProvider,
) {
    companion object {
        fun of(theme: ActiveTheme): WidgetColors {
            val (day, night) = when (theme.mode) {
                ThemeMode.SYSTEM -> theme.spec.light to theme.spec.dark
                ThemeMode.LIGHT -> theme.spec.light to theme.spec.light
                ThemeMode.DARK -> theme.spec.dark to theme.spec.dark
            }
            fun p(pick: (ThemeColors) -> Int) = ColorProvider(day = Color(pick(day)), night = Color(pick(night)))
            return WidgetColors(
                ink = p { it.ink }, onInk = p { it.onInk }, textStrong = p { it.textStrong }, muted = p { it.muted },
                faint = p { it.faint }, track = p { it.track }, fill = p { it.fill }, surface = p { it.surface },
                danger = p { it.danger },
                tabIdle = ColorProvider(day = idle(day), night = idle(night)),
            )
        }

        private fun idle(t: ThemeColors) = lerp(Color(t.muted), Color(t.faint), 0.5f)

        val Default = of(ActiveTheme.Default)
    }
}

val LocalWidgetColors = staticCompositionLocalOf { WidgetColors.Default }

/** Widgets can't use bundled fonts; the system's light sans stands in for the spec's Light weight. */
val WidgetLight = FontFamily("sans-serif-light")

/** True when the theme keeps the default corners, so the widget uses the system widget radius. */
fun ActiveTheme.usesSystemRadius(): Boolean = spec.cornerRadius == ThemeDefaults.CORNER_RADIUS
