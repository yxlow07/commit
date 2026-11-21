package dev.yxuan.commit.domain.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeParserTest {

    @Test
    fun parseOceanExample() {
        val file = File("../themes/examples/ocean.json").takeIf { it.exists() }
            ?: File("themes/examples/ocean.json")
        val json = file.readText()
        val result = ThemeParser.parse(json)
        assertTrue(result is ThemeParseResult.Ok)
        val spec = (result as ThemeParseResult.Ok).spec
        assertEquals("Ocean", spec.name)
        assertEquals(ThemeParser.parseColor("#1E5AA8"), spec.light.primary)
        assertEquals(ThemeParser.parseColor("#A4C8FF"), spec.dark.primary)
        assertEquals(20f, spec.cornerRadius)
        assertEquals(ThemeFont.ROUNDED, spec.font)
    }

    @Test
    fun parseForestExampleWithDefaultsFilled() {
        val file = File("../themes/examples/forest.json").takeIf { it.exists() }
            ?: File("themes/examples/forest.json")
        val json = file.readText()
        val result = ThemeParser.parse(json)
        assertTrue(result is ThemeParseResult.Ok)
        val spec = (result as ThemeParseResult.Ok).spec
        assertEquals("Forest", spec.name)
        assertEquals(ThemeParser.parseColor("#2E6B30"), spec.light.primary)
        // dark set was missing entirely, filled from ThemeDefaults.dark
        assertEquals(ThemeDefaults.dark.primary, spec.dark.primary)
        assertEquals(ThemeDefaults.dark.background, spec.dark.background)
        // missing light.error filled from ThemeDefaults.light
        assertEquals(ThemeDefaults.light.error, spec.light.error)
        assertEquals(8f, spec.cornerRadius)
        assertEquals(ThemeFont.SERIF, spec.font)
    }

    @Test
    fun parseHighContrastExample() {
        val file = File("../themes/examples/high-contrast.json").takeIf { it.exists() }
            ?: File("themes/examples/high-contrast.json")
        val json = file.readText()
        val result = ThemeParser.parse(json)
        assertTrue(result is ThemeParseResult.Ok)
        val spec = (result as ThemeParseResult.Ok).spec
        assertEquals("High contrast", spec.name)
        assertEquals(4f, spec.cornerRadius)
        assertEquals(ThemeFont.DEFAULT, spec.font)
    }

    @Test
    fun rejectUnsupportedSchemaVersion() {
        val json = """{"schemaVersion": 2, "name": "Test"}"""
        val result = ThemeParser.parse(json)
        assertTrue(result is ThemeParseResult.Invalid)
        val errors = (result as ThemeParseResult.Invalid).errors
        assertTrue(errors.any { it.contains("schemaVersion 2 isn't supported") })
    }

    @Test
    fun rejectMissingSchemaVersion() {
        val json = """{"name": "Test"}"""
        val result = ThemeParser.parse(json)
        assertTrue(result is ThemeParseResult.Invalid)
        val errors = (result as ThemeParseResult.Invalid).errors
        assertTrue(errors.any { it.contains("schemaVersion is missing") })
    }

    @Test
    fun rejectInvalidName() {
        val emptyName = """{"schemaVersion": 1, "name": "   "}"""
        val r1 = ThemeParser.parse(emptyName) as ThemeParseResult.Invalid
        assertTrue(r1.errors.any { it.contains("name can't be empty") })

        val longName = """{"schemaVersion": 1, "name": "${"a".repeat(41)}"}"""
        val r2 = ThemeParser.parse(longName) as ThemeParseResult.Invalid
        assertTrue(r2.errors.any { it.contains("name is longer than 40 characters") })

        val missingName = """{"schemaVersion": 1}"""
        val r3 = ThemeParser.parse(missingName) as ThemeParseResult.Invalid
        assertTrue(r3.errors.any { it.contains("name is missing") })
    }

    @Test
    fun rejectInvalidColors() {
        val json = """{
            "schemaVersion": 1,
            "name": "Bad Color",
            "light": {"primary": "#12345"}
        }"""
        val result = ThemeParser.parse(json) as ThemeParseResult.Invalid
        assertTrue(result.errors.any { it.contains("light.primary: \"#12345\" is not a colour like #1E5AA8.") })
    }

    @Test
    fun rejectInvalidCornerRadius() {
        val negative = """{"schemaVersion": 1, "name": "Negative", "shape": {"cornerRadius": -1}}"""
        val r1 = ThemeParser.parse(negative) as ThemeParseResult.Invalid
        assertTrue(r1.errors.any { it.contains("shape.cornerRadius must be a number from 0 to 32") })

        val tooLarge = """{"schemaVersion": 1, "name": "Too Big", "shape": {"cornerRadius": 33}}"""
        val r2 = ThemeParser.parse(tooLarge) as ThemeParseResult.Invalid
        assertTrue(r2.errors.any { it.contains("shape.cornerRadius must be a number from 0 to 32") })
    }

    @Test
    fun rejectInvalidFont() {
        val json = """{"schemaVersion": 1, "name": "Bad Font", "font": "comic-sans"}"""
        val result = ThemeParser.parse(json) as ThemeParseResult.Invalid
        assertTrue(result.errors.any { it.contains("font must be one of default, serif, mono or rounded") })
    }

    @Test
    fun rejectOversizedFile() {
        val json = """{"schemaVersion": 1, "name": "Big", "extra": "${"x".repeat(33 * 1024)}"}"""
        val result = ThemeParser.parse(json) as ThemeParseResult.Invalid
        assertTrue(result.errors.any { it.contains("larger than 32 KB") })
    }

    @Test
    fun rejectMalformedJson() {
        val r1 = ThemeParser.parse("not json") as ThemeParseResult.Invalid
        assertTrue(r1.errors.any { it.contains("isn't valid JSON") })

        val r2 = ThemeParser.parse("[1, 2, 3]") as ThemeParseResult.Invalid
        assertTrue(r2.errors.any { it.contains("must be a JSON object") })
    }

    @Test
    fun parseColorFormats() {
        assertEquals(0xFF1E5AA8.toInt(), ThemeParser.parseColor("#1E5AA8"))
        assertEquals(0x801E5AA8.toInt(), ThemeParser.parseColor("#801E5AA8"))
        assertNull(ThemeParser.parseColor("#12345"))
        assertNull(ThemeParser.parseColor("1E5AA8"))
        assertNull(ThemeParser.parseColor("#ZZZZZZ"))
    }

    @Test
    fun builtinThemesResolve() {
        assertEquals(ActiveTheme.Dynamic, BuiltinThemes.resolve(null) { null })
        assertEquals(ActiveTheme.Dynamic, BuiltinThemes.resolve(BuiltinThemes.DYNAMIC) { null })
        assertEquals(BuiltinThemes.light, BuiltinThemes.resolve(BuiltinThemes.LIGHT) { null })
        assertEquals(BuiltinThemes.dark, BuiltinThemes.resolve(BuiltinThemes.DARK) { null })

        val customSpec = ThemeDefaults.spec("Custom")
        val resolvedCustom = BuiltinThemes.resolve("custom-1") { id -> if (id == "custom-1") customSpec else null }
        assertTrue(resolvedCustom is ActiveTheme.Fixed)
        assertEquals("custom-1", resolvedCustom.id)
        assertEquals(ThemeMode.SYSTEM, (resolvedCustom as ActiveTheme.Fixed).mode)

        // Missing custom theme falls back to Dynamic
        val missingCustom = BuiltinThemes.resolve("missing") { null }
        assertEquals(ActiveTheme.Dynamic, missingCustom)
    }
}
