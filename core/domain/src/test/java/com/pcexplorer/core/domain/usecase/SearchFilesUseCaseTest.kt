package com.pcexplorer.core.domain.usecase

import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.repository.FileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SearchFilesUseCaseTest {

    private lateinit var fileRepository: FileRepository
    private lateinit var useCase: SearchFilesUseCase

    @Before
    fun setUp() {
        fileRepository = mockk()
        useCase = SearchFilesUseCase(fileRepository)
    }

    @Test
    fun `invoke with blank query returns empty list`() = runTest {
        val result = useCase("   ")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
        coVerify(exactly = 0) { fileRepository.searchFiles(any(), any()) }
    }

    @Test
    fun `invoke with empty query returns empty list`() = runTest {
        val result = useCase("")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
        coVerify(exactly = 0) { fileRepository.searchFiles(any(), any()) }
    }

    @Test
    fun `invoke trims query before searching`() = runTest {
        coEvery { fileRepository.searchFiles(any(), any()) } returns Result.success(emptyList())

        useCase("  test  ", "/path")

        coVerify { fileRepository.searchFiles("test", "/path") }
    }

    @Test
    fun `invoke calls repository with query and path`() = runTest {
        coEvery { fileRepository.searchFiles(any(), any()) } returns Result.success(emptyList())

        useCase("document", "/documents")

        coVerify { fileRepository.searchFiles("document", "/documents") }
    }

    @Test
    fun `invoke uses default empty path when not specified`() = runTest {
        coEvery { fileRepository.searchFiles(any(), any()) } returns Result.success(emptyList())

        useCase("query")

        coVerify { fileRepository.searchFiles("query", "") }
    }

    @Test
    fun `invoke returns files from repository`() = runTest {
        val files = listOf(
            FileItem.file("document.pdf", "/docs/document.pdf", 1000),
            FileItem.file("document.txt", "/docs/document.txt", 500)
        )
        coEvery { fileRepository.searchFiles(any(), any()) } returns Result.success(files)

        val result = useCase("document")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun `invoke returns failure when repository fails`() = runTest {
        val exception = Exception("Search error")
        coEvery { fileRepository.searchFiles(any(), any()) } returns Result.failure(exception)

        val result = useCase("query")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `invoke handles empty search results`() = runTest {
        coEvery { fileRepository.searchFiles(any(), any()) } returns Result.success(emptyList())

        val result = useCase("nonexistent")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun `invoke with only whitespace query returns empty list`() = runTest {
        val result = useCase("\t\n  ")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun `invoke preserves query with internal spaces`() = runTest {
        coEvery { fileRepository.searchFiles(any(), any()) } returns Result.success(emptyList())

        useCase("  hello world  ")

        coVerify { fileRepository.searchFiles("hello world", "") }
    }
}
