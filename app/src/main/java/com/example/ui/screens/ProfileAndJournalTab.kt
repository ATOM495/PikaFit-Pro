package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClassHierarchyScreen(
    state: HunterSystemState,
    onAvatarClick: () -> Unit = {},
    onOpenCoachTip: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val bio = state.biometrics
    val ps = state.playerStatus
    val currentTier = ps.currentTier
    val displayName = bio.hunterName.ifBlank { "Trainer" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("class_hierarchy_screen"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            TopTrainerHeader(
                trainerName = displayName,
                rankSubtitle = "${currentTier.classTitle} • ${currentTier.pokemonName}",
                streakDays = ps.streakDays,
                level = ps.level,
                avatarBase64 = bio.avatarBase64,
                pokemonDrawableRes = currentTier.pokemonDrawableRes,
                onAvatarClick = onAvatarClick,
                onOpenCoachTip = onOpenCoachTip,
                onOpenSettings = onOpenSettings
            )
        }

        item {
            PokemonClassHierarchyAndAchievementsCard(
                playerStatus = ps,
                unlockedAchievements = state.unlockedAchievements
            )
        }
    }
}

@Composable
internal fun PokemonClassHierarchyAndAchievementsCard(
    playerStatus: PlayerStatus,
    unlockedAchievements: List<AchievementItem>
) {
    val currentTier = playerStatus.currentTier
    // 100% Real XP and Progress (starts at 0 XP / 0% until user logs real activity)
    val displayXp = playerStatus.currentXp.coerceAtLeast(0)
    val progressPct = playerStatus.progressPercent.toInt().coerceIn(0, 100)
    val nextThreshold = currentTier.maxXp ?: (displayXp + 2500)
    val xpToNext = (nextThreshold - displayXp).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .testTag("pokemon_class_hierarchy_card")
    ) {
        Text(
            text = "POKÉMON CLASS HIERARCHY",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 0.6.sp
            ),
            color = TextPrimary
        )
        Text(
            text = "Auto-assigned by XP • Daily completion & streaks award XP!",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Active Class Tier Showcase Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            border = BorderStroke(1.dp, BorderAmberGlow),
            modifier = Modifier.fillMaxWidth()
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
                        text = "${currentTier.classTitle} • ${currentTier.pokemonName}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        ),
                        color = TextPrimary
                    )
                    Surface(
                        color = NeonAmber.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "${currentTier.xpMultiplier}x XP Pace",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = NeonAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = TextSecondary)) { append("TOTAL XP: ") }
                            withStyle(SpanStyle(color = NeonAmber, fontWeight = FontWeight.ExtraBold)) {
                                append("$displayXp XP ")
                            }
                            withStyle(SpanStyle(color = TextSecondary)) { append("(${currentTier.xpRangeLabel})") }
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "TIER ${currentTier.tierLevel}/7",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = currentTier.pokemonDrawableRes),
                        contentDescription = currentTier.pokemonName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ObsidianBg)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((progressPct / 100f).coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(AmberCrimsonGradient)
                            )
                            Text(
                                text = "$progressPct%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = TextPrimary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (currentTier.maxXp != null) {
                                "$xpToNext XP TO PROMOTION"
                            } else {
                                "MYTHIC CHAMPION TIER UNLOCKED"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = NeonAmber,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextSecondary)) { append("Reward: ") }
                        withStyle(SpanStyle(color = PikaYellow, fontWeight = FontWeight.Bold)) {
                            append(currentTier.rewardTitle)
                        }
                        withStyle(SpanStyle(color = TextSecondary)) { append(" • Daily Check-In: ") }
                        withStyle(SpanStyle(color = NeonAmber, fontWeight = FontWeight.Bold)) {
                            append("+${currentTier.dailyCompletionXp} XP")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "7-TIER PROGRESSION ROADMAP",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp
            ),
            color = NeonAmber
        )

        Spacer(modifier = Modifier.height(10.dp))

        TrainerRankProgression.tiers.forEach { tier ->
            val isCurrent = tier.tierLevel == currentTier.tierLevel
            val isUnlocked = displayXp >= tier.minXp
            RoadmapTimelineRow(
                tier = tier,
                isCurrent = isCurrent,
                isUnlocked = isUnlocked
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Achievements & Rewards Vault Card (Real Unlocked Achievements)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ACHIEVEMENTS & REWARDS VAULT (${unlockedAchievements.size} UNLOCKED)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (unlockedAchievements.isEmpty()) {
                    Text(
                        text = "Log your first meal, workout set, or progress photo to unlock Hunter badges!",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        unlockedAchievements.forEach { ach ->
                            val badgeColor = when (ach.rarity.uppercase()) {
                                "LEGENDARY" -> NeonCoral
                                "EPIC" -> HunterViolet
                                "RARE" -> HunterCyan
                                else -> PikaYellow
                            }
                            VaultBadgeTile(
                                title = ach.title,
                                subtitle = ach.description,
                                rarity = ach.rarity,
                                borderColor = badgeColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoadmapTimelineRow(
    tier: TrainerRankTier,
    isCurrent: Boolean,
    isUnlocked: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "TIER ${tier.tierLevel}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp
            ),
            color = if (isCurrent) NeonAmber else TextSecondary,
            modifier = Modifier.width(46.dp)
        )

        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isCurrent) NeonAmber else if (isUnlocked) HunterEmerald else BorderSubtle)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Surface(
            color = if (isCurrent) Color(0xFF231D14) else ObsidianCard,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isCurrent) NeonAmber.copy(alpha = 0.6f) else BorderSubtle
            ),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${tier.classTitle} • ${tier.pokemonName}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = TextSecondary)) {
                                append("(${tier.xpRangeLabel}) • ")
                            }
                            withStyle(
                                SpanStyle(
                                    color = if (isUnlocked) HunterEmerald else TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) {
                                append(if (isUnlocked) "Unlocked" else "Locked")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = tier.pokemonDrawableRes),
                        contentDescription = tier.pokemonName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (isCurrent) {
                        Surface(
                            color = NeonAmber,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "CURRENT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp
                                ),
                                color = ObsidianBg,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            Icon(
                                imageVector = if (isUnlocked) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                                contentDescription = if (isUnlocked) "Unlocked" else "Locked",
                                tint = if (isUnlocked) HunterEmerald else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${tier.xpMultiplier}x Pace",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultBadgeTile(
    title: String,
    subtitle: String,
    rarity: String,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.65f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(borderColor.copy(alpha = 0.2f))
                        .border(1.dp, borderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = borderColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = rarity,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = borderColor
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    ),
                    color = TextSecondary,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun ProfileAndJournalTab(
    state: HunterSystemState,
    journalState: DataLoadState<List<JournalEntry>>,
    onSaveBiometrics: (
        hunterName: String,
        hunterTitle: String,
        weightKg: Double,
        heightCm: Double,
        age: Int,
        biologicalSex: String,
        avatarBase64: String?
    ) -> Unit,
    onSaveBiometricsWithGoal: (
        hunterName: String,
        hunterTitle: String,
        weightKg: Double,
        heightCm: Double,
        age: Int,
        biologicalSex: String,
        avatarBase64: String?,
        targetWeightKg: Double,
        targetPhysique: String
    ) -> Unit = { name, title, w, h, a, sex, avatar, _, _ ->
        onSaveBiometrics(name, title, w, h, a, sex, avatar)
    },
    onLogProgressPhoto: (
        title: String,
        caption: String,
        poseTag: String,
        photoBase64: String,
        setAsAvatar: Boolean
    ) -> Unit,
    onDeleteJournalEntry: (String) -> Unit,
    onOpenAchievementsInSettings: () -> Unit = {},
    onOpenSettingsHub: (Int) -> Unit = {},
    onResetProgress: (Boolean) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedBio = state.biometrics
    val ps = state.playerStatus
    val currentTier = ps.currentTier

    // Empty-by-default inputs (showing saved values only as placeholders so no pre-filled text clutters inputs)
    var hunterName by rememberSaveable { mutableStateOf("") }
    var weightInput by rememberSaveable { mutableStateOf("") }
    var targetWeightInput by rememberSaveable { mutableStateOf("") }
    var heightInput by rememberSaveable { mutableStateOf("") }
    var ageInput by rememberSaveable { mutableStateOf("") }
    var selectedSex by rememberSaveable(savedBio.biologicalSex) { mutableStateOf(savedBio.biologicalSex) }
    var selectedPhysique by rememberSaveable(savedBio.targetPhysique) { mutableStateOf(savedBio.targetPhysique) }
    var avatarBase64Draft by remember(savedBio.avatarBase64) { mutableStateOf(savedBio.avatarBase64) }

    // Personal Photo Journal State
    var showPhotoUploadForm by rememberSaveable { mutableStateOf(false) }
    var photoTitle by rememberSaveable { mutableStateOf("") }
    var photoCaption by rememberSaveable { mutableStateOf("") }
    var selectedPoseTag by rememberSaveable { mutableStateOf("Front Relaxed") }
    var setPhotoAsAvatar by rememberSaveable { mutableStateOf(false) }
    var capturedPhotoBase64 by rememberSaveable { mutableStateOf("") }
    var showResetConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val encoded = encodeUriToBase64(context, uri)
                if (encoded.isNotBlank()) {
                    avatarBase64Draft = encoded
                }
            }
        }
    }

    val journalPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val encoded = encodeUriToBase64(context, uri)
                if (encoded.isNotBlank()) {
                    capturedPhotoBase64 = encoded
                    showPhotoUploadForm = true
                }
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = ObsidianCard,
            title = {
                Text("Reset Hunter Progress?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This resets your XP, Level, Streak, and today's food/water/workout logs to 0 (Beginner • Pikachu) and launches Trainer Onboarding.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetProgress(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCoral, contentColor = Color.White)
                ) {
                    Text("Reset Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    val journalEntries = (journalState as? DataLoadState.Success)?.data.orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("profile_journal_list"),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TopTrainerHeader(
                trainerName = savedBio.hunterName.ifBlank { "Trainer" },
                rankSubtitle = "${currentTier.classTitle} • ${currentTier.pokemonName}",
                streakDays = ps.streakDays,
                level = ps.level,
                avatarBase64 = avatarBase64Draft,
                pokemonDrawableRes = currentTier.pokemonDrawableRes,
                onAvatarClick = {
                    avatarPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onOpenSettings = { onOpenSettingsHub(0) }
            )
        }

        // Quick Settings, Feedback Board, Onboarding & Sign-Out Hub Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionChip(
                    icon = Icons.Filled.Settings,
                    label = "Full Settings Hub",
                    color = NeonAmber,
                    onClick = { onOpenSettingsHub(0) }
                )
                QuickActionChip(
                    icon = Icons.Filled.Feedback,
                    label = "Creator Feedback Board",
                    color = PikaYellow,
                    onClick = { onOpenSettingsHub(2) }
                )
                QuickActionChip(
                    icon = Icons.Filled.EmojiEvents,
                    label = "Class & Wallpapers",
                    color = HunterCyan,
                    onClick = onOpenAchievementsInSettings
                )
                QuickActionChip(
                    icon = Icons.Filled.RestartAlt,
                    label = "Reset Progress",
                    color = NeonCoral,
                    onClick = { showResetConfirmDialog = true }
                )
                QuickActionChip(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = "Sign Out",
                    color = TextSecondary,
                    onClick = onSignOut
                )
            }
        }

        // 1. TRAINER BIOMETRIC & GOAL CALIBRATION CARD
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                border = BorderStroke(1.dp, BorderAmberGlow),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
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
                                text = "TRAINER BIOMETRIC CALIBRATION",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = NeonAmber
                            )
                            Text(
                                text = "Current: ${savedBio.weightKg}kg • ${savedBio.heightCm.toInt()}cm • Age ${savedBio.age} (${savedBio.biologicalSex}) • BMI ${savedBio.bmi}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Surface(
                            onClick = {
                                avatarPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            color = NeonAmber.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, NeonAmber)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddAPhoto,
                                    contentDescription = "Change Avatar",
                                    tint = NeonAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "AVATAR",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = NeonAmber
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = hunterName,
                        onValueChange = { hunterName = it },
                        label = { Text("Trainer Name") },
                        placeholder = { Text(savedBio.hunterName) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_hunter_name")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            label = { Text("Weight (kg)") },
                            placeholder = { Text("${savedBio.weightKg}") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_weight_kg")
                        )
                        OutlinedTextField(
                            value = targetWeightInput,
                            onValueChange = { targetWeightInput = it },
                            label = { Text("Target (kg)") },
                            placeholder = { Text("${savedBio.targetWeightKg}") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_target_weight_kg")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { heightInput = it },
                            label = { Text("Height (cm)") },
                            placeholder = { Text("${savedBio.heightCm.toInt()}") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_height_cm")
                        )
                        OutlinedTextField(
                            value = ageInput,
                            onValueChange = { ageInput = it },
                            label = { Text("Age") },
                            placeholder = { Text("${savedBio.age}") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_age_years")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Biological Sex Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("MALE" to "Male ♂", "FEMALE" to "Female ♀").forEach { (sexKey, label) ->
                            val isSelected = selectedSex.equals(sexKey, ignoreCase = true)
                            Surface(
                                onClick = { selectedSex = sexKey },
                                color = if (isSelected) NeonAmber.copy(alpha = 0.2f) else ObsidianBg,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, if (isSelected) NeonAmber else BorderSubtle),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) NeonAmber else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Target Physique Chips
                    Text(
                        text = "Target Physique Goal",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HunterBiometrics.PHYSIQUE_OPTIONS.forEach { option ->
                            val selected = selectedPhysique == option
                            Surface(
                                onClick = { selectedPhysique = option },
                                color = if (selected) PikaYellow.copy(alpha = 0.18f) else ObsidianBg,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, if (selected) PikaYellow else BorderSubtle)
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) PikaYellow else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onSaveBiometricsWithGoal(
                                hunterName.ifBlank { savedBio.hunterName },
                                savedBio.hunterTitle,
                                weightInput.toDoubleOrNull() ?: savedBio.weightKg,
                                heightInput.toDoubleOrNull() ?: savedBio.heightCm,
                                ageInput.toIntOrNull() ?: savedBio.age,
                                selectedSex,
                                avatarBase64Draft.ifBlank { null },
                                targetWeightInput.toDoubleOrNull() ?: savedBio.targetWeightKg,
                                selectedPhysique
                            )
                            hunterName = ""
                            weightInput = ""
                            targetWeightInput = ""
                            heightInput = ""
                            ageInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonAmber,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_biometrics_button")
                    ) {
                        Text("SAVE BIOMETRICS & GOAL (+50 XP)", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // 2. PERSONAL PROGRESS PHOTO & BIOMETRIC JOURNAL CARD
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
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
                                text = "PERSONAL PHYSIQUE & BIOMETRIC JOURNAL",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "${journalEntries.size} archived check-in${if (journalEntries.size == 1) "" else "s"} in Cloud Vault",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Button(
                            onClick = {
                                journalPhotoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PikaYellow,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddAPhoto,
                                contentDescription = "Upload Progress Photo",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ADD PHOTO",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }

                    AnimatedVisibility(visible = showPhotoUploadForm && capturedPhotoBase64.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            val previewBitmap = remember(capturedPhotoBase64) {
                                decodeBase64ToBitmap(capturedPhotoBase64)
                            }
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Selected Progress Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, NeonAmber, RoundedCornerShape(14.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            OutlinedTextField(
                                value = photoTitle,
                                onValueChange = { photoTitle = it },
                                label = { Text("Check-In Title") },
                                placeholder = { Text("e.g., Week 4 Upper Body Pump") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = photoCaption,
                                onValueChange = { photoCaption = it },
                                label = { Text("Notes / Measurements") },
                                placeholder = { Text("Feeling stronger on compound lifts!") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "Front Relaxed",
                                    "Front Double Biceps",
                                    "Side Chest",
                                    "Back Lat Spread",
                                    "Most Muscular",
                                    "Abdominal & Thigh"
                                ).forEach { pose ->
                                    val selected = selectedPoseTag == pose
                                    FilterChip(
                                        selected = selected,
                                        onClick = { selectedPoseTag = pose },
                                        label = { Text(pose, fontSize = 11.sp) }
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = setPhotoAsAvatar,
                                    onCheckedChange = { setPhotoAsAvatar = it }
                                )
                                Text(
                                    text = "Also set as my Trainer Profile Avatar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }

                            Button(
                                onClick = {
                                    onLogProgressPhoto(
                                        photoTitle.ifBlank { "Physique Check-In ($selectedPoseTag)" },
                                        photoCaption,
                                        selectedPoseTag,
                                        capturedPhotoBase64,
                                        setPhotoAsAvatar
                                    )
                                    photoTitle = ""
                                    photoCaption = ""
                                    capturedPhotoBase64 = ""
                                    showPhotoUploadForm = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonAmber,
                                    contentColor = ObsidianBg
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("SAVE PHOTO TO JOURNAL (+85 XP)", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    if (journalEntries.isEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No journal entries yet. Save your biometrics or upload a physique progress photo to build your timeline!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Chronological Journal Timeline Items
        items(journalEntries, key = { it.id.ifBlank { it.title + it.createdAt } }) { entry ->
            JournalEntryCard(
                entry = entry,
                onDelete = { if (entry.id.isNotBlank()) onDeleteJournalEntry(entry.id) }
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = ObsidianCard,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntry,
    onDelete: () -> Unit
) {
    val dateLabel = remember(entry.createdAt) {
        val dt = entry.createdAt?.toDate() ?: Date()
        SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.US).format(dt)
    }
    val photoBitmap = remember(entry.photoBase64) {
        decodeBase64ToBitmap(entry.photoBase64)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title.ifBlank { "Trainer Check-In" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "$dateLabel • ${entry.poseTag}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonAmber
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete Entry",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (photoBitmap != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    bitmap = photoBitmap.asImageBitmap(),
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Weight: ${entry.weightKg} kg • BMI: ${entry.bmi} • Body Fat: ${entry.bodyFatPercent}% • Muscle: ${entry.muscleMassPercent}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = HunterCyan
            )
            if (entry.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

private suspend fun encodeUriToBase64(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext ""
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext ""
        val maxDim = 260
        val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height, 1f)
        val w = (decoded.width * ratio).toInt().coerceAtLeast(1)
        val h = (decoded.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, w, h, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) {
        ""
    }
}

fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
    if (base64Str.isBlank()) return null
    return try {
        val bytes = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}