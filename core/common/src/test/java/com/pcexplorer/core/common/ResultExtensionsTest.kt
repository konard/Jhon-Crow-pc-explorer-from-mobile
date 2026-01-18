package com.pcexplorer.core.common

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ResultExtensionsTest {

    @Test
    fun `flatMap transforms success value`() {
        val result = Result.success(5)
        val transformed = result.flatMap { Result.success(it * 2) }

        assertEquals(10, transformed.getOrNull())
    }

    @Test
    fun `flatMap propagates failure`() {
        val exception = Exception("Test error")
        val result = Result.failure<Int>(exception)
        val transformed = result.flatMap { Result.success(it * 2) }

        assertTrue(transformed.isFailure)
        assertEquals(exception, transformed.exceptionOrNull())
    }

    @Test
    fun `flatMap returns failure from transform`() {
        val exception = Exception("Transform error")
        val result = Result.success(5)
        val transformed = result.flatMap<Int, Int> { Result.failure(exception) }

        assertTrue(transformed.isFailure)
        assertEquals(exception, transformed.exceptionOrNull())
    }

    @Test
    fun `onError executes action for failure`() {
        val exception = Exception("Test error")
        var capturedError: Throwable? = null

        val result = Result.failure<Int>(exception)
        val returned = result.onError { capturedError = it }

        assertEquals(exception, capturedError)
        // Result is a value class, so we compare by value instead of reference
        assertEquals(result, returned)
    }

    @Test
    fun `onError does not execute action for success`() {
        var actionCalled = false

        val result = Result.success(5)
        result.onError { actionCalled = true }

        assertFalse(actionCalled)
    }

    @Test
    fun `getOrDefault returns value for success`() {
        val result = Result.success(5)

        assertEquals(5, result.getOrDefault(10))
    }

    @Test
    fun `getOrDefault returns default for failure`() {
        val result = Result.failure<Int>(Exception("Test"))

        assertEquals(10, result.getOrDefault(10))
    }

    @Test
    fun `getOrDefault returns default for null value`() {
        val result = Result.success<Int?>(null)

        assertEquals(10, result.getOrDefault(10))
    }

    @Test
    fun `runCatchingAsync returns success for normal execution`() = runTest {
        val result = runCatchingAsync { 42 }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `runCatchingAsync returns failure for exception`() = runTest {
        val exception = RuntimeException("Test error")

        val result = runCatchingAsync<Int> { throw exception }

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `runCatchingAsync handles suspend functions`() = runTest {
        val result = runCatchingAsync {
            kotlinx.coroutines.delay(10)
            "delayed result"
        }

        assertTrue(result.isSuccess)
        assertEquals("delayed result", result.getOrNull())
    }

    @Test
    fun `flatMap chains multiple transformations`() {
        val result = Result.success(2)
            .flatMap { Result.success(it * 3) }
            .flatMap { Result.success(it + 1) }

        assertEquals(7, result.getOrNull())
    }

    @Test
    fun `flatMap short-circuits on first failure`() {
        val exception = Exception("First error")
        var secondCalled = false

        val result = Result.success(2)
            .flatMap<Int, Int> { Result.failure(exception) }
            .flatMap {
                secondCalled = true
                Result.success(it + 1)
            }

        assertTrue(result.isFailure)
        assertFalse(secondCalled)
    }
}
