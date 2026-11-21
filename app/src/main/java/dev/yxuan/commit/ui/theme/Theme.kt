package dev.yxuan.commit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.yxuan.commit.R
import dev.yxuan.commit.domain.theme.ActiveTheme
import dev.yxuan.commit.domain.theme.ThemeColors
import dev.yxuan.commit.domain.theme.ThemeDefaults
import dev.yxuan.commit.domain.theme.ThemeFont
import dev.yxuan.commit.domain.theme.ThemeMode

private val DefaultTypography = Typography()

fun colorSchemeFor(colors: ThemeColors, isDark: Boolean): ColorScheme {
    val primary = Color(colors.primary)
    val onPrimary = Color(colors.onPrimary)
    val primaryContainer = Color(colors.primaryContainer)
    val onPrimaryContainer = Color(colors.onPrimaryContainer)
    val secondary = Color(colors.secondary)
    val onSecondary = Color(colors.onSecondary)
    val secondaryContainer = Color(colors.secondaryContainer)
    val onSecondaryContainer = Color(colors.onSecondaryContainer)
    val background = Color(colors.background)
    val onBackground = Color(colors.onBackground)
    val surface = Color(colors.surface)
    val onSurface = Color(colors.onSurface)
    val surfaceVariant = Color(colors.surfaceVariant)
    val onSurfaceVariant = Color(colors.onSurfaceVariant)
    val outline = Color(colors.outline)
    val error = Color(colors.error)
    val onError = Color(colors.onError)

    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            error = error,
            onError = onError,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            error = error,
            onError = onError,
        )
    }
}

@Composable
fun CommitTheme(
    active: ActiveTheme = ActiveTheme.Dynamic,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val (colorScheme, cornerRadius, font) = when (active) {
        is ActiveTheme.Dynamic -> {
            val scheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                systemDark -> colorSchemeFor(ThemeDefaults.dark, isDark = true)
                else -> colorSchemeFor(ThemeDefaults.light, isDark = false)
            }
            Triple(scheme, ThemeDefaults.CORNER_RADIUS, ThemeFont.DEFAULT)
        }
        is ActiveTheme.Fixed -> {
            val isDark = when (active.mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val colors = if (isDark) active.spec.dark else active.spec.light
            val scheme = colorSchemeFor(colors, isDark = isDark)
            Triple(scheme, active.spec.cornerRadius, active.spec.font)
        }
    }

    val fontFamily = when (font) {
        ThemeFont.DEFAULT -> FontFamily.Default
        ThemeFont.SERIF -> FontFamily.Serif
        ThemeFont.MONO -> FontFamily.Monospace
        ThemeFont.ROUNDED -> FontFamily(Font(R.font.nunito))
    }

    val typography = remember(fontFamily) {
        Typography(
            displayLarge = DefaultTypography.displayLarge.copy(fontFamily = fontFamily),
            displayMedium = DefaultTypography.displayMedium.copy(fontFamily = fontFamily),
            displaySmall = DefaultTypography.displaySmall.copy(fontFamily = fontFamily),
            headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = fontFamily),
            headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = fontFamily),
            headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = fontFamily),
            titleLarge = DefaultTypography.titleLarge.copy(fontFamily = fontFamily),
            titleMedium = DefaultTypography.titleMedium.copy(fontFamily = fontFamily),
            titleSmall = DefaultTypography.titleSmall.copy(fontFamily = fontFamily),
            bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = fontFamily),
            bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = fontFamily),
            bodySmall = DefaultTypography.bodySmall.copy(fontFamily = fontFamily),
            labelLarge = DefaultTypography.labelLarge.copy(fontFamily = fontFamily),
            labelMedium = DefaultTypography.labelMedium.copy(fontFamily = fontFamily),
            labelSmall = DefaultTypography.labelSmall.copy(fontFamily = fontFamily),
        )
    }

    val shapes = remember(cornerRadius) {
        val r = cornerRadius.dp
        Shapes(
            extraSmall = RoundedCornerShape((r / 4).coerceAtLeast(0.dp)),
            small = RoundedCornerShape((r / 2).coerceAtLeast(0.dp)),
            medium = RoundedCornerShape(r),
            large = RoundedCornerShape(r),
            extraLarge = RoundedCornerShape(r),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
