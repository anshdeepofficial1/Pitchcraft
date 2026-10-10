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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
            columns = GridCells.Fixed(2),
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
                        shape = RoundedCornerShape(26.dp),
                        color = ReferencePalette.SoftCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ReferencePalette.Outline)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    "Finding familiar faces",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    progress.progress.toString() + " / " + progress.total.toString() +
                                        " images  •  " + progress.facesFound.toString() + " faces",
                                    color = ReferencePalette.Secondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                "View",
                                color = ReferencePalette.AmberBright,
                                style = MaterialTheme.typography.labelLarge
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
                                "Detected people will appear here after your photos are indexed.",
                                color = ReferencePalette.Secondary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            items(items = people, key = { it.clusterId }) { person ->
                val shape = RoundedCornerShape(28.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.80f)
                        .clip(shape)
                        .background(ReferencePalette.SoftCard, shape)
                        .border(0.8.dp, ReferencePalette.Outline, shape)
                        .clickable { onPersonClick(person.clusterId) }
                ) {
                    AsyncImage(
                        model = person.coverCropPath?.let { File(it) },
                        contentDescription = person.name ?: "Person",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(1.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.10f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.84f)
                                    )
                                ),
                                shape
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 14.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            person.name?.takeIf { it.isNotBlank() } ?: "Person",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val secondaryText = listOfNotNull(
                            person.relation?.takeIf { it.isNotBlank() },
                            person.faceCount.toString() + " faces"
                        ).joinToString(" • ")
                        Text(
                            secondaryText,
                            color = Color.White.copy(alpha = 0.79f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
