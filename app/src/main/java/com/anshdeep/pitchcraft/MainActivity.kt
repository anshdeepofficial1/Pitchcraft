package com.anshdeep.pitchcraft

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.anshdeep.pitchcraft.data.FaceDatabase
import com.anshdeep.pitchcraft.data.GalleryRepository
import com.anshdeep.pitchcraft.data.MediaItem
import com.anshdeep.pitchcraft.data.PersonGroup
import com.anshdeep.pitchcraft.data.ScanStats
import com.anshdeep.pitchcraft.ml.FaceScanWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Blue = Color(0xFF2F6BFF)
private val BlueSoft = Color(0xFFEAF0FF)
private val Ink = Color(0xFF121722)
private val Muted = Color(0xFF6D7480)
private val Canvas = Color(0xFFF8F9FC)
private val CardBg = Color(0xFFFFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Blue,
                    onPrimary = Color.White,
                    background = Canvas,
                    surface = CardBg,
                    onSurface = Ink,
                    surfaceVariant = BlueSoft
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = Canvas) {
                    PermissionGate()
                }
            }
        }
    }
}

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GalleryRepository(app)
    private val db = FaceDatabase.get(app)

    var media by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var people by mutableStateOf<List<PersonGroup>>(emptyList())
        private set
    var stats by mutableStateOf(ScanStats())
        private set
    var favorites by mutableStateOf<Set<Long>>(emptySet())
        private set
    var loading by mutableStateOf(true)
        private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            val all = withContext(Dispatchers.IO) { repo.loadMedia() }
            val imageCount = all.count { !it.isVideo }
            val p = withContext(Dispatchers.IO) { db.getPeople() }
            val s = withContext(Dispatchers.IO) { db.stats(imageCount) }
            val f = withContext(Dispatchers.IO) { db.favoriteIds() }
            media = all
            people = p
            stats = s
            favorites = f
            loading = false
        }
    }

    fun refreshFaceState() {
        viewModelScope.launch {
            val imageCount = media.count { !it.isVideo }
            stats = withContext(Dispatchers.IO) { db.stats(imageCount) }
            people = withContext(Dispatchers.IO) { db.getPeople() }
        }
    }

    fun startScan() {
        FaceScanWorker.enqueue(getApplication())
        refreshFaceState()
    }

    fun resetAndScan() {
        viewModelScope.launch(Dispatchers.IO) {
            db.clearFaceIndex()
            FaceScanWorker.enqueue(getApplication())
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.toggleFavorite(id) }
            favorites = withContext(Dispatchers.IO) { db.favoriteIds() }
        }
    }

    fun renamePerson(id: Long, name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.renamePerson(id, name) }
            people = withContext(Dispatchers.IO) { db.getPeople() }
        }
    }

    fun hidePerson(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.hidePerson(id) }
            people = withContext(Dispatchers.IO) { db.getPeople() }
        }
    }

    fun mergePeople(from: Long, into: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.mergePeople(from, into) }
            people = withContext(Dispatchers.IO) { db.getPeople() }
        }
    }

    fun mediaForPerson(id: Long): List<MediaItem> {
        val ids = db.mediaIdsForPerson(id)
        return media.filter { it.id in ids }
    }
}

@Composable
private fun PermissionGate(vm: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = it.values.any { value -> value } || hasMediaPermission(context)
        if (granted) vm.refresh()
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(requiredPermissions())
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = BlueSoft) {
                Icon(Icons.Default.Photo, null, tint = Blue, modifier = Modifier.padding(22.dp).size(38.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text("Your photos stay on this phone", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(10.dp))
            Text(
                "Pitchcraft needs photo and video access to show your local gallery and build the People index. Originals are never copied to our storage.",
                color = Muted,
                lineHeight = 21.sp
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { launcher.launch(requiredPermissions()) }, shape = RoundedCornerShape(16.dp)) {
                Text("Allow photo access", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    } else {
        GalleryApp(vm)
    }
}

private fun requiredPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.POST_NOTIFICATIONS
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.POST_NOTIFICATIONS
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaPermission(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= 33 ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 34 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    else -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private enum class Tab(val title: String, val icon: ImageVector) {
    Photos("Photos", Icons.Default.Photo),
    Albums("Albums", Icons.Default.Album),
    Explore("Explore", Icons.Default.Search)
}

private sealed interface Overlay {
    data object None : Overlay
    data object Search : Overlay
    data object Settings : Overlay
    data class Album(val title: String, val ids: Set<Long>) : Overlay
    data class Person(val person: PersonGroup) : Overlay
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryApp(vm: GalleryViewModel) {
    var tab by remember { mutableStateOf(Tab.Photos) }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    var viewer by remember { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            vm.refreshFaceState()
        }
    }

    if (overlay != Overlay.None) {
        when (val current = overlay) {
            Overlay.Search -> SearchScreen(vm, onBack = { overlay = Overlay.None }, onOpen = { viewer = it })
            Overlay.Settings -> SettingsScreen(vm, onBack = { overlay = Overlay.None })
            is Overlay.Album -> CollectionScreen(
                current.title,
                vm.media.filter { it.id in current.ids },
                onBack = { overlay = Overlay.None },
                onOpen = { viewer = it }
            )
            is Overlay.Person -> PersonScreen(
                vm,
                current.person,
                onBack = { overlay = Overlay.None },
                onOpen = { viewer = it }
            )
            else -> Unit
        }
    } else {
        Scaffold(
            containerColor = Canvas,
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text(tab.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink) },
                    actions = {
                        IconButton(onClick = { overlay = Overlay.Search }) { Icon(Icons.Default.Search, "Search") }
                        IconButton(onClick = { overlay = Overlay.Settings }) { Icon(Icons.Default.MoreVert, "More") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White, modifier = Modifier.navigationBarsPadding()) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        ) { padding ->
            Crossfade(tab, label = "tab") { current ->
                when (current) {
                    Tab.Photos -> PhotosScreen(vm, Modifier.padding(padding), onOpen = { viewer = it })
                    Tab.Albums -> AlbumsScreen(vm, Modifier.padding(padding), onOpenAlbum = { overlay = it })
                    Tab.Explore -> ExploreScreen(
                        vm,
                        Modifier.padding(padding),
                        onSearch = { overlay = Overlay.Search },
                        onPerson = { overlay = Overlay.Person(it) },
                        onCollection = { title, ids -> overlay = Overlay.Album(title, ids) }
                    )
                }
            }
        }
    }

    viewer?.let { item ->
        Viewer(vm, item, onClose = { viewer = null }, onChanged = { vm.refresh() })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotosScreen(vm: GalleryViewModel, modifier: Modifier = Modifier, onOpen: (MediaItem) -> Unit) {
    if (vm.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Blue) }
        return
    }
    if (vm.media.isEmpty()) {
        EmptyState(modifier, "No photos yet", "Photos and videos stored on this device will appear here.")
        return
    }
    val groups = vm.media.groupBy { dayLabel(it.dateMillis) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            MomentsStrip(vm.media.take(16), onOpen)
        }
        groups.forEach { (day, itemsForDay) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(day, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 9.dp), fontWeight = FontWeight.SemiBold, color = Ink)
            }
            items(itemsForDay, key = { it.uri.toString() }) { item ->
                MediaTile(item, onClick = { onOpen(item) })
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MomentsStrip(items: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    if (items.isEmpty()) return
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Moments", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.weight(1f))
            Text("Recent highlights", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items.take(5)) { item ->
                Card(
                    modifier = Modifier.width(142.dp).height(176.dp).clickable { onOpen(item) },
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        MediaImage(item, Modifier.fillMaxSize(), ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                        Text(
                            if (item.isVideo) "Video" else SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(item.dateMillis)),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(1f).background(Color(0xFFE9ECF2)).clickable(onClick = onClick)) {
        MediaImage(item, Modifier.fillMaxSize(), ContentScale.Crop)
        if (item.isVideo) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.padding(3.dp).size(16.dp))
            }
        }
    }
}

@Composable
private fun MediaImage(item: MediaItem, modifier: Modifier, scale: ContentScale) {
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(item.uri)
        .apply { if (item.isVideo) videoFrameMillis(1000) }
        .crossfade(true)
        .build()
    AsyncImage(model = request, contentDescription = item.name, modifier = modifier, contentScale = scale)
}

@Composable
private fun AlbumsScreen(vm: GalleryViewModel, modifier: Modifier = Modifier, onOpenAlbum: (Overlay.Album) -> Unit) {
    val favorites = vm.media.filter { it.id in vm.favorites }
    val buckets = vm.media.groupBy { it.bucket }.entries.sortedByDescending { it.value.size }
    val special = buildList {
        if (favorites.isNotEmpty()) add("Favorites" to favorites)
        val videos = vm.media.filter { it.isVideo }
        if (videos.isNotEmpty()) add("Videos" to videos)
        val screenshots = vm.media.filter { it.bucket.contains("screenshot", true) || it.name.contains("screenshot", true) }
        if (screenshots.isNotEmpty()) add("Screenshots" to screenshots)
        val downloads = vm.media.filter { it.bucket.contains("download", true) }
        if (downloads.isNotEmpty()) add("Downloads" to downloads)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("On this device", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp), color = Ink)
        }
        items(special) { entry ->
            AlbumCard(entry.first, entry.second) {
                onOpenAlbum(Overlay.Album(entry.first, entry.second.map { it.id }.toSet()))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Folders", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp), color = Ink)
        }
        items(buckets, key = { it.key }) { entry ->
            AlbumCard(entry.key, entry.value) {
                onOpenAlbum(Overlay.Album(entry.key, entry.value.map { it.id }.toSet()))
            }
        }
    }
}

@Composable
private fun AlbumCard(title: String, items: List<MediaItem>, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BlueSoft),
            modifier = Modifier.fillMaxWidth().aspectRatio(1.08f)
        ) {
            if (items.isNotEmpty()) MediaImage(items.first(), Modifier.fillMaxSize(), ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Folder, null, tint = Blue, modifier = Modifier.size(42.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink)
        Text(items.size.toString() + " items", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun ExploreScreen(
    vm: GalleryViewModel,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit,
    onPerson: (PersonGroup) -> Unit,
    onCollection: (String, Set<Long>) -> Unit
) {
    val recent = vm.media.filter { System.currentTimeMillis() - it.dateMillis < 14L * 24L * 60L * 60L * 1000L }
    val screenshots = vm.media.filter { it.bucket.contains("screenshot", true) || it.name.contains("screenshot", true) }
    val videos = vm.media.filter { it.isVideo }
    val downloads = vm.media.filter { it.bucket.contains("download", true) }
    val camera = vm.media.filter { it.bucket.equals("Camera", true) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSearch)
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = Blue)
                    Spacer(Modifier.width(12.dp))
                    Text("Search photos, people and folders", color = Muted)
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            FaceIndexCard(vm)
        }

        if (vm.people.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("People", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 6.dp))
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(vm.people.take(10)) { person ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(78.dp).clickable { onPerson(person) }
                        ) {
                            AsyncImage(
                                model = person.coverPath?.let { File(it) },
                                contentDescription = person.name,
                                modifier = Modifier.size(72.dp).background(BlueSoft, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(person.name ?: "Person", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Ink)
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Categories", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 8.dp))
        }
        val categories = listOf(
            Triple("Recently added", recent, Icons.Default.AutoAwesome),
            Triple("Videos", videos, Icons.Default.VideoLibrary),
            Triple("Screenshots", screenshots, Icons.Default.Smartphone),
            Triple("Downloads", downloads, Icons.Default.Download),
            Triple("Camera", camera, Icons.Default.CameraAlt),
            Triple("Favorites", vm.media.filter { it.id in vm.favorites }, Icons.Default.Favorite)
        ).filter { it.second.isNotEmpty() }
        items(categories) { category ->
            CategoryCard(category.first, category.second, category.third) {
                onCollection(category.first, category.second.map { it.id }.toSet())
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FaceIndexCard(vm: GalleryViewModel) {
    val s = vm.stats
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = if (s.running) BlueSoft else Color.White,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Blue) {
                    Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("People on this device", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                    Text(
                        when {
                            s.running -> "Indexing " + s.scanned + " of " + s.total
                            s.scanned == 0 -> "Private on-device face grouping"
                            else -> s.people.toString() + " people • " + s.faces + " faces"
                        },
                        color = Muted,
                        fontSize = 13.sp
                    )
                }
                if (!s.running) {
                    TextButton(onClick = { vm.startScan() }) { Text(if (s.scanned == 0) "Start" else "Continue") }
                }
            }
            if (s.running) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { s.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = Blue,
                    trackColor = Color.White,
                    gapSize = 0.dp
                )
                if (s.current.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(s.current, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!s.error.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(s.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CategoryCard(title: String, items: List<MediaItem>, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().height(112.dp).clickable(onClick = onClick)
    ) {
        Box {
            if (items.isNotEmpty()) {
                MediaImage(items.first(), Modifier.fillMaxSize(), ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(5.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(items.size.toString() + " items", color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(vm: GalleryViewModel, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val peopleMatches = if (q.isBlank()) emptyList() else vm.people.filter { (it.name ?: "").contains(q, true) }
    val personIds = peopleMatches.flatMap { vm.mediaForPerson(it.id) }.map { it.id }.toSet()
    val results = if (q.isBlank()) vm.media.take(80) else vm.media.filter {
        it.name.contains(q, true) || it.bucket.contains(q, true) || it.id in personIds ||
            (q.equals("video", true) && it.isVideo) ||
            (q.equals("favorite", true) && it.id in vm.favorites)
    }

    Column(Modifier.fillMaxSize().background(Canvas).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search photos, people, folders") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )
        }
        if (peopleMatches.isNotEmpty()) {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(peopleMatches) { person ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
                        AsyncImage(model = person.coverPath?.let { File(it) }, null, Modifier.size(60.dp).background(BlueSoft, CircleShape), contentScale = ContentScale.Crop)
                        Text(person.name ?: "Person", fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize().padding(top = 8.dp)
        ) {
            items(results, key = { it.uri.toString() }) { MediaTile(it) { onOpen(it) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionScreen(title: String, media: List<MediaItem>, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Column { Text(title, fontWeight = FontWeight.Bold); Text(media.size.toString() + " items", color = Muted, fontSize = 12.sp) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(media, key = { it.uri.toString() }) { MediaTile(it) { onOpen(it) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonScreen(vm: GalleryViewModel, person: PersonGroup, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
    var rename by remember { mutableStateOf(false) }
    var merge by remember { mutableStateOf(false) }
    val media = vm.mediaForPerson(person.id)

    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Column {
                        Text(person.name ?: "Person", fontWeight = FontWeight.Bold)
                        Text(media.size.toString() + " photos", color = Muted, fontSize = 12.sp)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, "Rename") }
                    IconButton(onClick = { merge = true }) { Icon(Icons.Default.Person, "Merge") }
                    IconButton(onClick = { vm.hidePerson(person.id); onBack() }) { Icon(Icons.Default.Archive, "Hide") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(media, key = { it.uri.toString() }) { MediaTile(it) { onOpen(it) } }
        }
    }

    if (rename) {
        RenameDialog(person.name ?: "", onDismiss = { rename = false }) {
            vm.renamePerson(person.id, it)
            rename = false
        }
    }
    if (merge) {
        AlertDialog(
            onDismissRequest = { merge = false },
            title = { Text("Merge with another person") },
            text = {
                Column {
                    vm.people.filter { it.id != person.id }.take(12).forEach { other ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                vm.mergePeople(person.id, other.id)
                                merge = false
                                onBack()
                            }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(model = other.coverPath?.let { File(it) }, null, Modifier.size(42.dp).background(BlueSoft, CircleShape), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(12.dp))
                            Text(other.name ?: "Person " + other.id)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { merge = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this person") },
        text = { TextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("Gallery settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            SettingsRow(Icons.Default.Person, "People indexing", if (vm.stats.running) "Running on this device" else vm.stats.scanned.toString() + " / " + vm.stats.total + " scanned")
            Divider()
            SettingsRow(Icons.Default.GridView, "Local originals", "No duplicate photo storage")
            Divider()
            SettingsRow(Icons.Default.Smartphone, "Privacy", "Face data and names stay on this device")
            Spacer(Modifier.height(20.dp))
            if (vm.stats.running) {
                LinearProgressIndicator(progress = { vm.stats.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Blue)
            } else {
                Button(onClick = { vm.startScan() }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Person, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.stats.scanned == 0) "Start People indexing" else "Scan new photos")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { vm.resetAndScan() }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Rebuild face index")
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Face stack", fontWeight = FontWeight.Bold, color = Ink)
            Text("ML Kit face detector + MobileFaceNet embeddings + local cosine clustering. No image-generation model is included.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(13.dp), color = BlueSoft) {
            Icon(icon, null, tint = Blue, modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Viewer(vm: GalleryViewModel, item: MediaItem, onClose: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var info by remember { mutableStateOf(false) }
    val isFav = item.id in vm.favorites
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            onClose()
            onChanged()
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (item.isVideo) VideoPlayer(item)
            else MediaImage(item, Modifier.fillMaxSize(), ContentScale.Fit)

            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.36f)).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                Text(item.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { info = true }) { Icon(Icons.Default.Info, "Info", tint = Color.White) }
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.58f)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ViewerAction(Icons.Default.Share, "Share") { share(context, item) }
                    ViewerAction(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite") { vm.toggleFavorite(item.id) }
                    ViewerAction(Icons.Default.Edit, "Edit") { edit(context, item) }
                    ViewerAction(Icons.Default.Delete, "Trash") {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val pending = MediaStore.createTrashRequest(context.contentResolver, listOf(item.uri), true)
                            trashLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                        } else {
                            context.contentResolver.delete(item.uri, null, null)
                            onClose()
                            onChanged()
                        }
                    }
                }
            }
        }
    }

    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            title = { Text(item.name) },
            text = {
                Column {
                    Text("Folder: " + item.bucket)
                    Text("Type: " + item.mime)
                    Text("Resolution: " + item.width + " × " + item.height)
                    Text("Size: " + humanSize(item.size))
                    Text("Date: " + DateFormat.getDateTimeInstance().format(Date(item.dateMillis)))
                    Spacer(Modifier.height(8.dp))
                    Text("Original file remains in Android storage.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { info = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Icon(icon, label, tint = Color.White)
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun VideoPlayer(item: MediaItem) {
    val context = LocalContext.current
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
        modifier = Modifier.fillMaxSize()
    )
}

private fun share(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = item.mime
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}

private fun edit(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(item.uri, item.mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Edit")) }
}

@Composable
private fun EmptyState(modifier: Modifier, title: String, subtitle: String) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = BlueSoft) { Icon(Icons.Default.Photo, null, tint = Blue, modifier = Modifier.padding(18.dp).size(34.dp)) }
        Spacer(Modifier.height(14.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
        Text(subtitle, color = Muted, modifier = Modifier.padding(horizontal = 32.dp), lineHeight = 19.sp)
    }
}

private fun dayLabel(time: Long): String {
    val today = java.time.LocalDate.now()
    val date = java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM"))
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"))
    }
}

private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
}
