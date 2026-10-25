package com.example.lifedots

import android.annotation.SuppressLint
import android.Manifest
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.lifedots.ui.components.CalendarPreview
import com.example.lifedots.ui.components.calendarSelectionSummary
import com.example.lifedots.wallpaper.LockWallpaperController
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.WallpaperApplyMode
import com.example.lifedots.service.KeepAliveService
import com.example.lifedots.ui.components.rememberUxFeedback
import com.example.lifedots.ui.theme.LifeDotsTheme
import com.example.lifedots.updater.UpdateChecker
import com.example.lifedots.updater.UpdateInfo
import com.example.lifedots.updater.UpdateInstaller
import com.example.lifedots.updater.UpdateNotifier
import com.example.lifedots.wallpaper.LifeDotsWallpaperService
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private var applyingWallpaper by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Touch the prefs singleton on every launch so the rebrand migration
        // runs the moment a user opens the app — not only when the wallpaper
        // engine happens to start. Critical for users upgrading from upstream
        // LifeDots where view_mode=CONTINUOUS was persisted.
        LifeDotsPreferences.getInstance(this)
        enableEdgeToEdge()
        // Keep Android navigation and status controls available on every screen.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            show(WindowInsetsCompat.Type.systemBars())
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.BLACK
        // If the user opted into "calendar on lock, black on home" mode,
        // refresh the lock snapshot every time the app opens so the calendar
        // reflects today's date even when the user hasn't tapped Set again.
        setContent {
            LifeDotsTheme {
                val installer = remember { UpdateInstaller(this) }
                OnboardingScreen(
                    onSetWallpaper = { setAsWallpaper() },
                    applyingWallpaper = applyingWallpaper,
                    onOpenSettings = { openSettings() },
                    onAllowBackground = { requestIgnoreBatteryOptimizations() },
                    onOpenSamsungNeverSleeping = { openSamsungNeverSleeping() },
                    onInstallApk = { apk -> installer.launchInstaller(this, apk) },
                    autoCheckOnLaunch = true,
                    triggerFromNotification = intent?.getBooleanExtra(
                        UpdateNotifier.EXTRA_FROM_UPDATE_NOTIFICATION, false
                    ) ?: false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        // Shows the system dialog "Allow [app] to ignore battery optimizations? — Allow / Deny".
        // One tap and Samsung's Freecess + stock Android Doze stop killing the wallpaper.
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        if (!tryStart(intent, "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")) {
            // Fallback: open the full list, user finds the app manually
            tryStart(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                "IGNORE_BATTERY_OPTIMIZATION_SETTINGS"
            )
        }
    }

    private fun openSamsungNeverSleeping() {
        // Samsung One UI has a SECOND layer of restriction on top of standard Android
        // battery optimization: "Background usage limits → Never sleeping apps". The
        // user has to manually add the app to that list — there's no system dialog
        // that flips it. Best we can do is deep-link to the page.
        // Component path is fairly stable across One UI versions; if it ever changes,
        // tryStart falls back to opening the generic Device Care page.
        val samsungIntent = Intent().apply {
            setClassName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.notsleeping.NotSleepingActivity"
            )
        }
        if (tryStart(samsungIntent, "SAMSUNG_NEVER_SLEEPING")) return

        // Fallback 1: Samsung Device Care top-level
        val deviceCare = Intent(Intent.ACTION_MAIN).apply {
            setClassName("com.samsung.android.lool", "com.samsung.android.sm.ui.dashboard.SmartManagerDashBoardActivity")
        }
        if (tryStart(deviceCare, "SAMSUNG_DEVICE_CARE")) return

        // Fallback 2: app details, where most OEMs expose their battery/autostart controls.
        tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }, "APP_DETAILS_SETTINGS")
    }

    /**
     * Branch on the user's chosen apply mode:
     *  - BOTH_LIVE: open the live wallpaper picker (Android paints the engine
     *    on both screens — the default behaviour).
     *  - CALENDAR_LOCK_BLACK_HOME: skip the picker entirely; paint today's
     *    Yil/Umr snapshot to the lock screen and pure black to the home
     *    screen via WallpaperManager.setBitmap.
     */
    private fun setAsWallpaper() {
        val prefs = LifeDotsPreferences.getInstance(this)
        when (prefs.settings.applyMode) {
            WallpaperApplyMode.BOTH_LIVE -> openWallpaperPicker()
            WallpaperApplyMode.CALENDAR_LOCK_BLACK_HOME -> applyCalendarLockBlackHome()
        }
    }

    private fun applyCalendarLockBlackHome() {
        if (applyingWallpaper) return
        applyingWallpaper = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { LockWallpaperController.apply(this@MainActivity) }
                Toast.makeText(this@MainActivity, "Lock calendar and black home screen updated", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.w("LifeDots", "Could not apply lock calendar", e)
                Toast.makeText(this@MainActivity, e.message ?: "Couldn't update wallpaper. Try again.", Toast.LENGTH_LONG).show()
            } finally {
                applyingWallpaper = false
            }
        }
    }

    private fun refreshLockSnapshotIfNeeded() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { LockWallpaperController.refreshIfOwned(this@MainActivity) }
                .onFailure { Log.w("LifeDots", "Could not refresh owned lock calendar", it) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLockSnapshotIfNeeded()
    }

    private fun openWallpaperPicker() {
        val component = ComponentName(this@MainActivity, LifeDotsWallpaperService::class.java)
        Log.i("LifeDots", "openWallpaperPicker: trying CHANGE_LIVE_WALLPAPER for $component")

        if (prefersGenericLiveWallpaperChooser()) {
            val chooser = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStart(chooser, "OEM_LIVE_WALLPAPER_CHOOSER")) return
        }

        // Primary path: ACTION_CHANGE_LIVE_WALLPAPER with the LifeDots component
        // pre-selected. NEW_TASK is required on Samsung/Android 15 for cross-process
        // wallpaper picker launches; without it the system silently rejects the start.
        val primary = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(primary, "CHANGE_LIVE_WALLPAPER")) return

        // Fallback 1: generic live-wallpaper chooser (no preselection)
        val chooser = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(chooser, "LIVE_WALLPAPER_CHOOSER")) return

        // Fallback 2: open a wallpaper picker via the generic set-wallpaper chooser
        val anyWallpaper = Intent.createChooser(
            Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.set_wallpaper)
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (tryStart(anyWallpaper, "SET_WALLPAPER")) return

        Toast.makeText(
            this,
            "Couldn't open wallpaper picker. Open Settings → Wallpaper → Live wallpapers and pick LifeDots.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun prefersGenericLiveWallpaperChooser(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("honor") ||
            brand.contains("honor") ||
            manufacturer.contains("huawei") ||
            brand.contains("huawei")
    }

    private fun tryStart(intent: Intent, label: String): Boolean {
        val resolved = packageManager.resolveActivity(intent, 0)
        if (resolved == null) {
            Log.w("LifeDots", "$label: no activity resolves intent ${intent.action}")
            return false
        }
        Log.i("LifeDots", "$label: resolved to ${resolved.activityInfo.packageName}/${resolved.activityInfo.name}")
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("LifeDots", "$label: activity not found", e)
            false
        } catch (e: SecurityException) {
            Log.w("LifeDots", "$label: security exception", e)
            false
        } catch (e: Exception) {
            Log.w("LifeDots", "$label: failed", e)
            false
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}

@Composable
fun OnboardingScreen(
    onSetWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onAllowBackground: () -> Unit,
    onOpenSamsungNeverSleeping: () -> Unit,
    onInstallApk: (File) -> UpdateInstaller.LaunchResult,
    applyingWallpaper: Boolean = false,
    autoCheckOnLaunch: Boolean,
    triggerFromNotification: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var pendingInstallApk by remember { mutableStateOf<File?>(null) }
    var pendingInstallInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showSignatureHelp by remember { mutableStateOf(false) }

    fun handleInstall(apk: File) {
        val info = when (val state = updateState) {
            is UpdateUiState.Ready -> state.info
            is UpdateUiState.InstallPermissionRequired -> state.info
            else -> pendingInstallInfo
        }
        when (onInstallApk(apk)) {
            UpdateInstaller.LaunchResult.Started -> {
                pendingInstallApk = null
                pendingInstallInfo = null
            }
            UpdateInstaller.LaunchResult.NeedsUnknownSources -> {
                pendingInstallApk = apk
                pendingInstallInfo = info
                if (info != null) {
                    updateState = UpdateUiState.InstallPermissionRequired(info, apk)
                }
            }
            UpdateInstaller.LaunchResult.SignatureMismatch -> {
                pendingInstallApk = null
                pendingInstallInfo = null
                if (info != null) {
                    updateState = UpdateUiState.SignatureMismatch(info)
                }
            }
            UpdateInstaller.LaunchResult.Failed -> {
                pendingInstallApk = null
                pendingInstallInfo = null
                updateState = UpdateUiState.Error("Could not open Android package installer")
            }
        }
    }

    var batteryOptimized by remember { mutableStateOf(isBatteryOptimized(context)) }
    var notificationsAllowed by remember { mutableStateOf(hasPostNotificationsPermission(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsAllowed = hasPostNotificationsPermission(context)
        if (notificationsAllowed) KeepAliveService.start(context)
    }

    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, pendingInstallApk) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimized = isBatteryOptimized(context)
                notificationsAllowed = hasPostNotificationsPermission(context)
                val apk = pendingInstallApk
                if (apk != null && context.packageManager.canRequestPackageInstalls()) {
                    handleInstall(apk)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun checkForUpdate(silent: Boolean) {
        if (updateState is UpdateUiState.Checking || updateState is UpdateUiState.Downloading) return
        updateState = UpdateUiState.Checking
        scope.launch {
            val checker = UpdateChecker(context)
            updateState = when (val result = checker.check()) {
                is UpdateChecker.Result.UpdateAvailable -> {
                    UpdateNotifier.notify(context, result.info)
                    UpdateUiState.Available(result.info)
                }
                is UpdateChecker.Result.UpToDate ->
                    if (silent) UpdateUiState.Idle else UpdateUiState.UpToDate(result.version)
                is UpdateChecker.Result.NetworkError ->
                    if (silent) UpdateUiState.Idle else UpdateUiState.Error(result.message)
            }
        }
    }

    fun startDownload(info: UpdateInfo) {
        pendingInstallApk = null
        pendingInstallInfo = null
        updateState = UpdateUiState.Downloading(info, 0f)
        scope.launch {
            val installer = UpdateInstaller(context)
            val apk = installer.download(info) { downloaded, total ->
                val progress = if (total > 0) downloaded.toFloat() / total else 0f
                updateState = UpdateUiState.Downloading(info, progress)
            }
            if (apk != null) {
                pendingInstallApk = apk
                pendingInstallInfo = info
                updateState = UpdateUiState.Ready(info, apk)
            } else {
                updateState = UpdateUiState.Error("Download failed")
            }
        }
    }

    // Auto-check on launch (silent — only surfaces if there's actually an update),
    // and follow through immediately to download if the user opened the app from
    // an "update available" notification.
    LaunchedEffect(autoCheckOnLaunch, triggerFromNotification) {
        if (autoCheckOnLaunch) checkForUpdate(silent = true)
    }
    LaunchedEffect(updateState, triggerFromNotification) {
        if (triggerFromNotification) {
            (updateState as? UpdateUiState.Available)?.let { startDownload(it.info) }
        }
    }

    val preferences = remember { LifeDotsPreferences.getInstance(context) }
    val settings by preferences.settingsFlow.collectAsState()
    val feedback = rememberUxFeedback(settings.soundsEnabled, settings.vibrationsEnabled)

    val updateButtonText = when (val state = updateState) {
        is UpdateUiState.Checking -> "Checking"
        is UpdateUiState.Downloading -> "Downloading"
        is UpdateUiState.Available -> "Download v${state.info.versionName}"
        is UpdateUiState.Ready -> "Install Update"
        is UpdateUiState.InstallPermissionRequired -> "Allow Install"
        is UpdateUiState.SignatureMismatch -> "Update Help"
        is UpdateUiState.UpToDate -> "Up to Date"
        is UpdateUiState.Error -> "Try Again"
        else -> "Update"
    }

    fun handleUpdateButton() {
        feedback.click()
        when (val state = updateState) {
            is UpdateUiState.Available -> startDownload(state.info)
            is UpdateUiState.Ready -> handleInstall(state.apk)
            is UpdateUiState.InstallPermissionRequired -> handleInstall(state.apk)
            is UpdateUiState.SignatureMismatch -> { showSignatureHelp = true }
            else -> checkForUpdate(silent = false)
        }
    }

    fun handleKeepRunningButton() {
        feedback.click()
        when {
            !notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            batteryOptimized -> {
                KeepAliveService.start(context)
                onAllowBackground()
            }
            isSamsungLikeDevice() -> {
                KeepAliveService.start(context)
                onOpenSamsungNeverSleeping()
            }
            else -> {
                KeepAliveService.start(context)
                onAllowBackground()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF030302))) {
        Image(
            painter = painterResource(id = R.drawable.home_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.20f,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("O’lyapmiz", color = Color(0xFFC5A266), fontFamily = FontFamily.Serif,
                fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(calendarSelectionSummary(settings), color = Color(0xFFE3D8C5),
                textAlign = TextAlign.Center, fontSize = 15.sp)
            CalendarPreview(settings = settings, modifier = Modifier.fillMaxWidth())
            val gold = Color(0xFFC5A266)
            HomeActionButton(
                label = if (applyingWallpaper) "Applying wallpaper…" else "Set as Wallpaper",
                icon = HomeIcon.Picture,
                onClick = { feedback.click(); onSetWallpaper() },
                enabled = !applyingWallpaper,
                modifier = Modifier.fillMaxWidth(), gold = gold, large = true,
            )
            HomeActionButton(label = "Customize", icon = HomeIcon.Palette,
                onClick = { feedback.click(); onOpenSettings() },
                modifier = Modifier.fillMaxWidth(), gold = gold, large = true)
            HomeActionButton(label = updateButtonText, icon = HomeIcon.Download,
                onClick = { handleUpdateButton() }, modifier = Modifier.fillMaxWidth(),
                gold = gold, large = true,
                enabled = updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading)
            when (val state = updateState) {
                is UpdateUiState.Downloading -> Text("Downloaded ${(state.progress.coerceIn(0f, 1f) * 100).toInt()}%", color = gold)
                is UpdateUiState.Error -> Text(state.message, color = Color(0xFFFFBCAC), textAlign = TextAlign.Center)
                is UpdateUiState.InstallPermissionRequired -> Text("Allow updates from this app in Android settings, then return here.", color = gold)
                is UpdateUiState.SignatureMismatch -> Text("This release uses a different signing key. Your saved calendar is safe; open Update Help for next steps.", color = gold)
                else -> Unit
            }
            HomeActionButton(
                label = when {
                    !notificationsAllowed -> "Allow Notifications"
                    batteryOptimized -> "Allow Background Activity"
                    isSamsungLikeDevice() -> "Background Settings"
                    else -> "Keep Running"
                }, icon = HomeIcon.Settings,
                onClick = { handleKeepRunningButton() },
                modifier = Modifier.fillMaxWidth(), gold = gold, large = true,
            )
        }
    }
    if (showSignatureHelp) {
        AlertDialog(
            onDismissRequest = { showSignatureHelp = false },
            title = { Text("Update needs the original signing key") },
            text = { Text("Android cannot install this release over your current app because their signing keys differ. Keep this app and obtain an update signed with the same key. Uninstalling would erase your saved calendar, birthdays, goals and events, and the downloaded update. Your current settings have not been changed.") },
            confirmButton = { TextButton(onClick = { showSignatureHelp = false }) { Text("Keep my app") } },
        )
    }
}


private enum class HomeIcon {
    Settings,
    Picture,
    Palette,
    Download
}

@Composable
private fun PaintingHomeScrim(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Black.copy(alpha = 0.02f),
                    0.42f to Color.Transparent,
                    0.76f to Color.Black.copy(alpha = 0.04f),
                    1.00f to Color.Black.copy(alpha = 0.10f)
                ),
                startY = 0f,
                endY = size.height
            )
        )
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.10f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.12f)
                ),
                startX = 0f,
                endX = size.width
            )
        )
        drawRect(Color.Black.copy(alpha = 0.01f))
    }
}

@Composable
private fun TitleMark(
    modifier: Modifier = Modifier,
    gold: Color
) {
    Box(
        modifier = modifier.height(128.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = "O’lyapmiz",
            modifier = Modifier.fillMaxWidth(),
            color = gold,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Serif,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        GoldOrnament(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 59.dp)
                .size(width = 158.dp, height = 15.dp),
            gold = gold
        )
    }
}

@Composable
private fun GoldOrnament(
    modifier: Modifier = Modifier,
    gold: Color
) {
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val centerX = size.width / 2f
        val lineInset = size.width * 0.14f
        drawLine(
            color = gold.copy(alpha = 0.82f),
            start = Offset(0f, centerY),
            end = Offset(centerX - lineInset, centerY),
            strokeWidth = 1.3.dp.toPx()
        )
        drawLine(
            color = gold.copy(alpha = 0.82f),
            start = Offset(centerX + lineInset, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.3.dp.toPx()
        )
        val diamond = Path().apply {
            moveTo(centerX, centerY - size.height * 0.35f)
            lineTo(centerX + size.height * 0.32f, centerY)
            lineTo(centerX, centerY + size.height * 0.35f)
            lineTo(centerX - size.height * 0.32f, centerY)
            close()
        }
        drawPath(diamond, gold.copy(alpha = 0.84f), style = Stroke(width = 1.1.dp.toPx()))
        drawCircle(gold.copy(alpha = 0.84f), radius = 2.dp.toPx(), center = Offset(centerX - size.height * 0.44f, centerY))
        drawCircle(gold.copy(alpha = 0.84f), radius = 2.dp.toPx(), center = Offset(centerX + size.height * 0.44f, centerY))
        drawLine(
            color = gold.copy(alpha = 0.84f),
            start = Offset(centerX - size.height * 0.95f, centerY - size.height * 0.26f),
            end = Offset(centerX + size.height * 0.95f, centerY + size.height * 0.26f),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gold.copy(alpha = 0.84f),
            start = Offset(centerX - size.height * 0.95f, centerY + size.height * 0.26f),
            end = Offset(centerX + size.height * 0.95f, centerY - size.height * 0.26f),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    icon: HomeIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gold: Color,
    large: Boolean = false,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(Color(0xC20A0906))
            .border(BorderStroke(1.dp, gold.copy(alpha = 0.42f)), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(if (large) 34.dp else 24.dp)) {
                drawHomeIcon(
                    icon = icon,
                    color = gold.copy(alpha = if (enabled) 0.92f else 0.48f),
                    strokeWidth = if (large) 2.3.dp.toPx() else 1.8.dp.toPx()
                )
            }
            Spacer(modifier = Modifier.width(if (large) 16.dp else 5.dp))
            Text(
                text = label,
                color = gold.copy(alpha = if (enabled) 0.96f else 0.52f),
                fontFamily = FontFamily.Serif,
                fontSize = if (large) 20.sp else 10.5.sp,
                lineHeight = if (large) 24.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = if (large) 3 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeIcon(
    icon: HomeIcon,
    color: Color,
    strokeWidth: Float
) {
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    when (icon) {
        HomeIcon.Settings -> {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension * 0.34f
            val inner = size.minDimension * 0.18f
            for (i in 0 until 8) {
                val angle = i * PI.toFloat() / 4f
                val start = Offset(center.x + cos(angle) * outer * 0.78f, center.y + sin(angle) * outer * 0.78f)
                val end = Offset(center.x + cos(angle) * outer * 1.08f, center.y + sin(angle) * outer * 1.08f)
                drawLine(color, start, end, strokeWidth * 0.95f, StrokeCap.Round)
            }
            drawCircle(color, radius = outer * 0.82f, center = center, style = Stroke(width = strokeWidth * 1.2f))
            drawCircle(color, radius = inner, center = center, style = Stroke(width = strokeWidth * 1.1f))
        }
        HomeIcon.Picture -> {
            val rectTopLeft = Offset(size.width * 0.18f, size.height * 0.20f)
            val rectSize = Size(size.width * 0.64f, size.height * 0.62f)
            drawRoundRect(color, rectTopLeft, rectSize, CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(width = strokeWidth))
            val mountain = Path().apply {
                moveTo(size.width * 0.24f, size.height * 0.74f)
                lineTo(size.width * 0.42f, size.height * 0.54f)
                lineTo(size.width * 0.53f, size.height * 0.65f)
                lineTo(size.width * 0.63f, size.height * 0.50f)
                lineTo(size.width * 0.78f, size.height * 0.73f)
            }
            drawPath(mountain, color, style = stroke)
            drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(size.width * 0.34f, size.height * 0.35f))
        }
        HomeIcon.Palette -> {
            val outline = Path().apply {
                moveTo(size.width * 0.46f, size.height * 0.14f)
                cubicTo(size.width * 0.21f, size.height * 0.15f, size.width * 0.10f, size.height * 0.34f, size.width * 0.14f, size.height * 0.55f)
                cubicTo(size.width * 0.19f, size.height * 0.83f, size.width * 0.53f, size.height * 0.92f, size.width * 0.75f, size.height * 0.75f)
                cubicTo(size.width * 0.86f, size.height * 0.66f, size.width * 0.78f, size.height * 0.53f, size.width * 0.65f, size.height * 0.57f)
                cubicTo(size.width * 0.52f, size.height * 0.61f, size.width * 0.49f, size.height * 0.48f, size.width * 0.62f, size.height * 0.38f)
                cubicTo(size.width * 0.75f, size.height * 0.28f, size.width * 0.66f, size.height * 0.14f, size.width * 0.46f, size.height * 0.14f)
                close()
            }
            drawPath(outline, color, style = stroke)
            drawCircle(
                color = color,
                radius = size.minDimension * 0.12f,
                center = Offset(size.width * 0.62f, size.height * 0.66f),
                style = stroke
            )
            listOf(
                Offset(size.width * 0.34f, size.height * 0.37f),
                Offset(size.width * 0.48f, size.height * 0.29f),
                Offset(size.width * 0.27f, size.height * 0.55f)
            ).forEach {
                drawCircle(color, radius = 2.6.dp.toPx(), center = it)
            }
        }
        HomeIcon.Download -> {
            val cloud = Path().apply {
                moveTo(size.width * 0.21f, size.height * 0.60f)
                cubicTo(size.width * 0.14f, size.height * 0.55f, size.width * 0.17f, size.height * 0.42f, size.width * 0.30f, size.height * 0.42f)
                cubicTo(size.width * 0.33f, size.height * 0.26f, size.width * 0.56f, size.height * 0.23f, size.width * 0.64f, size.height * 0.39f)
                cubicTo(size.width * 0.78f, size.height * 0.38f, size.width * 0.86f, size.height * 0.50f, size.width * 0.80f, size.height * 0.61f)
            }
            drawPath(cloud, color, style = stroke)
            drawLine(color, Offset(size.width * 0.50f, size.height * 0.44f), Offset(size.width * 0.50f, size.height * 0.78f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.38f, size.height * 0.66f), Offset(size.width * 0.50f, size.height * 0.79f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.62f, size.height * 0.66f), Offset(size.width * 0.50f, size.height * 0.79f), strokeWidth, StrokeCap.Round)
        }
    }
}

private fun isBatteryOptimized(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun hasPostNotificationsPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val version: String) : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUiState
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateUiState
    data class InstallPermissionRequired(val info: UpdateInfo, val apk: File) : UpdateUiState
    data class SignatureMismatch(val info: UpdateInfo) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

private fun isSamsungLikeDevice(): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = Build.BRAND.lowercase()
    return manufacturer.contains("samsung") || brand.contains("samsung")
}
