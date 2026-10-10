package com.inferno.gallery.ui

import android.Manifest
import android.app.DatePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    clusterId: Long,
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onPhotoClick: (String, String?, String?) -> Unit,
    onStory: () -> Unit = {},
    onCreateCollage: (List<String>) -> Unit = {}
) {
    val people by viewModel.people.collectAsState()
    val media by viewModel.personMedia.collectAsState()
    val faces by viewModel.personFaces.collectAsState()
    val person = people.firstOrNull { it.clusterId == clusterId }
    val context = LocalContext.current

    var menuExpanded by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showRelationDialog by remember { mutableStateOf(false) }
    var showCoverDialog by remember { mutableStateOf(false) }
    var showFacesDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var nameDraft by remember(person?.name) { mutableStateOf(person?.name.orEmpty()) }
    var customRelation by remember(person?.relation) { mutableStateOf(person?.relation.orEmpty()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(clusterId) {
        viewModel.loadPersonMedia(clusterId)
    }

    val relationOptions = listOf(
        "Family", "Mother", "Father", "Brother", "Sister",
        "Partner", "Friend", "Relative", "Colleague", "Classmate", "Other"
    )

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Name this person") },
            text = {
                TextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    placeholder = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameDraft.isNotBlank()) {
                            viewModel.renamePerson(clusterId, nameDraft)
                        }
                        showNameDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRelationDialog) {
        Dialog(onDismissRequest = { showRelationDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Relationship",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "How is this person related to you?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    relationOptions.forEach { option ->
                        Surface(
                            onClick = {
                                if (option == "Other") {
                                    customRelation = ""
                                } else {
                                    viewModel.updatePersonRelation(clusterId, option)
                                    showRelationDialog = false
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (person?.relation == option)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Text(
                                option,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                fontWeight = if (person?.relation == option) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = customRelation,
                        onValueChange = { customRelation = it },
                        placeholder = { Text("Custom relation") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.updatePersonRelation(clusterId, null)
                                showRelationDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear") }
                        Button(
                            onClick = {
                                viewModel.updatePersonRelation(
                                    clusterId,
                                    customRelation.takeIf { it.isNotBlank() } ?: "Other"
                                )
                                showRelationDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save") }
                    }
                }
            }
        }
    }

    if (showCoverDialog) {
        Dialog(onDismissRequest = { showCoverDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Change main photo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Choose the face shown on the People card and profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(360.dp)
                    ) {
                        gridItems(faces, key = { it.id }) { face ->
                            val selected = person?.coverFaceId == face.id
                            AsyncImage(
                                model = face.cropCachePath?.let { File(it) },
                                contentDescription = "Face",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .then(
                                        if (selected) Modifier.border(
                                            3.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(18.dp)
                                        ) else Modifier
                                    )
                                    .clickable {
                                        viewModel.changePersonCover(clusterId, face.id)
                                        showCoverDialog = false
                                    }
                            )
                        }
                    }
                    TextButton(
                        onClick = { showCoverDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Close") }
                }
            }
        }
    }

    if (showFacesDialog) {
        Dialog(onDismissRequest = { showFacesDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Manage detected faces",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Separate an incorrect match into a new person.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(380.dp)
                    ) {
                        gridItems(faces, key = { it.id }) { face ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model = face.cropCachePath?.let { File(it) },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                                TextButton(
                                    onClick = {
                                        viewModel.splitFaceIntoNewPerson(face.id, clusterId)
                                    }
                                ) {
                                    Text(
                                        "Separate",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { showFacesDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Done") }
                }
            }
        }
    }

    if (showMergeDialog) {
        Dialog(onDismissRequest = { showMergeDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Merge person",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    people.filter { it.clusterId != clusterId }.take(30).forEach { target ->
                        Surface(
                            onClick = {
                                viewModel.mergePeople(clusterId, target.clusterId)
                                showMergeDialog = false
                                onBack()
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Row(
                                Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = target.coverCropPath?.let { File(it) },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(42.dp).clip(CircleShape)
                                )
                                Spacer(Modifier.size(10.dp))
                                Text(target.name ?: "Person ${target.clusterId}")
                            }
                        }
                    }
                    TextButton(
                        onClick = { showMergeDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Cancel") }
                }
            }
        }
    }

    Scaffold(
        containerColor = ReferencePalette.Black,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        AsyncImage(
                            model = person?.coverCropPath?.let { File(it) },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ReferencePalette.Espresso)
                                .border(1.dp, ReferencePalette.Amber, CircleShape)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                person?.name ?: "Person",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                "Personal memories",
                                style = MaterialTheme.typography.labelSmall,
                                color = ReferencePalette.Secondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReferencePalette.Espresso, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Text("⋮", style = MaterialTheme.typography.headlineSmall)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Change main photo") },
                                onClick = {
                                    menuExpanded = false
                                    showCoverDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Manage detected faces") },
                                onClick = {
                                    menuExpanded = false
                                    showFacesDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Merge with another person") },
                                onClick = {
                                    menuExpanded = false
                                    showMergeDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (person?.isFavorite == true)
                                            "Remove from favorites"
                                        else "Favorite person"
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.setPersonFavorite(
                                        clusterId,
                                        person?.isFavorite != true
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hide person") },
                                onClick = {
                                    menuExpanded = false
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(ReferencePalette.Espresso, ReferencePalette.Black)))
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(29.dp),
                        shadowElevation = 10.dp,
                        tonalElevation = 0.dp,
                        color = ReferencePalette.SoftCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier.fillMaxWidth().height(310.dp)
                    ) {
                        AsyncImage(
                            model = person?.coverCropPath?.let { File(it) },
                            contentDescription = person?.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Text(
                        person?.name ?: "Unnamed person",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(
                        buildString {
                            if (!person?.relation.isNullOrBlank()) {
                                append(person?.relation)
                                append(" • ")
                            }
                            append("${media.size} photos")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "${media.size}" to "Photos",
                            "${faces.size}" to "Faces",
                            (person?.relation ?: "Add") to "Relation"
                        ).forEach { (value, label) ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onStory,
                            enabled = media.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Story") }

                        FilledTonalButton(
                            onClick = {
                                onCreateCollage(media.take(6).map { it.uri.toString() })
                            },
                            enabled = media.size >= 2,
                            modifier = Modifier.weight(1f)
                        ) { Text("Collage") }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Photos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${media.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            gridItems(media, key = { it.id }) { item ->
                AsyncImage(
                    model = item.resolvedUri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPhotoClick(item.id, null, null) }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                val birthday = person?.birthdayEpochDay?.let {
                    runCatching {
                        LocalDate.ofEpochDay(it).format(
                            DateTimeFormatter.ofPattern("d MMMM yyyy")
                        )
                    }.getOrNull()
                }

                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 6.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "About this person",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )

                        ProfileRow(
                            title = "Name",
                            value = person?.name ?: "Add name",
                            onClick = { showNameDialog = true }
                        )
                        ProfileRow(
                            title = "Relationship",
                            value = person?.relation ?: "Add relationship",
                            onClick = { showRelationDialog = true }
                        )
                        ProfileRow(
                            title = "Birthday",
                            value = birthday ?: "Add birthday",
                            onClick = {
                                val initial = person?.birthdayEpochDay?.let(LocalDate::ofEpochDay)
                                    ?: LocalDate.now().minusYears(20)
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        val selected = LocalDate.of(year, month + 1, day)
                                        viewModel.updatePersonBirthday(
                                            clusterId,
                                            selected.toEpochDay(),
                                            true
                                        )
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            notificationPermissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        }
                                    },
                                    initial.year,
                                    initial.monthValue - 1,
                                    initial.dayOfMonth
                                ).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.38f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
