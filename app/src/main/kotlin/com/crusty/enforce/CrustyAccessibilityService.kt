package com.crusty.enforce

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import com.crusty.CrustyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class CrustyAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastPackage: String? = null
    private var lastTimestamp: Long = 0L
    private var launcherPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        resolveLauncherPackage()
    }

    private fun resolveLauncherPackage() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            launcherPackage = resolveInfo?.activityInfo?.packageName
        } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkgName = event.packageName?.toString() ?: return
        val now = System.currentTimeMillis()

        // Ignore self, system UI, and launcher
        if (pkgName == packageName ||
            pkgName == "com.android.systemui" ||
            pkgName == launcherPackage
        ) {
            return
        }

        // 500 ms debounce for same package
        if (pkgName == lastPackage && (now - lastTimestamp) < 500L) {
            return
        }

        lastPackage = pkgName
        lastTimestamp = now

        serviceScope.launch {
            _foregroundAppEvents.emit(pkgName)

            // Notify application container and enforcement service
            val container = (application as? CrustyApp)?.container
            if (container != null) {
                container.grantManager.onAppForeground(pkgName)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    companion object {
        var instance: CrustyAccessibilityService? = null
            private set

        private val _foregroundAppEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val foregroundAppEvents: SharedFlow<String> = _foregroundAppEvents.asSharedFlow()
    }
}
