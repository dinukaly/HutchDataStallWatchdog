package com.dinuka.hutchwatchdog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class StallWatchdogService : Service() {
    private lateinit var settings: WatchdogSettings
    private lateinit var recovery: NetworkRecoveryController
    private lateinit var connectivityManager: ConnectivityManager
    private val stateMachine = StallStateMachine()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var scheduledProbe: ScheduledFuture<*>? = null
    private var activeNetwork: Network? = null
    private var callbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            activeNetwork = network
            scheduleProbe(0L)
        }

        override fun onLost(network: Network) {
            if (activeNetwork == network) {
                activeNetwork = connectivityManager.activeNetwork
                publish(stateMachine.markNoCellular())
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = WatchdogSettings(this)
        recovery = NetworkRecoveryController(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWatchdog(userRequested = true)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startWatchdog()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopWatchdog(userRequested = false)
        scheduler.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startWatchdog() {
        settings.enabled = true
        publish(stateMachine.start())
        startForeground(NOTIFICATION_ID, buildNotification(WatchdogStore.snapshot))
        activeNetwork = connectivityManager.activeNetwork
        if (!callbackRegistered) {
            runCatching {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                callbackRegistered = true
            }
        }
        scheduleProbe(0L)
    }

    private fun stopWatchdog(userRequested: Boolean) {
        if (userRequested) settings.enabled = false
        scheduledProbe?.cancel(true)
        if (callbackRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            callbackRegistered = false
        }
        publish(stateMachine.stop())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun scheduleProbe(delayMs: Long) {
        scheduledProbe?.cancel(false)
        scheduledProbe = scheduler.schedule({ runProbeCycle() }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun runProbeCycle() {
        val network = activeNetwork ?: connectivityManager.activeNetwork
        activeNetwork = network
        if (!recovery.isCellularInternet(network)) {
            publish(stateMachine.markNoCellular())
            scheduleProbe(settings.preset.healthyIntervalMs)
            return
        }

        val probeRunner = ProbeRunner()
        val result = probeRunner.run(network, settings.customProbeUrl)
        val afterProbe = stateMachine.onProbe(result)
        publish(afterProbe)

        when (afterProbe.state) {
            WatchdogState.SUSPICIOUS -> {
                if (probeRunner.pokeBurst(network, settings.customProbeUrl)) {
                    publish(stateMachine.onProbe(ProbeResult(dnsOk = true, httpsOk = true)))
                    scheduleProbe(settings.preset.healthyIntervalMs)
                    return
                }
                scheduleProbe(RECOVERY_INTERVAL_MS)
            }
            WatchdogState.STALLED -> {
                publish(stateMachine.beginRecovery())
                recovery.askAndroidToRevalidate(network)
                scheduleProbe(RECOVERY_INTERVAL_MS)
            }
            WatchdogState.RECOVERING -> scheduleProbe(RECOVERY_INTERVAL_MS)
            else -> scheduleProbe(settings.preset.healthyIntervalMs)
        }
    }

    private fun publish(snapshot: WatchdogSnapshot) {
        WatchdogStore.update(snapshot)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(snapshot)) }
    }

    private fun buildNotification(snapshot: WatchdogSnapshot): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, StallWatchdogService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Hutch watchdog: ${snapshot.state.name.lowercase()}")
            .setContentText(snapshot.lastMessage)
            .setContentIntent(openIntent)
            .setOngoing(snapshot.running)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.watchdog_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Shows mobile data stall detection and recovery status"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "com.dinuka.hutchwatchdog.action.STOP"
        private const val CHANNEL_ID = "stall_watchdog"
        private const val NOTIFICATION_ID = 1001
        private const val RECOVERY_INTERVAL_MS = 15_000L

        fun start(context: Context) {
            val intent = Intent(context, StallWatchdogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, StallWatchdogService::class.java).setAction(ACTION_STOP))
        }
    }
}
