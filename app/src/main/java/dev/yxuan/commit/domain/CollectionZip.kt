package dev.yxuan.commit.domain

import java.io.InputStream
import java.util.zip.ZipInputStream

/** One item to create or update from a zip. [imagePath] points into [ImportPlan.images]. */
data class ImportItem(val group: String, val name: String?, val imagePath: String?, val collected: Boolean)

/**
 * What a collection zip contains, before anything is written: the collection [name] (from
 * collection.json, if any), the [groups] in order, the [items] in order, the image bytes by path,
 * and [warnings] for files that were skipped.
 */
data class ImportPlan(
    val name: String?,
    val groups: List<String>,
    val items: List<ImportItem>,
    val images: Map<String, ByteArray>,
    val warnings: List<String>,
)

/**
 * Reads a collection zip (format in docs/collection-zip-format.md):
 *  - a folder is a group; images at the root go into [UNGROUPED];
 *  - an image's file name without its extension is the item's name;
 *  - optional `items.csv` (group,name,image,collected) lists items in order, may name items
 *    without images, and overrides names; images it doesn't mention are still added;
 *  - optional `collection.json` gives the collection's name: {"name": "Stamps"};
 *  - a single folder wrapping everything (as zip tools often add) is looked through;
 *  - `__MACOSX/`, hidden files and anything that isn't an image, the CSV or the JSON are skipped.
 * Pure: no Android, so it is unit-tested directly.
 */
object CollectionZip {
    const val UNGROUPED = "Ungrouped"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    /** Refuse absurd archives rather than run out of memory. */
    private const val MAX_TOTAL_BYTES = 400L * 1024 * 1024

    fun parse(input: InputStream): ImportPlan {
        val files = LinkedHashMap<String, ByteArray>()
        val warnings = mutableListOf<String>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val path = entry.name.replace('\\', '/').trimStart('/')
                if (entry.isDirectory || path.isEmpty()) continue
                val segments = path.split('/')
                if (segments.any { it == "__MACOSX" || it.startsWith(".") || it == ".." }) continue
                val bytes = zip.readBytes()
                total += bytes.size
                require(total <= MAX_TOTAL_BYTES) { "The zip is too large (over 400 MB)." }
                files[path] = bytes
            }
        }
        val prefix = commonWrapper(files.keys)
        val byPath = files.mapKeys { (k, _) -> k.removePrefix(prefix) }

        val name = byPath.entries.firstOrNull { it.key.equals("collection.json", ignoreCase = true) }
            ?.let { jsonName(String(it.value, Charsets.UTF_8)) }
        val images = LinkedHashMap<String, ByteArray>()
        for ((path, bytes) in byPath) {
            val lower = path.lowercase()
            when {
                lower == "items.csv" || lower == "collection.json" -> Unit
                lower.substringAfterLast('.', "") in IMAGE_EXTENSIONS -> images[path] = bytes
                else -> warnings += "Skipped $path"
            }
        }

        val items = mutableListOf<ImportItem>()
        val csv = byPath.entries.firstOrNull { it.key.equals("items.csv", ignoreCase = true) }
        val listed = HashSet<String>()
        if (csv != null) {
            val rows = parseCsv(String(csv.value, Charsets.UTF_8).removePrefix("﻿"))
            val header = rows.firstOrNull()?.map { it.trim().lowercase() }.orEmpty()
            val col = { key: String -> header.indexOf(key) }
            val (gi, ni, ii, ci) = listOf(col("group"), col("name"), col("image"), col("collected"))
            require(ni >= 0 || ii >= 0) { "items.csv needs a header row with at least a name or image column." }
            rows.drop(1).forEachIndexed { i, row ->
                fun at(index: Int) = row.getOrNull(index)?.trim().orEmpty()
                if (row.all { it.isBlank() }) return@forEachIndexed
                val image = at(ii).takeIf { it.isNotEmpty() }?.removePrefix("/")?.let { findImage(it, images.keys) }
                if (at(ii).isNotEmpty() && image == null) warnings += "items.csv row ${i + 2}: no image ${at(ii)}"
                val itemName = at(ni).ifEmpty { image?.let(::stem) ?: "" }.ifEmpty { null }
                if (itemName == null && image == null) {
                    warnings += "items.csv row ${i + 2}: no name or image"
                    return@forEachIndexed
                }
                val group = at(gi).ifEmpty { image?.let(::groupOf) ?: UNGROUPED }
                image?.let(listed::add)
                items += ImportItem(group, itemName, image, at(ci).lowercase() in setOf("1", "true", "yes", "y", "x"))
            }
        }
        // Images not listed in the CSV (or all of them, without one), in archive order.
        for (path in images.keys) if (path !in listed) items += ImportItem(groupOf(path), stem(path), path, false)

        val groups = items.map { it.group }.distinct()
        return ImportPlan(name, groups, items, images.filterKeys { k -> items.any { it.imagePath == k } }, warnings)
    }

    /** "Group/Item one.jpg" → "Group"; "Loose.jpg" → [UNGROUPED]. Deeper folders use the first. */
    private fun groupOf(path: String): String = if ('/' in path) path.substringBefore('/').trim().ifEmpty { UNGROUPED } else UNGROUPED

    /** "Group/Item_one.jpg" → "Item one". */
    private fun stem(path: String): String = path.substringAfterLast('/').substringBeforeLast('.').replace('_', ' ').trim()

    /** A CSV image path, matched exactly or ignoring case. */
    private fun findImage(path: String, images: Set<String>): String? =
        images.firstOrNull { it == path } ?: images.firstOrNull { it.equals(path, ignoreCase = true) }

    /**
     * "monster-cans/" when every file sits inside that one folder and it looks like the real root
     * (it has subfolders, or the CSV / JSON), so a single-group zip ("Group/a.jpg") keeps its group.
     */
    private fun commonWrapper(paths: Collection<String>): String {
        if (paths.isEmpty() || paths.any { '/' !in it }) return ""
        val first = paths.first().substringBefore('/')
        if (paths.any { it.substringBefore('/') != first }) return ""
        val inner = paths.map { it.substringAfter('/') }
        val looksLikeRoot = inner.any { '/' in it } || inner.any { it.equals("items.csv", true) || it.equals("collection.json", true) }
        return if (looksLikeRoot) "$first/" else ""
    }

    private fun jsonName(json: String): String? =
        Regex("\"name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")?.replace("\\\\", "\\")?.trim()?.ifEmpty { null }

    /** RFC 4180-ish: commas, quoted fields with "" escapes, CRLF or LF rows. */
    internal fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == ',' -> { row += field.toString(); field.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.clear()
                    rows += row; row = mutableListOf()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}
