package com.example.lifedots.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.lifedots.preferences.TopViewMode
import com.example.lifedots.preferences.WallpaperApplyMode
import com.example.lifedots.preferences.WallpaperSettings
import com.example.lifedots.wallpaper.SnapshotRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal fun calendarSelectionSummary(settings: WallpaperSettings): String {
    val selected = if (settings.topViewMode == TopViewMode.YIL) "Yil · year calendar" else "Umr · life calendar"
    if (settings.applyMode == WallpaperApplyMode.CALENDAR_LOCK_BLACK_HOME) return "$selected selected · lock-screen image"
    return if (settings.autoSwitchSettings.enabled) "$selected selected · automatic rotation is on"
        else "$selected selected · automatic rotation is off"
}

/** A still of the selected saved calendar, using the wallpaper's renderer and screen aspect ratio. */
@Composable
fun CalendarPreview(settings: WallpaperSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val metrics = context.resources.displayMetrics
    val width = metrics.widthPixels.coerceAtLeast(1)
    val height = metrics.heightPixels.coerceAtLeast(1)
    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var preview by remember(settings, width, height) { mutableStateOf<Bitmap?>(null) }
    var previewError by remember { mutableStateOf(false) }
    var rendering by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTime = System.currentTimeMillis()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycleOwner) {
        while (true) {
            delay(60_000)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                refreshTime = System.currentTimeMillis()
            }
        }
    }
    LaunchedEffect(settings, refreshTime, configuration, width, height) {
        rendering = true
        // Coalesce rapid slider changes; keep rendering work and bitmap allocation off the UI thread.
        delay(120)
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val selected = settings.copy(autoSwitchSettings = settings.autoSwitchSettings.copy(enabled = false))
                val full = SnapshotRenderer.render(context, width, height, selected, refreshTime)
                val thumbnailWidth = minOf(width, 640)
                if (thumbnailWidth == width) full else {
                    Bitmap.createScaledBitmap(full, thumbnailWidth,
                        (height.toFloat() * thumbnailWidth / width).toInt().coerceAtLeast(1), true)
                        .also { if (it !== full) full.recycle() }
                }
            }
        }
        preview = result.getOrNull()
        previewError = result.isFailure
        rendering = false
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Resolve both dimensions before measurement. Combining an exact width with a
            // maximum height and aspectRatio can make Compose draw beyond its reported bounds.
            val aspect = width.toFloat() / height
            val previewWidth = minOf(maxWidth * 0.72f, 380.dp * aspect)
            val previewHeight = previewWidth / aspect
            Box(modifier = Modifier.size(width = previewWidth, height = previewHeight)
                .clip(RoundedCornerShape(16.dp)).background(Color.Black),
                contentAlignment = Alignment.Center) {
                val bitmap = preview
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), contentDescription = "Saved ${settings.topViewMode.name.lowercase()} calendar preview",
                        modifier = Modifier.matchParentSize(), contentScale = ContentScale.Fit)
                }
                if (rendering) CircularProgressIndicator(color = Color(0xFFC5A266))
                if (previewError) Text("Preview unavailable. Try reopening this screen.", color = Color.White,
                    modifier = Modifier.padding(16.dp))
            }
        }
        Text(if (settings.topViewMode == TopViewMode.UMR && settings.umrSettings.birthdayEpochMs == 0L)
            "Add your birthday in Customize to build your life calendar."
            else "Saved calendar preview · still image; wallpaper animation may differ",
            color = Color(0xFFAAA49A), fontSize = 12.sp)
    }
}
