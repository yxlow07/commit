package dev.yxuan.commit.domain.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeParserTest {

    private fun example(name: String): String =
        (File("../themes/examples/$name").takeIf { it.exists() } ?: File("themes/examples/$name")).readText()

    private fun ok(json: String): ThemeSpec {
        val result = ThemeParser.parse(json)
        assertTrue("expected Ok, got $result", result is ThemeParseResult.Ok)
        return (result as ThemeParseResult.Ok).spec
    }

    private fun errors(json: String): List<String> {
        val result = ThemeParser.parse(json)
        assertTrue("expected Invalid, got $result", result is ThemeParseResult.Invalid)
        return (result as ThemeParseResult.Invalid).errors
    }

    @Test
    fun parseOceanExample() {
        val spec = ok(example("ocean.json"))
        assertEquals("Ocean", spec.name)
        assertEquals(ThemeParser.parseColor("#14283D"), spec.light.ink)
        assertEquals(ThemeParser.parseColor("#E3ECF5"), spec.dark.ink)
        assertEquals(ThemeDefaults.light.scrim, spec.light.scrim) // not set: from Light
        assertEquals(32f, spec.cornerRadius)
        assertEquals(ThemeFont.ROUNDED, spec.font)
    }

    @Test
    fun parseForestExampleWithDefaultsFilled() {
        val spec = ok(example("forest.json"))
        assertEquals(ThemeParser.parseColor("#1F2A1E"), spec.light.ink)
        assertEquals(ThemeDefaults.light.danger, spec.light.danger) // missing key: from Light
        assertEquals(ThemeDefaults.dark, spec.dark) // no dark set: Dark
        assertEquals(12f, spec.cornerRadius)
        assertEquals(ThemeFont.SERIF, spec.font)
    }

    @Test
    fun parseHighContrastExample() {
        val spec = ok(example("high-contrast.json"))
        assertEquals("High contrast", spec.name)
        assertEquals(ThemeParser.parseColor("#000000"), spec.light.ink)
        assertEquals(8f, spec.cornerRadius)
        assertEquals(ThemeFont.DEFAULT, spec.font)
    }

    @Test
    fun version1FileIsMappedToTokens() {
        val spec = ok(example("v1/ocean.json"))
        assertEquals("Ocean", spec.name)
        assertEquals(ThemeParser.parseColor("#141C24"), spec.light.ink) // onSurface
        assertEquals(ThemeParser.parseColor("#4A5866"), spec.light.muted) // onSurfaceVariant
        assertEquals(ThemeParser.parseColor("#E6EEF6"), spec.light.fill) // surfaceVariant
        assertEquals(ThemeParser.parseColor("#BA1A1A"), spec.light.danger) // error
        assertEquals(ThemeParser.parseColor("#FFFFFF"), spec.light.onInk) // surface
        assertEquals(ThemeParser.parseColor("#DDE5EE"), spec.dark.ink)
        assertEquals(20f, spec.cornerRadius)
    }

    @Test
    fun rejectUnsupportedOrMissingSchemaVersion() {
        assertTrue(errors("""{"schemaVersion": 3, "name": "X", "light": {}}""").single().contains("isn't supported"))
        assertTrue(errors("""{"name": "X", "light": {}}""").single().contains("schemaVersion is missing"))
    }

    @Test
    fun nameRules() {
        assertTrue(errors("""{"schemaVersion": 2, "light": {}}""").contains("name is missing."))
        assertTrue(errors("""{"schemaVersion": 2, "name": "  ", "light": {}}""").contains("name can't be empty."))
        assertTrue(errors("""{"schemaVersion": 2, "name": 5, "light": {}}""").contains("name must be text."))
        assertTrue(errors("""{"schemaVersion": 2, "name": "${"x".repeat(41)}", "light": {}}""").single().contains("longer than 40"))
    }

    @Test
    fun noColoursSection() {
        assertEquals(listOf("This file has no colours section (light or dark)."), errors("""{"schemaVersion": 2, "name": "X"}"""))
    }

    @Test
    fun invalidColoursNameTheField() {
        val e = errors("""{"schemaVersion": 2, "name": "X", "light": {"ink": "#12345", "muted": 7}, "dark": "blue"}""")
        assertTrue(e.any { it.startsWith("light.ink:") && it.contains("is not a colour like #1F2123") })
        assertTrue(e.any { it.startsWith("light.muted:") })
        assertTrue(e.any { it.startsWith("dark must be an object") })
    }

    @Test
    fun radiusRules() {
        assertTrue(errors("""{"schemaVersion": 2, "name": "X", "light": {}, "shape": {"cornerRadius": 41}}""").single().contains("0 to 40"))
        assertTrue(errors("""{"schemaVersion": 2, "name": "X", "light": {}, "shape": {"cornerRadius": "8"}}""").single().contains("0 to 40"))
        assertEquals(0f, ok("""{"schemaVersion": 2, "name": "X", "light": {}, "shape": {"cornerRadius": 0}}""").cornerRadius)
    }

    @Test
    fun fontRules() {
        assertTrue(errors("""{"schemaVersion": 2, "name": "X", "light": {}, "font": "comic"}""").single().contains("font must be one of"))
    }

    @Test
    fun oversizedFileIsRefused() {
        val big = """{"schemaVersion": 2, "name": "X", "light": {}, "pad": "${"x".repeat(33 * 1024)}"}"""
        assertTrue(errors(big).single().contains("larger than 32 KB"))
    }

    @Test
    fun malformedJson() {
        assertEquals(listOf("This isn't valid JSON."), errors("{not json"))
        assertTrue(errors("[1, 2]").single().contains("must be a JSON object"))
    }

    @Test
    fun parseColorHandlesAlpha() {
        assertEquals(0xFF1E5AA8.toInt(), ThemeParser.parseColor("#1E5AA8"))
        assertEquals(0x731F2123, ThemeParser.parseColor("#731F2123"))
        assertNull(ThemeParser.parseColor("1E5AA8"))
        assertNull(ThemeParser.parseColor("#GGGGGG"))
    }

    @Test
    fun builtinResolution() {
        assertEquals(BuiltinThemes.automatic, BuiltinThemes.resolve(null) { null })
        assertEquals(BuiltinThemes.automatic, BuiltinThemes.resolve(BuiltinThemes.LEGACY_DYNAMIC) { null }) // old Material You id
        assertEquals(ThemeMode.LIGHT, BuiltinThemes.resolve(BuiltinThemes.LIGHT) { null }.mode)
        assertEquals(ThemeMode.DARK, BuiltinThemes.resolve(BuiltinThemes.DARK) { null }.mode)
        assertEquals(BuiltinThemes.automatic, BuiltinThemes.resolve("deleted-theme") { null })
        val imported = ThemeDefaults.spec("Mine")
        val resolved = BuiltinThemes.resolve("t1") { if (it == "t1") imported else null }
        assertEquals(imported, resolved.spec)
        assertEquals(ThemeMode.SYSTEM, resolved.mode)
    }
}
