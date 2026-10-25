package com.example.lifedots

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.lifedots.ui.components.BirthdayDraft
import com.example.lifedots.ui.components.CalendarPreview
import com.example.lifedots.ui.components.calendarSelectionSummary
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.core.view.WindowCompat
import com.example.lifedots.ui.components.IntervalSlider
import com.example.lifedots.ui.components.rememberUxFeedback
import com.example.lifedots.ui.theme.BrandColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifedots.preferences.AnimationType
import com.example.lifedots.preferences.DotShape
import com.example.lifedots.preferences.DotStyle
import com.example.lifedots.preferences.FluidStyle
import com.example.lifedots.preferences.GlassStyle
import com.example.lifedots.preferences.Event
import com.example.lifedots.preferences.Goal
import com.example.lifedots.preferences.GoalPosition
import com.example.lifedots.ui.components.EventEditorDialog
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.TextAlignment
import com.example.lifedots.preferences.ThemeOption
import com.example.lifedots.preferences.TopViewMode
import com.example.lifedots.preferences.UmrVisualMode
import com.example.lifedots.preferences.TreeStyle
import com.example.lifedots.preferences.ViewMode
import com.example.lifedots.preferences.WallpaperApplyMode
import com.example.lifedots.preferences.WallpaperSettings
import com.example.lifedots.ui.components.DateNumberInputs
import com.example.lifedots.ui.components.GoalEditorDialog
import com.example.lifedots.ui.components.WhoTab
import com.example.lifedots.ui.components.WhoTabs
import com.example.lifedots.ui.theme.LifeDotsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class SettingsActivity : ComponentActivity() {

    private lateinit var preferences: LifeDotsPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferences = LifeDotsPreferences.getInstance(this)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false   // light icons on black
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.parseColor("#0A0A0A")

        setContent {
            LifeDotsTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BrandColors.InkBlack,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                ) { innerPadding ->
                    SettingsScreen(
                        preferences = preferences,
                        modifier = Modifier.padding(innerPadding),
                        snackbarHostState = snackbarHostState,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    preferences: LifeDotsPreferences,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
) {
    val settings by preferences.settingsFlow.collectAsState()
    val scope = rememberCoroutineScope()

    var showGoalEditor by rememberSaveable { mutableStateOf(false) }
    var editingGoalId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEventEditor by rememberSaveable { mutableStateOf(false) }
    var editingEventId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingGoal = settings.goalSettings.goals.firstOrNull { it.id == editingGoalId }
    val editingEvent = settings.eventSettings.events.firstOrNull { it.id == editingEventId }

    ModernSettingsContent(
        settings = settings,
        preferences = preferences,
        snackbarHostState = snackbarHostState,
        scope = scope,
        onAddGoal = { editingGoalId = null; showGoalEditor = true },
        onEditGoal = { goal -> editingGoalId = goal.id; showGoalEditor = true },
        onAddEvent = { editingEventId = null; showEventEditor = true },
        onEditEvent = { event -> editingEventId = event.id; showEventEditor = true },
        modifier = modifier,
    )

    if (showEventEditor) {
        EventEditorDialog(
            event = editingEvent,
            onSave = { event ->
                if (editingEvent != null) {
                    preferences.updateEvent(event)
                } else {
                    preferences.addEvent(event)
                }
            },
            onDelete = editingEvent?.let { { preferences.deleteEvent(it.id) } },
            onDismiss = {
                showEventEditor = false
                editingEventId = null
            },
        )
    }

    if (showGoalEditor) {
        GoalEditorDialog(
            goal = editingGoal,
            onSave = { goal ->
                if (editingGoal != null) {
                    preferences.updateGoal(goal)
                } else {
                    preferences.addGoal(goal)
                }
            },
            onDelete = editingGoal?.let { { preferences.deleteGoal(it.id) } },
            onDismiss = {
                showGoalEditor = false
                editingGoalId = null
            }
        )
    }

}

private val ModernGold = Color(0xFFFFC62E)
private val ModernGoldMuted = Color(0xFFC7A35F)
private val ModernInk = Color(0xFF050505)
private val ModernPanel = Color(0xFF141414)
private val ModernPanelSoft = Color(0xFF1B1A16)
private val ModernStroke = Color(0xFF2A251B)
private val ModernTextMuted = Color(0xFFAAA49A)

private enum class SettingIcon {
    Sound,
    Phone,
    Sun,
    Text,
    Horizontal,
    Vertical,
    Scale,
    Target,
    Calendar,
    List,
    Grid,
}

@Composable
internal fun ModernSettingsContent(
    settings: WallpaperSettings,
    preferences: LifeDotsPreferences,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onAddGoal: () -> Unit,
    onEditGoal: (Goal) -> Unit,
    onAddEvent: () -> Unit,
    onEditEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = rememberUxFeedback(settings.soundsEnabled, settings.vibrationsEnabled)
    var showLifeDataEditor by rememberSaveable { mutableStateOf(false) }
    var editingWho by rememberSaveable { mutableStateOf(WhoTab.ME) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black, Color(0xFF050403), Color.Black)
                )
            ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 10.dp,
            top = 14.dp,
            end = 10.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ModernModeToggle(
                selected = settings.topViewMode,
                onSelected = { preferences.setTopViewMode(it) },
            )
        }

        item {
            Text(calendarSelectionSummary(settings), color = ModernTextMuted, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            CalendarPreview(settings = settings, modifier = Modifier.fillMaxWidth())
        }

        item {
            if (settings.applyMode == WallpaperApplyMode.CALENDAR_LOCK_BLACK_HOME) {
                Text("Lock-screen image refreshes daily; automatic switching requires live wallpaper.",
                    color = ModernTextMuted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
            } else ModernAutoSwitchCard(
                settings = settings,
                onEnabledChange = { wantOn ->
                    if (wantOn && settings.umrSettings.birthdayEpochMs == 0L) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Set your birthday first",
                                actionLabel = "OK",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    } else {
                        feedback.confirm()
                        preferences.setAutoSwitchEnabled(wantOn)
                    }
                },
                onIntervalChange = { ms -> preferences.setAutoSwitchIntervalMs(ms) },
            )
        }

        item {
            ModernSectionTitle("WALLPAPER APPLY")
            ModernPanelCard {
                val isLockOnly = settings.applyMode == WallpaperApplyMode.CALENDAR_LOCK_BLACK_HOME
                ModernSettingRow(
                    icon = SettingIcon.Phone,
                    title = "Lock screen only",
                    subtitle = if (isLockOnly)
                        "Calendar on lock, black home. Tap Set as Wallpaper to apply."
                    else
                        "Use the live wallpaper on both screens",
                    trailing = {
                        ModernSwitch(
                            checked = isLockOnly,
                                label = "Lock screen only",
                            onCheckedChange = { wantLockOnly ->
                                feedback.confirm()
                                preferences.setApplyMode(
                                    if (wantLockOnly) WallpaperApplyMode.CALENDAR_LOCK_BLACK_HOME
                                    else WallpaperApplyMode.BOTH_LIVE
                                )
                            },
                        )
                    },
                )
            }
        }

        if (settings.topViewMode == TopViewMode.UMR) {
            item {
                LifeDataCard(
                    meMs = settings.umrSettings.birthdayEpochMs,
                    dadMs = settings.umrSettings.dadBirthdayEpochMs,
                    momMs = settings.umrSettings.momBirthdayEpochMs,
                    onClick = {
                        editingWho = WhoTab.ME
                        showLifeDataEditor = true
                    },
                )
            }
            item {
                VisualizationSection(
                    current = settings.umrSettings.visualMode,
                    onChange = { preferences.setUmrVisualMode(it) },
                )
            }
        }

        if (settings.topViewMode == TopViewMode.YIL) {
            item {
                ModernSectionTitle("THEME")
                ModernPanelCard(contentPadding = 10.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ModernThemeOption(
                            label = "Light",
                            selected = settings.theme == ThemeOption.LIGHT,
                            background = Color(0xFFF8EFD7),
                            dotColors = listOf(Color(0xFF69645D), Color(0xFF858078)),
                            onClick = { preferences.setTheme(ThemeOption.LIGHT) },
                            modifier = Modifier.weight(1f),
                        )
                        ModernThemeOption(
                            label = "Dark",
                            selected = settings.theme == ThemeOption.DARK,
                            background = Color(0xFF25231F),
                            dotColors = listOf(Color(0xFFB6B0A6), Color(0xFFE7E0D2)),
                            onClick = { preferences.setTheme(ThemeOption.DARK) },
                            modifier = Modifier.weight(1f),
                        )
                        ModernThemeOption(
                            label = "AMOLED",
                            selected = settings.theme == ThemeOption.AMOLED,
                            background = Color.Black,
                            dotColors = listOf(Color(0xFFE5E0D4), ModernGold),
                            onClick = { preferences.setTheme(ThemeOption.AMOLED) },
                            modifier = Modifier.weight(1f),
                        )
                        ModernThemeOption(
                            label = "Custom",
                            selected = settings.theme == ThemeOption.CUSTOM,
                            background = Color(0xFF252015),
                            dotColors = listOf(Color(0xFFD9C88B), ModernGold),
                            onClick = { preferences.setTheme(ThemeOption.CUSTOM) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            // Transparency is per-mode. Yil and Umr each save their own
            // sliders so changing one doesn't disturb the other.
            val isUmr = settings.topViewMode == TopViewMode.UMR
            val filledValue = if (isUmr) settings.umrSettings.livedAlpha else settings.filledDotAlpha
            val emptyValue  = if (isUmr) settings.umrSettings.emptyAlpha  else settings.emptyDotAlpha
            ModernSectionTitle("TRANSPARENCY")
            ModernPanelCard {
                ModernPercentSlider(
                    title = "Filled Dots",
                    subtitle = "Opacity of past days",
                    value = filledValue,
                    valueText = "${(filledValue * 100).roundToInt()}%",
                    onValueChange = {
                        if (isUmr) preferences.setUmrLivedAlpha(it)
                        else preferences.setFilledDotAlpha(it)
                    },
                )
                ModernDivider()
                ModernPercentSlider(
                    title = "Empty Dots",
                    subtitle = "Opacity of future days",
                    value = emptyValue,
                    valueText = "${(emptyValue * 100).roundToInt()}%",
                    onValueChange = {
                        if (isUmr) preferences.setUmrEmptyAlpha(it)
                        else preferences.setEmptyDotAlpha(it)
                    },
                )
            }
        }

        if (settings.topViewMode == TopViewMode.YIL) {
            item {
                ModernPanelCard {
                    ModernSettingRow(
                        icon = SettingIcon.Sun,
                        title = "Highlight Today",
                        subtitle = "Show a distinct marker for today's dot",
                        trailing = {
                            ModernSwitch(
                                checked = settings.highlightToday,
                                label = "Highlight today",
                                onCheckedChange = { preferences.setHighlightToday(it) },
                            )
                        },
                    )
                }
            }
        }

        if (settings.topViewMode == TopViewMode.YIL) {
            item {
                ModernSectionTitle("VIEW MODE")
                ModernPanelCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ViewMode.CALENDAR to "Calendar", ViewMode.MONTHLY to "Monthly grid",
                            ViewMode.CONTINUOUS to "Continuous grid").forEach { (mode, label) ->
                            ModernSelectPill(icon = SettingIcon.Grid, label = label,
                                selected = settings.viewModeSettings.mode == mode,
                                onClick = { preferences.setViewMode(mode) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    ModernSettingRow(title = "Month labels", subtitle = "Show each month's name",
                        trailing = { ModernSwitch(checked = settings.viewModeSettings.showMonthLabels,
                            onCheckedChange = { preferences.setShowMonthLabels(it) }, label = "Month labels") })
                    ModernSettingRow(title = "Monday first", subtitle = "Start each week on Monday",
                        trailing = { ModernSwitch(checked = settings.calendarViewSettings.mondayFirst,
                            onCheckedChange = { preferences.setCalendarMondayFirst(it) }, label = "Monday first") })
                    ModernDivider(top = 16.dp, bottom = 14.dp)

                    Text(
                        text = "Calendar Columns",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ModernColumnOption(
                            label = "2 × 6",
                            columns = 2,
                            selected = settings.calendarViewSettings.columnsPerRow == 2,
                            onClick = { preferences.setCalendarColumns(2) },
                            modifier = Modifier.weight(1f),
                        )
                        ModernColumnOption(
                            label = "3 × 4",
                            columns = 3,
                            selected = settings.calendarViewSettings.columnsPerRow == 3,
                            onClick = { preferences.setCalendarColumns(3) },
                            modifier = Modifier.weight(1f),
                        )
                        ModernColumnOption(
                            label = "4 × 3", columns = 4,
                            selected = settings.calendarViewSettings.columnsPerRow == 4,
                            onClick = { preferences.setCalendarColumns(4) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                }
            }
        }

        item {
            // Position is per-mode (Yil 0%/0%/100% default, Umr 0%/7%/100% default).
            val isUmr = settings.topViewMode == TopViewMode.UMR
            val pos = if (isUmr) settings.umrSettings.position else settings.positionSettings
            ModernSectionTitle("POSITION & SCALE")
            ModernPanelCard {
                ModernOffsetRow(
                    icon = SettingIcon.Horizontal,
                    title = "Horizontal Offset",
                    subtitle = "Move left or right",
                    value = pos.horizontalOffset,
                    valueRange = -50f..50f,
                    valueText = "${pos.horizontalOffset.roundToInt()}%",
                    onValueChange = {
                        if (isUmr) preferences.setUmrHorizontalOffset(it)
                        else preferences.setHorizontalOffset(it)
                    },
                )
                ModernOffsetRow(
                    icon = SettingIcon.Vertical,
                    title = "Vertical Offset",
                    subtitle = "Move up or down",
                    value = pos.verticalOffset,
                    valueRange = -50f..50f,
                    valueText = "${pos.verticalOffset.roundToInt()}%",
                    onValueChange = {
                        if (isUmr) preferences.setUmrVerticalOffset(it)
                        else preferences.setVerticalOffset(it)
                    },
                )
                ModernOffsetRow(
                    icon = SettingIcon.Scale,
                    title = "Scale",
                    subtitle = "Resize the entire grid",
                    value = pos.scale,
                    valueRange = 0.5f..1.5f,
                    valueText = "${(pos.scale * 100).roundToInt()}%",
                    onValueChange = {
                        if (isUmr) preferences.setUmrScale(it)
                        else preferences.setScale(it)
                    },
                )
                val statsOffset = if (isUmr) settings.umrSettings.statsBandOffset
                                  else settings.yilStatsBandOffset
                ModernOffsetRow(
                    icon = SettingIcon.Vertical,
                    title = "Stats Bar Y",
                    subtitle = if (isUmr) "Move just the Me / Mom / Dad row"
                               else "Move the bottom day-of-year stats line",
                    value = statsOffset,
                    valueRange = -25f..40f,
                    valueText = "${statsOffset.roundToInt()}%",
                    onValueChange = {
                        if (isUmr) preferences.setUmrStatsBandOffset(it)
                        else preferences.setYilStatsBandOffset(it)
                    },
                )
            }
        }

        if (settings.topViewMode == TopViewMode.YIL) {
            item {
                ModernSectionTitle("GOAL COUNTDOWN")
                ModernPanelCard {
                    ModernSettingRow(
                        icon = SettingIcon.Target,
                        title = "Enable Goals",
                        subtitle = "Countdown to important future dates",
                        trailing = {
                            ModernSwitch(
                                checked = settings.goalSettings.enabled,
                                label = "Goals",
                                onCheckedChange = { preferences.setGoalsEnabled(it) },
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    ModernPrimaryButton(text = "+  Add Goal", onClick = onAddGoal)
                }
            }

            if (settings.goalSettings.goals.isNotEmpty()) {
                items(settings.goalSettings.goals, key = { it.id }) { goal ->
                    ModernGoalItem(goal = goal, onClick = { onEditGoal(goal) })
                }
            }
        }

        if (settings.topViewMode == TopViewMode.UMR) {
            item {
                ModernSectionTitle("EVENTS")
                ModernPanelCard {
                    ModernSettingRow(
                        icon = SettingIcon.Target,
                        title = "Enable Events",
                        subtitle = "Mark important dates (past or future) on your life calendar",
                        trailing = {
                            ModernSwitch(
                                checked = settings.eventSettings.enabled,
                                label = "Events",
                                onCheckedChange = { preferences.setEventsEnabled(it) },
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    ModernPrimaryButton(text = "+  Add Event", onClick = onAddEvent)
                }
            }

            if (settings.eventSettings.events.isNotEmpty()) {
                items(settings.eventSettings.events, key = { it.id }) { event ->
                    ModernEventItem(event = event, onClick = { onEditEvent(event) })
                }
            }
        }
    }

    if (showLifeDataEditor) {
        LifeDataEditorSheet(
            settings = settings,
            preferences = preferences,
            initialWho = editingWho,
            onDismiss = { showLifeDataEditor = false },
        )
    }
}

@Composable
private fun ModernModeToggle(
    selected: TopViewMode,
    onSelected: (TopViewMode) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(42.dp),
        color = ModernPanel,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .selectableGroup()
                .padding(2.dp),
        ) {
            ModernModeHalf(
                text = "Yil",
                selected = selected == TopViewMode.YIL,
                onClick = { onSelected(TopViewMode.YIL) },
                modifier = Modifier.weight(1f),
            )
            ModernModeHalf(
                text = "Umr",
                selected = selected == TopViewMode.UMR,
                onClick = { onSelected(TopViewMode.UMR) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModernModeHalf(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) ModernGold else Color.Transparent,
        label = "modeColor",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.Black else ModernGoldMuted,
        label = "modeText",
    )

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(38.dp))
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ModernAutoSwitchCard(
    settings: WallpaperSettings,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (Long) -> Unit,
) {
    val feedback = rememberUxFeedback(settings.soundsEnabled, settings.vibrationsEnabled)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-switch",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Automatically rotate between Yil and Umr.",
                    color = ModernTextMuted,
                    fontSize = 13.sp,
                )
            }
            ModernSwitch(
                checked = settings.autoSwitchSettings.enabled,
                                label = "Auto-switch",
                onCheckedChange = onEnabledChange,
            )
        }

        IntervalSlider(currentMs = settings.autoSwitchSettings.intervalMs,
            onIntervalChange = onIntervalChange, feedback = feedback)

        Text(
            text = if (settings.autoSwitchSettings.enabled) {
                "Auto-switch is on — wallpaper rotates automatically."
            } else {
                "Auto-switch is off — selected mode stays active."
            },
            color = ModernTextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ModernSectionTitle(text: String) {
    Text(
        text = text,
        color = ModernGold,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
    )
}

@Composable
private fun ModernPanelCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ModernPanel,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.035f),
                            Color.Transparent,
                            ModernGold.copy(alpha = 0.025f),
                        )
                    )
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
private fun ModernSettingRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: SettingIcon? = null,
    compact: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 4.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconDisk(icon = icon)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = ModernTextMuted,
                fontSize = 12.sp,
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun ModernActionRow(
    icon: SettingIcon,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDisk(icon = icon)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = ModernTextMuted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "›",
            color = ModernGold,
            fontSize = 42.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun ModernDivider(
    top: Dp = 10.dp,
    bottom: Dp = 10.dp,
) {
    Spacer(modifier = Modifier.height(top))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
    Spacer(modifier = Modifier.height(bottom))
}

@Composable
private fun ModernSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Setting",
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange,
        modifier = modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(
            checkedTrackColor = ModernGold, checkedThumbColor = Color.Black,
            uncheckedTrackColor = ModernPanel, uncheckedThumbColor = ModernTextMuted,
        ))
}

@Composable
private fun IconDisk(
    icon: SettingIcon,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ModernGold.copy(alpha = 0.11f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon == SettingIcon.Text) {
            Text(
                text = "Tt",
                color = ModernGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Canvas(modifier = Modifier.size(size * 0.58f)) {
                drawSettingIcon(icon, ModernGold)
            }
        }
    }
}

private fun DrawScope.drawSettingIcon(icon: SettingIcon, color: Color) {
    val stroke = size.minDimension * 0.09f
    val center = Offset(size.width / 2f, size.height / 2f)
    when (icon) {
        SettingIcon.Sound -> {
            val body = Path().apply {
                moveTo(size.width * 0.10f, size.height * 0.40f)
                lineTo(size.width * 0.30f, size.height * 0.40f)
                lineTo(size.width * 0.50f, size.height * 0.22f)
                lineTo(size.width * 0.50f, size.height * 0.78f)
                lineTo(size.width * 0.30f, size.height * 0.60f)
                lineTo(size.width * 0.10f, size.height * 0.60f)
                close()
            }
            drawPath(body, color, style = Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawArc(
                color = color,
                startAngle = -38f,
                sweepAngle = 76f,
                useCenter = false,
                topLeft = Offset(size.width * 0.48f, size.height * 0.25f),
                size = Size(size.width * 0.38f, size.height * 0.50f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -38f,
                sweepAngle = 76f,
                useCenter = false,
                topLeft = Offset(size.width * 0.58f, size.height * 0.13f),
                size = Size(size.width * 0.48f, size.height * 0.74f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        SettingIcon.Phone -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.26f, size.height * 0.08f),
                size = Size(size.width * 0.48f, size.height * 0.84f),
                cornerRadius = CornerRadius(size.width * 0.10f, size.width * 0.10f),
                style = Stroke(width = stroke),
            )
            drawCircle(color, radius = stroke * 0.6f, center = Offset(center.x, size.height * 0.80f))
            drawLine(
                color = color,
                start = Offset(size.width * 0.12f, size.height * 0.25f),
                end = Offset(size.width * 0.12f, size.height * 0.75f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.88f, size.height * 0.25f),
                end = Offset(size.width * 0.88f, size.height * 0.75f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        SettingIcon.Sun -> {
            drawCircle(color, radius = size.minDimension * 0.18f, center = center, style = Stroke(width = stroke))
            repeat(8) { i ->
                val angle = i * (Math.PI.toFloat() / 4f)
                val start = Offset(center.x + cos(angle) * size.width * 0.30f, center.y + sin(angle) * size.height * 0.30f)
                val end = Offset(center.x + cos(angle) * size.width * 0.43f, center.y + sin(angle) * size.height * 0.43f)
                drawLine(color, start, end, strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
        SettingIcon.Horizontal -> {
            drawLine(color, Offset(size.width * 0.14f, center.y), Offset(size.width * 0.86f, center.y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.14f, center.y), Offset(size.width * 0.28f, size.height * 0.35f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.14f, center.y), Offset(size.width * 0.28f, size.height * 0.65f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.86f, center.y), Offset(size.width * 0.72f, size.height * 0.35f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.86f, center.y), Offset(size.width * 0.72f, size.height * 0.65f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        SettingIcon.Vertical -> {
            drawLine(color, Offset(center.x, size.height * 0.14f), Offset(center.x, size.height * 0.86f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(center.x, size.height * 0.14f), Offset(size.width * 0.35f, size.height * 0.28f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(center.x, size.height * 0.14f), Offset(size.width * 0.65f, size.height * 0.28f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(center.x, size.height * 0.86f), Offset(size.width * 0.35f, size.height * 0.72f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(center.x, size.height * 0.86f), Offset(size.width * 0.65f, size.height * 0.72f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        SettingIcon.Scale -> {
            drawLine(color, Offset(size.width * 0.16f, size.height * 0.16f), Offset(size.width * 0.38f, size.height * 0.16f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.16f, size.height * 0.16f), Offset(size.width * 0.16f, size.height * 0.38f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.84f, size.height * 0.16f), Offset(size.width * 0.62f, size.height * 0.16f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.84f, size.height * 0.16f), Offset(size.width * 0.84f, size.height * 0.38f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.16f, size.height * 0.84f), Offset(size.width * 0.38f, size.height * 0.84f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.16f, size.height * 0.84f), Offset(size.width * 0.16f, size.height * 0.62f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.84f, size.height * 0.84f), Offset(size.width * 0.62f, size.height * 0.84f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.84f, size.height * 0.84f), Offset(size.width * 0.84f, size.height * 0.62f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        SettingIcon.Target -> {
            drawCircle(color, radius = size.minDimension * 0.36f, center = center, style = Stroke(width = stroke))
            drawCircle(color, radius = size.minDimension * 0.20f, center = center, style = Stroke(width = stroke))
            drawCircle(color, radius = stroke * 0.8f, center = center)
            drawLine(color, Offset(size.width * 0.60f, size.height * 0.40f), Offset(size.width * 0.88f, size.height * 0.12f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        SettingIcon.Calendar -> {
            drawRoundRect(color, Offset(size.width * 0.12f, size.height * 0.18f), Size(size.width * 0.76f, size.height * 0.70f), CornerRadius(6f, 6f), style = Stroke(width = stroke))
            drawLine(color, Offset(size.width * 0.12f, size.height * 0.36f), Offset(size.width * 0.88f, size.height * 0.36f), strokeWidth = stroke, cap = StrokeCap.Round)
            repeat(2) { row ->
                repeat(3) { col ->
                    drawCircle(
                        color = color,
                        radius = stroke * 0.5f,
                        center = Offset(size.width * (0.32f + col * 0.18f), size.height * (0.52f + row * 0.18f)),
                    )
                }
            }
        }
        SettingIcon.List -> {
            repeat(3) { row ->
                val y = size.height * (0.26f + row * 0.24f)
                drawLine(color, Offset(size.width * 0.16f, y), Offset(size.width * 0.28f, y), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.42f, y), Offset(size.width * 0.84f, y), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
        SettingIcon.Grid -> {
            repeat(3) { row ->
                repeat(3) { col ->
                    drawCircle(
                        color = color,
                        radius = stroke * 0.75f,
                        center = Offset(size.width * (0.28f + col * 0.22f), size.height * (0.28f + row * 0.22f)),
                    )
                }
            }
        }
        SettingIcon.Text -> Unit
    }
}

@Composable
private fun ModernThemeOption(
    label: String,
    selected: Boolean,
    background: Color,
    dotColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) ModernGold else Color.Transparent
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DotPreview(background = background, dotColors = dotColors)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                color = if (selected) ModernGold else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        if (selected) {
            CheckBadge(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun DotPreview(
    background: Color,
    dotColors: List<Color>,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(4) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { col ->
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(dotColors[(row + col) % dotColors.size])
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernPercentSlider(
    title: String,
    subtitle: String,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(text = subtitle, color = ModernTextMuted, fontSize = 11.sp, maxLines = 1)
            }
            Text(
                text = valueText,
                color = ModernGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        ModernValueSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            label = title,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ModernOffsetRow(
    icon: SettingIcon,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconDisk(icon = icon, size = 34.dp)
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                color = ModernGold,
                fontSize = 14.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.width(48.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        ModernValueSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            label = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 43.dp, end = 4.dp),
        )
    }
}

@Composable
private fun ModernSelectPill(
    icon: SettingIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) ModernGold else Color.White.copy(alpha = 0.72f)
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.25f),
        border = BorderStroke(1.3.dp, if (selected) ModernGold else Color.White.copy(alpha = 0.10f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                drawSettingIcon(icon, color)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = color,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ModernColumnOption(
    label: String,
    columns: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(14.dp))
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.22f),
            border = BorderStroke(1.3.dp, if (selected) ModernGold else Color.White.copy(alpha = 0.10f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CalendarMiniGrid(columns = columns, selected = selected)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    color = if (selected) ModernGold else ModernTextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (selected) {
            CheckBadge(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun CalendarMiniGrid(
    columns: Int,
    selected: Boolean,
) {
    val color = if (selected) ModernGold else Color.White.copy(alpha = 0.52f)
    val rows = 12 / columns
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(columns) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .border(1.5.dp, color, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernGoalPositionButton(
    label: String,
    selected: Boolean,
    position: GoalPosition,
    onClick: () -> Unit,
) {
    Box {
        Surface(
            modifier = Modifier
                .width(90.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.22f),
            border = BorderStroke(1.3.dp, if (selected) ModernGold else Color.White.copy(alpha = 0.10f)),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(16.dp),
                    contentAlignment = if (position == GoalPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(if (selected) ModernGold else Color.White.copy(alpha = 0.58f), RoundedCornerShape(2.dp))
                    )
                }
                Text(
                    text = label,
                    color = if (selected) ModernGold else ModernTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (selected) {
            CheckBadge(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun ModernPrimaryButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ModernGold)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ModernGoalItem(
    goal: Goal,
    onClick: () -> Unit,
) {
    ModernDatedRow(
        color = goal.color,
        title = goal.title,
        epochMs = goal.targetDate,
        onClick = onClick,
    )
}

@Composable
private fun ModernEventItem(
    event: Event,
    onClick: () -> Unit,
) {
    ModernDatedRow(
        color = event.color,
        title = event.title,
        epochMs = event.targetDate,
        onClick = onClick,
    )
}

@Composable
private fun ModernDatedRow(
    color: Int,
    title: String,
    epochMs: Long,
    onClick: () -> Unit,
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    ModernPanelCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = dateFormatter.format(Date(epochMs)),
                    color = ModernTextMuted,
                    fontSize = 14.sp,
                )
            }
            Text(text = "›", color = ModernGold, fontSize = 34.sp)
        }
    }
}

@Composable
private fun CheckBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(ModernGold),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "✓",
            color = Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun ModernValueSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Value",
) {
    Slider(value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        valueRange = valueRange, onValueChange = onValueChange, colors = modernSliderColors(),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label })
}

@Composable
private fun modernSliderColors() = SliderDefaults.colors(
    thumbColor = ModernGold,
    activeTrackColor = ModernGold,
    inactiveTrackColor = Color.White.copy(alpha = 0.20f),
    activeTickColor = ModernGold,
    inactiveTickColor = ModernGold.copy(alpha = 0.75f),
)


@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                color = BrandColors.AmberGold,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = BrandColors.InkBlackElevated,
            border = BorderStroke(1.dp, BrandColors.HairlineGold),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun BrandSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = BrandColors.InkBlack,
            checkedTrackColor = BrandColors.AmberGold,
            checkedBorderColor = BrandColors.AmberGold,
            uncheckedThumbColor = BrandColors.GoldenMuted,
            uncheckedTrackColor = BrandColors.InkBlackElevated,
            uncheckedBorderColor = BrandColors.HairlineGold,
        ),
    )
}

@Composable
fun ThemeOptionButton(
    label: String,
    backgroundColor: Color,
    dotColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DotShapeOption(
    shape: DotShape,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val dotColor = MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    when (shape) {
                        DotShape.CIRCLE -> {
                            drawCircle(color = dotColor)
                        }
                        DotShape.SQUARE -> {
                            drawRect(color = dotColor)
                        }
                        DotShape.ROUNDED_SQUARE -> {
                            drawRoundRect(
                                color = dotColor,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                            )
                        }
                        DotShape.DIAMOND -> {
                            val path = Path().apply {
                                moveTo(size.width / 2, 0f)
                                lineTo(size.width, size.height / 2)
                                lineTo(size.width / 2, size.height)
                                lineTo(0f, size.height / 2)
                                close()
                            }
                            drawPath(path, dotColor)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DotSizeOption(
    label: String,
    dotSize: Dp,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.height(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun GridDensityOption(
    label: String,
    columns: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(columns) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DotStyleOption(
    label: String,
    style: DotStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val dotColor = MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    when (style) {
                        DotStyle.FLAT -> {
                            drawCircle(color = dotColor)
                        }
                        DotStyle.GRADIENT -> {
                            drawCircle(
                                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                    colors = listOf(dotColor.copy(alpha = 0.5f), dotColor),
                                    center = androidx.compose.ui.geometry.Offset(
                                        size.width * 0.3f,
                                        size.height * 0.3f
                                    )
                                )
                            )
                        }
                        DotStyle.OUTLINED -> {
                            drawCircle(
                                color = dotColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                        }
                        DotStyle.SOFT_GLOW -> {
                            drawCircle(color = dotColor.copy(alpha = 0.3f), radius = size.minDimension / 2 * 1.3f)
                            drawCircle(color = dotColor)
                        }
                        DotStyle.NEON -> {
                            drawCircle(color = dotColor.copy(alpha = 0.2f), radius = size.minDimension / 2 * 1.5f)
                            drawCircle(color = dotColor.copy(alpha = 0.4f), radius = size.minDimension / 2 * 1.2f)
                            drawCircle(color = dotColor)
                        }
                        DotStyle.EMBOSSED -> {
                            drawCircle(color = dotColor.copy(alpha = 0.5f), center = androidx.compose.ui.geometry.Offset(size.width / 2 + 2, size.height / 2 + 2))
                            drawCircle(color = dotColor)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ViewModeOption(
    label: String,
    mode: ViewMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                when (mode) {
                    ViewMode.CONTINUOUS -> {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(4) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    repeat(5) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                    ViewMode.MONTHLY -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(2) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(2.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        repeat(4) {
                                            Box(
                                                modifier = Modifier
                                                    .size(3.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ViewMode.CALENDAR -> {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    repeat(3) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CalendarColumnsOption(
    label: String,
    columns: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val rows = 12 / columns
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(columns) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun TextAlignmentOption(
    label: String,
    alignment: TextAlignment,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(32.dp, 20.dp),
                contentAlignment = when (alignment) {
                    TextAlignment.LEFT -> Alignment.CenterStart
                    TextAlignment.CENTER -> Alignment.Center
                    TextAlignment.RIGHT -> Alignment.CenterEnd
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    )
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun GoalPositionOption(
    label: String,
    position: GoalPosition,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp, 40.dp)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                contentAlignment = if (position == GoalPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .width(20.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun GoalItem(
    goal: Goal,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val daysRemaining = remember(goal.targetDate) {
        val now = System.currentTimeMillis()
        ((goal.targetDate - now) / (1000 * 60 * 60 * 24)).toInt()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(goal.color))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = goal.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        daysRemaining > 0 -> "$daysRemaining days left"
                        daysRemaining == 0 -> "Today!"
                        else -> "${-daysRemaining} days ago"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Text(
                text = dateFormatter.format(Date(goal.targetDate)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun AnimationTypeOption(
    label: String,
    type: AnimationType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Animation preview icons
                when (type) {
                    AnimationType.FADE_IN -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)))
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))
                        }
                    }
                    AnimationType.PULSE -> {
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                    }
                    AnimationType.WAVE -> {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)))
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)))
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)))
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                            }
                        }
                    }
                    AnimationType.BREATHE -> {
                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)))
                    }
                    AnimationType.RIPPLE -> {
                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))
                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                    }
                    AnimationType.CASCADE -> {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(3) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    repeat(3) { col ->
                                        val alpha = if (row * 3 + col < 5) 1f else 0.3f
                                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)))
                                    }
                                }
                            }
                        }
                    }
                    AnimationType.NONE -> {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun GlassStyleOption(
    label: String,
    style: GlassStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when (style) {
                            GlassStyle.LIGHT_FROST -> Color.White.copy(alpha = 0.4f)
                            GlassStyle.HEAVY_FROST -> Color.White.copy(alpha = 0.7f)
                            GlassStyle.ACRYLIC -> Color(0xFFE0E8F0).copy(alpha = 0.6f)
                            GlassStyle.CRYSTAL -> Color(0xFFE8F4FF).copy(alpha = 0.5f)
                            GlassStyle.ICE -> Color(0xFFD0E8FF).copy(alpha = 0.6f)
                            GlassStyle.NONE -> Color.Transparent
                        }
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun TreeStyleOption(
    label: String,
    style: TreeStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(modifier = Modifier.size(32.dp)) {
                    val trunkColor = Color(0xFF8B4513)
                    val leafColor = when (style) {
                        TreeStyle.SAKURA -> Color(0xFFFF69B4)
                        else -> Color(0xFF228B22)
                    }

                    // Draw trunk
                    drawRect(
                        color = trunkColor,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width / 2 - 2, size.height * 0.5f),
                        size = androidx.compose.ui.geometry.Size(4f, size.height * 0.5f)
                    )

                    // Draw foliage based on style
                    when (style) {
                        TreeStyle.SIMPLE -> {
                            val path = Path().apply {
                                moveTo(size.width / 2, size.height * 0.1f)
                                lineTo(size.width * 0.2f, size.height * 0.5f)
                                lineTo(size.width * 0.8f, size.height * 0.5f)
                                close()
                            }
                            drawPath(path, leafColor)
                        }
                        TreeStyle.DETAILED, TreeStyle.WILLOW -> {
                            drawCircle(leafColor, radius = size.width * 0.35f, center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.3f))
                        }
                        TreeStyle.BONSAI -> {
                            drawOval(leafColor, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.15f), size = androidx.compose.ui.geometry.Size(size.width * 0.7f, size.height * 0.35f))
                        }
                        TreeStyle.SAKURA -> {
                            drawCircle(leafColor, radius = size.width * 0.3f, center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.3f))
                            // Add small dots for blossoms
                            for (i in 0 until 5) {
                                drawCircle(Color.White.copy(alpha = 0.8f), radius = 2f, center = androidx.compose.ui.geometry.Offset(size.width * (0.3f + i * 0.1f), size.height * (0.2f + (i % 2) * 0.15f)))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun FluidStyleOption(
    label: String,
    style: FluidStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when (style) {
                            FluidStyle.WATER -> Color(0xFF4488CC)
                            FluidStyle.LAVA -> Color(0xFFFF6600)
                            FluidStyle.MERCURY -> Color(0xFFB8B8C8)
                            FluidStyle.PLASMA -> Color(0xFF8844FF)
                            FluidStyle.AURORA -> Color(0xFF44FF88)
                            FluidStyle.NONE -> Color.Transparent
                        }
                    )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ===== Umr-mode inline composables (used only when topViewMode == UMR) =====

@Composable
private fun LifeDataCard(
    meMs: Long,
    dadMs: Long,
    momMs: Long,
    onClick: () -> Unit,
) {
    val fmt = remember { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }
    ModernSectionTitle("LIFE DATA")
    ModernPanelCard(
        modifier = Modifier.clickable { onClick() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            UmrDobRow("Me",  meMs,  fmt)
            UmrDobRow("Dad", dadMs, fmt)
            UmrDobRow("Mom", momMs, fmt)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap to edit",
            color = ModernGoldMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun UmrDobRow(label: String, ms: Long, fmt: SimpleDateFormat) {
    Row {
        Text("$label  ", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        Text(
            if (ms != 0L) fmt.format(Date(ms)) else "Not set",
            color = Color(0xFFEDE8DE),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun VisualizationSection(
    current: UmrVisualMode,
    onChange: (UmrVisualMode) -> Unit,
) {
    ModernSectionTitle("VISUALIZATION")
    ModernPanelCard(contentPadding = 4.dp) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for ((mode, label) in listOf(UmrVisualMode.DOTS to "Dots", UmrVisualMode.X_MARKS to "X-marks")) {
                val isActive = mode == current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isActive) Color(0xFF1B1A16) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onChange(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (isActive) ModernGold else ModernGoldMuted,
                        fontSize = 14.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LifeDataEditorSheet(
    settings: WallpaperSettings,
    preferences: LifeDotsPreferences,
    initialWho: WhoTab,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(initialWho) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = LocalDate.now()

    // Keep edits as a draft until Done; dismissing never changes saved birthdays.
    fun seedDay(ms: Long) =
        if (ms == 0L) "" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).dayOfMonth.toString().padStart(2, '0')
    fun seedMonth(ms: Long) =
        if (ms == 0L) "" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).monthValue.toString().padStart(2, '0')
    fun seedYear(ms: Long) =
        if (ms == 0L) "" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).year.toString()

    var meD by rememberSaveable { mutableStateOf(seedDay(settings.umrSettings.birthdayEpochMs)) }
    var meM by rememberSaveable { mutableStateOf(seedMonth(settings.umrSettings.birthdayEpochMs)) }
    var meY by rememberSaveable { mutableStateOf(seedYear(settings.umrSettings.birthdayEpochMs)) }
    var dadD by rememberSaveable { mutableStateOf(seedDay(settings.umrSettings.dadBirthdayEpochMs)) }
    var dadM by rememberSaveable { mutableStateOf(seedMonth(settings.umrSettings.dadBirthdayEpochMs)) }
    var dadY by rememberSaveable { mutableStateOf(seedYear(settings.umrSettings.dadBirthdayEpochMs)) }
    var momD by rememberSaveable { mutableStateOf(seedDay(settings.umrSettings.momBirthdayEpochMs)) }
    var momM by rememberSaveable { mutableStateOf(seedMonth(settings.umrSettings.momBirthdayEpochMs)) }
    var momY by rememberSaveable { mutableStateOf(seedYear(settings.umrSettings.momBirthdayEpochMs)) }

    var dateError by rememberSaveable { mutableStateOf<String?>(null) }
    fun saveDrafts(): Boolean {
        val drafts = listOf(WhoTab.ME to BirthdayDraft(meD, meM, meY),
            WhoTab.DAD to BirthdayDraft(dadD, dadM, dadY), WhoTab.MOM to BirthdayDraft(momD, momM, momY))
        val invalid = drafts.firstOrNull { !it.second.isBlank && it.second.resolve(today) == null }
        if (invalid != null) {
            selected = invalid.first
            dateError = "Enter a valid birthday from 1900 to today, using DD / MM / YYYY."
            return false
        }
        for ((who, draft) in drafts) {
            val ms = draft.resolve(today)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
            when (who) {
                WhoTab.ME -> preferences.setUmrBirthday(ms)
                WhoTab.DAD -> preferences.setUmrDadBirthday(ms)
                WhoTab.MOM -> preferences.setUmrMomBirthday(ms)
            }
        }
        return true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0A0906),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Enter your life data",
                    color = Color(0xFFEDE8DE),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (saveDrafts()) onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFC62E)),
                ) { Text("Done") }
            }
            WhoTabs(selected = selected, onSelected = { selected = it })
            Text(
                text = "Date of Birth",
                color = ModernGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            when (selected) {
                WhoTab.ME -> DateNumberInputs(
                    dayText = meD, monthText = meM, yearText = meY,
                    onDayChange   = { meD = it; dateError = null },
                    onMonthChange = { meM = it; dateError = null },
                    onYearChange  = { meY = it; dateError = null },
                )
                WhoTab.DAD -> DateNumberInputs(
                    dayText = dadD, monthText = dadM, yearText = dadY,
                    onDayChange   = { dadD = it; dateError = null },
                    onMonthChange = { dadM = it; dateError = null },
                    onYearChange  = { dadY = it; dateError = null },
                )
                WhoTab.MOM -> DateNumberInputs(
                    dayText = momD, monthText = momM, yearText = momY,
                    onDayChange   = { momD = it; dateError = null },
                    onMonthChange = { momM = it; dateError = null },
                    onYearChange  = { momY = it; dateError = null },
                )
            }
            dateError?.let { Text(it, color = Color(0xFFFFBCAC), fontSize = 13.sp) }
            Text("Tap Done to save. Clear all date fields to remove a birthday. Swipe down or go back to cancel.", color = ModernTextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
