package com.pcexplorer.features.browser

import com.pcexplorer.core.domain.model.FileItem
import com.pcexplorer.core.domain.model.SortBy
import com.pcexplorer.core.domain.model.SortOrder
import com.pcexplorer.core.domain.model.StorageInfo
import com.pcexplorer.core.domain.usecase.FileOperationsUseCase
import com.pcexplorer.core.domain.usecase.ListFilesUseCase
import com.pcexplorer.core.domain.usecase.SearchFilesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    private lateinit var listFilesUseCase: ListFilesUseCase
    private lateinit var searchFilesUseCase: SearchFilesUseCase
    private lateinit var fileOperationsUseCase: FileOperationsUseCase
    private lateinit var viewModel: BrowserViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        listFilesUseCase = mockk()
        searchFilesUseCase = mockk()
        fileOperationsUseCase = mockk()

        // Default mocks for init
        coEvery { fileOperationsUseCase.getDrives() } returns Result.success(listOf("C:"))
        coEvery { listFilesUseCase(any(), any()) } returns Result.success(emptyList())
        coEvery { fileOperationsUseCase.getStorageInfo(any()) } returns Result.success(
            StorageInfo(totalSpace = 1000000, freeSpace = 500000, driveLetter = "C:", volumeName = "Windows")
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BrowserViewModel {
        return BrowserViewModel(listFilesUseCase, searchFilesUseCase, fileOperationsUseCase)
    }

    // Initialization tests

    @Test
    fun `init loads drives`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { fileOperationsUseCase.getDrives() }
    }

    @Test
    fun `init sets first drive as current path`() = runTest {
        coEvery { fileOperationsUseCase.getDrives() } returns Result.success(listOf("C:", "D:"))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("C:", viewModel.uiState.value.currentPath)
        assertEquals(listOf("C:", "D:"), viewModel.uiState.value.drives)
    }

    @Test
    fun `init loads files from first drive`() = runTest {
        val files = listOf(
            FileItem.file("file1.txt", "/file1.txt", 100),
            FileItem.directory("folder", "/folder")
        )
        coEvery { listFilesUseCase(any(), any()) } returns Result.success(files)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.files.size)
    }

    @Test
    fun `init handles getDrives failure`() = runTest {
        coEvery { fileOperationsUseCase.getDrives() } returns Result.failure(Exception("No drives"))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("No drives", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // loadFiles tests

    @Test
    fun `loadFiles updates files in state`() = runTest {
        val files = listOf(
            FileItem.file("doc.pdf", "/docs/doc.pdf", 2048),
            FileItem.directory("images", "/docs/images")
        )
        coEvery { listFilesUseCase("/docs", any()) } returns Result.success(files)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadFiles("/docs")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.files.size)
        assertEquals("/docs", viewModel.uiState.value.currentPath)
    }

    @Test
    fun `loadFiles sets loading state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { listFilesUseCase(any(), any()) } coAnswers {
            assertTrue(viewModel.uiState.value.isLoading)
            Result.success(emptyList())
        }

        viewModel.loadFiles("/test")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadFiles handles failure`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { listFilesUseCase("/error", any()) } returns Result.failure(Exception("Access denied"))

        viewModel.loadFiles("/error")
        advanceUntilIdle()

        assertEquals("Access denied", viewModel.uiState.value.error)
    }

    @Test
    fun `loadFiles clears previous error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { listFilesUseCase("/error", any()) } returns Result.failure(Exception("Error"))
        viewModel.loadFiles("/error")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        coEvery { listFilesUseCase("/success", any()) } returns Result.success(emptyList())
        viewModel.loadFiles("/success")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
    }

    // navigateUp tests

    @Test
    fun `navigateUp returns false when at root`() = runTest {
        coEvery { fileOperationsUseCase.getDrives() } returns Result.success(listOf("C:"))

        viewModel = createViewModel()
        advanceUntilIdle()

        val result = viewModel.navigateUp()

        assertFalse(result)
    }

    @Test
    fun `navigateUp returns true and loads parent when in subfolder`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { listFilesUseCase("/docs/subfolder", any()) } returns Result.success(emptyList())
        viewModel.loadFiles("/docs/subfolder")
        advanceUntilIdle()

        coEvery { listFilesUseCase("/docs", any()) } returns Result.success(emptyList())
        val result = viewModel.navigateUp()
        advanceUntilIdle()

        assertTrue(result)
        coVerify { listFilesUseCase("/docs", any()) }
    }

    // openItem tests

    @Test
    fun `openItem loads directory contents when item is directory`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val directory = FileItem.directory("subfolder", "/docs/subfolder")
        coEvery { listFilesUseCase("/docs/subfolder", any()) } returns Result.success(emptyList())

        viewModel.openItem(directory)
        advanceUntilIdle()

        coVerify { listFilesUseCase("/docs/subfolder", any()) }
    }

    @Test
    fun `openItem does nothing for files`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val file = FileItem.file("document.pdf", "/docs/document.pdf", 1024)

        viewModel.openItem(file)
        advanceUntilIdle()

        // Should not load files for the file path
        coVerify(exactly = 1) { listFilesUseCase(any(), any()) } // Only the initial load
    }

    // search tests

    @Test
    fun `search updates files and sets searching state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val searchResults = listOf(
            FileItem.file("test1.txt", "/test1.txt", 100),
            FileItem.file("test2.txt", "/subfolder/test2.txt", 200)
        )
        coEvery { searchFilesUseCase("test", any()) } returns Result.success(searchResults)

        viewModel.search("test")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.files.size)
        assertTrue(viewModel.uiState.value.isSearching)
        assertEquals("test", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `search with empty query loads files`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("   ")
        advanceUntilIdle()

        coVerify(atLeast = 2) { listFilesUseCase(any(), any()) }
    }

    @Test
    fun `search handles failure`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { searchFilesUseCase("error", any()) } returns Result.failure(Exception("Search failed"))

        viewModel.search("error")
        advanceUntilIdle()

        assertEquals("Search failed", viewModel.uiState.value.error)
    }

    // setSortOrder tests

    @Test
    fun `setSortOrder changes sort and reloads files`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSortOrder(SortBy.SIZE)
        advanceUntilIdle()

        assertEquals(SortBy.SIZE, viewModel.uiState.value.sortOrder.sortBy)
        coVerify(atLeast = 2) { listFilesUseCase(any(), any()) }
    }

    @Test
    fun `setSortOrder toggles direction when same sort selected`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSortOrder(SortBy.NAME)
        advanceUntilIdle()
        val firstDirection = viewModel.uiState.value.sortOrder.direction

        viewModel.setSortOrder(SortBy.NAME)
        advanceUntilIdle()

        assertNotEquals(firstDirection, viewModel.uiState.value.sortOrder.direction)
    }

    // Selection tests

    @Test
    fun `toggleSelection adds file to selection`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection("/file.txt")

        assertTrue(viewModel.uiState.value.selectedFiles.contains("/file.txt"))
        assertTrue(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun `toggleSelection removes file from selection`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection("/file.txt")
        viewModel.toggleSelection("/file.txt")

        assertFalse(viewModel.uiState.value.selectedFiles.contains("/file.txt"))
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun `selectAll selects all files`() = runTest {
        val files = listOf(
            FileItem.file("file1.txt", "/file1.txt", 100),
            FileItem.file("file2.txt", "/file2.txt", 200)
        )
        coEvery { listFilesUseCase(any(), any()) } returns Result.success(files)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectAll()

        assertEquals(2, viewModel.uiState.value.selectedFiles.size)
        assertTrue(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun `clearSelection clears all selections`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection("/file1.txt")
        viewModel.toggleSelection("/file2.txt")
        viewModel.clearSelection()

        assertTrue(viewModel.uiState.value.selectedFiles.isEmpty())
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }

    // deleteSelected tests

    @Test
    fun `deleteSelected deletes selected files and reloads`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection("/file1.txt")
        viewModel.toggleSelection("/file2.txt")

        coEvery { fileOperationsUseCase.delete(any()) } returns Result.success(Unit)

        viewModel.deleteSelected()
        advanceUntilIdle()

        coVerify { fileOperationsUseCase.delete(listOf("/file1.txt", "/file2.txt")) }
        assertTrue(viewModel.uiState.value.selectedFiles.isEmpty())
    }

    @Test
    fun `deleteSelected does nothing with empty selection`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteSelected()
        advanceUntilIdle()

        coVerify(exactly = 0) { fileOperationsUseCase.delete(any()) }
    }

    @Test
    fun `deleteSelected handles failure`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection("/protected.txt")
        coEvery { fileOperationsUseCase.delete(any()) } returns Result.failure(Exception("Permission denied"))

        viewModel.deleteSelected()
        advanceUntilIdle()

        assertEquals("Permission denied", viewModel.uiState.value.error)
    }

    // createFolder tests

    @Test
    fun `createFolder calls use case and reloads`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val newFolder = FileItem.directory("NewFolder", "/NewFolder")
        coEvery { fileOperationsUseCase.createFolder(any(), any()) } returns Result.success(newFolder)

        viewModel.createFolder("NewFolder")
        advanceUntilIdle()

        coVerify { fileOperationsUseCase.createFolder(any(), "NewFolder") }
    }

    @Test
    fun `createFolder handles failure`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { fileOperationsUseCase.createFolder(any(), any()) } returns Result.failure(Exception("Already exists"))

        viewModel.createFolder("ExistingFolder")
        advanceUntilIdle()

        assertEquals("Already exists", viewModel.uiState.value.error)
    }

    // rename tests

    @Test
    fun `rename calls use case and reloads`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val renamedFile = FileItem.file("new.txt", "/new.txt", 100)
        coEvery { fileOperationsUseCase.rename(any(), any()) } returns Result.success(renamedFile)

        viewModel.rename("/old.txt", "new.txt")
        advanceUntilIdle()

        coVerify { fileOperationsUseCase.rename("/old.txt", "new.txt") }
    }

    // clearError tests

    @Test
    fun `clearError clears error from state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { listFilesUseCase("/error", any()) } returns Result.failure(Exception("Error"))
        viewModel.loadFiles("/error")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }

    // changeDrive tests

    @Test
    fun `changeDrive clears history and loads new drive`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { listFilesUseCase("D:", any()) } returns Result.success(emptyList())

        viewModel.changeDrive("D:")
        advanceUntilIdle()

        assertEquals("D:", viewModel.uiState.value.currentPath)
        assertTrue(viewModel.uiState.value.pathHistory.isEmpty())
    }
}

// BrowserUiState tests
class BrowserUiStateTest {

    @Test
    fun `BrowserUiState default values`() {
        val state = BrowserUiState()

        assertTrue(state.files.isEmpty())
        assertEquals("", state.currentPath)
        assertTrue(state.pathHistory.isEmpty())
        assertTrue(state.selectedFiles.isEmpty())
        assertEquals(SortBy.NAME, state.sortOrder.sortBy)
        assertFalse(state.isLoading)
        assertFalse(state.isSearching)
        assertEquals("", state.searchQuery)
        assertNull(state.error)
        assertNull(state.storageInfo)
        assertTrue(state.drives.isEmpty())
        assertFalse(state.isSelectionMode)
    }
}
