package com.pcexplorer.core.domain.usecase

import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.model.SortBy
import com.pcexplorer.core.domain.model.SortDirection
import com.pcexplorer.core.domain.model.SortOrder
import com.pcexplorer.core.domain.repository.FileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ListFilesUseCaseTest {

    private lateinit var fileRepository: FileRepository
    private lateinit var useCase: ListFilesUseCase

    @Before
    fun setUp() {
        fileRepository = mockk()
        useCase = ListFilesUseCase(fileRepository)
    }

    @Test
    fun `invoke calls repository with path and sortOrder`() = runTest {
        val path = "/documents"
        val sortOrder = SortOrder(SortBy.DATE, SortDirection.DESCENDING)
        coEvery { fileRepository.listFiles(path, sortOrder) } returns Result.success(emptyList())

        useCase(path, sortOrder)

        coVerify { fileRepository.listFiles(path, sortOrder) }
    }

    @Test
    fun `invoke returns files from repository`() = runTest {
        val files = listOf(
            FileItem.file("test.txt", "/test.txt", 100),
            FileItem.directory("folder", "/folder")
        )
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(files)

        val result = useCase("/")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun `invoke returns failure when repository fails`() = runTest {
        val exception = Exception("Network error")
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.failure(exception)

        val result = useCase("/")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `invoke sorts directories before files`() = runTest {
        val files = listOf(
            FileItem.file("b.txt", "/b.txt", 100),
            FileItem.directory("a_dir", "/a_dir"),
            FileItem.file("a.txt", "/a.txt", 200),
            FileItem.directory("z_dir", "/z_dir")
        )
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(files)

        val result = useCase("/", SortOrder(SortBy.NAME, SortDirection.ASCENDING))

        val sortedFiles = result.getOrNull()!!
        assertTrue(sortedFiles[0].isDirectory)
        assertTrue(sortedFiles[1].isDirectory)
        assertFalse(sortedFiles[2].isDirectory)
        assertFalse(sortedFiles[3].isDirectory)
    }

    @Test
    fun `invoke sorts by name ascending`() = runTest {
        val files = listOf(
            FileItem.file("charlie.txt", "/charlie.txt", 100),
            FileItem.file("alpha.txt", "/alpha.txt", 200),
            FileItem.file("bravo.txt", "/bravo.txt", 150)
        )
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(files)

        val result = useCase("/", SortOrder(SortBy.NAME, SortDirection.ASCENDING))

        val sortedFiles = result.getOrNull()!!
        assertEquals("alpha.txt", sortedFiles[0].name)
        assertEquals("bravo.txt", sortedFiles[1].name)
        assertEquals("charlie.txt", sortedFiles[2].name)
    }

    @Test
    fun `invoke sorts by name descending`() = runTest {
        val files = listOf(
            FileItem.file("alpha.txt", "/alpha.txt", 100),
            FileItem.file("charlie.txt", "/charlie.txt", 200),
            FileItem.file("bravo.txt", "/bravo.txt", 150)
        )
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(files)

        val result = useCase("/", SortOrder(SortBy.NAME, SortDirection.DESCENDING))

        val sortedFiles = result.getOrNull()!!
        assertEquals("charlie.txt", sortedFiles[0].name)
        assertEquals("bravo.txt", sortedFiles[1].name)
        assertEquals("alpha.txt", sortedFiles[2].name)
    }

    @Test
    fun `invoke sorts by date ascending`() = runTest {
        val files = listOf(
            FileItem("c.txt", "/c.txt", false, 100, 3000),
            FileItem("a.txt", "/a.txt", false, 100, 1000),
            FileItem("b.txt", "/b.txt", false, 100, 2000)
        )
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(files)

        val result = useCase("/", SortOrder(SortBy.DATE, SortDirection.ASCENDING))

        val sortedFiles = result.getOrNull()!!
        assertEquals(1000L, sortedFiles[0].lastModified)
        assertEquals(2000L, sortedFiles[1].lastModified)
        assertEquals(3000L, sortedFiles[2].lastModified)
    }

    @Test
    fun `invoke sorts by date descending`() = runTest {
        val files = listOf(
            FileItem("a.txt", "/a.txt", false, 100, 1000),
            FileItem("c.txt", "/c.txt", false, 100, 3000),
            FileItem("b.txt", "/b.txt", false, 100, 2000)
        )
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(files)

        val result = useCase("/", SortOrder(SortBy.DATE, SortDirection.DESCENDING))

        val sortedFiles = result.getOrNull()!!
        assertEquals(3000L, sortedFiles[0].lastModified)
        assertEquals(2000L, sortedFiles[1].lastModified)
        assertEquals(1000L, sortedFiles[2].lastModified)
    }

    @Test
    fun `invoke sorts by size ascending`() = runTest {
        val files = listOf(
            FileItem.file("large.txt", "/large.txt", 1000),
            FileItem.file("small.txt", "/small.txt", 100),
            FileItem.file("medium.txt", "/medium.txt", 500)
        )
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(files)

        val result = useCase("/", SortOrder(SortBy.SIZE, SortDirection.ASCENDING))

        val sortedFiles = result.getOrNull()!!
        assertEquals(100L, sortedFiles[0].size)
        assertEquals(500L, sortedFiles[1].size)
        assertEquals(1000L, sortedFiles[2].size)
    }

    @Test
    fun `invoke sorts by size descending`() = runTest {
        val files = listOf(
            FileItem.file("small.txt", "/small.txt", 100),
            FileItem.file("large.txt", "/large.txt", 1000),
            FileItem.file("medium.txt", "/medium.txt", 500)
        )
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(files)

        val result = useCase("/", SortOrder(SortBy.SIZE, SortDirection.DESCENDING))

        val sortedFiles = result.getOrNull()!!
        assertEquals(1000L, sortedFiles[0].size)
        assertEquals(500L, sortedFiles[1].size)
        assertEquals(100L, sortedFiles[2].size)
    }

    @Test
    fun `invoke sorts by type ascending`() = runTest {
        val files = listOf(
            FileItem.file("file.pdf", "/file.pdf", 100),
            FileItem.file("file.txt", "/file.txt", 100),
            FileItem.file("file.doc", "/file.doc", 100)
        )
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(files)

        val result = useCase("/", SortOrder(SortBy.TYPE, SortDirection.ASCENDING))

        val sortedFiles = result.getOrNull()!!
        assertEquals("doc", sortedFiles[0].extension)
        assertEquals("pdf", sortedFiles[1].extension)
        assertEquals("txt", sortedFiles[2].extension)
    }

    @Test
    fun `invoke sorts by type descending`() = runTest {
        val files = listOf(
            FileItem.file("file.doc", "/file.doc", 100),
            FileItem.file("file.txt", "/file.txt", 100),
            FileItem.file("file.pdf", "/file.pdf", 100)
        )
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(files)

        val result = useCase("/", SortOrder(SortBy.TYPE, SortDirection.DESCENDING))

        val sortedFiles = result.getOrNull()!!
        assertEquals("txt", sortedFiles[0].extension)
        assertEquals("pdf", sortedFiles[1].extension)
        assertEquals("doc", sortedFiles[2].extension)
    }

    @Test
    fun `invoke uses default sortOrder when not specified`() = runTest {
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(emptyList())

        useCase("/documents")

        coVerify { fileRepository.listFiles("/documents", SortOrder()) }
    }

    @Test
    fun `invoke handles empty file list`() = runTest {
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(emptyList())

        val result = useCase("/")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun `invoke sorts case-insensitively by name`() = runTest {
        val files = listOf(
            FileItem.file("Zebra.txt", "/Zebra.txt", 100),
            FileItem.file("alpha.txt", "/alpha.txt", 100),
            FileItem.file("Beta.txt", "/Beta.txt", 100)
        )
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(files)

        val result = useCase("/", SortOrder(SortBy.NAME, SortDirection.ASCENDING))

        val sortedFiles = result.getOrNull()!!
        assertEquals("alpha.txt", sortedFiles[0].name)
        assertEquals("Beta.txt", sortedFiles[1].name)
        assertEquals("Zebra.txt", sortedFiles[2].name)
    }

    @Test
    fun `invoke sorts case-insensitively by type`() = runTest {
        val files = listOf(
            FileItem.file("file.TXT", "/file.TXT", 100),
            FileItem.file("file.doc", "/file.doc", 100),
            FileItem.file("file.PDF", "/file.PDF", 100)
        )
        coEvery { fileRepository.listFiles(any(), any()) } returns Result.success(files)

        val result = useCase("/", SortOrder(SortBy.TYPE, SortDirection.ASCENDING))

        val sortedFiles = result.getOrNull()!!
        assertEquals("doc", sortedFiles[0].extension)
        assertEquals("PDF", sortedFiles[1].extension)
        assertEquals("TXT", sortedFiles[2].extension)
    }
}
