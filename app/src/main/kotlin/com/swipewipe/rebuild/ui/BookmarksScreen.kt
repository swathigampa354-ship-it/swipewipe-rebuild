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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.swipewipe.rebuild.GalleryItem
import com.swipewipe.rebuild.db.BookmarkEntity

@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkEntity>,
    byId: Map<String, GalleryItem>,
    onRemove: (BookmarkEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(12.dp)) {
        Text(
            "Bookmarks (${bookmarks.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (bookmarks.isEmpty()) {
            Text(
                "Right-swipe photos in the deck to bookmark them here.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            items(bookmarks, key = { b -> b.mediaSource + b.mediaId }) { b ->
                val item = byId[b.mediaId]
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item?.uri,
                            contentDescription = item?.displayName ?: b.mediaId,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp)
                        )
                        Column(
                            Modifier.weight(1f).padding(start = 10.dp)
                        ) {
                            Text(
                                item?.displayName ?: b.mediaId,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1
                            )
                            Text(
                                b.mediaSource + " • " + java.text.SimpleDateFormat(
                                    "yyyy-MM-dd", java.util.Locale.US
                                ).format(java.util.Date(b.createdAt)),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        TextButton(onClick = { onRemove(b) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}
