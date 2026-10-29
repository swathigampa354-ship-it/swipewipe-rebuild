package com.swipewipe.rebuild.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.swipewipe.rebuild.GalleryItem
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SwipeDecision { DELETE, KEEP, BOOKMARK }

@Composable
fun DeckScreen(
    items: List<GalleryItem>,
    loading: Boolean,
    confirmDelete: Boolean,
    onDecide: (GalleryItem, SwipeDecision, onConfirmed: (() -> Unit)?) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val top = items.firstOrNull()
    Box(modifier.fillMaxSize().padding(12.dp)) {
        when {
            loading -> Text(
                "Loading gallery…",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center)
            )
            top == null -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("All caught up", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Nothing left to review. Pull down to refresh or rescan.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onRescan, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Rescan")
                }
            }
            else -> {
                Box(
                    Modifier.align(Alignment.Center)
                        .fillMaxWidth()
                        .height(460.dp)
                ) {
                    SwipeCard(top, confirmDelete, onDecide)
                }
                Text(
                    "${items.size} left • swipe left = delete, right = keep",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
                )
            }
        }
    }
}

@Composable
private fun SwipeCard(
    item: GalleryItem,
    confirmDelete: Boolean,
    onDecide: (GalleryItem, SwipeDecision, onConfirmed: (() -> Unit)?) -> Unit
) {
    var offsetX by remember(item.id) { mutableStateOf(0f) }
    var offsetY by remember(item.id) { mutableStateOf(0f) }
    val density = LocalDensity.current

    val flyAnim = remember(item.id) { Animatable(1f) }
    LaunchedEffect(item.id) {
        if (flyAnim.value < 1f) flyAnim.animateTo(1f, tween(120))
    }

    val scaleX = remember(item.id) { Animatable(1f) }
    val dragging = abs(offsetX) > 40f

    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
                rotationZ = (offsetX / 40f).coerceIn(-12f, 12f)
                scaleX = if (dragging) 1.02f else 1f
                scaleY = if (dragging) 1.02f else 1f
                alpha = if (dragging) 0.92f else 1f
            }
            .pointerInput(item.id, confirmDelete) {
                detectDragGestures(
                    onDragStart = { offsetX = 0f; offsetY = 0f },
                    onDragEnd = {
                        val px = offsetX
                        offsetX = 0f
                        offsetY = 0f
                        val thresholdPx = with(density) { 130.dp.toPx() }
                        when {
                            px < -thresholdPx -> onDecide(item, SwipeDecision.DELETE) {}
                            px > thresholdPx -> onDecide(item, SwipeDecision.KEEP) {}
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        offsetX += drag.x
                        offsetY += drag.y * 0.25f
                    }
                )
            }
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (abs(offsetX) > 50) {
                Text(
                    when {
                        offsetX < 0 -> "DELETE"
                        else -> "KEEP"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (offsetX < 0)
                        androidx.compose.ui.graphics.Color(0xFFEF5350)
                    else
                        androidx.compose.ui.graphics.Color(0xFF66BB6A),
                    modifier = Modifier
                        .align(if (offsetX < 0) Alignment.TopStart else Alignment.TopEnd)
                        .padding(24.dp)
                        .graphicsLayer {
                            alpha = (abs(offsetX) / 300f).coerceIn(0f, 1f)
                        }
                )
            }
        }
    }
}
