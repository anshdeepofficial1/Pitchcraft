package com.inferno.gallery.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.inferno.gallery.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    clusterId: Long,
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onPhotoClick: (String, String?, String?) -> Unit
) {
    val people by viewModel.people.collectAsState()
    val media by viewModel.personMedia.collectAsState()
    val faces by viewModel.personFaces.collectAsState()
    val person = people.firstOrNull { it.clusterId == clusterId }

    var showRename by remember { mutableStateOf(false) }
    var showMerge by remember { mutableStateOf(false) }
    var nameDraft by remember(person?.name) { mutableStateOf(person?.name.orEmpty()) }

    LaunchedEffect(clusterId) {
        viewModel.loadPersonMedia(clusterId)
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Name this person") },
            text = {
                TextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    placeholder = { Text("Person name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameDraft.isNotBlank()) viewModel.renamePerson(clusterId, nameDraft)
                        showRename = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            person?.name ?: "Person",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${media.size} photos • ${faces.size} faces",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    IconButton(onClick = { showRename = true }) {
                        Icon(
                            imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_ms_person),
                            contentDescription = "Rename"
                        )
                    }
                    Box {
                        IconButton(onClick = { showMerge = true }) {
                            Text("⋮", style = MaterialTheme.typography.headlineSmall)
                        }
                        DropdownMenu(expanded = showMerge, onDismissRequest = { showMerge = false }) {
                            people.filter { it.clusterId != clusterId }.take(40).forEach { target ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Merge into ${target.name ?: "Person ${target.clusterId}"}"
                                        )
                                    },
                                    onClick = {
                                        showMerge = false
                                        viewModel.mergePeople(clusterId, target.clusterId)
                                        onBack()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (person?.isFavorite == true) "Remove favorite" else "Favorite person") },
                                onClick = {
                                    showMerge = false
                                    viewModel.setPersonFavorite(clusterId, person?.isFavorite != true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hide person") },
                                onClick = {
                                    showMerge = false
                                    viewModel.setPersonHidden(clusterId, true)
                                    onBack()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (faces.isNotEmpty()) {
                Text(
                    "Face matches",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(faces, key = { it.id }) { face ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 1.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = face.cropCachePath?.let { File(it) },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(82.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                TextButton(
                                    onClick = { viewModel.splitFaceIntoNewPerson(face.id, clusterId) }
                                ) {
                                    Text("Separate", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Photos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                if (person?.name.isNullOrBlank()) {
                    FilledTonalButton(onClick = { showRename = true }) {
                        Text("Add name")
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                gridItems(media, key = { it.id }) { item ->
                    AsyncImage(
                        model = item.resolvedUri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { onPhotoClick(item.id, null, null) }
                    )
                }
            }
        }
    }
}
