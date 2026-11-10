package com.example.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.model.HunterSystemState
import com.example.ui.theme.*
import com.example.ui.viewmodel.AiSyncState
import com.example.ui.viewmodel.DataLoadState
import com.example.ui.viewmodel.FoodEntryRecord
import com.example.ui.viewmodel.HunterViewModel
import com.example.ui.viewmodel.HunterViewModelFactory
import com.example.ui.viewmodel.PikaCoachTip
import com.example.ui.viewmodel.PikaCoachUiState
import com.example.ui.viewmodel.WaterEntryRecord
import com.example.util.SmartFoodNutritionEngine
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.roundToInt

@Composable
fun PikachuMascotBadge(
    @DrawableRes drawableRes: Int = R.drawable.pika_logo_hi,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = drawableRes),
        contentDescription = "Pikachu Mascot",
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
fun TopTrainerHeader(
    trainerName: String,
    rankSubtitle: String,
    streakDays: Int,
    level: Int,
    avatarBase64: String = "",
    @DrawableRes pokemonDrawableRes: Int = R.drawable.pika_logo_hi,
    showPikachuAvatar: Boolean = true,
    onAvatarClick: () -> Unit = {},
    onOpenCoachTip: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val customAvatarBitmap = remember(avatarBase64) {
        decodeBase64ToBitmap(avatarBase64)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onAvatarClick() }
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ObsidianCardElevated)
                        .border(1.8.dp, NeonAmber, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (customAvatarBitmap != null) {
                        Image(
                            bitmap = customAvatarBitmap.asImageBitmap(),
                            contentDescription = "Trainer Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                                .clip(CircleShape)
                        )
                    } else if (showPikachuAvatar) {
                        Image(
                            painter = painterResource(id = pokemonDrawableRes),
                            contentDescription = "Guardian Emblem",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PersonOutline,
                            contentDescription = "Avatar",
                            tint = NeonAmber,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = trainerName.ifBlank { "TRAINER" }.uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = rankSubtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = NeonAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Real Dual Streak + Level Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PillStreakGradient)
                        .clickable { onOpenCoachTip() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "STREAK ${streakDays}D ⚡",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.28f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LVL $level",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.5.sp
                                ),
                                color = PikaYellow
                            )
                        }
                    }
                }

                // Settings & Feedback Hub Icon Button
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ObsidianCardElevated)
                        .border(1.dp, BorderSubtle, CircleShape)
                        .testTag("top_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings & Feedback",
                        tint = NeonAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Keep charging, ${trainerName.ifBlank { "Trainer" }.replaceFirstChar { it.uppercase() }}! ⚡",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 23.sp
            ),
            color = TextPrimary
        )
    }
}

@Composable
fun MainHunterScreen(
    currentUserId: String,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: HunterViewModel = viewModel(
        key = "hunter_vm_$currentUserId",
        factory = HunterViewModelFactory(context, currentUserId)
    )

    val state by viewModel.systemState.collectAsState()
    val isSessionLoaded by viewModel.isSessionLoaded.collectAsState()
    val aiSyncState by viewModel.aiSyncState.collectAsState()
    val pikaCoachState by viewModel.pikaCoachState.collectAsState()
    val journalState by viewModel.journalState.collectAsState()
    val feedbackState by viewModel.feedbackState.collectAsState()
    val releaseInfo by viewModel.appReleaseInfo.collectAsState()
    val chatMessages by viewModel.nutritionChatMessages.collectAsState()
    val isChatLoading by viewModel.isNutritionChatLoading.collectAsState()
    val foodHistory by viewModel.foodHistory.collectAsState()
    val waterHistory by viewModel.waterHistory.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: Home, 1: Analytics, 2: PikaBot, 3: Ranks, 4: Profile & Journal
    var showSettingsScreen by rememberSaveable { mutableStateOf(false) }
    var settingsFocusSection by rememberSaveable { mutableIntStateOf(0) }

    // 1. Show Step-by-Step Trainer Onboarding if user has not completed onboarding yet
    if (isSessionLoaded && !state.biometrics.onboardingCompleted) {
        PikaFitOnboardingScreen(
            initialBiometrics = state.biometrics,
            isDark = isDark,
            onCompleteOnboarding = { name, age, sex, heightCm, weightKg, goal, exp ->
                viewModel.completeTrainerOnboarding(
                    trainerName = name,
                    age = age,
                    biologicalSex = sex,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    fitnessGoal = goal,
                    experienceLevel = exp
                )
            }
        )
        return
    }

    // 2. Full Settings & Creator Feedback Hub Screen when opened
    if (showSettingsScreen) {
        SettingsAndFeedbackScreen(
            state = state,
            feedbackState = feedbackState,
            currentUserId = currentUserId,
            initialFocusSection = settingsFocusSection,
            releaseInfo = releaseInfo,
            isDark = isDark,
            onToggleTheme = onToggleTheme,
            onBack = { showSettingsScreen = false },
            onSaveProfileAndTargets = { name, title, w, h, a, sex, bf, mm, cal, water, avatar ->
                viewModel.updateSettingsProfileAndTargets(
                    hunterName = name,
                    hunterTitle = title,
                    weightKg = w,
                    heightCm = h,
                    age = a,
                    biologicalSex = sex,
                    customBodyFatPercent = bf,
                    customMuscleMassPercent = mm,
                    caloriesTarget = cal,
                    waterTargetLiters = water,
                    avatarBase64 = avatar
                )
            },
            onSaveProfileWithGoal = { name, title, w, h, a, sex, bf, mm, cal, water, avatar, targetW, physique ->
                viewModel.updateSettingsProfileAndTargets(
                    hunterName = name,
                    hunterTitle = title,
                    weightKg = w,
                    heightCm = h,
                    age = a,
                    biologicalSex = sex,
                    customBodyFatPercent = bf,
                    customMuscleMassPercent = mm,
                    caloriesTarget = cal,
                    waterTargetLiters = water,
                    avatarBase64 = avatar,
                    targetWeightKg = targetW,
                    targetPhysique = physique
                )
            },
            onResetProgress = { clearJournal ->
                showSettingsScreen = false
                viewModel.resetHunterProgress(clearJournal)
            },
            onSubmitFeedback = { cat, rating, msg ->
                viewModel.submitPublicFeedback(cat, rating, msg)
            },
            onUpdateFeedbackStatus = { id, status ->
                viewModel.updateFeedbackStatus(id, status)
            },
            onDeleteFeedback = { id ->
                viewModel.deleteFeedback(id)
            }
        )
        return
    }

    // 3. Pika-Coach AI Popup Dialog when triggered
    val activeCoachTip: PikaCoachTip? = when (val cs = pikaCoachState) {
        is PikaCoachUiState.Ready -> if (cs.showPopup) cs.tip else null
        is PikaCoachUiState.Error -> if (cs.showPopup) cs.fallbackTip else null
        else -> null
    }

    if (activeCoachTip != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPikaCoachPopup() },
            containerColor = ObsidianCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.pika_logo_hi),
                        contentDescription = "Pika-Coach",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, NeonAmber, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PIKA-COACH TELEMETRY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = NeonAmber
                        )
                        Text(
                            text = activeCoachTip.headline,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }
                }
            },
            text = {
                Text(
                    text = activeCoachTip.advice,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissPikaCoachPopup()
                        when (activeCoachTip.actionType) {
                            "LOG_FOOD" -> viewModel.quickAdjustCalories(350, 28.0)
                            "LOG_WATER" -> viewModel.quickAddWater(0.5)
                            "LOG_WORKOUT" -> selectedTab = 0
                            else -> {}
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = ObsidianBg)
                ) {
                    Text(activeCoachTip.actionLabel, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.generatePikaCoachTip(showAsPopup = true) }) {
                        Text("Next Tip ⚡", color = PikaYellow)
                    }
                    TextButton(onClick = { viewModel.dismissPikaCoachPopup() }) {
                        Text("Close", color = TextSecondary)
                    }
                }
            }
        )
    }

    Scaffold(
        containerColor = ObsidianBg,
        bottomBar = {
            PikaFitBottomBar(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .padding(innerPadding)
        ) {
            // Live AI Sync Feedback Banner
            AiSyncStatusBanner(
                aiSyncState = aiSyncState,
                onDismiss = { viewModel.dismissAiSyncBanner() }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> HomeDashboardContent(
                        state = state,
                        foodHistory = foodHistory,
                        waterHistory = waterHistory,
                        onLogExerciseSet = { ex, sets, reps, ov ->
                            viewModel.logExerciseSet(ex, sets, reps, ov)
                        },
                        onLogFoodItem = { meal, cal, pro, carbs, fats, fiber, grams ->
                            viewModel.logFoodItem(
                                mealName = meal,
                                calories = cal,
                                proteinG = pro,
                                carbsG = carbs,
                                fatsG = fats,
                                fiberG = fiber,
                                grams = grams
                            )
                        },
                        onQuickWater = { liters ->
                            viewModel.quickAddWater(liters)
                        },
                        onUndoFood = { viewModel.undoLastFoodEntry() },
                        onUndoWater = { viewModel.undoLastWaterEntry() },
                        onToggleQuest = { index -> viewModel.toggleQuestStatus(index) },
                        onSubmitTacticalCommand = { cmd -> viewModel.submitTacticalCommand(cmd) },
                        onOpenPikaBot = { selectedTab = 2 },
                        onAvatarClick = { selectedTab = 4 },
                        onOpenCoachTip = { viewModel.reopenPikaCoachPopup() },
                        onOpenSettings = {
                            settingsFocusSection = 0
                            showSettingsScreen = true
                        }
                    )
                    1 -> BiometricChartsScreen(
                        state = state,
                        journalState = journalState,
                        nutritionChatMessages = chatMessages,
                        isNutritionChatLoading = isChatLoading,
                        onSendNutritionMessage = { viewModel.sendNutritionChatMessage(it) },
                        onClearNutritionChat = { viewModel.clearNutritionChat() },
                        onLogBiometricPoint = { w, bf, mm, note ->
                            viewModel.logBiometricDataPoint(w, bf, mm, note)
                        },
                        onLogFoodItem = { name, cal, pro ->
                            viewModel.logFoodIntake(name, cal, pro)
                        },
                        onLogExerciseSet = { ex, sets, reps, ov ->
                            viewModel.logExerciseSet(ex, sets, reps, ov)
                        },
                        onUndoFood = { viewModel.undoLastFoodIntake() },
                        onUndoWater = { viewModel.undoLastWaterIntake() },
                        onOpenSettings = {
                            settingsFocusSection = 0
                            showSettingsScreen = true
                        },
                        onOpenCoachTip = { viewModel.reopenPikaCoachPopup() }
                    )
                    2 -> PikaBotScreen(
                        state = state,
                        messages = chatMessages,
                        isLoading = isChatLoading,
                        onSendMessage = { viewModel.sendNutritionChatMessage(it) },
                        onClearChat = { viewModel.clearNutritionChat() },
                        onLogRecommendedFood = { meal, cal, pro ->
                            viewModel.logFoodIntake(meal, cal, pro)
                        },
                        onApplyDietPlan = { msgId, goal, title, cal, pro, water ->
                            viewModel.applyRecommendedDietPlan(msgId, goal, title, cal, pro, water)
                        }
                    )
                    3 -> ClassHierarchyScreen(
                        state = state,
                        onAvatarClick = { selectedTab = 4 },
                        onOpenCoachTip = { viewModel.reopenPikaCoachPopup() },
                        onOpenSettings = {
                            settingsFocusSection = 4
                            showSettingsScreen = true
                        }
                    )
                    4 -> ProfileAndJournalTab(
                        state = state,
                        journalState = journalState,
                        onSaveBiometrics = { name, title, w, h, a, sex, avatar ->
                            viewModel.updateBiometrics(name, title, w, h, a, sex, avatar)
                        },
                        onSaveBiometricsWithGoal = { name, title, w, h, a, sex, avatar, targetW, physique ->
                            viewModel.updateBiometricsWithGoal(name, title, w, h, a, sex, avatar, targetW, physique)
                        },
                        onLogProgressPhoto = { title, cap, pose, b64, setAv ->
                            viewModel.logProgressPhoto(title, cap, pose, b64, setAv)
                        },
                        onDeleteJournalEntry = { viewModel.deleteJournalEntry(it) },
                        onOpenAchievementsInSettings = { selectedTab = 3 },
                        onOpenSettingsHub = { section ->
                            settingsFocusSection = section
                            showSettingsScreen = true
                        },
                        onResetProgress = { clearJournal ->
                            viewModel.resetHunterProgress(clearJournal)
                        },
                        onSignOut = {
                            try {
                                FirebaseAuth.getInstance().signOut()
                            } catch (_: Exception) {}
                            onSignedOut()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AiSyncStatusBanner(
    aiSyncState: AiSyncState,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(visible = aiSyncState !is AiSyncState.Idle) {
        val (borderColor, text) = when (aiSyncState) {
            is AiSyncState.Analyzing -> PikaYellow to "Analyzing: ${aiSyncState.command}..."
            is AiSyncState.Success -> HunterEmerald to aiSyncState.message
            is AiSyncState.Error -> NeonCoral to aiSyncState.message
            AiSyncState.Idle -> BorderSubtle to ""
        }
        Surface(
            color = ObsidianCardElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = borderColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDashboardContent(
    state: HunterSystemState,
    foodHistory: List<FoodEntryRecord>,
    waterHistory: List<WaterEntryRecord>,
    onLogExerciseSet: (exerciseName: String, sets: Int, reps: String, overload: Boolean) -> Unit,
    onLogFoodItem: (mealName: String, calories: Int, proteinG: Double, carbsG: Double, fatsG: Double, fiberG: Double, grams: Int) -> Unit,
    onQuickWater: (Double) -> Unit,
    onUndoFood: () -> Unit,
    onUndoWater: () -> Unit,
    onToggleQuest: (Int) -> Unit,
    onSubmitTacticalCommand: (String) -> Unit,
    onOpenPikaBot: () -> Unit,
    onAvatarClick: () -> Unit,
    onOpenCoachTip: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val bio = state.biometrics
    val ps = state.playerStatus
    val currentTier = ps.currentTier
    val vit = state.statBreakdown.vitality
    val stam = state.statBreakdown.stamina
    val str = state.statBreakdown.strength
    val displayName = bio.hunterName.ifBlank { "Trainer" }

    // 100% Real Workout Burn & Diet Summary (0 kcal and 0 sets until user logs exercises!)
    val workoutSummary = remember(str.exercisesLogged, bio, vit.caloriesLogged, vit.caloriesTarget, vit.proteinG) {
        SmartFoodNutritionEngine.calculateWorkoutBurnAndDietPlan(
            exercises = str.exercisesLogged,
            bio = bio,
            caloriesLogged = vit.caloriesLogged,
            caloriesTarget = vit.caloriesTarget,
            proteinLogged = vit.proteinG
        )
    }

    // 100% Real Energy, Diet, and Hydration percentages (starts at 0% when nothing is logged!)
    val caloriesLogged = vit.caloriesLogged.coerceAtLeast(0)
    val workoutKcal = workoutSummary.totalWorkoutBurnKcal.coerceAtLeast(0)
    val dietProgress = (caloriesLogged.toFloat() / vit.caloriesTarget.coerceAtLeast(1)).coerceIn(0f, 1f)
    val workoutProgress = (workoutSummary.totalSetsCompleted.toFloat() / 12f).coerceIn(0f, 1f)
    val waterProgress = (stam.waterLiters.toFloat() / stam.targetLiters.toFloat().coerceAtLeast(0.5f)).coerceIn(0f, 1f)
    val proteinProgress = (vit.proteinG.toFloat() / bio.recommendedProteinG.coerceAtLeast(1)).coerceIn(0f, 1f)

    val totalEnergyPct = (((dietProgress * 0.5f) + (waterProgress * 0.25f) + (workoutProgress * 0.25f)) * 100f)
        .roundToInt()
        .coerceIn(0, 100)

    // Interactive States for Smart Food Logger, Exercise Logger & Tactical AI Command
    var showSmartFoodForm by rememberSaveable { mutableStateOf(false) }
    var foodQuery by rememberSaveable { mutableStateOf("") }
    var foodAmountText by rememberSaveable { mutableStateOf("150") }
    var isQuantityMode by rememberSaveable { mutableStateOf(false) }

    var showExerciseModal by rememberSaveable { mutableStateOf(false) }
    var showRecoveryMeals by rememberSaveable { mutableStateOf(false) }
    var exerciseNameInput by rememberSaveable { mutableStateOf("") }
    var exerciseSetsInput by rememberSaveable { mutableStateOf("3") }
    var exerciseRepsInput by rememberSaveable { mutableStateOf("12") }
    var exerciseOverloadInput by rememberSaveable { mutableStateOf(true) }

    var tacticalCmdInput by rememberSaveable { mutableStateOf("") }

    val smartFoodCalc = remember(foodQuery, foodAmountText, isQuantityMode) {
        val q = foodQuery.trim().ifBlank { "Idli" }
        val amt = foodAmountText.toIntOrNull()?.coerceIn(1, 3000) ?: if (isQuantityMode) 3 else 150
        SmartFoodNutritionEngine.calculateForGramsOrQuantity(
            foodInput = q,
            amountValue = amt,
            isQuantityMode = isQuantityMode
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_dashboard_list"),
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
                showPikachuAvatar = true,
                onAvatarClick = onAvatarClick,
                onOpenCoachTip = onOpenCoachTip,
                onOpenSettings = onOpenSettings
            )
        }

        // 1. TOTAL ENERGY & DAILY TRIPLE HERO CARD (100% Real Values in Obsidian/NeonAmber Theme)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                border = BorderStroke(1.dp, BorderAmberGlow)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurfaceGradient)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Large Total Energy Ring
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1.15f)
                    ) {
                        Text(
                            text = "TOTAL ENERGY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(118.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val stroke = 11.dp.toPx()
                                drawArc(
                                    color = BorderSubtle,
                                    startAngle = 135f,
                                    sweepAngle = 270f,
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        listOf(NeonCoral, NeonOrange, PikaYellow, NeonCoral)
                                    ),
                                    startAngle = 135f,
                                    sweepAngle = 270f * (totalEnergyPct / 100f),
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = NeonAmber,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "$totalEnergyPct%",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${ps.currentXp} XP",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                    color = PikaYellow
                                )
                            }
                        }
                    }

                    // Center: Workouts Burn & Diet Intake Sub-Rings (Real Values)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(0.95f)
                    ) {
                        Text(
                            text = "WORKOUTS",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextSecondary
                        )
                        MiniMetricRing(
                            valueText = "$workoutKcal",
                            unitText = "kcal",
                            progress = workoutProgress,
                            ringColor = NeonCoral
                        )
                        MiniMetricRing(
                            valueText = "$caloriesLogged",
                            unitText = "kcal",
                            topLabel = "DIET",
                            progress = dietProgress,
                            ringColor = NeonAmber
                        )
                    }

                    // Right: Daily Triple Column (Real Protein, Water, Workout Sets)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "DAILY TRIPLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            ),
                            color = TextSecondary
                        )
                        DailyTripleStatItem(
                            label = "PROTEIN (G)",
                            current = "${vit.proteinG.toInt()}",
                            target = "${bio.recommendedProteinG}",
                            progress = proteinProgress
                        )
                        DailyTripleStatItem(
                            label = "WATER (L)",
                            current = "${stam.waterLiters}",
                            target = "${stam.targetLiters}",
                            progress = waterProgress
                        )
                        DailyTripleStatItem(
                            label = "WORKOUT SETS",
                            current = "${workoutSummary.totalSetsCompleted}",
                            target = "12",
                            progress = workoutProgress
                        )
                    }
                }
            }
        }

        // 2. SMART NUTRITION, HYDRATION & UNDO COMMAND CARD
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurfaceGradient)
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Restaurant,
                                contentDescription = null,
                                tint = NeonAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "FUEL & HYDRATION TRACKER",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.7.sp
                                    ),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "$caloriesLogged / ${vit.caloriesTarget} kcal • P: ${vit.proteinG.toInt()}g • C: ${vit.carbsG.toInt()}g • F: ${vit.fatsG.toInt()}g",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Surface(
                            onClick = { showSmartFoodForm = !showSmartFoodForm },
                            color = NeonAmber.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, NeonAmber)
                        ) {
                            Text(
                                text = if (showSmartFoodForm) "CLOSE" else "+ LOG FOOD",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = NeonAmber,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Water & PikaBot Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onQuickWater(0.25) },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, HunterCyan.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+250ml 💧", color = HunterCyan, style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { onQuickWater(0.5) },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, HunterCyan.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+500ml 💧", color = HunterCyan, style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onOpenPikaBot,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, PikaYellow.copy(alpha = 0.7f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("Ask PikaBot ⚡", color = PikaYellow, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Undo Row (when food or water has been logged)
                    if (caloriesLogged > 0 || stam.waterLiters > 0.0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (caloriesLogged > 0) {
                                TextButton(onClick = onUndoFood) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Undo Food",
                                        tint = NeonCoral,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Undo Food (${foodHistory.lastOrNull()?.mealName ?: "Last Meal"})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCoral
                                    )
                                }
                            }
                            if (stam.waterLiters > 0.0) {
                                TextButton(onClick = onUndoWater) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Undo Water",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Undo Water (${waterHistory.lastOrNull()?.litersDelta ?: 0.25}L)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Expandable Smart Grams / Pieces Food Logger
                    AnimatedVisibility(visible = showSmartFoodForm) {
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderAmberGlow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SMART GRAMS / PIECES CONVERTER",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = PikaYellow
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilterChip(
                                            selected = !isQuantityMode,
                                            onClick = {
                                                isQuantityMode = false
                                                foodAmountText = "150"
                                            },
                                            label = { Text("Grams (g)", fontSize = 10.sp) }
                                        )
                                        FilterChip(
                                            selected = isQuantityMode,
                                            onClick = {
                                                isQuantityMode = true
                                                foodAmountText = "3"
                                            },
                                            label = { Text("Pieces", fontSize = 10.sp) }
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = foodQuery,
                                        onValueChange = { foodQuery = it },
                                        label = { Text("Indian / Global Food") },
                                        placeholder = { Text("Idli, Dosa, Chicken Gravy, Eggs...") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.4f)
                                    )
                                    OutlinedTextField(
                                        value = foodAmountText,
                                        onValueChange = { foodAmountText = it.filter { ch -> ch.isDigit() } },
                                        label = { Text(if (isQuantityMode) "Qty" else "Grams") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(0.7f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick Food Presets
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        Triple("Idli", "4", true),
                                        Triple("Masala Dosa", "2", true),
                                        Triple("Chicken Gravy", "180", false),
                                        Triple("Boiled Eggs", "3", true),
                                        Triple("Paneer Bhurji", "150", false),
                                        Triple("Poha", "150", false)
                                    ).forEach { (name, amt, qtyMode) ->
                                        AssistChip(
                                            onClick = {
                                                foodQuery = name
                                                foodAmountText = amt
                                                isQuantityMode = qtyMode
                                            },
                                            label = {
                                                Text(
                                                    text = if (qtyMode) "$amt $name" else "${amt}g $name",
                                                    fontSize = 10.5.sp,
                                                    color = TextPrimary
                                                )
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${smartFoodCalc.foodName} [${smartFoodCalc.servingLabel}]: ${smartFoodCalc.calories} kcal • P: ${smartFoodCalc.proteinG}g • C: ${smartFoodCalc.carbsG}g • F: ${smartFoodCalc.fatsG}g",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = NeonAmber
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        onLogFoodItem(
                                            "${smartFoodCalc.foodName} (${smartFoodCalc.servingLabel})",
                                            smartFoodCalc.calories,
                                            smartFoodCalc.proteinG,
                                            smartFoodCalc.carbsG,
                                            smartFoodCalc.fatsG,
                                            smartFoodCalc.fiberG,
                                            smartFoodCalc.grams
                                        )
                                        foodQuery = ""
                                        showSmartFoodForm = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonAmber,
                                        contentColor = ObsidianBg
                                    ),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "LOG ${smartFoodCalc.calories} KCAL & CHECK IN (+45 XP)",
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. WORKOUT OVERLOAD & EXERCISE CALORIE BURN CARD (100% Real Exercises Logged)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurfaceGradient)
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.FitnessCenter,
                                contentDescription = null,
                                tint = NeonAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "WORKOUT OVERLOAD",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = TextSecondary
                            )
                        }

                        if (workoutSummary.totalWorkoutBurnKcal > 0) {
                            TextButton(onClick = { showRecoveryMeals = !showRecoveryMeals }) {
                                Text(
                                    text = if (showRecoveryMeals) "Hide Recovery Plan" else "Recovery Meals ⚡",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PikaYellow
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left Sub-Card: Real Sets & Burn Telemetry
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.weight(1.05f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Iron & Hypertrophy",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "$workoutKcal kcal burned",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCoral
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(BorderSubtle)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(workoutProgress)
                                            .fillMaxHeight()
                                            .background(PikaYellow)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(BorderSubtle)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(dietProgress)
                                            .fillMaxHeight()
                                            .background(NeonOrange)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MiniSparkline(modifier = Modifier.size(40.dp, 18.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${workoutSummary.totalSetsCompleted}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "SETS",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = TextSecondary
                                        )
                                    }
                                    MiniSparkline(modifier = Modifier.size(40.dp, 18.dp))
                                }
                            }
                        }

                        // Right Column: Real Logged Exercises + LOG SET Button
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (str.exercisesLogged.isEmpty()) {
                                Text(
                                    text = "No sets logged today yet. Tap LOG SET or a quick lift chip below!",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = TextSecondary
                                )
                            } else {
                                str.exercisesLogged.takeLast(4).forEachIndexed { idx, ex ->
                                    OverloadExerciseCheckRow(
                                        name = "${ex.exerciseName} (${ex.setsCompleted}x${ex.repsPerSet})",
                                        checked = ex.progressiveOverloadHit,
                                        showSpark = idx == 0,
                                        onToggle = {}
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(AmberCrimsonGradient)
                                    .clickable { showExerciseModal = !showExerciseModal }
                                    .testTag("open_log_set_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (showExerciseModal) "CLOSE LOGGER" else "+ LOG SET",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.6.sp
                                    ),
                                    color = ObsidianBg
                                )
                            }
                        }
                    }

                    // Quick 1-Tap Compound Lift Chips
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("Squats", 4, "10"),
                            Triple("Declined Pushups", 3, "12"),
                            Triple("Lateral Raise", 3, "15"),
                            Triple("Pull-Ups", 3, "8"),
                            Triple("Bench Press", 4, "8"),
                            Triple("Deadlifts", 3, "6")
                        ).forEach { (liftName, sets, reps) ->
                            Surface(
                                onClick = { onLogExerciseSet(liftName, sets, reps, true) },
                                color = ObsidianBg,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Text(
                                    text = "+ $liftName (${sets}x$reps)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // Expandable Custom Exercise Set Logger
                    AnimatedVisibility(visible = showExerciseModal) {
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, NeonAmber),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "LOG PROGRESSIVE OVERLOAD SET (+75 XP)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = NeonAmber
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = exerciseNameInput,
                                    onValueChange = { exerciseNameInput = it },
                                    label = { Text("Exercise Name") },
                                    placeholder = { Text("e.g., Incline Dumbbell Press, Barbell Squat") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = exerciseSetsInput,
                                        onValueChange = { exerciseSetsInput = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("Sets") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = exerciseRepsInput,
                                        onValueChange = { exerciseRepsInput = it },
                                        label = { Text("Reps / Weight") },
                                        placeholder = { Text("12 reps") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = exerciseOverloadInput,
                                        onCheckedChange = { exerciseOverloadInput = it }
                                    )
                                    Text(
                                        text = "Progressive Overload Hit (+1 rep or +2.5kg)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary
                                    )
                                }
                                Button(
                                    onClick = {
                                        val s = exerciseSetsInput.toIntOrNull()?.coerceIn(1, 20) ?: 3
                                        onLogExerciseSet(
                                            exerciseNameInput.ifBlank { "Hypertrophy Lift" },
                                            s,
                                            exerciseRepsInput.ifBlank { "10" },
                                            exerciseOverloadInput
                                        )
                                        exerciseNameInput = ""
                                        showExerciseModal = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonAmber,
                                        contentColor = ObsidianBg
                                    ),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("RECORD WORKOUT SET", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    // Expandable Post-Workout Calorie Burn & Anabolic Recovery Meal Blueprint
                    AnimatedVisibility(visible = showRecoveryMeals && workoutSummary.totalWorkoutBurnKcal > 0) {
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, PikaYellow.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = workoutSummary.goalModeTitle,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = PikaYellow
                                )
                                Text(
                                    text = workoutSummary.surplusOrDeficitAdvice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                workoutSummary.recommendedMeals.forEach { rec ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = rec.mealName,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${rec.calories} kcal • ${rec.proteinG}g Protein",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NeonAmber
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onLogFoodItem(
                                                    rec.mealName,
                                                    rec.calories,
                                                    rec.proteinG,
                                                    rec.carbsG,
                                                    rec.fatsG,
                                                    3.0,
                                                    200
                                                )
                                            },
                                            shape = RoundedCornerShape(50),
                                            border = BorderStroke(1.dp, NeonAmber)
                                        ) {
                                            Text("LOG", color = NeonAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. DAILY HUNTER QUESTS & TACTICAL AI COMMAND CARD (Replaces fake newsletter tasks with real Quests & AI Command)
        item {
            val quests = state.questLog
            val completedCount = quests.count { it.status == "COMPLETED" }
            val totalQuests = quests.size.coerceAtLeast(1)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurfaceGradient)
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircleOutline,
                                contentDescription = null,
                                tint = NeonAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DAILY HUNTER QUESTS",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "$completedCount/${quests.size} Completed",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NeonAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        quests.forEachIndexed { index, quest ->
                            val isDone = quest.status == "COMPLETED"
                            ProductivityTaskTile(
                                title = "${quest.questName} (+${quest.xpAwarded} XP)",
                                checked = isDone,
                                highlighted = !isDone && index == 0,
                                onClick = { onToggleQuest(index) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quest Completion Dots Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Quests Cleared",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (i in 1..quests.size.coerceAtLeast(3)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (i <= completedCount) NeonAmber else BorderSubtle)
                                )
                            }
                        }
                        Text(
                            text = "$completedCount/$totalQuests",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BorderSubtle)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Tactical Briefing + Natural Language AI Command Input
                    Text(
                        text = "TACTICAL BRIEFING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.7.sp
                        ),
                        color = PikaYellow
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.tacticalBriefing,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tacticalCmdInput,
                            onValueChange = { tacticalCmdInput = it },
                            placeholder = {
                                Text(
                                    text = "AI Command: e.g., Did 3x12 pushups & ate 4 eggs...",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tactical_command_input")
                        )
                        IconButton(
                            onClick = {
                                if (tacticalCmdInput.isNotBlank()) {
                                    onSubmitTacticalCommand(tacticalCmdInput)
                                    tacticalCmdInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(AmberCrimsonGradient)
                                .testTag("tactical_command_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Execute AI Command",
                                tint = ObsidianBg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMetricRing(
    valueText: String,
    unitText: String,
    topLabel: String? = null,
    progress: Float,
    ringColor: Color
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(66.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            drawCircle(
                color = BorderSubtle,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (topLabel != null) {
                Text(
                    text = topLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = TextSecondary
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                ),
                color = TextPrimary
            )
            Text(
                text = unitText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun DailyTripleStatItem(
    label: String,
    current: String,
    target: String,
    progress: Float
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = TextSecondary
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = current,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                ),
                color = TextPrimary
            )
            Text(
                text = "/$target",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BorderSubtle)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(NeonAmber)
            )
        }
    }
}

@Composable
private fun OverloadExerciseCheckRow(
    name: String,
    checked: Boolean,
    showSpark: Boolean = false,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (checked) NeonAmber else Color.Transparent)
                    .border(1.5.dp, if (checked) NeonAmber else TextSecondary, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = ObsidianBg,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showSpark) {
            MiniSparkline(modifier = Modifier.size(28.dp, 12.dp))
        }
    }
}

@Composable
private fun MiniSparkline(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.8f)
            lineTo(w * 0.3f, h * 0.5f)
            lineTo(w * 0.6f, h * 0.65f)
            lineTo(w, h * 0.15f)
        }
        drawPath(
            path = path,
            color = NeonOrange,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun ProductivityTaskTile(
    title: String,
    checked: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = ObsidianBg.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (highlighted) 1.5.dp else 1.dp,
            color = if (checked) HunterEmerald.copy(alpha = 0.6f) else if (highlighted) NeonAmber else BorderSubtle
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (checked) NeonAmber else Color.Transparent)
                    .border(1.5.dp, if (checked) NeonAmber else BorderSubtle, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = ObsidianBg,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp,
                    textDecoration = if (checked) TextDecoration.LineThrough else null
                ),
                color = if (checked) TextSecondary else TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PikaFitBottomBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val items = listOf(
        Triple(0, "Home", Icons.Filled.Home),
        Triple(1, "Analytics", Icons.Filled.Equalizer),
        Triple(2, "PikaBot", Icons.Filled.Bolt),
        Triple(3, "Ranks", Icons.Filled.EmojiEvents),
        Triple(4, "Profile", Icons.Filled.PersonOutline)
    )

    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (index, label, icon) ->
                val selected = selectedTab == index
                val tint = if (selected) NeonAmber else TextSecondary
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onSelectTab(index) }
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = tint
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (selected) NeonAmber else Color.Transparent)
                    )
                }
            }
        }
    }
}