package dev.yxuan.commit.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yxuan.commit.domain.theme.ThemeColors

/** The design spec's colour tokens (app/App Design Spec.md, "Colour"). Never hard-code colours in screens. */
@Immutable
data class CommitColors(
    val ink: Color,
    val onInk: Color,
    val textStrong: Color,
    val muted: Color,
    val faint: Color,
    val track: Color,
    val fill: Color,
    val surface: Color,
    val background: Color,
    val danger: Color,
    val scrim: Color,
    val isDark: Boolean,
) {
    /** Heatmap shades, lightest (no activity) to darkest; in dark themes they run toward `ink`. */
    val heat: List<Color>
        get() = listOf(fill, track, faint, muted, ink)

    companion object {
        fun from(c: ThemeColors, isDark: Boolean) = CommitColors(
            ink = Color(c.ink), onInk = Color(c.onInk), textStrong = Color(c.textStrong), muted = Color(c.muted),
            faint = Color(c.faint), track = Color(c.track), fill = Color(c.fill), surface = Color(c.surface),
            background = Color(c.background), danger = Color(c.danger), scrim = Color(c.scrim), isDark = isDark,
        )
    }
}

/** The design spec's type styles ("Type"). Times, dates, counts and weights use tabular figures ([CommitType.tnum]). */
@Immutable
data class CommitType(
    val displayXl: TextStyle,
    val displayL: TextStyle,
    val displayM: TextStyle,
    val titleScreen: TextStyle,
    val tabSelected: TextStyle,
    val titleDialog: TextStyle,
    val bodyL: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val meta: TextStyle,
    val caption: TextStyle,
    val micro: TextStyle,
) {
    companion object {
        fun of(family: FontFamily): CommitType {
            fun s(size: Int, weight: FontWeight, tracking: Float = 0f, line: Float = 1.3f) = TextStyle(
                fontFamily = family, fontSize = size.sp, fontWeight = weight, letterSpacing = tracking.sp,
                lineHeight = (size * line).sp,
            )
            return CommitType(
                displayXl = s(96, FontWeight.Light, -3f, 1f).tnum(),
                displayL = s(64, FontWeight.Light, -2f, 1f).tnum(),
                displayM = s(48, FontWeight.Light, 0f, 1f).tnum(),
                titleScreen = s(40, FontWeight.Bold, -1f, 1.1f),
                tabSelected = s(30, FontWeight.Light, -1f, 1.1f),
                titleDialog = s(20, FontWeight.Bold),
                bodyL = s(17, FontWeight.Normal, line = 1.45f),
                body = s(16, FontWeight.Normal),
                label = s(15, FontWeight.Medium),
                meta = s(14, FontWeight.Normal),
                caption = s(13, FontWeight.Medium),
                micro = s(12, FontWeight.Normal),
            )
        }
    }
}

/** Tabular figures, for every time, date, count and weight. */
fun TextStyle.tnum(): TextStyle = copy(fontFeatureSettings = "tnum")

/** Corner radii from the spec ("Spacing and shape"); [panel] (widget and dialog) comes from the theme. */
@Immutable
data class CommitShapes(
    val panel: Dp = 28.dp,
    val preview: Dp = 24.dp,
    val field: Dp = 12.dp,
    val segmentTrack: Dp = 14.dp,
    val segment: Dp = 11.dp,
    val heatCell: Dp = 3.dp,
)

/** Spacing steps (4, 8, 12, 16, 20, 24, 36, 48) and the spec's fixed sizes. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val section = 24.dp
    val xxxl = 36.dp
    val huge = 48.dp

    /** App screen padding: 24 sides, 48 top. */
    val screenSide = 24.dp
    val screenTop = 48.dp
    val touch = 44.dp
    val dueColumn = 56.dp
    val rowApp = 56.dp
    val rowWidget = 52.dp
}

internal val LocalCommitColors = staticCompositionLocalOf { CommitColors.from(dev.yxuan.commit.domain.theme.ThemeDefaults.light, false) }
internal val LocalCommitType = staticCompositionLocalOf { CommitType.of(FontFamily.Default) }
internal val LocalCommitShapes = staticCompositionLocalOf { CommitShapes() }

/** Access to the design tokens inside [CommitTheme]: `Commit.colors.ink`, `Commit.type.body`. */
object Commit {
    val colors: CommitColors
        @Composable @ReadOnlyComposable get() = LocalCommitColors.current
    val type: CommitType
        @Composable @ReadOnlyComposable get() = LocalCommitType.current
    val shapes: CommitShapes
        @Composable @ReadOnlyComposable get() = LocalCommitShapes.current
}
