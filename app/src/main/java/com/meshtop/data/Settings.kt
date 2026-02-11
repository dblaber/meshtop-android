package com.meshtop.data

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("meshtop_settings", Context.MODE_PRIVATE)

    fun load(): ConnectionSettings = ConnectionSettings(
        broker = prefs.getString("broker", "") ?: "",
        port = prefs.getInt("port", 1883),
        username = prefs.getString("username", "") ?: "",
        password = prefs.getString("password", "") ?: "",
        topic = prefs.getString("topic", "msh/US/2/e/LongFast/#") ?: "msh/US/2/e/LongFast/#",
        myNodes = prefs.getStringSet("my_nodes", emptySet()) ?: emptySet(),
        persistSession = prefs.getBoolean("persist_session", false),
        dbHost = prefs.getString("db_host", "") ?: "",
        dbPort = prefs.getInt("db_port", 5432),
        dbName = prefs.getString("db_name", "") ?: "",
        dbUser = prefs.getString("db_user", "") ?: "",
        dbPassword = prefs.getString("db_password", "") ?: "",
    )

    fun save(settings: ConnectionSettings) {
        prefs.edit()
            .putString("broker", settings.broker)
            .putInt("port", settings.port)
            .putString("username", settings.username)
            .putString("password", settings.password)
            .putString("topic", settings.topic)
            .putStringSet("my_nodes", settings.myNodes)
            .putBoolean("persist_session", settings.persistSession)
            .putString("db_host", settings.dbHost)
            .putInt("db_port", settings.dbPort)
            .putString("db_name", settings.dbName)
            .putString("db_user", settings.dbUser)
            .putString("db_password", settings.dbPassword)
            .apply()
    }
}
