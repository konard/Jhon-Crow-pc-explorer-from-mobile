package com.pcexplorer.app

import android.app.Application
import com.pcexplorer.core.common.Logger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PcExplorerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.init(BuildConfig.DEBUG)
    }
}
