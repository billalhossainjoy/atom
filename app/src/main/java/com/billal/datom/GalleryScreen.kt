package com.billal.datom

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.billal.datom.media.MediaImage
import com.billal.datom.media.MediaImageStore
import java.util.Locale

fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

fun hasMediaPermission(context: android.content.Context): Boolean =
    mediaPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
fun GalleryScreen(
    onImageClick: (MediaImage) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(hasMediaPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermission = grants.values.all { it }
        if (hasPermission) MediaImageStore.load(context)
    }

    val images by MediaImageStore.images.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Gallery", style = MaterialTheme.typography.headlineMedium)

        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Photo and video access is needed to show your media.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { permissionLauncher.launch(mediaPermissions()) },
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text("Grant access") }
            }
        } else if (images.isEmpty()) {
            Text(
                "No media found.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                items(images, key = { it.id }) { image ->
                    GalleryThumb(image, onClick = { onImageClick(image) })
                }
            }
        }
    }
}

@Composable
private fun GalleryThumb(image: MediaImage, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onClick)) {
        AsyncImage(
            model = image.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (image.isVideo) {
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Video",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            if (image.durationMs > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        formatDuration(image.durationMs),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ImageDetailScreen(
    image: MediaImage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Box(modifier = Modifier.fillMaxSize()) {
            if (image.isVideo) {
                val context = LocalContext.current
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(image.uri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = image.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
