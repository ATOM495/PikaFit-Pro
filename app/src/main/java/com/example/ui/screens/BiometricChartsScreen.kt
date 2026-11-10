package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HunterBiometrics
import com.example.data.model.HunterSystemState
import com.example.data.model.JournalEntry
import com.example.data.model.StreakDateUtils
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataLoadState
import com.example.ui.viewmodel.NutritionChatMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class RechartsMetricMode(val label: String) {
    COMPOSED_ALL("COMPOSED (ALL 3)"),
    WEIGHT_KG("WEIGHT (KG)"),
    BODY_FAT_PCT("BODY FAT (%)"),
    MUSCLE_MASS("MUSCLE MASS (%)")
}

private data class BiometricChartPoint(
    val label: String,
    val weightKg: Double,
    val bodyFatPercent: Double,
    val muscleMassPercent: Double,
    val note: String
)

@Composable
fun BiometricChartsScreen(
    state: HunterSystemState,
    journalState: DataLoadState<List<JournalEntry>>,
    nutritionChatMessages: List<NutritionChatMessage> = emptyList(),
    isNutritionChatLoading: Boolean = false,
    onSendNutritionMessage: (String) -> Unit = {},
    onClearNutritionChat: () -> Unit = {},
    onLogBiometricPoint: (weightKg: Double, bodyFatPercent: Double?, muscleMassPercent: Double?, note: String) -> Unit,
    onLogFoodItem: (mealName: String, calories: Int, proteinG: Double) -> Unit,
    onLogExerciseSet: (exerciseName: String, sets: Int, reps: String, overload: Boolean) -> Unit,
    onUndoFood: () -> Unit = {},
    onUndoWater: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenCoachTip: () -> Unit = {}
) {
    val bio = state.biometrics
    val ps = state.playerStatus
    val currentTier = ps.currentTier
    val displayName = bio.hunterName.ifBlank { "Trainer" }
    var selectedMode by rememberSaveable { mutableStateOf(RechartsMetricMode.COMPOSED_ALL) }
    var showLogPointForm by rememberSaveable { mutableStateOf(false) }

    var weightInput by rememberSaveable { mutableStateOf("") }
    var bfInput by rememberSaveable { mutableStateOf("") }
    var muscleInput by rememberSaveable { mutableStateOf("") }
    var noteInput by rememberSaveable { mutableStateOf("") }

    // Build real chronological points from JournalEntry history + current calibrated biometrics
    val realPoints = remember(journalState, bio) {
        val entries = (journalState as? DataLoadState.Success)?.data.orEmpty()
        val sdf = SimpleDateFormat("dd MMM", Locale.US)
        val historical = entries
            .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
            .takeLast(7)
            .map { entry ->
                val dateStr = entry.createdAt?.toDate()?.let { sdf.format(it) } ?: "Log"
                BiometricChartPoint(
                    label = "${entry.weightKg}\n$dateStr",
                    weightKg = entry.weightKg,
                    bodyFatPercent = entry.bodyFatPercent,
                    muscleMassPercent = entry.muscleMassPercent,
                    note = entry.title.ifBlank { "Journal Entry" }
                )
            }
        val currentPoint = BiometricChartPoint(
            label = "${bio.weightKg}\nNow",
            weightKg = bio.weightKg,
            bodyFatPercent = bio.bodyFatPercent,
            muscleMassPercent = bio.muscleMassPercent,
            note = "Current Calibrated Telemetry"
        )
        if (historical.isEmpty()) {
            listOf(currentPoint)
        } else {
            val last = historical.last()
            if (last.weightKg == currentPoint.weightKg &&
                last.bodyFatPercent == currentPoint.bodyFatPercent &&
                last.muscleMassPercent == currentPoint.muscleMassPercent
            ) {
                historical
            } else {
                (historical + currentPoint).takeLast(8)
            }
        }
    }

    var inspectedIndex by remember(realPoints.size) {
        mutableIntStateOf((realPoints.size - 1).coerceAtLeast(0))
    }
    val safeInspectedIndex = inspectedIndex.coerceIn(0, (realPoints.size - 1).coerceAtLeast(0))
    val inspectedPoint = realPoints[safeInspectedIndex]

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("biometric_charts_screen_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TopTrainerHeader(
                trainerName = displayName,
                rankSubtitle = "${currentTier.classTitle} • ${currentTier.pokemonName}",
                streakDays = ps.streakDays,
                level = ps.level,
                avatarBase64 = bio.avatarBase64,
                pokemonDrawableRes = currentTier.pokemonDrawableRes,
                onAvatarClick = onOpenSettings,
                onOpenCoachTip = onOpenCoachTip,
                onOpenSettings = onOpenSettings
            )
        }

        // 1. RECHARTS BIOMETRIC TELEMETRY CARD (Real User Data + Interactive Logger)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .testTag("recharts_visualizer_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.65f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurfaceGradient)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RECHARTS BIOMETRIC TELEMETRY",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = TextPrimary
                            )
                            Text(
                                text = "Real Weight, Body Fat % & Muscle Mass (${realPoints.size} logged point${if (realPoints.size == 1) "" else "s"})",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = TextSecondary
                            )
                        }
                        Surface(
                            onClick = { showLogPointForm = !showLogPointForm },
                            color = NeonAmber.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, NeonAmber),
                            modifier = Modifier.testTag("toggle_log_biometric_point_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Log Biometrics",
                                    tint = NeonAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showLogPointForm) "CLOSE" else "LOG POINT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp
                                    ),
                                    color = NeonAmber
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = showLogPointForm) {
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderAmberGlow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "PLOT NEW BIOMETRIC DATA POINT (+50 XP)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = NeonAmber
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = weightInput,
                                        onValueChange = { weightInput = it },
                                        label = { Text("Weight (kg)") },
                                        placeholder = { Text("${bio.weightKg}") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("chart_weight_input")
                                    )
                                    OutlinedTextField(
                                        value = bfInput,
                                        onValueChange = { bfInput = it },
                                        label = { Text("Body Fat %") },
                                        placeholder = { Text("${bio.bodyFatPercent}") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("chart_bf_input")
                                    )
                                    OutlinedTextField(
                                        value = muscleInput,
                                        onValueChange = { muscleInput = it },
                                        label = { Text("Muscle (kg)") },
                                        placeholder = { Text("${bio.muscleMassKg}") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("chart_muscle_input")
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = noteInput,
                                    onValueChange = { noteInput = it },
                                    label = { Text("Check-In Note (optional)") },
                                    placeholder = { Text("e.g., Morning fasted weigh-in") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        val w = weightInput.toDoubleOrNull() ?: bio.weightKg
                                        val bf = bfInput.toDoubleOrNull()
                                        val mm = muscleInput.toDoubleOrNull()
                                        onLogBiometricPoint(w, bf, mm, noteInput)
                                        weightInput = ""
                                        bfInput = ""
                                        muscleInput = ""
                                        noteInput = ""
                                        showLogPointForm = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonAmber,
                                        contentColor = ObsidianBg
                                    ),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("chart_submit_biometric_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MonitorWeight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SAVE & PLOT POINT (+50 XP)", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode Filter Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RechartsMetricMode.entries.forEach { mode ->
                            val isSelected = selectedMode == mode
                            val pillColor = when (mode) {
                                RechartsMetricMode.COMPOSED_ALL -> NeonAmber
                                RechartsMetricMode.WEIGHT_KG -> HunterCyan
                                RechartsMetricMode.BODY_FAT_PCT -> PikaYellow
                                RechartsMetricMode.MUSCLE_MASS -> NeonOrange
                            }
                            Surface(
                                onClick = { selectedMode = mode },
                                color = if (isSelected) pillColor.copy(alpha = 0.18f) else ObsidianBg,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, pillColor.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = pillColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Real Data Multi-Wave Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                            .testTag("recharts_canvas_box")
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                            for (r in 1..3) {
                                val y = (h / 4f) * r
                                drawLine(
                                    color = BorderSubtle,
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            val count = realPoints.size
                            fun xForIndex(i: Int): Float =
                                if (count <= 1) w * 0.5f else (w * i.toFloat()) / (count - 1).toFloat()

                            val minW = (realPoints.minOf { it.weightKg } - 5.0).coerceAtLeast(30.0)
                            val maxW = (realPoints.maxOf { it.weightKg } + 5.0).coerceAtLeast(minW + 10.0)
                            fun yForWeight(v: Double): Float =
                                (h - (((v - minW) / (maxW - minW)).toFloat().coerceIn(0.1f, 0.9f) * h))

                            fun yForPercent(pct: Double): Float =
                                (h - ((pct / 100.0).toFloat().coerceIn(0.1f, 0.9f) * h))

                            fun buildSmoothPath(ySelector: (BiometricChartPoint) -> Float): Path {
                                return Path().apply {
                                    if (count == 1) {
                                        val y = ySelector(realPoints[0])
                                        moveTo(0f, y)
                                        lineTo(w, y)
                                    } else {
                                        realPoints.forEachIndexed { idx, pt ->
                                            val x = xForIndex(idx)
                                            val y = ySelector(pt)
                                            if (idx == 0) {
                                                moveTo(x, y)
                                            } else {
                                                val prevX = xForIndex(idx - 1)
                                                val prevY = ySelector(realPoints[idx - 1])
                                                val midX = (prevX + x) / 2f
                                                cubicTo(midX, prevY, midX, y, x, y)
                                            }
                                        }
                                    }
                                }
                            }

                            if (selectedMode == RechartsMetricMode.COMPOSED_ALL || selectedMode == RechartsMetricMode.WEIGHT_KG) {
                                drawPath(
                                    path = buildSmoothPath { yForWeight(it.weightKg) },
                                    color = HunterCyan,
                                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            if (selectedMode == RechartsMetricMode.COMPOSED_ALL || selectedMode == RechartsMetricMode.MUSCLE_MASS) {
                                drawPath(
                                    path = buildSmoothPath { yForPercent(it.muscleMassPercent) },
                                    color = NeonOrange,
                                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            if (selectedMode == RechartsMetricMode.COMPOSED_ALL || selectedMode == RechartsMetricMode.BODY_FAT_PCT) {
                                drawPath(
                                    path = buildSmoothPath { yForPercent(it.bodyFatPercent) },
                                    color = PikaYellow,
                                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            val cx = xForIndex(safeInspectedIndex)
                            drawLine(
                                color = TextSecondary,
                                start = Offset(cx, 0f),
                                end = Offset(cx, h),
                                strokeWidth = 1.2.dp.toPx(),
                                pathEffect = dash
                            )
                            drawCircle(
                                color = NeonOrange,
                                radius = 4.5.dp.toPx(),
                                center = Offset(cx, yForPercent(inspectedPoint.muscleMassPercent))
                            )
                            drawCircle(
                                color = HunterCyan,
                                radius = 4.5.dp.toPx(),
                                center = Offset(cx, yForWeight(inspectedPoint.weightKg))
                            )
                            drawCircle(
                                color = PikaYellow,
                                radius = 4.dp.toPx(),
                                center = Offset(cx, yForPercent(inspectedPoint.bodyFatPercent))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Interactive X-Axis Point Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (realPoints.size == 1) Arrangement.Center else Arrangement.SpaceBetween
                    ) {
                        realPoints.forEachIndexed { idx, pt ->
                            val isSelected = idx == safeInspectedIndex
                            Text(
                                text = pt.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                ),
                                color = if (isSelected) NeonAmber else TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { inspectedIndex = idx }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Real Inspected Point Pill
                    Surface(
                        color = ObsidianBg,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = "INSPECTED: ${inspectedPoint.weightKg} kg • ${inspectedPoint.bodyFatPercent}% BF • ${inspectedPoint.muscleMassPercent}% Muscle (${inspectedPoint.note})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 2. DAILY PRODUCTIVE STREAK CARD (Real Streak & Real 7-Day Activity Calendar)
        item {
            DailyProductiveStreakCard(
                state = state,
                onLogFoodItem = onLogFoodItem,
                onLogExerciseSet = onLogExerciseSet,
                onUndoFood = onUndoFood,
                onUndoWater = onUndoWater
            )
        }

        // 3. BODY COMP_MATRIX CARD (Real Biometrics)
        item {
            BodyCompMatrixCard(bio = bio)
        }
    }
}

@Composable
fun DailyProductiveStreakCard(
    state: HunterSystemState,
    onLogFoodItem: (mealName: String, calories: Int, proteinG: Double) -> Unit,
    onLogExerciseSet: (exerciseName: String, sets: Int, reps: String, overload: Boolean) -> Unit,
    onUndoFood: () -> Unit = {},
    onUndoWater: () -> Unit = {}
) {
    val ps = state.playerStatus
    var expandedForm by rememberSaveable { mutableIntStateOf(0) }
    var foodName by rememberSaveable { mutableStateOf("") }
    var foodCalories by rememberSaveable { mutableStateOf("") }
    var foodProtein by rememberSaveable { mutableStateOf("") }
    var exerciseName by rememberSaveable { mutableStateOf("") }
    var exerciseSets by rememberSaveable { mutableStateOf("3") }
    var exerciseReps by rememberSaveable { mutableStateOf("10") }

    // Build real last-7-days flame status from ps.productiveHistoryDates & ps.checkedInToday
    val days = remember(ps.productiveHistoryDates, ps.checkedInToday, ps.streakDays) {
        val historySet = ps.productiveHistoryDates.toSet()
        val dayFormat = SimpleDateFormat("EEE", Locale.US)
        (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val dateStr = StreakDateUtils.daysAgoDateString(daysAgo)
            val isToday = daysAgo == 0
            val label = if (isToday) "TODAY" else dayFormat.format(cal.time).uppercase(Locale.US)
            val active = if (isToday) {
                ps.checkedInToday || dateStr in historySet
            } else {
                dateStr in historySet || (ps.checkedInToday && daysAgo < ps.streakDays) || (!ps.checkedInToday && daysAgo <= ps.streakDays)
            }
            Triple(label, active, isToday)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .testTag("daily_productive_streak_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceGradient)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DAILY PRODUCTIVE STREAK",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextSecondary
                    )
                    Text(
                        text = if (ps.checkedInToday) {
                            "Checked in today (${ps.todayFoodInputs} food • ${ps.todayExerciseInputs} workout)"
                        } else {
                            "Log 1 meal or workout set to check in today!"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ps.checkedInToday) HunterEmerald else TextSecondary
                    )
                }
                Text(
                    text = "${ps.streakDays} DAY STREAK",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = NeonAmber,
                    modifier = Modifier.testTag("streak_days_headline")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { (label, active, isToday) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium
                            ),
                            color = if (isToday) PikaYellow else TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) NeonAmber.copy(alpha = 0.16f) else ObsidianBg
                                )
                                .border(
                                    width = 1.8.dp,
                                    color = when {
                                        isToday && active -> HunterEmerald
                                        isToday -> NeonCoral
                                        active -> NeonAmber
                                        else -> BorderSubtle
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalFireDepartment,
                                contentDescription = label,
                                tint = when {
                                    isToday && active -> HunterEmerald
                                    isToday -> NeonCoral
                                    active -> NeonAmber
                                    else -> TextMuted
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (active) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (isToday) HunterEmerald else NeonAmber,
                                modifier = Modifier.size(12.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, TextMuted, CircleShape)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        expandedForm = if (expandedForm == 1) 0 else 1
                    },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("streak_open_food_input_button")
                ) {
                    Text("LOG FOOD", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        expandedForm = if (expandedForm == 2) 0 else 2
                    },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("streak_open_exercise_input_button")
                ) {
                    Text("LOG EXERCISE", color = NeonAmber, fontWeight = FontWeight.Bold)
                }
            }

            // Undo Row for accidental entries
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onUndoFood,
                    modifier = Modifier.testTag("streak_undo_food_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo Food",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Undo Last Food", color = TextSecondary, fontSize = 11.5.sp)
                }
                TextButton(
                    onClick = onUndoWater,
                    modifier = Modifier.testTag("streak_undo_water_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo Water",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Undo Last Water", color = TextSecondary, fontSize = 11.5.sp)
                }
            }

            AnimatedVisibility(visible = expandedForm == 1) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = foodName,
                        onValueChange = { foodName = it },
                        label = { Text("Meal Name (e.g., 3 Idli + Sambar, Chicken Curry)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("streak_food_name_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = foodCalories,
                            onValueChange = { foodCalories = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Calories (kcal)") },
                            placeholder = { Text("450") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = foodProtein,
                            onValueChange = { foodProtein = it },
                            label = { Text("Protein (g)") },
                            placeholder = { Text("30") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val cal = foodCalories.toIntOrNull() ?: 450
                            val pro = foodProtein.toDoubleOrNull() ?: 30.0
                            onLogFoodItem(foodName.ifBlank { "Logged Meal" }, cal, pro)
                            foodName = ""
                            foodCalories = ""
                            foodProtein = ""
                            expandedForm = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = ObsidianBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("streak_submit_food_button")
                    ) {
                        Text("SUBMIT FOOD & CHECK IN", fontWeight = FontWeight.Bold)
                    }
                }
            }

            AnimatedVisibility(visible = expandedForm == 2) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = exerciseName,
                        onValueChange = { exerciseName = it },
                        label = { Text("Exercise Name (e.g., Squats, Pushups, Rows)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("streak_exercise_name_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = exerciseSets,
                            onValueChange = { exerciseSets = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Sets") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = exerciseReps,
                            onValueChange = { exerciseReps = it },
                            label = { Text("Reps / Duration") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val s = exerciseSets.toIntOrNull()?.coerceIn(1, 20) ?: 3
                            onLogExerciseSet(
                                exerciseName.ifBlank { "Progressive Overload Set" },
                                s,
                                exerciseReps.ifBlank { "10" },
                                true
                            )
                            exerciseName = ""
                            expandedForm = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = ObsidianBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("streak_submit_exercise_button")
                    ) {
                        Text("SUBMIT EXERCISE & CHECK IN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyCompMatrixCard(bio: HunterBiometrics) {
    val bmiProgress = ((bio.bmi - 12.0) / 28.0).toFloat().coerceIn(0.15f, 1f)
    val leanProgress = (bio.leanMassPercent / 100.0).toFloat().coerceIn(0.15f, 1f)
    val bmrProgress = ((bio.bmrKcal - 900).toFloat() / 2200f).coerceIn(0.2f, 1f)

    val muscleWeight = (bio.muscleMassPercent / 100.0).toFloat().coerceIn(0.25f, 0.85f)
    val fatWeight = (bio.bodyFatPercent / 100.0).toFloat().coerceIn(0.05f, 0.55f)
    val boneWeight = (bio.boneMassPercent / 100.0).toFloat().coerceIn(0.03f, 0.15f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .testTag("body_composition_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurfaceGradient)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BODY COMP_MATRIX",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    ),
                    color = TextSecondary
                )
                Text(
                    text = "${bio.weightKg} kg → Target ${bio.targetWeightKg} kg",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = NeonAmber
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularGaugeTile(
                    label = "BMI",
                    mainValue = "${bio.bmi}",
                    subValue = bio.bmiCategory.substringBefore(" /"),
                    sweepColor = HunterCyan,
                    progress = bmiProgress,
                    modifier = Modifier.weight(1f)
                )
                CircularGaugeTile(
                    label = "LEAN MASS",
                    mainValue = "${bio.leanMassPercent}%",
                    subValue = "${bio.leanBodyMassKg} kg",
                    sweepColor = NeonAmber,
                    progress = leanProgress,
                    modifier = Modifier.weight(1f)
                )
                CircularGaugeTile(
                    label = "BMR",
                    mainValue = "${bio.bmrKcal}",
                    subValue = "kcal/d",
                    sweepColor = PikaYellow,
                    progress = bmrProgress,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-Segment Progress Bar (Real Muscle, Fat, Bone proportions)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ObsidianBg)
            ) {
                Box(
                    modifier = Modifier
                        .weight(muscleWeight)
                        .fillMaxHeight()
                        .background(AmberCrimsonGradient)
                )
                Box(
                    modifier = Modifier
                        .weight(fatWeight)
                        .fillMaxHeight()
                        .background(PikaYellow)
                )
                Box(
                    modifier = Modifier
                        .weight(boneWeight)
                        .fillMaxHeight()
                        .background(HunterCyan)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendDotItem(color = NeonOrange, text = "Muscle: ${bio.muscleMassPercent}% (${bio.muscleMassKg}kg)")
                LegendDotItem(color = PikaYellow, text = "Fat: ${bio.bodyFatPercent}% (${bio.fatMassKg}kg)")
                LegendDotItem(color = HunterCyan, text = "Bone: ${bio.boneMassKg}kg")
            }
        }
    }
}

@Composable
private fun CircularGaugeTile(
    label: String,
    mainValue: String,
    subValue: String,
    sweepColor: Color,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianBg.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 6.dp)
                .size(86.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 5.5.dp.toPx()
                drawArc(
                    color = BorderSubtle,
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = sweepColor,
                    startAngle = 140f,
                    sweepAngle = 260f * progress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = TextSecondary
                )
                Text(
                    text = mainValue,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    ),
                    color = TextPrimary
                )
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LegendDotItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TextPrimary
        )
    }
}