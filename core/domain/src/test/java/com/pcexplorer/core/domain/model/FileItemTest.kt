package com.pcexplorer.core.domain.model

import org.junit.Assert.*
import org.junit.Test

class FileItemTest {

    @Test
    fun `FileItem extracts extension from filename`() {
        val file = FileItem(
            name = "document.pdf",
            path = "/documents/document.pdf",
            isDirectory = false,
            size = 1024,
            lastModified = 0
        )

        assertEquals("pdf", file.extension)
    }

    @Test
    fun `FileItem returns empty extension for file without extension`() {
        val file = FileItem(
            name = "README",
            path = "/README",
            isDirectory = false,
            size = 100,
            lastModified = 0
        )

        assertEquals("", file.extension)
    }

    @Test
    fun `FileItem handles multiple dots in filename`() {
        val file = FileItem(
            name = "archive.tar.gz",
            path = "/archive.tar.gz",
            isDirectory = false,
            size = 2048,
            lastModified = 0
        )

        assertEquals("gz", file.extension)
    }

    @Test
    fun `FileItem isFile returns true for non-directory`() {
        val file = FileItem(
            name = "test.txt",
            path = "/test.txt",
            isDirectory = false,
            size = 100,
            lastModified = 0
        )

        assertTrue(file.isFile)
        assertFalse(file.isDirectory)
    }

    @Test
    fun `FileItem isFile returns false for directory`() {
        val dir = FileItem(
            name = "folder",
            path = "/folder",
            isDirectory = true,
            size = 0,
            lastModified = 0
        )

        assertFalse(dir.isFile)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `formattedSize returns bytes for small files`() {
        val file = FileItem(
            name = "tiny.txt",
            path = "/tiny.txt",
            isDirectory = false,
            size = 512,
            lastModified = 0
        )

        assertEquals("512 B", file.formattedSize)
    }

    @Test
    fun `formattedSize returns KB for kilobyte files`() {
        val file = FileItem(
            name = "small.txt",
            path = "/small.txt",
            isDirectory = false,
            size = 2048, // 2 KB
            lastModified = 0
        )

        assertEquals("2 KB", file.formattedSize)
    }

    @Test
    fun `formattedSize returns MB for megabyte files`() {
        val file = FileItem(
            name = "medium.zip",
            path = "/medium.zip",
            isDirectory = false,
            size = 5 * 1024 * 1024, // 5 MB
            lastModified = 0
        )

        assertEquals("5 MB", file.formattedSize)
    }

    @Test
    fun `formattedSize returns GB for gigabyte files`() {
        val file = FileItem(
            name = "large.iso",
            path = "/large.iso",
            isDirectory = false,
            size = 2L * 1024 * 1024 * 1024, // 2 GB
            lastModified = 0
        )

        assertEquals("2.00 GB", file.formattedSize)
    }

    @Test
    fun `formattedSize handles zero size`() {
        val file = FileItem(
            name = "empty.txt",
            path = "/empty.txt",
            isDirectory = false,
            size = 0,
            lastModified = 0
        )

        assertEquals("0 B", file.formattedSize)
    }

    @Test
    fun `formattedSize handles exact boundary at 1024`() {
        val file = FileItem(
            name = "boundary.txt",
            path = "/boundary.txt",
            isDirectory = false,
            size = 1024,
            lastModified = 0
        )

        assertEquals("1 KB", file.formattedSize)
    }

    @Test
    fun `directory factory creates directory FileItem`() {
        val dir = FileItem.directory(
            name = "Documents",
            path = "/home/user/Documents",
            lastModified = 1234567890L
        )

        assertEquals("Documents", dir.name)
        assertEquals("/home/user/Documents", dir.path)
        assertTrue(dir.isDirectory)
        assertEquals(0, dir.size)
        assertEquals(1234567890L, dir.lastModified)
    }

    @Test
    fun `file factory creates file FileItem`() {
        val file = FileItem.file(
            name = "photo.jpg",
            path = "/photos/photo.jpg",
            size = 4096,
            lastModified = 1234567890L
        )

        assertEquals("photo.jpg", file.name)
        assertEquals("/photos/photo.jpg", file.path)
        assertFalse(file.isDirectory)
        assertEquals(4096, file.size)
        assertEquals(1234567890L, file.lastModified)
        assertEquals("jpg", file.extension)
    }

    @Test
    fun `file factory uses default lastModified`() {
        val file = FileItem.file(
            name = "test.txt",
            path = "/test.txt",
            size = 100
        )

        assertEquals(0L, file.lastModified)
    }

    @Test
    fun `directory factory uses default lastModified`() {
        val dir = FileItem.directory(
            name = "folder",
            path = "/folder"
        )

        assertEquals(0L, dir.lastModified)
    }

    @Test
    fun `FileItem with custom mimeType`() {
        val file = FileItem(
            name = "video.mp4",
            path = "/video.mp4",
            isDirectory = false,
            size = 10000,
            lastModified = 0,
            mimeType = "video/mp4"
        )

        assertEquals("video/mp4", file.mimeType)
    }

    @Test
    fun `FileItem default mimeType is empty`() {
        val file = FileItem(
            name = "test.txt",
            path = "/test.txt",
            isDirectory = false,
            size = 100,
            lastModified = 0
        )

        assertEquals("", file.mimeType)
    }

    @Test
    fun `FileItem data class equality works correctly`() {
        val file1 = FileItem(
            name = "test.txt",
            path = "/test.txt",
            isDirectory = false,
            size = 100,
            lastModified = 12345
        )

        val file2 = FileItem(
            name = "test.txt",
            path = "/test.txt",
            isDirectory = false,
            size = 100,
            lastModified = 12345
        )

        assertEquals(file1, file2)
        assertEquals(file1.hashCode(), file2.hashCode())
    }

    @Test
    fun `FileItem copy preserves values`() {
        val original = FileItem(
            name = "test.txt",
            path = "/test.txt",
            isDirectory = false,
            size = 100,
            lastModified = 12345
        )

        val copy = original.copy(name = "renamed.txt")

        assertEquals("renamed.txt", copy.name)
        assertEquals("/test.txt", copy.path)
        assertEquals(100, copy.size)
    }
}
