package com.pcexplorer.core.common

import timber.log.Timber

/**
 * Logger wrapper for consistent logging across the app.
 * Logging is disabled by default and can be enabled via init().
 */
object Logger {
    private var isEnabled = false

    /**
     * Initialize logging. Call this in Application.onCreate() for debug builds.
     */
    fun init(debugMode: Boolean = false) {
        isEnabled = debugMode
        if (debugMode) {
            Timber.plant(Timber.DebugTree())
        }
    }

    fun d(tag: String, message: String) {
        if (isEnabled) Timber.tag(tag).d(message)
    }

    fun i(tag: String, message: String) {
        if (isEnabled) Timber.tag(tag).i(message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (isEnabled) {
            if (throwable != null) {
                Timber.tag(tag).w(throwable, message)
            } else {
                Timber.tag(tag).w(message)
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isEnabled) {
            if (throwable != null) {
                Timber.tag(tag).e(throwable, message)
            } else {
                Timber.tag(tag).e(message)
            }
        }
    }
}
