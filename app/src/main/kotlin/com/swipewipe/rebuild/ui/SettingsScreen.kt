package com.swipewipe.rebuild.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipewipe.rebuild.SettingsStore
import com.swipewipe.rebuild.UserSettings
import com.swipewipe.rebuild.graph.GraphAuthActivity

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onSetting: suspend (SettingsStore.() -> Unit) -> Unit,
    onRunSimilarScan: () -> Unit,
    onRunCloudSync: (Boolean) -> Unit,
    onCompressNow: () -> Unit,
    onTestStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var clientId by remember(settings.graphClientId) {
        mutableStateOf(settings.graphClientId)
    }
    val signInLabel = if (settings.graphSignedIn) "Reconnect OneDrive" else "Sign in to OneDrive"
    val act = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Deck sort", style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.selectableGroup()) {
                        listOf(
                            "date_added DESC" to "Newest first",
                            "date_added ASC" to "Oldest first"
                        ).forEach { (value, label) ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .selectable(
                                        selected = settings.sortOrder == value,
                                        onClick = { onSetting { setSortOrder(value) } }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.sortOrder == value,
                                    onClick = { onSetting { setSortOrder(value) } }
                                )
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Confirm every delete", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Shows a dialog before each file is removed",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(
                        checked = settings.confirmDelete,
                        onCheckedChange = { v -> onSetting { setConfirmDelete(v) } }
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Dark mode", style = MaterialTheme.typography.titleSmall)
                    }
                    Switch(
                        checked = settings.darkMode != 1,
                        onCheckedChange = { v ->
                            onSetting { setDarkMode(if (v) 2 else 1) }
                        }
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("App lock (biometric)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Requires fingerprint/face to reopen the app",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(
                        checked = settings.appLock,
                        onCheckedChange = { v -> onSetting { setAppLock(v) } }
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Photo compress quality", style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.selectableGroup()) {
                        listOf(50, 80, 90).forEach { q ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .selectable(
                                        selected = settings.compressQuality == q,
                                        onClick = { onSetting { setCompressQuality(q) } }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.compressQuality == q,
                                    onClick = { onSetting { setCompressQuality(q) } }
                                )
                                Text("$q% JPEG")
                            }
                        }
                    }
                    Button(onClick = onCompressNow, modifier = Modifier.padding(top = 10.dp)) {
                        Text("Compress batches now (runs in background)")
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("OneDrive (Microsoft Graph)", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = { Text("Microsoft app client id (yours)") },
                        placeholder = { Text("XXXXXXXX-XXXX-…") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true
                    )
                    Button(
                        onClick = { onSetting { setGraphClientId(clientId) } },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Save client id") }
                    Row(Modifier.padding(top = 10.dp)) {
                        Button(
                            onClick = {
                                act.startActivity(
                                    android.content.Intent(
                                        act,
                                        GraphAuthActivity::class.java
                                    )
                                )
                            },
                            enabled = settings.graphClientId.isNotEmpty()
                        ) { Text(signInLabel) }
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onRunCloudSync(false) }) { Text("Sync now") }
                        OutlinedButton(onClick = { onRunCloudSync(true) }) {
                            Text("Force full resync")
                        }
                    }
                    Text(
                        "You must register your own Microsoft app (from https://portal.azure.com → " +
                            "Microsoft Entra ID → App registrations, add a 'Mobile and desktop " +
                            "applications' platform redirect URI " +
                            "swipewipe-rebuild://graph-auth, and enable 'Allow public client flows'). " +
                            "The rebuild ships no client id of its own.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Maintenance", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = onRunSimilarScan) { Text("Rescan similar images") }
                    }
                }
            }
        }
    }
}
