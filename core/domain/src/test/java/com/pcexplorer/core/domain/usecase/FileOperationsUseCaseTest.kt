package com.pcexplorer.core.domain.usecase

import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.model.StorageInfo
import com.pcexplorer.core.domain.repository.FileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FileOperationsUseCaseTest {

    private lateinit var fileRepository: FileRepository
    private lateinit var useCase: FileOperationsUseCase

    @Before
    fun setUp() {
        fileRepository = mockk()
        useCase = FileOperationsUseCase(fileRepository)
    }

    // createFolder tests

    @Test
    fun `createFolder calls repository with path and name`() = runTest {
        val createdFolder = FileItem.directory("NewFolder", "/documents/NewFolder")
        coEvery { fileRepository.createFolder(any(), any()) } returns Result.success(createdFolder)

        useCase.createFolder("/documents", "NewFolder")

        coVerify { fileRepository.createFolder("/documents", "NewFolder") }
    }

    @Test
    fun `createFolder returns created folder`() = runTest {
        val createdFolder = FileItem.directory("NewFolder", "/documents/NewFolder")
        coEvery { fileRepository.createFolder(any(), any()) } returns Result.success(createdFolder)

        val result = useCase.createFolder("/documents", "NewFolder")

        assertTrue(result.isSuccess)
        assertEquals("NewFolder", result.getOrNull()?.name)
    }

    @Test
    fun `createFolder returns failure when repository fails`() = runTest {
        val exception = Exception("Folder already exists")
        coEvery { fileRepository.createFolder(any(), any()) } returns Result.failure(exception)

        val result = useCase.createFolder("/documents", "ExistingFolder")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // rename tests

    @Test
    fun `rename calls repository with path and newName`() = runTest {
        val renamedFile = FileItem.file("newname.txt", "/documents/newname.txt", 100)
        coEvery { fileRepository.rename(any(), any()) } returns Result.success(renamedFile)

        useCase.rename("/documents/oldname.txt", "newname.txt")

        coVerify { fileRepository.rename("/documents/oldname.txt", "newname.txt") }
    }

    @Test
    fun `rename returns renamed file`() = runTest {
        val renamedFile = FileItem.file("newname.txt", "/documents/newname.txt", 100)
        coEvery { fileRepository.rename(any(), any()) } returns Result.success(renamedFile)

        val result = useCase.rename("/documents/oldname.txt", "newname.txt")

        assertTrue(result.isSuccess)
        assertEquals("newname.txt", result.getOrNull()?.name)
    }

    @Test
    fun `rename returns failure when repository fails`() = runTest {
        val exception = Exception("File not found")
        coEvery { fileRepository.rename(any(), any()) } returns Result.failure(exception)

        val result = useCase.rename("/nonexistent.txt", "new.txt")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // delete tests

    @Test
    fun `delete calls repository with paths`() = runTest {
        coEvery { fileRepository.delete(any()) } returns Result.success(Unit)

        val paths = listOf("/file1.txt", "/file2.txt")
        useCase.delete(paths)

        coVerify { fileRepository.delete(paths) }
    }

    @Test
    fun `delete returns success when repository succeeds`() = runTest {
        coEvery { fileRepository.delete(any()) } returns Result.success(Unit)

        val result = useCase.delete(listOf("/file.txt"))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `delete returns failure when repository fails`() = runTest {
        val exception = Exception("Permission denied")
        coEvery { fileRepository.delete(any()) } returns Result.failure(exception)

        val result = useCase.delete(listOf("/protected.txt"))

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `delete handles empty list`() = runTest {
        coEvery { fileRepository.delete(any()) } returns Result.success(Unit)

        val result = useCase.delete(emptyList())

        coVerify { fileRepository.delete(emptyList()) }
        assertTrue(result.isSuccess)
    }

    // getFileInfo tests

    @Test
    fun `getFileInfo calls repository with path`() = runTest {
        val fileInfo = FileItem.file("test.txt", "/test.txt", 100)
        coEvery { fileRepository.getFileInfo(any()) } returns Result.success(fileInfo)

        useCase.getFileInfo("/test.txt")

        coVerify { fileRepository.getFileInfo("/test.txt") }
    }

    @Test
    fun `getFileInfo returns file information`() = runTest {
        val fileInfo = FileItem.file("test.txt", "/test.txt", 100)
        coEvery { fileRepository.getFileInfo(any()) } returns Result.success(fileInfo)

        val result = useCase.getFileInfo("/test.txt")

        assertTrue(result.isSuccess)
        assertEquals("test.txt", result.getOrNull()?.name)
        assertEquals(100L, result.getOrNull()?.size)
    }

    @Test
    fun `getFileInfo returns failure when repository fails`() = runTest {
        val exception = Exception("File not found")
        coEvery { fileRepository.getFileInfo(any()) } returns Result.failure(exception)

        val result = useCase.getFileInfo("/nonexistent.txt")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // getStorageInfo tests

    @Test
    fun `getStorageInfo calls repository with drivePath`() = runTest {
        val storageInfo = StorageInfo(
            totalSpace = 1000000,
            freeSpace = 500000,
            driveLetter = "C:"
        )
        coEvery { fileRepository.getStorageInfo(any()) } returns Result.success(storageInfo)

        useCase.getStorageInfo("C:")

        coVerify { fileRepository.getStorageInfo("C:") }
    }

    @Test
    fun `getStorageInfo returns storage information`() = runTest {
        val storageInfo = StorageInfo(
            totalSpace = 1000000,
            freeSpace = 500000,
            driveLetter = "C:"
        )
        coEvery { fileRepository.getStorageInfo(any()) } returns Result.success(storageInfo)

        val result = useCase.getStorageInfo("C:")

        assertTrue(result.isSuccess)
        assertEquals(1000000L, result.getOrNull()?.totalSpace)
        assertEquals(500000L, result.getOrNull()?.freeSpace)
    }

    @Test
    fun `getStorageInfo uses default empty path when not specified`() = runTest {
        val storageInfo = StorageInfo(totalSpace = 1000, freeSpace = 500)
        coEvery { fileRepository.getStorageInfo(any()) } returns Result.success(storageInfo)

        useCase.getStorageInfo()

        coVerify { fileRepository.getStorageInfo("") }
    }

    @Test
    fun `getStorageInfo returns failure when repository fails`() = runTest {
        val exception = Exception("Drive not found")
        coEvery { fileRepository.getStorageInfo(any()) } returns Result.failure(exception)

        val result = useCase.getStorageInfo("X:")

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // getDrives tests

    @Test
    fun `getDrives calls repository`() = runTest {
        coEvery { fileRepository.getDrives() } returns Result.success(listOf("C:", "D:"))

        useCase.getDrives()

        coVerify { fileRepository.getDrives() }
    }

    @Test
    fun `getDrives returns list of drives`() = runTest {
        val drives = listOf("C:", "D:", "E:")
        coEvery { fileRepository.getDrives() } returns Result.success(drives)

        val result = useCase.getDrives()

        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
        assertEquals(listOf("C:", "D:", "E:"), result.getOrNull())
    }

    @Test
    fun `getDrives returns failure when repository fails`() = runTest {
        val exception = Exception("Cannot enumerate drives")
        coEvery { fileRepository.getDrives() } returns Result.failure(exception)

        val result = useCase.getDrives()

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `getDrives handles empty drive list`() = runTest {
        coEvery { fileRepository.getDrives() } returns Result.success(emptyList())

        val result = useCase.getDrives()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }
}
