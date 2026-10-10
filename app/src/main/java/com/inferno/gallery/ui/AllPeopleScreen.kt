package com.inferno.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllPeopleScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onPersonClick: (Long) -> Unit,
    onProgressClick: () -> Unit
) {
    val people by viewModel.people.collectAsState()
    val progress by viewModel.faceProgress.collectAsState()

    Scaffold(
        containerColor = ReferencePalette.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "People",
                            color = ReferencePalette.Cream,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            people.size.toString() + " people on this device",
                            color = ReferencePalette.Secondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ReferencePalette.Espresso,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(ReferencePalette.Espresso, ReferencePalette.Black))
                )
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "Your people",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 22.sp
                    )
                    Text(
                        "Private face collections, stored only on your device",
                        style = MaterialTheme.typography.bodySmall,
                        color = ReferencePalette.Secondary
                    )
                }
            }

            if (progress.isIndexing) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        onClick = onProgressClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = ReferencePalette.SoftCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReferencePalette.Outline)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Finding familiar faces", color = ReferencePalette.Cream,
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${progress.progress} / ${progress.total} photos • ${progress.facesFound} faces",
                                        color = ReferencePalette.Secondary,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Details", color = ReferencePalette.AmberBright)
                            }
                            LinearProgressIndicator(
                                progress = { if (progress.total > 0) (progress.progress.toFloat() / progress.total).coerceIn(0f,1f) else 0f },
                                modifier = Modifier.fillMaxWidth().height(5.dp),
                                color = ReferencePalette.AmberBright,
                                trackColor = ReferencePalette.Espresso
                            )
                        }
                    }
                }
            }

            if (people.isEmpty() && !progress.isIndexing) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clickable { viewModel.startFaceIndexing() }
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF342113), Color(0xFF19130F))
                                ),
                                RoundedCornerShape(29.dp)
                            )
                            .border(1.dp, ReferencePalette.Outline, RoundedCornerShape(29.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            Modifier.padding(horizontal = 27.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "No face groups yet",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                "Tap to privately find familiar faces in your gallery.",
                                color = ReferencePalette.Secondary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                            Text("Start scanning", color = ReferencePalette.AmberBright,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            items(items = people, key = { it.clusterId }) { person ->
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { onPersonClick(person.clusterId) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(99.dp)
                            .clip(CircleShape)
                            .background(ReferencePalette.Espresso)
                            .border(1.5.dp, ReferencePalette.Amber.copy(alpha = 0.65f), CircleShape)
                            .padding(3.dp)
                            .clip(CircleShape)
                    ) {
                        AsyncImage(
                            model = person.coverCropPath?.let { File(it) },
                            contentDescription = person.name ?: "Person",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        person.name?.takeIf { it.isNotBlank() } ?: "Person",
                        color = ReferencePalette.Cream,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${person.faceCount} photos",
                        color = ReferencePalette.Secondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
