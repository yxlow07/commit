package dev.yxuan.commit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.yxuan.commit.R
import dev.yxuan.commit.domain.theme.ActiveTheme
import dev.yxuan.commit.domain.theme.ThemeFont
import dev.yxuan.commit.domain.theme.ThemeMode

/** Hanken Grotesk (variable font, OFL), the spec's one family. One entry per weight the spec uses. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val HankenGrotesk: FontFamily = FontFamily(
    listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.Bold).map { w ->
        Font(R.font.hanken_grotesk, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    }
)

private fun familyFor(font: ThemeFont): FontFamily = when (font) {
    ThemeFont.DEFAULT -> HankenGrotesk
    ThemeFont.SERIF -> FontFamily.Serif
    ThemeFont.MONO -> FontFamily.Monospace
    ThemeFont.ROUNDED -> FontFamily(Font(R.font.nunito))
}

/**
 * Material colour scheme from the tokens, so stock Material 3 parts (pickers, switches, menus,
 * snackbars) fit the greyscale design: `ink` is the "primary", `danger` the "error".
 */
fun materialSchemeFor(c: CommitColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.ink, onPrimary = c.onInk,
        primaryContainer = c.fill, onPrimaryContainer = c.ink,
        secondary = c.ink, onSecondary = c.onInk,
        secondaryContainer = c.fill, onSecondaryContainer = c.ink,
        tertiary = c.ink, onTertiary = c.onInk,
        background = c.background, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.fill, onSurfaceVariant = c.muted,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.fill,
        surfaceTint = c.surface, inverseSurface = c.ink, inverseOnSurface = c.onInk,
        outline = c.faint, outlineVariant = c.track,
        error = c.danger, onError = c.onInk, scrim = c.scrim,
    )
}

/** Whether [active] draws dark right now. */
@Composable
fun ActiveTheme.isDark(): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Tokens for [active] in the current light/dark state. */
@Composable
fun ActiveTheme.colors(): CommitColors {
    val dark = isDark()
    return remember(this, dark) { CommitColors.from(if (dark) spec.dark else spec.light, dark) }
}

@Composable
fun CommitTheme(
    active: ActiveTheme = ActiveTheme.Default,
    content: @Composable () -> Unit,
) {
    val colors = active.colors()
    val family = familyFor(active.spec.font)
    val type = remember(family) { CommitType.of(family) }
    val shapes = remember(active.spec.cornerRadius) { CommitShapes(panel = active.spec.cornerRadius.dp) }

    // Material slots mapped onto the spec's styles, for stock components.
    val typography = remember(type) {
        Typography(
            displayLarge = type.displayL, displayMedium = type.displayM, displaySmall = type.titleScreen,
            headlineLarge = type.titleScreen, headlineMedium = type.tabSelected, headlineSmall = type.titleDialog,
            titleLarge = type.titleDialog, titleMedium = type.bodyL, titleSmall = type.label,
            bodyLarge = type.body, bodyMedium = type.meta, bodySmall = type.caption,
            labelLarge = type.label, labelMedium = type.caption, labelSmall = type.micro,
        )
    }
    val materialShapes = remember(shapes) {
        Shapes(
            extraSmall = RoundedCornerShape(shapes.segment),
            small = RoundedCornerShape(shapes.field),
            medium = RoundedCornerShape(shapes.field),
            large = RoundedCornerShape(shapes.panel),
            extraLarge = RoundedCornerShape(shapes.panel),
        )
    }

    CompositionLocalProvider(
        LocalCommitColors provides colors,
        LocalCommitType provides type,
        LocalCommitShapes provides shapes,
    ) {
        MaterialTheme(
            colorScheme = materialSchemeFor(colors),
            typography = typography,
            shapes = materialShapes,
            content = content,
        )
    }
}
