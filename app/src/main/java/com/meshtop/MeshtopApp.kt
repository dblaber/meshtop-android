package com.meshtop

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.meshtop.data.MeshtasticMonitor

class MeshtopApp : Application() {

    lateinit var monitor: MeshtasticMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        monitor = MeshtasticMonitor()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        // Persistent service notification (low importance = silent)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                "Monitoring Service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps meshtop connected in the background"
            }
        )

        // Text message notifications (high importance = heads-up)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                "Mesh Messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Text messages from the mesh network"
            }
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "meshtop_service"
        const val CHANNEL_MESSAGES = "meshtop_messages"
    }
}
