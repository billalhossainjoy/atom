package com.billal.datom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.billal.datom.appwrite.AppwriteConfig
import com.billal.datom.appwrite.AppwriteSync
import com.billal.datom.appwrite.isOnline
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var endpoint by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var databaseId by remember { mutableStateOf("") }
    var tableId by remember { mutableStateOf("") }

    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppwriteConfig.load(context)
        endpoint = AppwriteConfig.endpoint
        projectId = AppwriteConfig.projectId
        apiKey = AppwriteConfig.apiKey
        databaseId = AppwriteConfig.databaseId
        tableId = AppwriteConfig.tableId
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Appwrite connection",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("Endpoint") },
            placeholder = { Text("https://<region>.cloud.appwrite.io/v1") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = projectId,
            onValueChange = { projectId = it },
            label = { Text("Project ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = databaseId,
            onValueChange = { databaseId = it },
            label = { Text("Database ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = tableId,
            onValueChange = { tableId = it },
            label = { Text("Table ID (default: messages)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        status?.let {
            Text(
                it,
                color = if (statusIsError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (apiKey.isBlank()) {
                        status = "API key is required"
                        statusIsError = true
                        return@Button
                    }
                    if (endpoint.isNotBlank() && !endpoint.startsWith("http")) {
                        status = "Endpoint must start with http:// or https://"
                        statusIsError = true
                        return@Button
                    }
                    AppwriteConfig.save(
                        context,
                        endpoint.ifBlank { AppwriteConfig.DEFAULT_ENDPOINT },
                        projectId.ifBlank { AppwriteConfig.DEFAULT_PROJECT_ID },
                        apiKey,
                        databaseId.ifBlank { AppwriteConfig.DEFAULT_DATABASE_ID },
                        tableId.ifBlank { AppwriteConfig.DEFAULT_TABLE_ID }
                    )
                    status = "Saved"
                    statusIsError = false
                    if (isOnline(context)) {
                        busy = true
                        scope.launch {
                            val ok = AppwriteSync.testConnection(context)
                            busy = false
                            if (ok) {
                                status = "Connected — syncing…"
                                AppwriteSync.syncAll(context)
                                status = "Synced"
                                statusIsError = false
                            } else {
                                status = "Saved, but connection test failed"
                                statusIsError = true
                            }
                        }
                    } else {
                        status = "Saved. No internet — will sync when online."
                        statusIsError = false
                    }
                },
                enabled = !busy
            ) { Text("Save & Sync") }

            Button(
                onClick = {
                    if (!AppwriteConfig.isConfigured()) {
                        status = "Save your settings first"
                        statusIsError = true
                        return@Button
                    }
                    if (!isOnline(context)) {
                        status = "No internet connection"
                        statusIsError = true
                        return@Button
                    }
                    busy = true
                    status = "Testing connection…"
                    scope.launch {
                        val ok = AppwriteSync.testConnection(context)
                        busy = false
                        if (ok) {
                            status = "Connected — syncing…"
                            AppwriteSync.syncAll(context)
                            status = "Connected and synced"
                            statusIsError = false
                        } else {
                            status = "Connection failed — check endpoint, project, key, table"
                            statusIsError = true
                        }
                    }
                },
                enabled = !busy
            ) { Text("Test") }
        }

        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text("Working…")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "SMS permission is required to read your inbox. " +
                "Messages sync automatically at launch, on new SMS, " +
                "and whenever internet reconnects.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
