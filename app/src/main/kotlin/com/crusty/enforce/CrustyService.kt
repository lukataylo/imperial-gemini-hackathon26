package com.crusty.enforce

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.crusty.CrustyApp
import com.crusty.R
import com.crusty.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CrustyService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var fallbackMonitor: UsageStatsMonitor? = null

    companion object {
        const val CHANNEL_ID = "crusty_foreground_service"
        const val NOTIFICATION_ID = 9001

        fun start(context: Context) {
            val intent = Intent(context, CrustyService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            startForegroundWithNotification()
        } catch (e: Exception) {
            // START_STICKY restarts us in the background, where startForeground() is
            // disallowed and throws. Uncaught, that is a crash loop.
            android.util.Log.e("CrustyService", "startForeground rejected", e)
            stopSelf()
            return
        }

        val container = (application as? CrustyApp)?.container
        if (container != null) {
            // Warm-load the model engine immediately
            serviceScope.launch {
                container.inferenceManager.initialize()
            }

            // Start fallback usage stats monitor
            fallbackMonitor = UsageStatsMonitor(this).also { monitor ->
                monitor.start(serviceScope)
                serviceScope.launch {
                    monitor.foregroundAppEvents.collect { pkg ->
                        if (CrustyAccessibilityService.instance == null) {
                            container.grantManager.onAppForeground(pkg)
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Crusty Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps on-device protection warm and active"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.foreground_service_notification_title))
            .setContentText(getString(R.string.foreground_service_notification_text))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fallbackMonitor?.stop()
        serviceScope.cancel()
    }
}
