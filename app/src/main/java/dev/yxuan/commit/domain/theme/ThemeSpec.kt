package dev.yxuan.commit.domain.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Themes (F9): a JSON file of design values only. Format v2 uses the design spec's tokens
 * (app/App Design Spec.md, "Tokens"); v1 files (Material keys, M9) are still read and mapped.
 * The same format and rules are implemented on the web (web/lib/domain/theme.ts); see themes/README.md.
 */

enum class ThemeFont(val key: String) { DEFAULT("default"), SERIF("serif"), MONO("mono"), ROUNDED("rounded") }

/** One colour set (light or dark), ARGB ints, named after the design spec's tokens. */
data class ThemeColors(
    val ink: Int, val onInk: Int, val textStrong: Int, val muted: Int, val faint: Int,
    val track: Int, val fill: Int, val surface: Int, val background: Int, val danger: Int, val scrim: Int,
) {
    fun toMap(): Map<String, Int> = mapOf(
        "ink" to ink, "onInk" to onInk, "textStrong" to textStrong, "muted" to muted, "faint" to faint,
        "track" to track, "fill" to fill, "surface" to surface, "background" to background, "danger" to danger, "scrim" to scrim,
    )

    companion object {
        val KEYS = listOf("ink", "onInk", "textStrong", "muted", "faint", "track", "fill", "surface", "background", "danger", "scrim")

        fun fromMap(m: Map<String, Int>) = ThemeColors(
            m.getValue("ink"), m.getValue("onInk"), m.getValue("textStrong"), m.getValue("muted"), m.getValue("faint"),
            m.getValue("track"), m.getValue("fill"), m.getValue("surface"), m.getValue("background"), m.getValue("danger"), m.getValue("scrim"),
        )
    }
}

data class ThemeSpec(
    val name: String,
    val light: ThemeColors,
    val dark: ThemeColors,
    /** Corner radius of the widget and dialogs, dp. */
    val cornerRadius: Float,
    val font: ThemeFont,
)

/** The design spec's Light and Dark palettes. Missing theme values come from here. */
object ThemeDefaults {
    const val CORNER_RADIUS = 28f

    val light = ThemeColors(
        ink = 0xFF1F2123.toInt(), onInk = 0xFFFFFFFF.toInt(), textStrong = 0xFF45474B.toInt(), muted = 0xFF6B6E73.toInt(),
        faint = 0xFF9C9FA4.toInt(), track = 0xFFE3E4E6.toInt(), fill = 0xFFEEEFF0.toInt(), surface = 0xFFFFFFFF.toInt(),
        background = 0xFFF6F6F5.toInt(), danger = 0xFFB42318.toInt(), scrim = 0x731F2123,
    )

    val dark = ThemeColors(
        ink = 0xFFECEDEE.toInt(), onInk = 0xFF17181A.toInt(), textStrong = 0xFFC9CBCE.toInt(), muted = 0xFFA3A6AB.toInt(),
        faint = 0xFF6B6E73.toInt(), track = 0xFF2E3033.toInt(), fill = 0xFF2A2C2F.toInt(), surface = 0xFF17181A.toInt(),
        background = 0xFF0F1011.toInt(), danger = 0xFFF4877B.toInt(), scrim = 0x99000000.toInt(),
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
    const val SCHEMA_VERSION = 2
    const val MAX_BYTES = 32 * 1024
    const val MAX_NAME = 40
    const val MAX_RADIUS = 40.0
    private val COLOR = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

    /** v1 (Material) key → v2 token, so themes imported before v2 keep working. */
    private val V1_KEYS = mapOf(
        "ink" to "onSurface", "onInk" to "surface", "textStrong" to "onSurface", "muted" to "onSurfaceVariant",
        "faint" to "outline", "track" to "outline", "fill" to "surfaceVariant", "surface" to "surface",
        "background" to "background", "danger" to "error",
    )

    fun parse(text: String): ThemeParseResult {
        if (text.toByteArray().size > MAX_BYTES) return invalid("The file is larger than 32 KB; a theme is a few hundred bytes.")
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            return invalid("This isn't valid JSON.")
        }
        if (root !is JsonObject) return invalid("A theme must be a JSON object, like { \"schemaVersion\": 2, \"name\": \"Ocean\" }.")

        val errors = mutableListOf<String>()

        val version = (root["schemaVersion"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        when {
            root["schemaVersion"] == null -> errors += "schemaVersion is missing; this app reads version $SCHEMA_VERSION."
            version != 1 && version != SCHEMA_VERSION -> errors += "schemaVersion ${root["schemaVersion"]} isn't supported; this app reads version $SCHEMA_VERSION."
        }

        val name = (root["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        when {
            root["name"] == null -> errors += "name is missing."
            name == null -> errors += "name must be text."
            name.isEmpty() -> errors += "name can't be empty."
            name.length > MAX_NAME -> errors += "name is longer than $MAX_NAME characters."
        }

        val v1 = version == 1
        if (root["light"] == null && root["dark"] == null) errors += "This file has no colours section (light or dark)."
        val light = colors(root["light"], "light", ThemeDefaults.light, v1, errors)
        val dark = colors(root["dark"], "dark", ThemeDefaults.dark, v1, errors)

        var radius = ThemeDefaults.CORNER_RADIUS
        when (val shape = root["shape"]) {
            null -> Unit
            !is JsonObject -> errors += "shape must be an object, like { \"cornerRadius\": 28 }."
            else -> shape["cornerRadius"]?.let { r ->
                val value = (r as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
                if (value == null || value < 0 || value > MAX_RADIUS) errors += "shape.cornerRadius must be a number from 0 to 40."
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

    private fun colors(element: JsonElement?, path: String, defaults: ThemeColors, v1: Boolean, errors: MutableList<String>): ThemeColors {
        if (element == null) return defaults
        if (element !is JsonObject) {
            errors += "$path must be an object of colours, like { \"ink\": \"#1F2123\" }."
            return defaults
        }
        val values = defaults.toMap().toMutableMap()
        for (key in ThemeColors.KEYS) {
            val sourceKey = if (v1) V1_KEYS[key] ?: continue else key
            val raw = element[sourceKey] ?: continue
            val text = (raw as? JsonPrimitive)?.takeIf { it.isString }?.content
            val argb = text?.let(::parseColor)
            if (argb == null) errors += "$path.$sourceKey: $raw is not a colour like #1F2123."
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

/** The theme in use: a spec and whether it follows the system's light/dark setting. */
data class ActiveTheme(val id: String, val spec: ThemeSpec, val mode: ThemeMode) {
    companion object {
        /** The default: the spec's Light and Dark, following the system. */
        val Default get() = BuiltinThemes.automatic
    }
}

/** Built-in themes (F9.3): Automatic (default), Light and Dark. */
object BuiltinThemes {
    const val AUTOMATIC = "builtin:system"
    /** Material You before the design spec; stored ids from then mean Automatic now. */
    const val LEGACY_DYNAMIC = "builtin:dynamic"
    const val LIGHT = "builtin:light"
    const val DARK = "builtin:dark"

    val automatic = ActiveTheme(AUTOMATIC, ThemeDefaults.spec("Automatic"), ThemeMode.SYSTEM)
    val light = ActiveTheme(LIGHT, ThemeDefaults.spec("Light"), ThemeMode.LIGHT)
    val dark = ActiveTheme(DARK, ThemeDefaults.spec("Dark"), ThemeMode.DARK)
    val all: List<ActiveTheme> = listOf(automatic, light, dark)

    /**
     * Resolves `settings.active_theme_id`: a built-in id, an imported theme's id (looked up with
     * [imported]), or null. Anything unknown or no longer valid falls back to Automatic.
     */
    fun resolve(id: String?, imported: (String) -> ThemeSpec?): ActiveTheme = when (id) {
        null, AUTOMATIC, LEGACY_DYNAMIC -> automatic
        LIGHT -> light
        DARK -> dark
        else -> imported(id)?.let { ActiveTheme(id, it, ThemeMode.SYSTEM) } ?: automatic
    }
}
