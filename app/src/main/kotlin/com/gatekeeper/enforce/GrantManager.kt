package com.gatekeeper.enforce

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.gatekeeper.data.LedgerRepository
import com.gatekeeper.data.SettingsRepository
import com.gatekeeper.model.AccessMode
import com.gatekeeper.model.Grant
import com.gatekeeper.model.PreCheck
import com.gatekeeper.policy.PolicyEngine
import com.gatekeeper.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface GrantManager {
    val activeGrant: StateFlow<Grant?>
    suspend fun onAppForeground(packageName: String)
    suspend fun startGrant(grant: Grant, plea: String, proposedMinutes: Int)
    suspend fun endGrant(appId: String, overranByMinutes: Int? = null, honoured: Boolean? = null)
    fun isAppWatched(packageName: String): Boolean
}

class DefaultGrantManager(
    private val context: Context,
    private val ledgerRepository: LedgerRepository,
    private val policyEngine: PolicyEngine,
    private val settingsRepository: SettingsRepository,
    private val grayscaleOverlayManager: GrayscaleOverlayManager,
    private val scope: CoroutineScope
) : GrantManager {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val _activeGrant = MutableStateFlow<Grant?>(null)
    override val activeGrant: StateFlow<Grant?> = _activeGrant.asStateFlow()

    private var expiryJob: Job? = null
    private var headsUpJob: Job? = null

    /** Last package seen in the foreground, watched or not. Needed to tell whether a
     *  grant was honoured (user left before expiry) or overran (still inside at T-0). */
    @Volatile private var currentForegroundApp: String? = null

    companion object {
        const val CHANNEL_ID_COUNTDOWN = "gatekeeper_countdown"
        const val CHANNEL_ID_ALERTS = "gatekeeper_alerts"
        const val NOTIFICATION_ID_COUNTDOWN = 1001
        const val NOTIFICATION_ID_HEADS_UP = 1002
    }

    init {
        createNotificationChannels()
        scope.launch {
            _activeGrant.value = ledgerRepository.getActiveGrant()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val countdownChannel = NotificationChannel(
                CHANNEL_ID_COUNTDOWN,
                "Access Countdown",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows remaining time on granted access"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Gatekeeper Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Time warnings and limit alerts"
            }

            notificationManager.createNotificationChannel(countdownChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    override fun isAppWatched(packageName: String): Boolean {
        val watchedApps = settingsRepository.settingsData.value.watchedApps
        return watchedApps.any { it.packageName == packageName && it.isWatched }
    }

    override suspend fun onAppForeground(packageName: String) {
        val previous = currentForegroundApp
        currentForegroundApp = packageName

        // User left the granted app before time ran out — that is a kept promise.
        val active = _activeGrant.value
        if (active != null && previous == active.appId && packageName != active.appId) {
            val now = System.currentTimeMillis()
            if (now < active.expiresAt) {
                endGrant(active.appId, overranByMinutes = 0, honoured = true)
                return
            }
        }

        if (!settingsRepository.isInterceptionEnabled()) return
        if (!isAppWatched(packageName)) return

        val now = System.currentTimeMillis()
        val snapshot = ledgerRepository.getSnapshot(packageName, now)
        val rules = settingsRepository.getRules()

        val preCheck = policyEngine.evaluate(packageName, now, snapshot, rules)

        when (preCheck) {
            is PreCheck.ActiveGrant -> {
                // Let through seamlessly
            }
            is PreCheck.Denied -> {
                // Launch BlockActivity in denial mode
                BlockActivity.launch(context, packageName, denialReason = preCheck.reason, retryAfterMinutes = preCheck.retryAfterMinutes)
            }
            is PreCheck.Negotiable -> {
                // Launch BlockActivity in negotiation mode
                BlockActivity.launch(context, packageName)
            }
        }
    }

    override suspend fun startGrant(grant: Grant, plea: String, proposedMinutes: Int) {
        _activeGrant.value = grant
        ledgerRepository.recordGrant(grant, plea, proposedMinutes)

        if (grant.mode == AccessMode.GRAYSCALE) {
            grayscaleOverlayManager.enableGrayscale()
        }

        showCountdownNotification(grant)
        scheduleGrantTimers(grant)
    }

    override suspend fun endGrant(appId: String, overranByMinutes: Int?, honoured: Boolean?) {
        val now = System.currentTimeMillis()
        val ending = _activeGrant.value
        _activeGrant.value = null
        grayscaleOverlayManager.disableGrayscale()
        cancelNotifications()
        expiryJob?.cancel()
        headsUpJob?.cancel()

        // Budgets only deplete if we write usage back. Without this every limit is infinite.
        if (ending != null && ending.appId == appId) {
            val consumedMillis = (now - ending.startedAt).coerceAtLeast(0L)
            val consumedMinutes = Math.round(consumedMillis / 60_000.0).toInt()
            if (consumedMinutes > 0) {
                val today = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                ledgerRepository.addUsageMinutes(appId, today, consumedMinutes)
            }
        }

        ledgerRepository.resolveGrantOutcome(appId, now, overranByMinutes, honoured)
    }

    private fun scheduleGrantTimers(grant: Grant) {
        expiryJob?.cancel()
        headsUpJob?.cancel()

        // Drive from the grant's own expiry, not from now. The offer may have sat on screen
        // before the user accepted it; timing from accept-time hands out free extra minutes.
        val now = System.currentTimeMillis()
        val grantDurationMillis = (grant.expiresAt - now).coerceAtLeast(0L)
        val headsUpDelayMillis = (grantDurationMillis - 2 * 60_000L).coerceAtLeast(0L)

        // Heads up at T-2 minutes (Screen 3 in 03-UI-SPEC.md)
        if (grantDurationMillis > 2 * 60_000L) {
            headsUpJob = scope.launch {
                delay(headsUpDelayMillis)
                if (isActive && _activeGrant.value?.appId == grant.appId) {
                    showHeadsUpNotification(grant.appId)
                }
            }
        }

        // Expiry at T-0 (Screen 3 in 03-UI-SPEC.md: That's the ten minutes)
        expiryJob = scope.launch {
            delay(grantDurationMillis)
            if (isActive) {
                // Still inside the app when time ran out: they used every minute they asked
                // for. That is the broken promise the agent quotes back on the next attempt.
                val stillInside = currentForegroundApp == grant.appId
                endGrant(
                    grant.appId,
                    overranByMinutes = if (stillInside) 0 else null,
                    honoured = !stillInside
                )
                if (stillInside) onAppForeground(grant.appId)
            }
        }
    }

    private fun showCountdownNotification(grant: Grant) {
        val appName = settingsRepository.settingsData.value.watchedApps
            .firstOrNull { it.packageName == grant.appId }?.appName ?: "App"

        val openIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val chronometerBase = SystemClock.elapsedRealtime() + (grant.minutes * 60 * 1000L)

        val modeText = if (grant.mode == AccessMode.GRAYSCALE) "Grayscale" else "Full"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_COUNTDOWN)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$appName access granted ($modeText)")
            .setContentText("Promise: \"${grant.promise.take(50)}\"")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + (grant.minutes * 60 * 1000L))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID_COUNTDOWN, notification)
    }

    private fun showHeadsUpNotification(appId: String) {
        val appName = settingsRepository.settingsData.value.watchedApps
            .firstOrNull { it.packageName == appId }?.appName ?: "App"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("2 minutes remaining")
            .setContentText("Access to $appName ends soon. Finish up your task.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_HEADS_UP, notification)
    }

    private fun cancelNotifications() {
        notificationManager.cancel(NOTIFICATION_ID_COUNTDOWN)
        notificationManager.cancel(NOTIFICATION_ID_HEADS_UP)
    }
}
