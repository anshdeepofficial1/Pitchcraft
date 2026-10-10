package com.inferno.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleProcessingScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onPersonClick: (Long) -> Unit
) {
    val progress by viewModel.faceProgress.collectAsState()
    val people by viewModel.people.collectAsState()
    val fraction = if (progress.total > 0) {
        (progress.progress.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Scaffold(
        containerColor = ReferencePalette.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Your people", fontWeight = FontWeight.SemiBold, color = ReferencePalette.Cream)
                        Text(
                            if (progress.isIndexing) progress.stage.ifBlank { "Detecting & grouping" }
                            else progress.stage.ifBlank { "Ready" },
                            style = MaterialTheme.typography.labelSmall,
                            color = ReferencePalette.Secondary
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = ReferencePalette.Espresso, navigationIconContentColor = ReferencePalette.Cream
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium, color = ReferencePalette.Cream)
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(ReferencePalette.Espresso, ReferencePalette.Black))
            ).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = RoundedCornerShape(29.dp),
                    color = ReferencePalette.SoftCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ReferencePalette.Outline),
                    shadowElevation = 0.dp
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (progress.isIndexing) "Finding familiar faces" else "People collection",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = ReferencePalette.Cream,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${progress.progress} of ${progress.total} images processed",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                "${(fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = ReferencePalette.AmberBright
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                            color = ReferencePalette.AmberBright,
                            trackColor = ReferencePalette.Espresso
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatText(progress.facesFound.toString(), "faces")
                            StatText(people.size.toString(), "people")
                            StatText(progress.failed.toString(), "failed")
                        }
                        progress.currentImageName?.let {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Now processing",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                it,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        if (progress.isIndexing) {
                            FilledTonalButton(
                                onClick = viewModel::stopFaceIndexing,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Pause scan") }
                        } else {
                            Button(
                                onClick = viewModel::startFaceIndexing,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (progress.progress > 0) "Continue scan" else "Start face scan") }
                        }
                    }
                }
            }

            if (progress.recentUris.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            "Recently processed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(progress.recentUris.reversed()) { uri ->
                                AsyncImage(
                                    model = android.net.Uri.parse(uri),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(18.dp))
                                )
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Detected people",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            gridItems(people, key = { it.clusterId }) { person ->
                Column(
                    modifier = Modifier.clickable { onPersonClick(person.clusterId) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ReferencePalette.Espresso,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReferencePalette.Outline),
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    ) {
                        AsyncImage(
                            model = person.coverCropPath?.let { File(it) },
                            contentDescription = person.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        person.name ?: "Person",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${person.faceCount} faces",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatText(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
