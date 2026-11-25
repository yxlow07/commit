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
    /** Unselected tabs: the light `faint` grey, so the selected tab clearly leads. */
    val tabIdle: ColorProvider,
    /**
     * The widget panel: `surface` at 72 %, so the wallpaper shows through. Widgets can't blur what's
     * behind them (the launcher draws them), so this is see-through rather than frosted.
     */
    val panel: ColorProvider,
    /**
     * The selected tab's dash: `ink` at 22 %. A tint of the text colour stays visible whatever shows
     * through the see-through panel; the solid `track` grey matched the panel over light wallpaper.
     */
    val tabDash: ColorProvider,
    /** Raw `muted` for day and night, so [toward] can mix it with a commitment's colour. */
    private val mutedDay: Color = Color.Gray,
    private val mutedNight: Color = Color.Gray,
) {
    /** `muted` moved 35 % toward [color], like the Agenda page's commitment line. */
    fun toward(color: Int): ColorProvider =
        ColorProvider(day = lerp(mutedDay, Color(color), 0.35f), night = lerp(mutedNight, Color(color), 0.35f))

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
                tabIdle = p { it.faint },
                panel = ColorProvider(day = Color(day.surface).copy(alpha = 0.72f), night = Color(night.surface).copy(alpha = 0.72f)),
                tabDash = ColorProvider(day = Color(day.ink).copy(alpha = 0.22f), night = Color(night.ink).copy(alpha = 0.3f)),
                mutedDay = Color(day.muted), mutedNight = Color(night.muted),
            )
        }

        val Default = of(ActiveTheme.Default)
    }
}

val LocalWidgetColors = staticCompositionLocalOf { WidgetColors.Default }

/** Widgets can't use bundled fonts; the system's light sans stands in for the spec's Light weight. */
val WidgetLight = FontFamily("sans-serif-light")

/** True when the theme keeps the default corners, so the widget uses the system widget radius. */
fun ActiveTheme.usesSystemRadius(): Boolean = spec.cornerRadius == ThemeDefaults.CORNER_RADIUS
