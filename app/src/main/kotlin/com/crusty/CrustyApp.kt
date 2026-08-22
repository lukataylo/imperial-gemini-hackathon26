package com.crusty

import android.app.Application
import com.crusty.di.AppContainer
import com.crusty.di.DefaultAppContainer
import com.crusty.enforce.CrustyService

class CrustyApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = DefaultAppContainer(this)

        // Warm up background service and model engine
        CrustyService.start(this)
    }

    companion object {
        lateinit var instance: CrustyApp
            private set
    }
}
