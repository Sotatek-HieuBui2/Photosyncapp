package com.example.photosync.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photosync.data.local.MediaItemEntity

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaItemCard(
    item: MediaItemEntity, 
    onClick: () -> Unit
) {
    val itemType = if (item.mimeType.startsWith("video/")) "Video" else "Photo"
    val syncStatus = if (item.isSynced) "Synced" else "Not synced"
    
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(
                onClick = onClick,
                onClickLabel = "Open $itemType"
            )
            .semantics {
                contentDescription = "$itemType ${item.fileName}, $syncStatus"
            },
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Optimize Cloud Image Loading: Use thumbnail size for grid
            val model = if (item.isLocal) {
                item.id 
            } else {
                // Append parameters for thumbnail size (w400-h400-c for crop)
                // This ensures we don't download the full multi-megabyte image for a small card
                item.remoteUrl?.let { "$it=w400-h400-c" } ?: item.id
            }
            
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(true)
                    .placeholder(android.R.color.darker_gray) // Simple placeholder
                    .build(),
                contentDescription = null, // Handled by parent semantics
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Gradient Scrim for visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f)
                            )
                        )
                    )
            )

            // Sync Status Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = MaterialTheme.shapes.extraSmall
                    )
                    .padding(4.dp)
            ) {
                if (!item.isLocal) {
                        Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Cloud Only",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                } else if (item.isSynced) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Synced",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Not Synced",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            // Video Indicator
            if (item.mimeType.startsWith("video/")) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                )
            }
        }
    }
}
