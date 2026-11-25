package com.billal.datom

import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.billal.datom.appwrite.ConnectionTestQueue
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.billal.datom.appwrite.DeviceEnrollment
import com.billal.datom.appwrite.EnrollmentSession
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
    var sessionUser by remember { mutableStateOf(ConnectionTestQueue.approvedUser(context)) }
    var approvedUser by remember { mutableStateOf(ConnectionTestQueue.approvedUser(context)) }
    var enrollmentUser by remember { mutableStateOf("") }
    var enrollmentSecret by remember { mutableStateOf("") }
    var sessionStatus by remember { mutableStateOf("") }
    val workFlow = remember(context) {
        WorkManager.getInstance(context.applicationContext)
            .getWorkInfosForUniqueWorkFlow(ConnectionTestQueue.NAME)
    }
    val workInfos by workFlow.collectAsState(initial = emptyList())
    val testState = workInfos.firstOrNull { !it.state.isFinished }?.state
        ?: workInfos.firstOrNull()?.state
    val deviceReference = remember(context) {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { id -> MessageDigest.getInstance("SHA-256")
                .digest(("datom-device-v1:" + id).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) } }
    }

    LaunchedEffect(Unit) {
        try {
            fingerprint = withContext(Dispatchers.IO) {
                DeviceEnrollment.removeLegacyApiKey(context)
                DeviceEnrollment.fingerprint()
            }
            // A remembered account allows verification to be queued while offline.
            sessionUser = approvedUser ?: runCatching { EnrollmentSession.currentUser(context) }.getOrNull()
            ConnectionTestQueue.enqueueApproved(context)
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
        Text("Cloud connection & privacy", style = MaterialTheme.typography.headlineMedium)
        Text("This device", style = MaterialTheme.typography.titleMedium)
        Text(Build.MANUFACTURER + " " + Build.MODEL)
        Text("Android " + Build.VERSION.RELEASE + " · SDK " + Build.VERSION.SDK_INT)
        Text("Local device reference", style = MaterialTheme.typography.labelMedium)
        Text(deviceReference ?: "Unavailable", style = MaterialTheme.typography.bodySmall)
        Text("The reference is derived from Android ID and stays on this device. It is not an authentication credential.", style = MaterialTheme.typography.bodySmall)
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
            Text("Local identity prepared")
            Text("Public-key fingerprint", style = MaterialTheme.typography.labelMedium)
            Text(fingerprint.orEmpty(), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !busy && sessionUser == null, onClick = {
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
        if (sessionUser != null) {
            Text(if (approvedUser == sessionUser) "Enrollment saved on this device" else "Appwrite session found")
            Text(sessionUser.orEmpty(), style = MaterialTheme.typography.bodySmall)
            Text("Cloud SMS sync is still off.")
            if (approvedUser != sessionUser) {
                Text("Allow automatic verification of your private device registration on launch and when queued work can reconnect. No SMS is uploaded.")
                Button(enabled = !busy, onClick = {
                    sessionUser?.let { ConnectionTestQueue.approve(context, it); approvedUser = it }
                }) { Text("Allow automatic device verification") }
            }
            Text("Device verification: " + when (testState) {
                WorkInfo.State.ENQUEUED -> "Waiting for network or retry"
                WorkInfo.State.RUNNING -> "Checking registration"
                WorkInfo.State.SUCCEEDED -> "Last check succeeded"
                WorkInfo.State.FAILED -> "Registration not verified"
                WorkInfo.State.CANCELLED -> "Cancelled"
                WorkInfo.State.BLOCKED -> "Waiting"
                null -> "Not checked"
            })
            OutlinedButton(enabled = !busy && approvedUser == sessionUser && testState?.isFinished != false,
                onClick = { sessionUser?.let { ConnectionTestQueue.enqueue(context, it) } }) {
                Text("Check connection")
            }
            OutlinedButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        ConnectionTestQueue.revoke(context)
                        approvedUser = null
                        EnrollmentSession.disconnect(context)
                        sessionUser = null
                        sessionStatus = "Disconnected"
                    } catch (_: Exception) {
                        sessionStatus = "Automatic verification stopped locally. Session revocation needs internet; retry when online."
                    } finally {
                        busy = false
                    }
                }
            }) { Text("Disconnect Appwrite session") }
        } else if (fingerprint != null) {
            Text("Enter the one-time credentials issued specifically for this device by your administrator. Do not enter an Appwrite server API key.")
            Text("Connecting allows automatic verification of your private device registration. No SMS is uploaded. You can disconnect at any time.")
            OutlinedTextField(
                value = enrollmentUser,
                onValueChange = { enrollmentUser = it },
                label = { Text("Enrollment user ID") },
                singleLine = true
            )
            OutlinedTextField(
                value = enrollmentSecret,
                onValueChange = { enrollmentSecret = it },
                label = { Text("One-time enrollment token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Button(enabled = !busy && enrollmentUser.isNotBlank() && enrollmentSecret.isNotBlank(),
                onClick = {
                    scope.launch {
                        busy = true
                        sessionStatus = ""
                        try {
                            sessionUser = EnrollmentSession.redeem(context, enrollmentUser, enrollmentSecret)
                            sessionUser?.let { ConnectionTestQueue.approve(context, it); approvedUser = it }
                            sessionStatus = "Session connected. Device verification queued; SMS sync remains disabled."
                        } catch (_: Exception) {
                            sessionStatus = "Enrollment failed. Check the token, connection and account."
                        } finally {
                            enrollmentSecret = ""
                            busy = false
                        }
                    }
                }) { Text("Connect Appwrite session") }
        }
        if (sessionStatus.isNotBlank()) Text(sessionStatus)
        if (error) Text("Device identity operation failed. Cloud sync remains off.",
            color = MaterialTheme.colorScheme.error)
    }
}
