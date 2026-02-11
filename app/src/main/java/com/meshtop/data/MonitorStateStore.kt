package com.meshtop.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "MonitorStateStore"
private const val FILE_NAME = "monitor_state.json"

class MonitorStateStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }

    fun save(snapshot: MonitorSnapshot) {
        try {
            val data = json.encodeToString(MonitorSnapshot.serializer(), snapshot)
            file.writeText(data)
            Log.d(TAG, "Saved state: ${snapshot.totalPackets} packets, ${snapshot.gateways.size} gateways")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save state", e)
        }
    }

    fun load(): MonitorSnapshot? {
        return try {
            if (!file.exists()) return null
            val data = file.readText()
            json.decodeFromString(MonitorSnapshot.serializer(), data)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load state, starting fresh", e)
            clear()
            null
        }
    }

    fun clear() {
        file.delete()
    }
}
