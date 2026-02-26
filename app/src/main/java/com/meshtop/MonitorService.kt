package com.meshtop

import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meshtop.data.MessageInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private val notifiedPacketIds = mutableSetOf<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Acquire partial wake lock to keep MQTT alive when screen is off
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "meshtop::mqtt").apply {
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, MeshtopApp.CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("meshtop")
            .setContentText("Monitoring mesh network...")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        try {
            startForeground(NOTIFICATION_ID_SERVICE, notification)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.e("MonitorService", "Foreground service not allowed, stopping", e)
                stopSelf()
                return START_NOT_STICKY
            }
            throw e
        }

        // Watch for new messages and fire notifications
        observeMessages()

        // Periodically update persistent notification with stats
        observeStats()

        return START_STICKY
    }

    private fun observeMessages() {
        val monitor = (application as MeshtopApp).monitor
        scope.launch {
            var firstEmission = true
            monitor.state
                .map { it.recentMessages }
                .distinctUntilChanged()
                .collectLatest { messages ->
                    if (firstEmission) {
                        // Seed all existing IDs silently — these are MQTT backlog or
                        // restored session data, not genuinely new messages.
                        firstEmission = false
                        messages.forEach { notifiedPacketIds.add(it.packetId) }
                        return@collectLatest
                    }
                    for (msg in messages) {
                        if (msg.packetId in notifiedPacketIds) continue
                        notifiedPacketIds.add(msg.packetId)
                        if (notifiedPacketIds.size > 500) {
                            val oldest = notifiedPacketIds.take(250)
                            notifiedPacketIds.removeAll(oldest.toSet())
                        }
                        fireMessageNotification(msg)
                    }
                }
        }
    }

    private fun observeStats() {
        val monitor = (application as MeshtopApp).monitor
        scope.launch {
            while (isActive) {
                delay(5000)
                val s = monitor.state.value
                val statusText = if (s.connected) "Connected" else "Reconnecting..."
                val text = "$statusText | Pkts: ${s.totalPackets} | GWs: ${s.gateways.size}"

                val openAppIntent = Intent(this@MonitorService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    this@MonitorService, 0, openAppIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val notification = NotificationCompat.Builder(this@MonitorService, MeshtopApp.CHANNEL_SERVICE)
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentTitle("meshtop")
                    .setContentText(text)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .build()

                try {
                    NotificationManagerCompat.from(this@MonitorService).notify(NOTIFICATION_ID_SERVICE, notification)
                } catch (_: SecurityException) { }
            }
        }
    }

    private fun fireMessageNotification(msg: MessageInfo) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, msg.packetId, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val hops = if (msg.hopStart > 0) msg.hopStart - msg.hopLimit else -1
        val hopStr = if (hops >= 0) "${hops}hop" else ""
        val gwStr = msg.gatewayName.take(6)
        val subText = listOfNotNull(
            gwStr,
            hopStr.ifEmpty { null },
            msg.rssi?.let { "RSSI:${"%.0f".format(it)}" },
        ).joinToString(" | ")

        val notification = NotificationCompat.Builder(this, MeshtopApp.CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("${msg.fromName} \u2192 ${msg.toName}")
            .setContentText(msg.text)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg.text).setSummaryText(subText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(msg.packetId, notification)
        } catch (_: SecurityException) { }
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID_SERVICE = 1
    }
}
