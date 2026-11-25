package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.entity.CollectionItemEntity
import dev.yxuan.commit.data.db.entity.CollectionSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CollectionZipTest {
    private val png = byteArrayOf(1, 2, 3)

    private fun zip(vararg files: Pair<String, ByteArray>): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, bytes) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return ByteArrayInputStream(out.toByteArray())
    }

    private fun text(s: String) = s.toByteArray()

    @Test
    fun foldersAreGroupsAndFileNamesAreItems() {
        val plan = CollectionZip.parse(zip(
            "Ultra/Ultra White.jpg" to png,
            "Ultra/Ultra_Paradise.png" to png,
            "Juice/Mango Loco.webp" to png,
            "Original.jpg" to png,
            "notes.txt" to text("hi"),
            "__MACOSX/Ultra/._Ultra White.jpg" to png,
            ".DS_Store" to png,
        ))
        assertEquals(listOf("Ultra", "Juice", "Ungrouped"), plan.groups)
        assertEquals(
            listOf(
                ImportItem("Ultra", "Ultra White", "Ultra/Ultra White.jpg", false),
                ImportItem("Ultra", "Ultra Paradise", "Ultra/Ultra_Paradise.png", false),
                ImportItem("Juice", "Mango Loco", "Juice/Mango Loco.webp", false),
                ImportItem("Ungrouped", "Original", "Original.jpg", false),
            ),
            plan.items,
        )
        assertEquals(listOf("Skipped notes.txt"), plan.warnings)
        assertEquals(4, plan.images.size)
    }

    @Test
    fun csvSetsOrderNamesAndCollectedAndAllowsNameOnlyItems() {
        val csv = "group,name,image,collected\r\n" +
            "Ultra,\"Ultra White, Zero Sugar\",Ultra/white.jpg,yes\r\n" +
            "Ultra,Ultra Gold,,0\r\n" +
            "Juice,,Juice/Pipeline Punch.jpg,1\r\n" +
            ",,,\r\n"
        val plan = CollectionZip.parse(zip(
            "items.csv" to text(csv),
            "collection.json" to text("""{ "name": "Monster cans" }"""),
            "Ultra/white.jpg" to png,
            "Juice/Pipeline Punch.jpg" to png,
            "Juice/Khaotic.jpg" to png,
        ))
        assertEquals("Monster cans", plan.name)
        assertEquals(
            listOf(
                ImportItem("Ultra", "Ultra White, Zero Sugar", "Ultra/white.jpg", true),
                ImportItem("Ultra", "Ultra Gold", null, false),
                ImportItem("Juice", "Pipeline Punch", "Juice/Pipeline Punch.jpg", true),
                // Not in the CSV: still added, from its folder and file name.
                ImportItem("Juice", "Khaotic", "Juice/Khaotic.jpg", false),
            ),
            plan.items,
        )
        assertEquals(listOf("Ultra", "Juice"), plan.groups)
    }

    @Test
    fun aSingleWrappingFolderIsLookedThroughButASingleGroupIsKept() {
        val wrapped = CollectionZip.parse(zip("monster-cans/Ultra/a.jpg" to png, "monster-cans/Juice/b.jpg" to png))
        assertEquals(listOf("Ultra", "Juice"), wrapped.groups)
        val oneGroup = CollectionZip.parse(zip("Ultra/a.jpg" to png, "Ultra/b.jpg" to png))
        assertEquals(listOf("Ultra"), oneGroup.groups)
    }

    @Test
    fun csvRowsWithMissingImagesAreWarnedAbout() {
        val plan = CollectionZip.parse(zip("items.csv" to text("name,image\nGhost,nope.jpg\n")))
        assertEquals(listOf(ImportItem("Ungrouped", "Ghost", null, false)), plan.items)
        assertTrue(plan.warnings.single().contains("no image nope.jpg"))
    }

    private fun item(id: String, name: String?, collected: Boolean, order: Int, created: Long) =
        CollectionItemEntity(id = id, updatedAt = 0, groupId = "g", name = name, collected = collected, sortOrder = order, createdAt = created)

    @Test
    fun sortModes() {
        val items = listOf(
            item("a", "Zero Ultra", true, 0, 30),
            item("b", "absolutely zero", false, 1, 10),
            item("c", null, false, 2, 20),
            item("d", "Mango", true, 3, 40),
        )
        fun ids(sort: CollectionSort) = CollectionSorting.sorted(items, sort).map { it.id }
        assertEquals(listOf("a", "b", "c", "d"), ids(CollectionSort.MANUAL))
        assertEquals(listOf("b", "d", "a", "c"), ids(CollectionSort.NAME))
        assertEquals(listOf("b", "c", "a", "d"), ids(CollectionSort.MISSING_FIRST))
        assertEquals(listOf("a", "d", "b", "c"), ids(CollectionSort.COLLECTED_FIRST))
        assertEquals(listOf("d", "a", "c", "b"), ids(CollectionSort.NEWEST))
    }

    @Test
    fun pngIsRecognisedFromItsBytes() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0)
        assertTrue(dev.yxuan.commit.data.photo.CollectionPhotos.isPng(png))
        assertFalse(dev.yxuan.commit.data.photo.CollectionPhotos.isPng(jpeg))
        assertFalse(dev.yxuan.commit.data.photo.CollectionPhotos.isPng(byteArrayOf(0x89.toByte(), 0x50)))
    }
}
