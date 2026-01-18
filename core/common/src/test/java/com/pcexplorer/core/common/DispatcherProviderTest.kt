package com.pcexplorer.core.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.junit.Assert.*
import org.junit.Test

class DispatcherProviderTest {

    @Test
    fun `DefaultDispatcherProvider main returns Main dispatcher`() {
        val provider = DefaultDispatcherProvider()

        assertEquals(Dispatchers.Main, provider.main)
    }

    @Test
    fun `DefaultDispatcherProvider io returns IO dispatcher`() {
        val provider = DefaultDispatcherProvider()

        assertEquals(Dispatchers.IO, provider.io)
    }

    @Test
    fun `DefaultDispatcherProvider default returns Default dispatcher`() {
        val provider = DefaultDispatcherProvider()

        assertEquals(Dispatchers.Default, provider.default)
    }

    @Test
    fun `DispatcherProvider can be mocked for testing`() {
        val testDispatcher = StandardTestDispatcher()
        val testProvider = TestDispatcherProvider(testDispatcher)

        assertEquals(testDispatcher, testProvider.main)
        assertEquals(testDispatcher, testProvider.io)
        assertEquals(testDispatcher, testProvider.default)
    }
}

/**
 * Test implementation of DispatcherProvider for unit testing.
 */
class TestDispatcherProvider(
    private val testDispatcher: TestDispatcher
) : DispatcherProvider {
    override val main = testDispatcher
    override val io = testDispatcher
    override val default = testDispatcher
}
