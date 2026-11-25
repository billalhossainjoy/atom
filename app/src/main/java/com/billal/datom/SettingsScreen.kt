package com.billal.datom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.billal.datom.appwrite.DeviceEnrollment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmed by remember { mutableStateOf(false) }
    var fingerprint by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            fingerprint = withContext(Dispatchers.IO) {
                DeviceEnrollment.removeLegacyApiKey(context)
                DeviceEnrollment.fingerprint()
            }
        } catch (_: Exception) {
            error = true
        } finally {
            busy = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Local inbox", style = MaterialTheme.typography.titleMedium)
        Text("Messages remain on this device and available offline.")
        Text("Cloud enrollment", style = MaterialTheme.typography.titleMedium)
        Text("Cloud sync is off. Preparing a device identity does not send your messages or grant cloud access. Any future backup must be explicitly enabled by the device owner after enrollment.")
        if (fingerprint == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = confirmed, onCheckedChange = { confirmed = it }, enabled = !busy)
                Text("I own or am authorized to enroll this device.")
            }
            Button(enabled = confirmed && !busy, onClick = {
                busy = true
                error = false
                scope.launch {
                    try {
                        fingerprint = withContext(Dispatchers.IO) { DeviceEnrollment.prepare() }
                    } catch (_: Exception) {
                        error = true
                    } finally {
                        busy = false
                    }
                }
            }) { Text("Prepare device identity") }
        } else {
            Text("Identity prepared — not enrolled")
            Text("Public-key fingerprint", style = MaterialTheme.typography.labelMedium)
            Text(fingerprint.orEmpty(), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !busy, onClick = {
                busy = true
                error = false
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { DeviceEnrollment.reset() }
                        fingerprint = null
                        confirmed = false
                    } catch (_: Exception) {
                        error = true
                    } finally {
                        busy = false
                    }
                }
            }) { Text("Reset local identity") }
        }
        if (error) Text("Device identity operation failed. Cloud sync remains off.",
            color = MaterialTheme.colorScheme.error)
    }
}
