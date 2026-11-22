package dev.yxuan.commit.widget

import dev.yxuan.commit.data.db.entity.TodoEntity
import dev.yxuan.commit.domain.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TickedRowsTest {
    private fun item(id: String, due: Long?, done: Boolean) =
        TodoItem(TodoEntity(id = id, updatedAt = 0, commitmentId = "c", title = id), due, false, done)

    @Test
    fun tickedRowsStayWhereTheyWere() {
        // B was the 2nd row (index 1) when ticked; as a chore its next due date now sorts it last.
        val items = listOf(item("A", 1, false), item("B", 99, true), item("C", 3, false), item("D", null, false))
        assertEquals(listOf("A", "B", "C", "D"), withTickedRows(items, mapOf("B" to 1)).map { it.todo.id })
    }

    @Test
    fun doneItemsNotTickedHereAreHiddenAndOpenOnesSorted() {
        val items = listOf(item("C", 3, false), item("X", 2, true), item("A", 1, false))
        assertEquals(listOf("A", "C"), withTickedRows(items, emptyMap()).map { it.todo.id })
    }

    @Test
    fun positionsPastTheEndAreClamped() {
        val items = listOf(item("A", 1, false), item("B", 2, true))
        assertEquals(listOf("A", "B"), withTickedRows(items, mapOf("B" to 7)).map { it.todo.id })
    }
}
