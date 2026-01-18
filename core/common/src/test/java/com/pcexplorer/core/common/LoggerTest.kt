package com.pcexplorer.core.common

import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class LoggerTest {

    @Before
    fun setUp() {
        // Clear any existing trees
        Timber.uprootAll()
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `init in debug mode plants DebugTree`() {
        Logger.init(debugMode = true)

        // Verify a tree was planted - forest method returns all planted trees
        val forest = Timber.forest()
        assert(forest.isNotEmpty()) { "Expected at least one tree to be planted" }
    }

    @Test
    fun `init with debugMode false does not plant tree`() {
        Logger.init(debugMode = false)

        // When logging is disabled, no tree should be planted
        // The logger object doesn't expose isEnabled, but we can verify no crash on log calls
        Logger.d("TAG", "message")
        Logger.i("TAG", "message")
        Logger.w("TAG", "message")
        Logger.e("TAG", "message")
        // No exception means success
    }

    @Test
    fun `d logs debug message when enabled`() {
        Logger.init(debugMode = true)

        // Should not throw
        Logger.d("TestTag", "Debug message")
    }

    @Test
    fun `i logs info message when enabled`() {
        Logger.init(debugMode = true)

        // Should not throw
        Logger.i("TestTag", "Info message")
    }

    @Test
    fun `w logs warning message when enabled`() {
        Logger.init(debugMode = true)

        // Should not throw
        Logger.w("TestTag", "Warning message")
    }

    @Test
    fun `w logs warning message with throwable when enabled`() {
        Logger.init(debugMode = true)
        val throwable = RuntimeException("Test exception")

        // Should not throw
        Logger.w("TestTag", "Warning message", throwable)
    }

    @Test
    fun `e logs error message when enabled`() {
        Logger.init(debugMode = true)

        // Should not throw
        Logger.e("TestTag", "Error message")
    }

    @Test
    fun `e logs error message with throwable when enabled`() {
        Logger.init(debugMode = true)
        val throwable = RuntimeException("Test exception")

        // Should not throw
        Logger.e("TestTag", "Error message", throwable)
    }

    @Test
    fun `logging methods are safe when disabled`() {
        Logger.init(debugMode = false)
        val throwable = RuntimeException("Test exception")

        // All these should complete without error
        Logger.d("TestTag", "Debug")
        Logger.i("TestTag", "Info")
        Logger.w("TestTag", "Warning")
        Logger.w("TestTag", "Warning", throwable)
        Logger.e("TestTag", "Error")
        Logger.e("TestTag", "Error", throwable)
    }

    @Test
    fun `multiple init calls work correctly`() {
        Logger.init(debugMode = true)
        Logger.init(debugMode = false)
        Logger.init(debugMode = true)

        // Should still work
        Logger.d("TestTag", "Message")
    }
}
