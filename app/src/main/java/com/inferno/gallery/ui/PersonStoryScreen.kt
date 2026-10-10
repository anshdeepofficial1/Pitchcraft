package com.inferno.gallery.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun PersonStoryScreen(
    clusterId: Long,
    viewModel: GalleryViewModel,
    onBack: () -> Unit
) {
    val media by viewModel.personMedia.collectAsState()
    val people by viewModel.people.collectAsState()
    val person = people.firstOrNull { it.clusterId == clusterId }
    var index by remember(clusterId) { mutableIntStateOf(0) }

    LaunchedEffect(clusterId) {
        viewModel.loadPersonMedia(clusterId)
    }

    LaunchedEffect(index, media.size) {
        if (media.isNotEmpty()) {
            delay(3500)
            if (index < media.lastIndex) {
                index++
            }
        }
    }

    if (media.isEmpty()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Preparing story…", color = Color.White)
        }
        return
    }

    if (index > media.lastIndex) index = media.lastIndex
    val current = media[index]

    Box(
        Modifier.fillMaxSize().background(Color.Black)
    ) {
        AsyncImage(
            model = current.resolvedUri,
            contentDescription = current.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.25f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                    )
                )
        )

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 18.dp, start = 14.dp, end = 14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                media.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (i <= index) Color.White
                                else Color.White.copy(alpha = 0.32f)
                            )
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        person?.name ?: "Person",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val relation = person?.relation
                    if (!relation.isNullOrBlank()) {
                        Text(
                            relation,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                IconButton(onClick = onBack) {
                    Text(
                        "×",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.36f)
                .align(Alignment.CenterStart)
                .clickable {
                    if (index > 0) index--
                }
        )
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.36f)
                .align(Alignment.CenterEnd)
                .clickable {
                    if (index < media.lastIndex) index++
                }
        )

        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.22f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
        ) {
            Text(
                "${index + 1} / ${media.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp)
            )
        }
    }
}
