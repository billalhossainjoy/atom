package com.billal.datom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Local inbox", style = MaterialTheme.typography.titleMedium)
        Text("Messages are read from this device and remain available offline.")
        Text("Cloud sync", style = MaterialTheme.typography.titleMedium)
        Text("Not enabled. Secure device enrollment and explicit backup consent are required before SMS can be uploaded. Server API keys must remain on the backend.")
    }
}
