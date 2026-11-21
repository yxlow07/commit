package dev.yxuan.commit.domain.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Themes (F9): a JSON file of design values only (colours, corner radius, font family). The same
 * format and rules are implemented on the web (web/lib/domain/theme.ts); see themes/README.md.
 */

enum class ThemeFont(val key: String) { DEFAULT("default"), SERIF("serif"), MONO("mono"), ROUNDED("rounded") }

/** One colour set (light or dark); every colour is ARGB. */
data class ThemeColors(
    val primary: Int, val onPrimary: Int, val primaryContainer: Int, val onPrimaryContainer: Int,
    val secondary: Int, val onSecondary: Int, val secondaryContainer: Int, val onSecondaryContainer: Int,
    val background: Int, val onBackground: Int, val surface: Int, val onSurface: Int,
    val surfaceVariant: Int, val onSurfaceVariant: Int, val outline: Int, val error: Int, val onError: Int,
) {
    fun toMap(): Map<String, Int> = mapOf(
        "primary" to primary, "onPrimary" to onPrimary, "primaryContainer" to primaryContainer, "onPrimaryContainer" to onPrimaryContainer,
        "secondary" to secondary, "onSecondary" to onSecondary, "secondaryContainer" to secondaryContainer, "onSecondaryContainer" to onSecondaryContainer,
        "background" to background, "onBackground" to onBackground, "surface" to surface, "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant, "outline" to outline, "error" to error, "onError" to onError,
    )

    companion object {
        val KEYS = listOf(
            "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
            "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
            "background", "onBackground", "surface", "onSurface",
            "surfaceVariant", "onSurfaceVariant", "outline", "error", "onError",
        )

        fun fromMap(m: Map<String, Int>) = ThemeColors(
            m.getValue("primary"), m.getValue("onPrimary"), m.getValue("primaryContainer"), m.getValue("onPrimaryContainer"),
            m.getValue("secondary"), m.getValue("onSecondary"), m.getValue("secondaryContainer"), m.getValue("onSecondaryContainer"),
            m.getValue("background"), m.getValue("onBackground"), m.getValue("surface"), m.getValue("onSurface"),
            m.getValue("surfaceVariant"), m.getValue("onSurfaceVariant"), m.getValue("outline"), m.getValue("error"), m.getValue("onError"),
        )
    }
}

data class ThemeSpec(
    val name: String,
    val light: ThemeColors,
    val dark: ThemeColors,
    val cornerRadius: Float,
    val font: ThemeFont,
)

/** Commit's own palette (same values as the web's default tokens). Missing theme values come from here. */
object ThemeDefaults {
    const val CORNER_RADIUS = 16f

    val light = ThemeColors(
        primary = 0xFF4F5BD5.toInt(), onPrimary = 0xFFFFFFFF.toInt(), primaryContainer = 0xFFDFE2FF.toInt(), onPrimaryContainer = 0xFF0B1468.toInt(),
        secondary = 0xFF5C5D72.toInt(), onSecondary = 0xFFFFFFFF.toInt(), secondaryContainer = 0xFFE1E0F9.toInt(), onSecondaryContainer = 0xFF191A2C.toInt(),
        background = 0xFFF7F7FB.toInt(), onBackground = 0xFF1B1B21.toInt(), surface = 0xFFFFFFFF.toInt(), onSurface = 0xFF1B1B21.toInt(),
        surfaceVariant = 0xFFF0F0F6.toInt(), onSurfaceVariant = 0xFF5F5F6B.toInt(), outline = 0xFFE2E2EA.toInt(),
        error = 0xFFC62828.toInt(), onError = 0xFFFFFFFF.toInt(),
    )

    val dark = ThemeColors(
        primary = 0xFFAAB3FF.toInt(), onPrimary = 0xFF141A5C.toInt(), primaryContainer = 0xFF343A73.toInt(), onPrimaryContainer = 0xFFDFE2FF.toInt(),
        secondary = 0xFFC5C4DD.toInt(), onSecondary = 0xFF2E2F42.toInt(), secondaryContainer = 0xFF444559.toInt(), onSecondaryContainer = 0xFFE1E0F9.toInt(),
        background = 0xFF121216.toInt(), onBackground = 0xFFE7E7EE.toInt(), surface = 0xFF1B1B21.toInt(), onSurface = 0xFFE7E7EE.toInt(),
        surfaceVariant = 0xFF26262E.toInt(), onSurfaceVariant = 0xFFA1A1AD.toInt(), outline = 0xFF2F2F39.toInt(),
        error = 0xFFFF8A80.toInt(), onError = 0xFF690005.toInt(),
    )

    fun spec(name: String) = ThemeSpec(name, light, dark, CORNER_RADIUS, ThemeFont.DEFAULT)
}

sealed interface ThemeParseResult {
    data class Ok(val spec: ThemeSpec) : ThemeParseResult
    data class Invalid(val errors: List<String>) : ThemeParseResult
}

/**
 * Validates a theme file and fills missing values from [ThemeDefaults] (F9.2). Every problem is
 * reported as one plain sentence naming the field.
 */
object ThemeParser {
    const val SCHEMA_VERSION = 1
    const val MAX_BYTES = 32 * 1024
    const val MAX_NAME = 40
    const val MAX_RADIUS = 32.0
    private val COLOR = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

    fun parse(text: String): ThemeParseResult {
        if (text.toByteArray().size > MAX_BYTES) return invalid("The file is larger than 32 KB; a theme is a few hundred bytes.")
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            return invalid("This isn't valid JSON.")
        }
        if (root !is JsonObject) return invalid("A theme must be a JSON object, like { \"schemaVersion\": 1, \"name\": \"Ocean\" }.")

        val errors = mutableListOf<String>()

        when (val v = root["schemaVersion"]) {
            null -> errors += "schemaVersion is missing; this app reads version $SCHEMA_VERSION."
            else -> if ((v as? JsonPrimitive)?.intOrNull != SCHEMA_VERSION) errors += "schemaVersion ${v} isn't supported; this app reads version $SCHEMA_VERSION."
        }

        val name = (root["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        when {
            root["name"] == null -> errors += "name is missing."
            name == null -> errors += "name must be text."
            name.isEmpty() -> errors += "name can't be empty."
            name.length > MAX_NAME -> errors += "name is longer than $MAX_NAME characters."
        }

        val light = colors(root["light"], "light", ThemeDefaults.light, errors)
        val dark = colors(root["dark"], "dark", ThemeDefaults.dark, errors)

        var radius = ThemeDefaults.CORNER_RADIUS
        when (val shape = root["shape"]) {
            null -> Unit
            !is JsonObject -> errors += "shape must be an object, like { \"cornerRadius\": 16 }."
            else -> shape["cornerRadius"]?.let { r ->
                val value = (r as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
                if (value == null || value < 0 || value > MAX_RADIUS) errors += "shape.cornerRadius must be a number from 0 to 32."
                else radius = value.toFloat()
            }
        }

        var font = ThemeFont.DEFAULT
        root["font"]?.let { f ->
            val key = (f as? JsonPrimitive)?.takeIf { it.isString }?.content
            val match = ThemeFont.entries.firstOrNull { it.key == key }
            if (match == null) errors += "font must be one of default, serif, mono or rounded."
            else font = match
        }

        if (errors.isNotEmpty()) return ThemeParseResult.Invalid(errors)
        return ThemeParseResult.Ok(ThemeSpec(name!!, light, dark, radius, font))
    }

    /** Parses a stored theme; null if it no longer validates (callers then fall back to the default). */
    fun parseOrNull(text: String): ThemeSpec? = (parse(text) as? ThemeParseResult.Ok)?.spec

    private fun colors(element: JsonElement?, path: String, defaults: ThemeColors, errors: MutableList<String>): ThemeColors {
        if (element == null) return defaults
        if (element !is JsonObject) {
            errors += "$path must be an object of colours, like { \"primary\": \"#1E5AA8\" }."
            return defaults
        }
        val values = defaults.toMap().toMutableMap()
        for (key in ThemeColors.KEYS) {
            val raw = element[key] ?: continue
            val text = (raw as? JsonPrimitive)?.takeIf { it.isString }?.content
            val argb = text?.let(::parseColor)
            if (argb == null) errors += "$path.$key: $raw is not a colour like #1E5AA8."
            else values[key] = argb
        }
        return ThemeColors.fromMap(values)
    }

    /** "#RRGGBB" → opaque ARGB; "#AARRGGBB" → ARGB; anything else → null. */
    fun parseColor(text: String): Int? {
        if (!COLOR.matches(text)) return null
        val hex = text.substring(1).toLong(16)
        return if (text.length == 7) (0xFF000000L or hex).toInt() else hex.toInt()
    }

    private fun invalid(message: String) = ThemeParseResult.Invalid(listOf(message))
}

/** How a theme picks light or dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The theme in use: wallpaper colours (Material You) or a fixed spec. */
sealed interface ActiveTheme {
    val id: String

    data object Dynamic : ActiveTheme {
        override val id = BuiltinThemes.DYNAMIC
    }

    data class Fixed(override val id: String, val spec: ThemeSpec, val mode: ThemeMode) : ActiveTheme
}

/** Built-in themes (F9.3): Material You (default), Light and Dark (Commit's palette, forced). */
object BuiltinThemes {
    const val DYNAMIC = "builtin:dynamic"
    const val LIGHT = "builtin:light"
    const val DARK = "builtin:dark"

    val light = ActiveTheme.Fixed(LIGHT, ThemeDefaults.spec("Light"), ThemeMode.LIGHT)
    val dark = ActiveTheme.Fixed(DARK, ThemeDefaults.spec("Dark"), ThemeMode.DARK)
    val all: List<ActiveTheme> = listOf(ActiveTheme.Dynamic, light, dark)

    /**
     * Resolves `settings.active_theme_id`: a built-in id, an imported theme's id (looked up with
     * [imported]), or null. Anything unknown or no longer valid falls back to Material You.
     */
    fun resolve(id: String?, imported: (String) -> ThemeSpec?): ActiveTheme = when (id) {
        null, DYNAMIC -> ActiveTheme.Dynamic
        LIGHT -> light
        DARK -> dark
        else -> imported(id)?.let { ActiveTheme.Fixed(id, it, ThemeMode.SYSTEM) } ?: ActiveTheme.Dynamic
    }
}
