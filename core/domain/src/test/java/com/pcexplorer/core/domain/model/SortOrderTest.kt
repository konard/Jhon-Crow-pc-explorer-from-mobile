package com.pcexplorer.core.domain.model

import org.junit.Assert.*
import org.junit.Test

class SortOrderTest {

    @Test
    fun `SortOrder default values`() {
        val sortOrder = SortOrder()

        assertEquals(SortBy.NAME, sortOrder.sortBy)
        assertEquals(SortDirection.ASCENDING, sortOrder.direction)
    }

    @Test
    fun `SortOrder with custom sortBy`() {
        val sortOrder = SortOrder(sortBy = SortBy.DATE)

        assertEquals(SortBy.DATE, sortOrder.sortBy)
        assertEquals(SortDirection.ASCENDING, sortOrder.direction)
    }

    @Test
    fun `SortOrder with custom direction`() {
        val sortOrder = SortOrder(direction = SortDirection.DESCENDING)

        assertEquals(SortBy.NAME, sortOrder.sortBy)
        assertEquals(SortDirection.DESCENDING, sortOrder.direction)
    }

    @Test
    fun `SortOrder with all custom values`() {
        val sortOrder = SortOrder(
            sortBy = SortBy.SIZE,
            direction = SortDirection.DESCENDING
        )

        assertEquals(SortBy.SIZE, sortOrder.sortBy)
        assertEquals(SortDirection.DESCENDING, sortOrder.direction)
    }

    @Test
    fun `toggle changes ascending to descending`() {
        val sortOrder = SortOrder(
            sortBy = SortBy.NAME,
            direction = SortDirection.ASCENDING
        )

        val toggled = sortOrder.toggle()

        assertEquals(SortBy.NAME, toggled.sortBy)
        assertEquals(SortDirection.DESCENDING, toggled.direction)
    }

    @Test
    fun `toggle changes descending to ascending`() {
        val sortOrder = SortOrder(
            sortBy = SortBy.NAME,
            direction = SortDirection.DESCENDING
        )

        val toggled = sortOrder.toggle()

        assertEquals(SortBy.NAME, toggled.sortBy)
        assertEquals(SortDirection.ASCENDING, toggled.direction)
    }

    @Test
    fun `toggle preserves sortBy`() {
        val sortOrder = SortOrder(
            sortBy = SortBy.DATE,
            direction = SortDirection.ASCENDING
        )

        val toggled = sortOrder.toggle()

        assertEquals(SortBy.DATE, toggled.sortBy)
    }

    @Test
    fun `double toggle returns to original direction`() {
        val original = SortOrder(
            sortBy = SortBy.SIZE,
            direction = SortDirection.ASCENDING
        )

        val doubleToggled = original.toggle().toggle()

        assertEquals(original.direction, doubleToggled.direction)
    }

    @Test
    fun `SortBy enum contains all expected values`() {
        val values = SortBy.values()

        assertEquals(4, values.size)
        assertTrue(values.contains(SortBy.NAME))
        assertTrue(values.contains(SortBy.DATE))
        assertTrue(values.contains(SortBy.SIZE))
        assertTrue(values.contains(SortBy.TYPE))
    }

    @Test
    fun `SortDirection enum contains all expected values`() {
        val values = SortDirection.values()

        assertEquals(2, values.size)
        assertTrue(values.contains(SortDirection.ASCENDING))
        assertTrue(values.contains(SortDirection.DESCENDING))
    }

    @Test
    fun `SortBy valueOf works correctly`() {
        assertEquals(SortBy.NAME, SortBy.valueOf("NAME"))
        assertEquals(SortBy.DATE, SortBy.valueOf("DATE"))
        assertEquals(SortBy.SIZE, SortBy.valueOf("SIZE"))
        assertEquals(SortBy.TYPE, SortBy.valueOf("TYPE"))
    }

    @Test
    fun `SortDirection valueOf works correctly`() {
        assertEquals(SortDirection.ASCENDING, SortDirection.valueOf("ASCENDING"))
        assertEquals(SortDirection.DESCENDING, SortDirection.valueOf("DESCENDING"))
    }

    @Test
    fun `SortOrder data class equality works correctly`() {
        val sortOrder1 = SortOrder(SortBy.NAME, SortDirection.ASCENDING)
        val sortOrder2 = SortOrder(SortBy.NAME, SortDirection.ASCENDING)

        assertEquals(sortOrder1, sortOrder2)
        assertEquals(sortOrder1.hashCode(), sortOrder2.hashCode())
    }

    @Test
    fun `SortOrder data class inequality for different sortBy`() {
        val sortOrder1 = SortOrder(SortBy.NAME, SortDirection.ASCENDING)
        val sortOrder2 = SortOrder(SortBy.DATE, SortDirection.ASCENDING)

        assertNotEquals(sortOrder1, sortOrder2)
    }

    @Test
    fun `SortOrder data class inequality for different direction`() {
        val sortOrder1 = SortOrder(SortBy.NAME, SortDirection.ASCENDING)
        val sortOrder2 = SortOrder(SortBy.NAME, SortDirection.DESCENDING)

        assertNotEquals(sortOrder1, sortOrder2)
    }

    @Test
    fun `SortOrder copy preserves values`() {
        val original = SortOrder(SortBy.DATE, SortDirection.DESCENDING)
        val copy = original.copy()

        assertEquals(original, copy)
    }

    @Test
    fun `SortOrder copy with new sortBy`() {
        val original = SortOrder(SortBy.DATE, SortDirection.DESCENDING)
        val copy = original.copy(sortBy = SortBy.SIZE)

        assertEquals(SortBy.SIZE, copy.sortBy)
        assertEquals(SortDirection.DESCENDING, copy.direction)
    }
}
