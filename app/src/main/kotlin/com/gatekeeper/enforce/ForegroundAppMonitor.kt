package com.gatekeeper.enforce

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface ForegroundAppMonitor {
    val foregroundAppEvents: Flow<String>
    fun start(scope: CoroutineScope)
    fun stop()
}

class UsageStatsMonitor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ForegroundAppMonitor {

    private val _foregroundAppEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val foregroundAppEvents: Flow<String> = _foregroundAppEvents.asSharedFlow()

    private var pollJob: Job? = null
    private var lastPackage: String? = null
    private var lastEventTime: Long = 0L

    override fun start(scope: CoroutineScope) {
        if (pollJob?.isActive == true) return

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return

        pollJob = scope.launch(dispatcher) {
            var lastQueryTime = System.currentTimeMillis() - 5000L

            while (isActive) {
                val now = System.currentTimeMillis()
                val events = usageStatsManager.queryEvents(lastQueryTime, now)
                val event = UsageEvents.Event()

                var latestForegroundPackage: String? = null
                var latestForegroundTime = 0L

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                        event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                    ) {
                        if (event.timeStamp > latestForegroundTime) {
                            latestForegroundTime = event.timeStamp
                            latestForegroundPackage = event.packageName
                        }
                    }
                }

                if (latestForegroundPackage != null &&
                    latestForegroundPackage != context.packageName &&
                    latestForegroundPackage != lastPackage
                ) {
                    lastPackage = latestForegroundPackage
                    lastEventTime = now
                    _foregroundAppEvents.emit(latestForegroundPackage)
                }

                lastQueryTime = now - 1000L
                delay(1000L)
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        pollJob = null
    }
}
