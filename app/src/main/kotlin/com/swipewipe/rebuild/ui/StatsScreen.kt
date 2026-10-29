package com.swipewipe.rebuild.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipewipe.rebuild.db.StatsEntity

fun formatBytes(b: Long): String {
    if (b <= 0) return "0 B"
    val kb = b / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> "%.2f GB".format(gb)
        mb >= 1 -> "%.1f MB".format(mb)
        else -> "%.0f KB".format(kb)
    }
}

@Composable
fun StatsScreen(
    stats: List<StatsEntity>,
    cloudAggregates: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    val totalSwiped = stats.sumOf { it.swipedCount }
    val totalDeleted = stats.sumOf { it.deletedCount }
    val totalFreed = stats.sumOf { it.totalSize }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Stats",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp).fillMaxWidth()) {
                    Text("All-time", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Swipes", "$totalSwiped", Modifier.weight(1f))
                        Stat("Deleted", "$totalDeleted", Modifier.weight(1f))
                        Stat("Size freed", formatBytes(totalFreed), Modifier.weight(1f))
                    }
                }
            }
        }
        items(stats, key = { it.mediaSource }) { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (s.mediaSource == "image") "Photos" else "Videos",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${s.timesSeenCount} seen",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Text(
                        "swiped ${s.swipedCount} • deleted ${s.deletedCount} • " +
                            "freed ${formatBytes(s.totalSize)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        if (cloudAggregates.isNotEmpty()) {
            item {
                Text(
                    "OneDrive months",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            items(cloudAggregates, key = { it.first }) { (month, count) ->
                Card(Modifier.fillMaxWidth().height(44.dp)) {
                    Row(
                        Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(month, modifier = Modifier.weight(1f))
                        Text("$count items")
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
