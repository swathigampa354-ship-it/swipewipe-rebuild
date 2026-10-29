package com.swipewipe.rebuild.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.swipewipe.rebuild.GalleryItem

@Composable
fun GroupsScreen(
    groups: List<List<GalleryItem>>,
    scanning: Boolean,
    onScan: () -> Unit,
    onDeleteGroup: (List<GalleryItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    var pending by remember { mutableStateOf<List<GalleryItem>?>(null) }
    Column(modifier.fillMaxSize().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Similar images (${groups.size} groups)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onScan, enabled = !scanning) {
                Text(if (scanning) "Scanning…" else "Rescan")
            }
        }
        if (groups.isEmpty() && !scanning) {
            Text(
                "No duplicate groups found yet. Run a scan to build the signature index.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(groups, key = { "g" + it[0].id }) { group ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${group.size} similar photos",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { pending = group }) {
                                Text("Delete group")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.take(4).forEach { item ->
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = item.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(84.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    pending?.let { group ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Delete ${group.size} photos?") },
            text = { Text("This removes these files from your device via MediaStore. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGroup(group)
                    pending = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Cancel") }
            }
        )
    }
}
