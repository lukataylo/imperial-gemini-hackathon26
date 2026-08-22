package com.gatekeeper

import android.app.Application
import com.gatekeeper.di.AppContainer
import com.gatekeeper.di.DefaultAppContainer
import com.gatekeeper.enforce.GatekeeperService

class GatekeeperApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = DefaultAppContainer(this)

        // Warm up background service and model engine
        GatekeeperService.start(this)
    }

    companion object {
        lateinit var instance: GatekeeperApp
            private set
    }
}
