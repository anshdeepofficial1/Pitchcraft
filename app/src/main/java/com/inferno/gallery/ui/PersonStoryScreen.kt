package com.inferno.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

/**
 * Private, device-only People story viewer.
 *
 * Plays the person's real indexed photos with configurable duration, shuffle,
 * repeat, pause/resume and tap-to-skip. No media or face records are written.
 */
@Composable
fun PersonStoryScreen(
    clusterId: Long,
    viewModel: GalleryViewModel,
    onBack: () -> Unit
) {
    val media by viewModel.personMedia.collectAsState()
    val people by viewModel.people.collectAsState()
    val person = people.firstOrNull { it.clusterId == clusterId }
    var position by remember(clusterId) { mutableIntStateOf(0) }
    var playing by remember(clusterId) { mutableStateOf(true) }
    var shuffle by remember(clusterId) { mutableStateOf(false) }
    var repeat by remember(clusterId) { mutableStateOf(false) }
    var intervalSeconds by remember(clusterId) { mutableIntStateOf(4) }

    // An order of references into the original media list, not duplicate media.
    val order = remember(media, shuffle) {
        media.indices.toList().let { if (shuffle) it.shuffled() else it }
    }
    val safePosition = if (order.isEmpty()) 0 else position.coerceIn(0, order.lastIndex)

    LaunchedEffect(clusterId) {
        viewModel.loadPersonMedia(clusterId)
    }

    LaunchedEffect(playing, safePosition, order.size, intervalSeconds, repeat, shuffle) {
        if (playing && order.isNotEmpty()) {
            delay(intervalSeconds * 1000L)
            if (safePosition < order.lastIndex) {
                position = safePosition + 1
            } else if (repeat && order.size > 1) {
                position = 0
            } else {
                playing = false
            }
        }
    }

    if (order.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(ReferencePalette.Espresso, ReferencePalette.Black))
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Preparing memories", color = ReferencePalette.Cream,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Your private photos will appear here.",
                    color = ReferencePalette.Secondary,
                    style = MaterialTheme.typography.bodySmall)
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = ReferencePalette.Amber,
                    modifier = Modifier.padding(top = 9.dp)
                ) {
                    Text("Back to People",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
                        color = ReferencePalette.Cream,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        return
    }

    val current = media[order[safePosition]]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ReferencePalette.Black)
    ) {
        AsyncImage(
            model = current.resolvedUri,
            contentDescription = current.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.30f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(ReferencePalette.Black.copy(alpha = 0.9f), Color.Transparent)
                    )
                )
        )

        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.39f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, ReferencePalette.Black.copy(alpha = 0.94f))
                    )
                )
        )

        // Left / right touch targets stay separate from bottom controls.
        Box(
            modifier = Modifier
                .fillMaxHeight(0.45f)
                .fillMaxWidth(0.32f)
                .align(Alignment.CenterStart)
                .clickable {
                    position = (safePosition - 1).coerceAtLeast(0)
                    playing = true
                }
        )
        Box(
            modifier = Modifier
                .fillMaxHeight(0.45f)
                .fillMaxWidth(0.32f)
                .align(Alignment.CenterEnd)
                .clickable {
                    if (safePosition < order.lastIndex) {
                        position = safePosition + 1
                        playing = true
                    } else if (repeat) {
                        position = 0
                        playing = true
                    } else {
                        playing = false
                    }
                }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 27.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Segment indicators: cap preview to 24 to remain tappable and visible
            // for users with hundreds of person photos.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val segments = order.size.coerceAtMost(24)
                val active = if (order.size <= 24) safePosition
                    else (safePosition * segments / order.size).coerceAtMost(segments - 1)
                repeat(segments) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(
                                if (i <= active) ReferencePalette.AmberBright
                                else Color.White.copy(alpha = 0.30f)
                            )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = person?.coverCropPath?.let { File(it) },
                    contentDescription = "Person profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(43.dp)
                        .clip(CircleShape)
                        .background(ReferencePalette.Espresso)
                        .border(1.4.dp, ReferencePalette.AmberBright, CircleShape)
                )
                Column(Modifier.weight(1f)) {
                    Text(person?.name?.takeIf { it.isNotBlank() } ?: "Your memories",
                        color = ReferencePalette.Cream,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${safePosition + 1} of ${order.size} photos",
                        color = ReferencePalette.Secondary,
                        style = MaterialTheme.typography.labelMedium)
                }
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.13f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, Color.White.copy(alpha = 0.18f)
                    )
                ) {
                    Text("✕",
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                        color = ReferencePalette.Cream, fontSize = 18.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 19.dp, vertical = 35.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                current.name.substringBeforeLast('.').take(70),
                style = MaterialTheme.typography.bodyMedium,
                color = ReferencePalette.Cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                onClick = { playing = !playing },
                shape = CircleShape,
                color = ReferencePalette.AmberBright,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, Color.White.copy(alpha = 0.35f)
                ),
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (playing) "Ⅱ" else "▶", color = ReferencePalette.Black,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(29.dp))
                    .background(ReferencePalette.SoftCard.copy(alpha = 0.94f))
                    .border(1.dp, ReferencePalette.Outline, RoundedCornerShape(29.dp))
                    .padding(horizontal = 7.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StoryActionChip("Shuffle", shuffle) {
                    shuffle = !shuffle
                    position = 0
                    playing = true
                }
                StoryActionChip("${intervalSeconds}s", false) {
                    val steps = listOf(2, 4, 7, 12)
                    val next = steps.indexOf(intervalSeconds).let { (it + 1) % steps.size }
                    intervalSeconds = steps[next]
                }
                StoryActionChip("Repeat", repeat) {
                    repeat = !repeat
                }
            }
        }
    }
}

@Composable
private fun StoryActionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) ReferencePalette.Amber.copy(alpha = 0.45f)
                else ReferencePalette.Espresso.copy(alpha = 0.8f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) ReferencePalette.Cream else ReferencePalette.Secondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
