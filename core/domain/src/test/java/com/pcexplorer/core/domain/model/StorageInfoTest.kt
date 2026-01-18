package com.pcexplorer.core.domain.model

import org.junit.Assert.*
import org.junit.Test

class StorageInfoTest {

    @Test
    fun `usedSpace is calculated from totalSpace and freeSpace`() {
        val storage = StorageInfo(
            totalSpace = 1000,
            freeSpace = 300
        )

        assertEquals(700, storage.usedSpace)
    }

    @Test
    fun `usagePercent calculates correct percentage`() {
        val storage = StorageInfo(
            totalSpace = 1000,
            freeSpace = 250
        )

        assertEquals(0.75f, storage.usagePercent, 0.001f)
    }

    @Test
    fun `usagePercent returns 0 for zero totalSpace`() {
        val storage = StorageInfo(
            totalSpace = 0,
            freeSpace = 0
        )

        assertEquals(0f, storage.usagePercent, 0.001f)
    }

    @Test
    fun `usagePercent handles full storage`() {
        val storage = StorageInfo(
            totalSpace = 1000,
            freeSpace = 0
        )

        assertEquals(1.0f, storage.usagePercent, 0.001f)
    }

    @Test
    fun `usagePercent handles empty storage`() {
        val storage = StorageInfo(
            totalSpace = 1000,
            freeSpace = 1000
        )

        assertEquals(0f, storage.usagePercent, 0.001f)
    }

    @Test
    fun `formattedTotal returns bytes for small values`() {
        val storage = StorageInfo(
            totalSpace = 512,
            freeSpace = 256
        )

        assertEquals("512 B", storage.formattedTotal)
    }

    @Test
    fun `formattedTotal returns KB for kilobyte values`() {
        val storage = StorageInfo(
            totalSpace = 5 * 1024,
            freeSpace = 1024
        )

        assertEquals("5 KB", storage.formattedTotal)
    }

    @Test
    fun `formattedTotal returns MB for megabyte values`() {
        val storage = StorageInfo(
            totalSpace = 100L * 1024 * 1024,
            freeSpace = 50L * 1024 * 1024
        )

        assertEquals("100 MB", storage.formattedTotal)
    }

    @Test
    fun `formattedTotal returns GB for gigabyte values`() {
        val storage = StorageInfo(
            totalSpace = 500L * 1024 * 1024 * 1024,
            freeSpace = 100L * 1024 * 1024 * 1024
        )

        assertEquals("500.00 GB", storage.formattedTotal)
    }

    @Test
    fun `formattedFree formats free space correctly`() {
        val storage = StorageInfo(
            totalSpace = 1000L * 1024 * 1024,
            freeSpace = 250L * 1024 * 1024
        )

        assertEquals("250 MB", storage.formattedFree)
    }

    @Test
    fun `formattedUsed formats used space correctly`() {
        val storage = StorageInfo(
            totalSpace = 1000L * 1024 * 1024,
            freeSpace = 250L * 1024 * 1024
        )

        assertEquals("750 MB", storage.formattedUsed)
    }

    @Test
    fun `StorageInfo with driveLetter`() {
        val storage = StorageInfo(
            totalSpace = 1000,
            freeSpace = 500,
            driveLetter = "C:"
        )

        assertEquals("C:", storage.driveLetter)
    }

    @Test
    fun `StorageInfo with volumeName`() {
        val storage = StorageInfo(
            totalSpace = 1000,
            freeSpace = 500,
            volumeName = "System Drive"
        )

        assertEquals("System Drive", storage.volumeName)
    }

    @Test
    fun `StorageInfo default driveLetter is empty`() {
        val storage = StorageInfo(
            totalSpace = 1000,
            freeSpace = 500
        )

        assertEquals("", storage.driveLetter)
    }

    @Test
    fun `StorageInfo default volumeName is empty`() {
        val storage = StorageInfo(
            totalSpace = 1000,
            freeSpace = 500
        )

        assertEquals("", storage.volumeName)
    }

    @Test
    fun `StorageInfo with custom usedSpace overrides calculation`() {
        val storage = StorageInfo(
            totalSpace = 1000,
            freeSpace = 300,
            usedSpace = 600 // Override default calculation
        )

        assertEquals(600, storage.usedSpace)
    }

    @Test
    fun `StorageInfo data class equality works correctly`() {
        val storage1 = StorageInfo(
            totalSpace = 1000,
            freeSpace = 300,
            driveLetter = "D:",
            volumeName = "Data"
        )

        val storage2 = StorageInfo(
            totalSpace = 1000,
            freeSpace = 300,
            driveLetter = "D:",
            volumeName = "Data"
        )

        assertEquals(storage1, storage2)
        assertEquals(storage1.hashCode(), storage2.hashCode())
    }

    @Test
    fun `StorageInfo copy preserves values`() {
        val original = StorageInfo(
            totalSpace = 1000,
            freeSpace = 300,
            driveLetter = "C:",
            volumeName = "System"
        )

        val copy = original.copy(freeSpace = 500)

        assertEquals(1000, copy.totalSpace)
        assertEquals(500, copy.freeSpace)
        assertEquals("C:", copy.driveLetter)
        assertEquals("System", copy.volumeName)
    }

    @Test
    fun `formattedTotal handles boundary at 1024`() {
        val storage = StorageInfo(
            totalSpace = 1024,
            freeSpace = 0
        )

        assertEquals("1 KB", storage.formattedTotal)
    }

    @Test
    fun `formattedTotal handles zero`() {
        val storage = StorageInfo(
            totalSpace = 0,
            freeSpace = 0
        )

        assertEquals("0 B", storage.formattedTotal)
    }
}
