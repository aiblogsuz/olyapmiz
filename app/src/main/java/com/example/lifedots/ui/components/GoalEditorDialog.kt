package com.example.lifedots.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.lifedots.preferences.Event
import com.example.lifedots.preferences.Goal
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private val DialogInk = Color(0xFF151008)
private val DialogInkSoft = Color(0xFF21180D)
private val DialogGold = Color(0xFFD1AA62)
private val DialogGoldBright = Color(0xFFFFC84A)
private val DialogGoldDark = Color(0xFF5D421F)
private val PaperBase = Color(0xFFC7B083)
private val PaperLight = Color(0xFFE1CFA2)
private val PaperInk = Color(0xFF1E160B)

/**
 * Event editor — a thin re-brand of [GoalEditorDialog] for Umr events.
 * Events and goals share the same data shape (title / target date / colour),
 * so the editor reuses the goal dialog and converts at the boundary. This
 * keeps the two types distinct in preferences (they never bleed into each
 * other) while presenting the right copy ("Add Event", "Event Title").
 */
@Composable
fun EventEditorDialog(
    event: Event? = null,
    onSave: (Event) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    GoalEditorDialog(
        goal = event?.let {
            Goal(id = it.id, title = it.title, targetDate = it.targetDate, color = it.color)
        },
        onSave = { saved ->
            onSave(
                Event(
                    id = saved.id,
                    title = saved.title,
                    targetDate = saved.targetDate,
                    color = saved.color,
                )
            )
        },
        onDelete = onDelete,
        onDismiss = onDismiss,
        isEvent = true,
    )
}

@Composable
fun GoalEditorDialog(
    goal: Goal? = null,
    onSave: (Goal) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    isEvent: Boolean = false,
) {
    val itemLabel = if (isEvent) "Event" else "Goal"
    var title by remember { mutableStateOf(goal?.title ?: "") }
    val initialDate: LocalDate? = remember(goal) {
        goal?.targetDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    }
    var dayText by remember { mutableStateOf(initialDate?.dayOfMonth?.toString()?.padStart(2, '0') ?: "") }
    var monthText by remember { mutableStateOf(initialDate?.monthValue?.toString()?.padStart(2, '0') ?: "") }
    var yearText by remember { mutableStateOf(initialDate?.year?.toString() ?: "") }
    var selectedColor by remember { mutableIntStateOf(goal?.color ?: 0xFF2D75A8.toInt()) }

    val resolvedDate: LocalDate? = remember(dayText, monthText, yearText) {
        val d = dayText.toIntOrNull()
        val m = monthText.toIntOrNull()
        val y = yearText.toIntOrNull()
        if (d == null || m == null || y == null) null
        else runCatching { LocalDate.of(y, m, d) }.getOrNull()
    }
    val presetColors = listOf(
        0xFF2D75A8.toInt(),
        0xFF6B8C3B.toInt(),
        0xFFC77822.toInt(),
        0xFFA23654.toInt(),
        0xFF5D3A9B.toInt(),
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(18.dp),
            color = DialogInk,
            border = androidx.compose.foundation.BorderStroke(1.dp, DialogGoldDark),
            shadowElevation = 18.dp,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                DialogGold.copy(alpha = 0.13f),
                                DialogInk,
                                Color.Black.copy(alpha = 0.94f),
                            ),
                            center = Offset(120f, 40f),
                            radius = 680f,
                        )
                    )
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    repeat(70) { i ->
                        val x = ((i * 83) % size.width.toInt()).toFloat()
                        val y = ((i * 47) % size.height.toInt()).toFloat()
                        drawCircle(
                            color = DialogGold.copy(alpha = if (i % 3 == 0) 0.045f else 0.022f),
                            radius = (2 + (i % 5)).toFloat(),
                            center = Offset(x, y),
                        )
                    }
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.035f),
                        topLeft = Offset(2f, 2f),
                        size = Size(size.width - 4f, size.height - 4f),
                        cornerRadius = CornerRadius(42f, 42f),
                        style = Stroke(width = 2f),
                    )
                }
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 14.dp),
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (goal == null) "Add $itemLabel" else "Edit $itemLabel",
                        color = DialogGold,
                        fontFamily = FontFamily.Serif,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    DialogCloseButton(onClick = onDismiss)
                }

                Spacer(modifier = Modifier.height(16.dp))

                GoalParchmentInput(
                    title = title,
                    onTitleChange = { title = it },
                    labelText = "$itemLabel Title",
                    placeholderText = "Enter ${itemLabel.lowercase()} title...",
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Target Date",
                    color = DialogGold,
                    fontFamily = FontFamily.Serif,
                    fontSize = 21.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                DateNumberInputs(
                    dayText = dayText,
                    monthText = monthText,
                    yearText = yearText,
                    onDayChange = { dayText = it },
                    onMonthChange = { monthText = it },
                    onYearChange = { yearText = it },
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Color",
                    color = DialogGold,
                    fontFamily = FontFamily.Serif,
                    fontSize = 21.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                GoalColorStrip(
                    colors = presetColors,
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it },
                )

                Spacer(modifier = Modifier.height(20.dp))
                GoalSeparator()
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DialogTextButton(
                        text = if (goal != null && onDelete != null) "Delete" else "Cancel",
                        onClick = {
                            if (goal != null && onDelete != null) {
                                onDelete()
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    DialogSaveButton(
                        text = "Save $itemLabel",
                        enabled = title.isNotBlank() && resolvedDate != null,
                        onClick = {
                            val date = resolvedDate
                            if (title.isNotBlank() && date != null) {
                                val targetMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                onSave(
                                    Goal(
                                        id = goal?.id ?: UUID.randomUUID().toString(),
                                        title = title.trim(),
                                        targetDate = targetMs,
                                        color = selectedColor,
                                    )
                                )
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1.35f),
                    )
                }
                }
            }
        }
    }

}

@Composable
private fun DialogCloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(DialogInkSoft, Color.Black.copy(alpha = 0.82f))
                )
            )
            .border(1.dp, DialogGoldDark, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "×",
            color = DialogGold,
            fontSize = 34.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GoalParchmentInput(
    title: String,
    onTitleChange: (String) -> Unit,
    labelText: String = "Event Title",
    placeholderText: String = "Enter event title...",
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                Brush.linearGradient(
                    listOf(PaperLight, PaperBase, Color(0xFFB59A68))
                )
            )
            .border(1.dp, Color(0xFF7B653D), RoundedCornerShape(15.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.08f),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(28f, 28f),
                style = Stroke(width = 2f),
            )
            repeat(22) { i ->
                val x = (i * 47 % size.width.toInt()).toFloat()
                val y = (i * 29 % size.height.toInt()).toFloat()
                drawCircle(Color.White.copy(alpha = 0.06f), radius = 9f, center = Offset(x, y))
            }
            drawQuillSketch()
        }
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(
                text = labelText,
                color = PaperInk,
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(13.dp))
            Box {
                if (title.isBlank()) {
                    Text(
                        text = placeholderText,
                        color = PaperInk.copy(alpha = 0.58f),
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 19.sp,
                    )
                }
                BasicTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = PaperInk,
                        fontFamily = FontFamily.Serif,
                        fontSize = 19.sp,
                        fontStyle = FontStyle.Italic,
                    ),
                    modifier = Modifier.fillMaxWidth(0.68f),
                )
            }
            Spacer(modifier = Modifier.height(9.dp))
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(1.dp)
                    .background(PaperInk.copy(alpha = 0.25f))
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawQuillSketch() {
    val ink = PaperInk.copy(alpha = 0.35f)
    val baseX = size.width * 0.82f
    val baseY = size.height * 0.70f
    drawLine(
        color = ink,
        start = Offset(baseX, baseY),
        end = Offset(size.width * 0.95f, size.height * 0.22f),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )
    val feather = Path().apply {
        moveTo(size.width * 0.93f, size.height * 0.22f)
        cubicTo(size.width * 0.99f, size.height * 0.24f, size.width * 0.96f, size.height * 0.50f, baseX, baseY)
        cubicTo(size.width * 0.86f, size.height * 0.44f, size.width * 0.88f, size.height * 0.29f, size.width * 0.93f, size.height * 0.22f)
        close()
    }
    drawPath(feather, ink.copy(alpha = 0.18f))
    repeat(5) { i ->
        val y = size.height * (0.35f + i * 0.07f)
        drawLine(
            color = ink.copy(alpha = 0.32f),
            start = Offset(size.width * 0.89f, y),
            end = Offset(size.width * (0.95f - i * 0.015f), y - 18f),
            strokeWidth = 1.4f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun GoalDatePill(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color.Black.copy(alpha = 0.55f), DialogInkSoft, Color.Black.copy(alpha = 0.55f))
                )
            )
            .border(1.2.dp, DialogGoldDark, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarGlyph()
        Text(
            text = text,
            color = DialogGold,
            fontFamily = FontFamily.Serif,
            fontSize = 21.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "⌄",
            color = DialogGold,
            fontSize = 22.sp,
        )
    }
}

@Composable
private fun CalendarGlyph() {
    Canvas(modifier = Modifier.size(26.dp)) {
        val stroke = 3.2f
        drawRoundRect(
            color = DialogGold,
            topLeft = Offset(size.width * 0.12f, size.height * 0.18f),
            size = Size(size.width * 0.76f, size.height * 0.70f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = DialogGold,
            start = Offset(size.width * 0.12f, size.height * 0.36f),
            end = Offset(size.width * 0.88f, size.height * 0.36f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        repeat(2) { row ->
            repeat(3) { col ->
                drawCircle(
                    color = DialogGold,
                    radius = 1.8f,
                    center = Offset(size.width * (0.32f + col * 0.18f), size.height * (0.52f + row * 0.18f)),
                )
            }
        }
    }
}

@Composable
private fun GoalColorStrip(
    colors: List<Int>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, DialogGoldDark, RoundedCornerShape(13.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        colors.forEach { color ->
            GoalColorOrb(
                color = Color(color),
                selected = selectedColor == color,
                onClick = { onColorSelected(color) },
            )
        }
    }
}

@Composable
private fun GoalColorOrb(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(if (selected) 52.dp else 44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            if (selected) {
                drawCircle(DialogGold, radius = radius, center = center)
                drawCircle(Color.Black.copy(alpha = 0.45f), radius = radius * 0.86f, center = center)
            }
            val innerRadius = if (selected) radius * 0.76f else radius * 0.90f
            drawCircle(
                color = Color.Black.copy(alpha = 0.42f),
                radius = innerRadius,
                center = Offset(center.x + radius * 0.08f, center.y + radius * 0.10f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f),
                        color.copy(alpha = 1.0f),
                        color.copy(alpha = 0.92f),
                        color.copy(alpha = 0.62f),
                    ),
                    center = Offset(size.width * 0.36f, size.height * 0.28f),
                    radius = innerRadius * 1.35f,
                ),
                radius = innerRadius,
                center = center,
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.55f),
                radius = innerRadius,
                center = center,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = innerRadius * 0.14f,
                center = Offset(size.width * 0.36f, size.height * 0.30f),
            )
        }
    }
}

@Composable
private fun GoalSeparator() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        val y = size.height / 2f
        drawLine(DialogGoldDark, Offset(0f, y), Offset(size.width * 0.45f, y), strokeWidth = 1.2f)
        drawLine(DialogGoldDark, Offset(size.width * 0.55f, y), Offset(size.width, y), strokeWidth = 1.2f)
        val diamond = Path().apply {
            moveTo(size.width * 0.50f, y - 8f)
            lineTo(size.width * 0.525f, y)
            lineTo(size.width * 0.50f, y + 8f)
            lineTo(size.width * 0.475f, y)
            close()
        }
        drawPath(diamond, DialogGoldDark)
        drawCircle(DialogGoldDark, radius = 2.5f, center = Offset(size.width * 0.45f, y))
        drawCircle(DialogGoldDark, radius = 2.5f, center = Offset(size.width * 0.55f, y))
    }
}

@Composable
private fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .border(1.dp, DialogGoldDark, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = DialogGold,
            fontFamily = FontFamily.Serif,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DialogSaveButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) {
                    Brush.linearGradient(
                        listOf(
                            DialogGoldBright,
                            Color(0xFFC2882F),
                            Color(0xFF7C4D14),
                        )
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            DialogGold.copy(alpha = 0.95f),
                            Color(0xFFB17B2C).copy(alpha = 0.92f),
                            Color(0xFF6A4314).copy(alpha = 0.90f),
                        )
                    )
                }
            )
            .border(1.dp, if (enabled) DialogGold else DialogGoldDark, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.Black.copy(alpha = if (enabled) 0.95f else 0.72f),
            fontFamily = FontFamily.Serif,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun getDefaultTargetDate(): Long {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, 30)
    return calendar.timeInMillis
}
