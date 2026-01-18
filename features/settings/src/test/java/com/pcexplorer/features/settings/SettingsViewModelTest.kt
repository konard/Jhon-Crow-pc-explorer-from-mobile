package com.pcexplorer.features.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var packageManager: PackageManager
    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        editor = mockk(relaxed = true)
        sharedPreferences = mockk {
            every { getString("theme_mode", any()) } returns ThemeMode.SYSTEM.name
            every { getBoolean("auto_connect", any()) } returns true
            every { getInt("buffer_size_kb", any()) } returns 32
            every { getInt("parallel_transfers", any()) } returns 2
            every { edit() } returns editor
        }
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor

        val packageInfo = mockk<PackageInfo>()
        packageInfo.versionName = "2.0.0"

        packageManager = mockk {
            every { getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
        }

        context = mockk {
            every { getSharedPreferences("settings", Context.MODE_PRIVATE) } returns sharedPreferences
            every { packageName } returns "com.pcexplorer"
            every { packageManager } returns packageManager
        }

        viewModel = SettingsViewModel(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Initial state tests

    @Test
    fun `initial state loads from SharedPreferences`() = runTest {
        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
        assertTrue(viewModel.uiState.value.autoConnect)
        assertEquals(32, viewModel.uiState.value.bufferSizeKb)
        assertEquals(2, viewModel.uiState.value.parallelTransfers)
    }

    @Test
    fun `initial state loads app version`() = runTest {
        assertEquals("2.0.0", viewModel.uiState.value.appVersion)
    }

    @Test
    fun `loads custom values from SharedPreferences`() = runTest {
        every { sharedPreferences.getString("theme_mode", any()) } returns ThemeMode.DARK.name
        every { sharedPreferences.getBoolean("auto_connect", any()) } returns false
        every { sharedPreferences.getInt("buffer_size_kb", any()) } returns 64
        every { sharedPreferences.getInt("parallel_transfers", any()) } returns 4

        val vm = SettingsViewModel(context)

        assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)
        assertFalse(vm.uiState.value.autoConnect)
        assertEquals(64, vm.uiState.value.bufferSizeKb)
        assertEquals(4, vm.uiState.value.parallelTransfers)
    }

    @Test
    fun `handles missing app version gracefully`() = runTest {
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } throws PackageManager.NameNotFoundException()

        val vm = SettingsViewModel(context)

        assertEquals("1.0.0", vm.uiState.value.appVersion)
    }

    // setThemeMode tests

    @Test
    fun `setThemeMode updates state`() = runTest {
        viewModel.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
    }

    @Test
    fun `setThemeMode saves to SharedPreferences`() = runTest {
        viewModel.setThemeMode(ThemeMode.LIGHT)

        verify { editor.putString("theme_mode", ThemeMode.LIGHT.name) }
        verify { editor.apply() }
    }

    @Test
    fun `setThemeMode to SYSTEM`() = runTest {
        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setThemeMode(ThemeMode.SYSTEM)

        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
        verify { editor.putString("theme_mode", ThemeMode.SYSTEM.name) }
    }

    // setAutoConnect tests

    @Test
    fun `setAutoConnect updates state`() = runTest {
        viewModel.setAutoConnect(false)

        assertFalse(viewModel.uiState.value.autoConnect)
    }

    @Test
    fun `setAutoConnect saves to SharedPreferences`() = runTest {
        viewModel.setAutoConnect(false)

        verify { editor.putBoolean("auto_connect", false) }
        verify { editor.apply() }
    }

    @Test
    fun `setAutoConnect toggle`() = runTest {
        viewModel.setAutoConnect(false)
        viewModel.setAutoConnect(true)

        assertTrue(viewModel.uiState.value.autoConnect)
    }

    // setBufferSize tests

    @Test
    fun `setBufferSize updates state`() = runTest {
        viewModel.setBufferSize(64)

        assertEquals(64, viewModel.uiState.value.bufferSizeKb)
    }

    @Test
    fun `setBufferSize saves to SharedPreferences`() = runTest {
        viewModel.setBufferSize(128)

        verify { editor.putInt("buffer_size_kb", 128) }
        verify { editor.apply() }
    }

    @Test
    fun `setBufferSize various values`() = runTest {
        val sizes = listOf(16, 32, 64, 128, 256)

        sizes.forEach { size ->
            viewModel.setBufferSize(size)
            assertEquals(size, viewModel.uiState.value.bufferSizeKb)
        }
    }

    // setParallelTransfers tests

    @Test
    fun `setParallelTransfers updates state`() = runTest {
        viewModel.setParallelTransfers(4)

        assertEquals(4, viewModel.uiState.value.parallelTransfers)
    }

    @Test
    fun `setParallelTransfers saves to SharedPreferences`() = runTest {
        viewModel.setParallelTransfers(3)

        verify { editor.putInt("parallel_transfers", 3) }
        verify { editor.apply() }
    }

    @Test
    fun `setParallelTransfers various values`() = runTest {
        val counts = listOf(1, 2, 3, 4, 5)

        counts.forEach { count ->
            viewModel.setParallelTransfers(count)
            assertEquals(count, viewModel.uiState.value.parallelTransfers)
        }
    }

    // Integration tests

    @Test
    fun `multiple settings changes`() = runTest {
        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setAutoConnect(false)
        viewModel.setBufferSize(64)
        viewModel.setParallelTransfers(4)

        val state = viewModel.uiState.value
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertFalse(state.autoConnect)
        assertEquals(64, state.bufferSizeKb)
        assertEquals(4, state.parallelTransfers)
    }
}

// ThemeMode tests
class ThemeModeTest {

    @Test
    fun `ThemeMode enum values`() {
        val modes = ThemeMode.values()

        assertEquals(3, modes.size)
        assertTrue(modes.contains(ThemeMode.LIGHT))
        assertTrue(modes.contains(ThemeMode.DARK))
        assertTrue(modes.contains(ThemeMode.SYSTEM))
    }

    @Test
    fun `ThemeMode valueOf`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.valueOf("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.valueOf("DARK"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.valueOf("SYSTEM"))
    }
}

// SettingsUiState tests
class SettingsUiStateTest {

    @Test
    fun `SettingsUiState default values`() {
        val state = SettingsUiState()

        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertTrue(state.autoConnect)
        assertEquals(32, state.bufferSizeKb)
        assertEquals(2, state.parallelTransfers)
        assertEquals("1.0.0", state.appVersion)
    }

    @Test
    fun `SettingsUiState with custom values`() {
        val state = SettingsUiState(
            themeMode = ThemeMode.DARK,
            autoConnect = false,
            bufferSizeKb = 128,
            parallelTransfers = 4,
            appVersion = "3.0.0"
        )

        assertEquals(ThemeMode.DARK, state.themeMode)
        assertFalse(state.autoConnect)
        assertEquals(128, state.bufferSizeKb)
        assertEquals(4, state.parallelTransfers)
        assertEquals("3.0.0", state.appVersion)
    }

    @Test
    fun `SettingsUiState copy`() {
        val original = SettingsUiState()
        val modified = original.copy(themeMode = ThemeMode.LIGHT, bufferSizeKb = 64)

        assertEquals(ThemeMode.LIGHT, modified.themeMode)
        assertEquals(64, modified.bufferSizeKb)
        // Other values unchanged
        assertTrue(modified.autoConnect)
        assertEquals(2, modified.parallelTransfers)
    }
}
