package com.inferno.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import com.inferno.gallery.R

/**
 * Screen reference: warm-black canvas, soft espresso illumination,
 * overlapping dark media cards, tiny white labels, amber glass controls.
 *
 * Presentation only: consumes existing Paging data. It never changes files,
 * the Room database, face clusters, local profile or media permissions.
 */
internal object ReferencePalette {
    val Black = Color(0xFF0A0908)
    val Espresso = Color(0xFF24190F)
    val SoftCard = Color(0xFF25211D)
    val Amber = Color(0xFFBF6F1D)
    val AmberBright = Color(0xFFDB902F)
    val Cream = Color(0xFFFFFAF3)
    val Secondary = Color(0xFFCFC4B6)
    val Outline = Color(0x50FFE9D4)
}

@Composable
internal fun ReferenceFilterTabs(
    selectedFilter: Int,
    onSelectFilter: (Int) -> Unit,
    onAlbumsClick: () -> Unit,
    onPeopleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val filters = listOf("All", "Camera")
        filters.forEachIndexed { index, name ->
            ReferenceChip(name, selectedFilter == index, onClick = { onSelectFilter(index) })
        }
        ReferenceChip("Albums", false, onClick = onAlbumsClick)
        ReferenceChip("People", false, onClick = onPeopleClick)
    }
}

@Composable
private fun ReferenceChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) Brush.horizontalGradient(
                    listOf(ReferencePalette.AmberBright, ReferencePalette.Amber)
                ) else Brush.horizontalGradient(
                    listOf(Color(0xFF302A24).copy(alpha = 0.86f), Color(0xFF231E1A).copy(alpha = 0.86f))
                )
            )
            .border(
                0.75.dp,
                if (selected) Color(0xFFEEBD7D) else Color.White.copy(alpha = 0.27f),
                CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = Color.White,
            maxLines = 1
        )
    }
}

/**
 * Unlike the former small-square photo grid, the first two available items
 * become a layered cinematic cover, followed by full-width editorial cards.
 * No placeholder photos are manufactured: all cards show real device media.
 */
@Composable
internal fun ReferenceGallerySurface(
    pagedMedia: LazyPagingItems<GalleryListItem>,
    onMediaClick: (GalleryItem) -> Unit,
    onMediaLongClick: (GalleryItem) -> Unit,
    isSelectionMode: Boolean,
    selectedUris: Set<String>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val firstMediaIndices = (0 until minOf(pagedMedia.itemCount, 16))
        .filter { pagedMedia[it] is GalleryListItem.Item }
        .take(2)
    val first = firstMediaIndices.getOrNull(0)?.let {
        (pagedMedia[it] as? GalleryListItem.Item)?.galleryItem
    }
    val second = firstMediaIndices.getOrNull(1)?.let {
        (pagedMedia[it] as? GalleryListItem.Item)?.galleryItem
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        ReferencePalette.Espresso,
                        ReferencePalette.Black,
                        ReferencePalette.Black
                    )
                )
            ),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 15.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 18.dp
        ),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        if (first != null) {
            item(key = "reference_cover") {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().height(if (second != null) 355.dp else 270.dp)
                ) {
                    ReferencePhotoCard(
                        item = first,
                        modifier = Modifier
                            .fillMaxWidth(if (second != null) 0.79f else 1f)
                            .height(263.dp)
                            .align(Alignment.TopStart),
                        onClick = { onMediaClick(first) },
                        onLongClick = { onMediaLongClick(first) },
                        overline = "YOUR MOMENTS",
                        selected = isSelectionMode && first.uri.toString() in selectedUris
                    )
                    if (second != null) {
                        ReferencePhotoCard(
                            item = second,
                            modifier = Modifier
                                .fillMaxWidth(0.81f)
                                .height(240.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = (-5).dp, y = (-3).dp)
                                .rotate(-8f),
                            onClick = { onMediaClick(second) },
                            onLongClick = { onMediaLongClick(second) },
                            overline = "MEMORIES IN FOCUS",
                            selected = isSelectionMode && second.uri.toString() in selectedUris
                        )
                    }
                }
            }
        }

        item(key = "reference_collection_label") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Your collection",
                        color = ReferencePalette.Cream,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-.4).sp
                    )
                    Text(
                        "Every moment, beautifully kept",
                        color = ReferencePalette.Secondary,
                        fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(35.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_ms_image),
                        contentDescription = null,
                        tint = ReferencePalette.Cream,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        items(count = pagedMedia.itemCount) { index ->
            val entry = pagedMedia[index]
            if (index !in firstMediaIndices) {
                when (entry) {
                    is GalleryListItem.Item -> {
                        val photo = entry.galleryItem
                        ReferencePhotoCard(
                            item = photo,
                            modifier = Modifier.fillMaxWidth().height(225.dp),
                            onClick = { onMediaClick(photo) },
                            onLongClick = { onMediaLongClick(photo) },
                            overline = if (photo.isVideo) "VIDEO" else "PHOTO",
                            selected = isSelectionMode && photo.uri.toString() in selectedUris
                        )
                    }
                    is GalleryListItem.Header -> {
                        Text(
                            entry.title,
                            color = ReferencePalette.Secondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp, start = 5.dp)
                        )
                    }
                    else -> {
                        // A Paging placeholder, never assume that an item exists.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(175.dp)
                                .clip(RoundedCornerShape(27.dp))
                                .background(ReferencePalette.SoftCard)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReferencePhotoCard(
    item: GalleryItem,
    modifier: Modifier,
    overline: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(29.dp))
            .background(ReferencePalette.SoftCard)
            .border(
                if (selected) 2.dp else 0.9.dp,
                if (selected) ReferencePalette.AmberBright else Color.White.copy(alpha = 0.22f),
                RoundedCornerShape(29.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = item.resolvedUri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.24f),
                        0.38f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.80f)
                    )
                )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                overline,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
                color = Color.White.copy(alpha = 0.80f)
            )
            Text(
                item.name.substringBeforeLast('.').ifBlank { "Untitled memory" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(13.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.29f))
                .border(0.6.dp, Color.White.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                androidx.compose.ui.graphics.vector.ImageVector.vectorResource(
                    if (selected) R.drawable.ic_ms_check_circle
                    else if (item.isVideo) R.drawable.ic_ms_play_arrow
                    else R.drawable.ic_ms_image
                ),
                contentDescription = if (selected) "Selected" else if (item.isVideo) "Video" else "Photo",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun ReferenceEmptyGallery(modifier: Modifier = Modifier, subtitle: String = "Your photos and videos will appear here as soon as they are available.") {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ReferencePalette.Espresso, ReferencePalette.Black))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 35.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(RoundedCornerShape(33.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3D2412), Color(0xFF18130E))))
                    .border(1.dp, ReferencePalette.Amber.copy(alpha = 0.55f), RoundedCornerShape(33.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_ms_image),
                    contentDescription = null,
                    tint = ReferencePalette.AmberBright,
                    modifier = Modifier.size(43.dp)
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "Your story starts here",
                color = ReferencePalette.Cream,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-.4).sp,
                textAlign = TextAlign.Center
            )
            Text(
                subtitle,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = ReferencePalette.Secondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
