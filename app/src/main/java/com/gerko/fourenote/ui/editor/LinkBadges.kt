package com.gerko.fourenote.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.ink.PageLink
import com.gerko.fourenote.data.ink.linkAnchorBounds
import kotlin.math.roundToInt

/**
 * A small link badge at the top-right corner of each link's anchors on the page. Tapping one
 * follows the link; long-pressing offers to remove it. The badge of a link just followed pulses,
 * so the reader can see where they landed.
 */
@Composable
fun LinkBadges(
    page: PageState,
    pxPerUnit: Float,
    flashLinkId: String?,
    onFollow: (PageLink) -> Unit,
    onRemove: (PageLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val pageWidth = constraints.maxWidth.toFloat()
        val pageHeight = constraints.maxHeight.toFloat()
        for (link in page.links) {
            val bounds = linkAnchorBounds(link, page.strokes, page.texts, page.images) ?: continue
            LinkBadge(
                link = link,
                x = bounds[2] * pxPerUnit,
                y = bounds[1] * pxPerUnit,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                flashing = link.id == flashLinkId,
                onFollow = { onFollow(link) },
                onRemove = { onRemove(link) },
            )
        }
    }
}

@Composable
private fun LinkBadge(
    link: PageLink,
    x: Float,
    y: Float,
    pageWidth: Float,
    pageHeight: Float,
    flashing: Boolean,
    onFollow: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // Three quick pulses: enough to catch the eye without holding it.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(flashing) {
        if (!flashing) return@LaunchedEffect
        repeat(3) {
            pulse.animateTo(1.6f, tween(160))
            pulse.animateTo(1f, tween(160))
        }
    }
    val size = 28.dp
    val half = with(androidx.compose.ui.platform.LocalDensity.current) { size.toPx() / 2 }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // Centred on the anchors' top-right corner, kept inside the page.
            .offset {
                IntOffset(
                    (x.coerceIn(half, (pageWidth - half).coerceAtLeast(half)) - half).roundToInt(),
                    (y.coerceIn(half, (pageHeight - half).coerceAtLeast(half)) - half).roundToInt(),
                )
            }
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            },
    ) {
        Surface(
            shape = CircleShape,
            color = if (flashing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 2.dp,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .combinedClickable(onClick = onFollow, onLongClick = { menu = true }),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = "Open link",
                    tint = if (flashing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Remove link") },
                onClick = {
                    menu = false
                    onRemove()
                },
            )
        }
    }
}
