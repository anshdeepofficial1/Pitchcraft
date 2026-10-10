package com.inferno.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import com.inferno.gallery.ui.theme.ShapeFull
import com.inferno.gallery.ui.utils.pressScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inferno.gallery.ui.theme.MotionTokens
import com.inferno.gallery.ui.theme.NavDockLabelStyle
import com.inferno.gallery.ui.utils.tick
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.painterResource
import com.inferno.gallery.R

/**
 * Material 3 Expressive Floating Navigation Pill
 * Features a single accented indicator pill that smoothly slides across the tabs
 * with graceful non-bouncy physics and zero performance overhead.
 */
/**
 * Reference-style amber glass dock: five compact pictograms, one raised cream
 * central monogram, no labels, no expanding tabs or opaque Material bottom bar.
 * All destinations are backed by existing app routes.
 */
@Composable
fun FloatingNavigationPill(
    currentRoute: String?,
    onNavigateToPhotos: () -> Unit,
    onNavigateToAlbums: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToPeople: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onCentralAction: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.82f)
            .height(58.dp)
            .clip(ShapeFull)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(
                        Color(0xFFCD802D).copy(alpha = 0.93f),
                        Color(0xFFAD5816).copy(alpha = 0.96f),
                        Color(0xFFCF7B27).copy(alpha = 0.93f)
                    )
                )
            )
            .border(1.dp, Color(0x66FFE0B3), ShapeFull),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReferenceDockIcon(
                icon = R.drawable.ic_ms_image,
                label = "Home",
                selected = currentRoute == "photos",
                onClick = onNavigateToPhotos
            )
            ReferenceDockIcon(
                icon = R.drawable.ic_ms_photo_album,
                label = "Albums",
                selected = currentRoute == "albums",
                onClick = onNavigateToAlbums
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFFFFF7EC))
                     .clickable(onClick = onCentralAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.pitchcraft_mark),
                    contentDescription = "Create album",
                    modifier = Modifier.size(34.dp)
                )
            }
            ReferenceDockIcon(
                icon = R.drawable.ic_ms_person,
                label = "People",
                selected = currentRoute == "all_people",
                onClick = onNavigateToPeople
            )
            ReferenceDockIcon(
                icon = R.drawable.ic_ms_star,
                label = "Favorites",
                selected = currentRoute?.startsWith("album/Favorites") == true,
                onClick = onNavigateToFavorites
            )
        }
    }
}

@Composable
private fun ReferenceDockIcon(
    icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(
                if (selected) Color.White.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = label,
            tint = Color(0xFFFFF9F1),
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
fun SlidingPillTabItem(
    icon: @Composable () -> Unit,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current

    val touchScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tabItemTouchScale"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            Color(0xFF9B4108)
        } else {
            Color.White.copy(alpha = 0.92f)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tabItemContentColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = touchScale
                scaleY = touchScale
            }
            .clip(ShapeFull)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    view.tick()
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon()
            }
        }
    }
}

@Composable
fun DockItem(
    icon: @Composable () -> Unit,
    label: String,
    isSelected: Boolean,
    isTertiary: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current

    val touchScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dockItemTouchScale"
    )

    val pillColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (isTertiary) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (isTertiary) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "contentColor"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = touchScale
                scaleY = touchScale
            }
            .clip(ShapeFull)
            .background(pillColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    view.tick()
                    onClick()
                }
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + expandHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        initialOffsetX = { -it / 4 }
                    ),
                    exit = fadeOut(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + shrinkHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + slideOutHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        targetOffsetX = { -it / 4 }
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        icon()
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

fun getTabRouteIndex(route: String?): Int {
    return when (route) {
        "photos" -> 0
        "albums", "album/{bucketName}" -> 1
        "search" -> 2
        "settings" -> 3
        else -> 0
    }
}

// Fast, fluid tab crossfade transitions: eliminates heavy full-screen grid slide bottleneck
fun getEnterTransition(initialRoute: String?, targetRoute: String?): androidx.compose.animation.EnterTransition {
    return fadeIn(
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 180,
            easing = MotionTokens.EmphasizedDecelerateEasing
        )
    )
}

fun getExitTransition(initialRoute: String?, targetRoute: String?): androidx.compose.animation.ExitTransition {
    return fadeOut(
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 120,
            easing = MotionTokens.EmphasizedAccelerateEasing
        )
    )
}
