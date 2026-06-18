package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.rikkahub.A2A_SERVER_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.web.A2aServerManager
import org.koin.android.ext.android.inject

private const val TAG = "A2aServerService"

class A2aServerService : Service() {

    companion object {
        const val ACTION_START = "me.rerere.rikkahub.action.A2A_SERVER_START"
        const val ACTION_STOP = "me.rerere.rikkahub.action.A2A_SERVER_STOP"
        const val EXTRA_PORT = "port"
        const val EXTRA_LOCALHOST_ONLY = "localhost_only"
        const val NOTIFICATION_ID = 2002
    }

    private val a2aServerManager: A2aServerManager by inject()
    private val settingsStore: SettingsStore by inject()

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e ->
            if (shouldLogServiceError(e)) Log.e(TAG, "A2aServerService coroutine failed", e)
        }
    )
    private var stateObserverJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, 9000)
                val localhostOnly = intent.getBooleanExtra(EXTRA_LOCALHOST_ONLY, true)
                startForegroundCompat()
                startObservingState()
                a2aServerManager.start(port = port, localhostOnly = localhostOnly)
            }

            ACTION_STOP -> {
                a2aServerManager.stop()
                serviceScope.launch {
                    settingsStore.update { it.copy(a2aEnabled = false) }
                }
            }

            null -> {
                startForegroundCompat()
                serviceScope.launch {
                    val settings = settingsStore.settingsFlowRaw.first()
                    if (settings.a2aEnabled) {
                        startObservingState()
                        a2aServerManager.start(
                            port = settings.a2aServerPort,
                            localhostOnly = settings.a2aServerLocalhostOnly,
                        )
                    } else {
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildStartingNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildStartingNotification())
        }
    }

    private fun startObservingState() {
        if (stateObserverJob != null) return
        stateObserverJob = serviceScope.launch {
            var wasRunning = false
            a2aServerManager.state.collect { state ->
                when {
                    state.isRunning -> {
                        wasRunning = true
                        updateNotification(buildRunningNotification(state.url ?: "http://localhost:${state.port}"))
                    }

                    wasRunning && !state.isRunning && !state.isLoading -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun updateNotification(notification: android.app.Notification) {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildLaunchPendingIntent() = PendingIntent.getActivity(
        this,
        0,
        packageManager.getLaunchIntentForPackage(packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildStartingNotification() = NotificationCompat.Builder(this, A2A_SERVER_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.small_icon)
        .setContentTitle("A2A server")
        .setContentText("Starting A2A server")
        .setContentIntent(buildLaunchPendingIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun buildRunningNotification(url: String): android.app.Notification {
        val stopIntent = Intent(this, A2aServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, A2A_SERVER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle("A2A server running")
            .setContentText("$url/a2a")
            .setContentIntent(buildLaunchPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }
}
