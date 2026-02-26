package com.meshtop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meshtop.data.ConnectionSettings
import com.meshtop.ui.theme.SurfaceDark
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: ConnectionSettings,
    onSave: (ConnectionSettings) -> Unit,
    onReconnect: () -> Unit,
    onClearStorage: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var broker by rememberSaveable { mutableStateOf(settings.broker) }
    var port by rememberSaveable { mutableStateOf(settings.port.toString()) }
    var username by rememberSaveable { mutableStateOf(settings.username) }
    var password by rememberSaveable { mutableStateOf(settings.password) }
    var topic by rememberSaveable { mutableStateOf(settings.topic) }
    var myNodes by rememberSaveable { mutableStateOf(settings.myNodes.joinToString(", ")) }
    var persistSession by rememberSaveable { mutableStateOf(settings.persistSession) }

    var dbHost by rememberSaveable { mutableStateOf(settings.dbHost) }
    var dbPort by rememberSaveable { mutableStateOf(settings.dbPort.toString()) }
    var dbName by rememberSaveable { mutableStateOf(settings.dbName) }
    var dbUser by rememberSaveable { mutableStateOf(settings.dbUser) }
    var dbPassword by rememberSaveable { mutableStateOf(settings.dbPassword) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .imePadding()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Connection Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        OutlinedTextField(
            value = broker,
            onValueChange = { broker = it },
            label = { Text("MQTT Broker") },
            placeholder = { Text("mqtt.meshtastic.org") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text("MQTT Topic") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = myNodes,
            onValueChange = { myNodes = it },
            label = { Text("My Nodes (comma separated)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Storage",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Persist session data",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Save gateways, nodes, messages, and packets across app restarts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = persistSession,
                onCheckedChange = { persistSession = it },
            )
        }

        OutlinedButton(
            onClick = {
                val current = ConnectionSettings(
                    broker = broker.trim(),
                    port = port.trim().toIntOrNull() ?: 1883,
                    username = username.trim(),
                    password = password.trim(),
                    topic = topic.trim(),
                    myNodes = myNodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                    persistSession = persistSession,
                    dbHost = dbHost.trim(),
                    dbPort = dbPort.trim().toIntOrNull() ?: 5432,
                    dbName = dbName.trim(),
                    dbUser = dbUser.trim(),
                    dbPassword = dbPassword.trim(),
                )
                onSave(current)
                onClearStorage()
            },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear Stored Data")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "PostgreSQL Database (Optional)",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = "Load node names from a PostgreSQL database. Leave host blank to disable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = dbHost,
            onValueChange = { dbHost = it },
            label = { Text("DB Host") },
            placeholder = { Text("db.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = dbPort,
            onValueChange = { dbPort = it },
            label = { Text("DB Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = dbName,
            onValueChange = { dbName = it },
            label = { Text("Database Name") },
            placeholder = { Text("meshtastic") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = dbUser,
            onValueChange = { dbUser = it },
            label = { Text("DB Username") },
            placeholder = { Text("username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = dbPassword,
            onValueChange = { dbPassword = it },
            label = { Text("DB Password") },
            placeholder = { Text("username") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    val newSettings = ConnectionSettings(
                        broker = broker.trim(),
                        port = port.trim().toIntOrNull() ?: 1883,
                        username = username.trim(),
                        password = password.trim(),
                        topic = topic.trim(),
                        myNodes = myNodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                        persistSession = persistSession,
                        dbHost = dbHost.trim(),
                        dbPort = dbPort.trim().toIntOrNull() ?: 5432,
                        dbName = dbName.trim(),
                        dbUser = dbUser.trim(),
                        dbPassword = dbPassword.trim(),
                    )
                    onSave(newSettings)
                    onReconnect()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Save & Reconnect")
            }
        }
    }
}
