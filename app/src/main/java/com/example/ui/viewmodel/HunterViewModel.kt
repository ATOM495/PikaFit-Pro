package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AchievementItem
import com.example.data.model.AppReleaseInfo
import com.example.data.model.ExerciseLogged
import com.example.data.model.FeedbackEntry
import com.example.data.model.HunterBiometrics
import com.example.data.model.HunterSystemState
import com.example.data.model.JournalEntry
import com.example.data.model.QuestItem
import com.example.data.model.StreakDateUtils
import com.example.data.model.TacticalLog
import com.example.data.model.TrainerRankProgression
import com.example.data.repository.AiRepository
import com.example.data.repository.FirebaseAiRepository
import com.example.data.repository.HunterRepository
import com.example.util.SmartFoodNutritionEngine
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import kotlin.math.round

sealed interface AiSyncState {
    data object Idle : AiSyncState
    data class Analyzing(val command: String) : AiSyncState
    data class Success(val message: String) : AiSyncState
    data class Error(val message: String) : AiSyncState
}

sealed interface DataLoadState<out T> {
    data object Loading : DataLoadState<Nothing>
    data class Success<T>(val data: T) : DataLoadState<T>
    data class Error(val message: String) : DataLoadState<Nothing>
}

data class PikaCoachTip(
    val headline: String,
    val advice: String,
    val actionLabel: String,
    val actionType: String, // LOG_FOOD, LOG_WATER, LOG_WORKOUT, KEEP_GOING
    val coachMood: String   // ENERGIZED, ALERT_INACTIVE, PROUD, TACTICAL
)

sealed interface PikaCoachUiState {
    data object Idle : PikaCoachUiState
    data class Loading(val previewTip: PikaCoachTip) : PikaCoachUiState
    data class Ready(val tip: PikaCoachTip, val showPopup: Boolean) : PikaCoachUiState
    data class Error(val errorMessage: String, val fallbackTip: PikaCoachTip, val showPopup: Boolean) : PikaCoachUiState
}

data class NutritionChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "model"
    val text: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val suggestedMealName: String? = null,
    val suggestedCalories: Int? = null,
    val suggestedProteinG: Double? = null,
    val suggestedPlanGoalKey: String? = null,
    val suggestedPlanTitle: String? = null,
    val suggestedPlanCaloriesTarget: Int? = null,
    val suggestedPlanProteinG: Int? = null,
    val suggestedPlanWaterLiters: Double? = null,
    val planApplied: Boolean = false
) {
    val isUser: Boolean
        get() = role == "user"
}

data class FoodEntryRecord(
    val id: String = UUID.randomUUID().toString(),
    val mealName: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatsG: Double,
    val fiberG: Double = 0.0,
    val grams: Int = 100,
    val xpAwarded: Int,
    val triggeredDailyCheckIn: Boolean,
    val previousStreakDays: Int
)

data class WaterEntryRecord(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val litersDelta: Double,
    val xpAwarded: Int
)

class HunterViewModel(
    private val repository: HunterRepository,
    private val currentUserId: String,
    private val aiRepository: AiRepository = FirebaseAiRepository()
) : ViewModel() {

    private val _systemState = MutableStateFlow(HunterSystemState.createResetState())
    val systemState: StateFlow<HunterSystemState> = _systemState.asStateFlow()

    val sessionState: StateFlow<DataLoadState<HunterSystemState>> = _systemState
        .map<HunterSystemState, DataLoadState<HunterSystemState>> { DataLoadState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataLoadState.Success(_systemState.value)
        )

    private val _isSessionLoaded = MutableStateFlow(false)
    val isSessionLoaded: StateFlow<Boolean> = _isSessionLoaded.asStateFlow()

    private val _aiSyncState = MutableStateFlow<AiSyncState>(AiSyncState.Idle)
    val aiSyncState: StateFlow<AiSyncState> = _aiSyncState.asStateFlow()

    private val _pikaCoachState = MutableStateFlow<PikaCoachUiState>(PikaCoachUiState.Idle)
    val pikaCoachState: StateFlow<PikaCoachUiState> = _pikaCoachState.asStateFlow()

    private val _nutritionChatMessages = MutableStateFlow<List<NutritionChatMessage>>(emptyList())
    val nutritionChatMessages: StateFlow<List<NutritionChatMessage>> = _nutritionChatMessages.asStateFlow()

    private val _isNutritionChatLoading = MutableStateFlow(false)
    val isNutritionChatLoading: StateFlow<Boolean> = _isNutritionChatLoading.asStateFlow()

    private val _foodHistory = MutableStateFlow<List<FoodEntryRecord>>(emptyList())
    val foodHistory: StateFlow<List<FoodEntryRecord>> = _foodHistory.asStateFlow()

    private val _waterHistory = MutableStateFlow<List<WaterEntryRecord>>(emptyList())
    val waterHistory: StateFlow<List<WaterEntryRecord>> = _waterHistory.asStateFlow()

    private var hasSeededInitialSession = false
    private var hasAutoTriggeredCoachThisSession = false

    val logsState: StateFlow<DataLoadState<List<TacticalLog>>> = repository.observeLogs()
        .map<List<TacticalLog>, DataLoadState<List<TacticalLog>>> { DataLoadState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing tactical logs", error)
            emit(DataLoadState.Error(error.message ?: "Failed to load tactical logs"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataLoadState.Loading
        )

    val journalState: StateFlow<DataLoadState<List<JournalEntry>>> = repository.observeJournalEntries()
        .map<List<JournalEntry>, DataLoadState<List<JournalEntry>>> { DataLoadState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing personal journal entries", error)
            emit(DataLoadState.Error(error.message ?: "Failed to load personal journal"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataLoadState.Loading
        )

    val feedbackState: StateFlow<DataLoadState<List<FeedbackEntry>>> = repository.observePublicFeedback()
        .map<List<FeedbackEntry>, DataLoadState<List<FeedbackEntry>>> { DataLoadState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing public feedback", error)
            emit(DataLoadState.Error(error.message ?: "Failed to load public feedback feed"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataLoadState.Loading
        )

    val appReleaseInfo: StateFlow<AppReleaseInfo> = repository.observeAppReleaseInfo()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppReleaseInfo()
        )

    private val _dismissedUpdateVersion = MutableStateFlow<Long?>(null)
    val dismissedUpdateVersion: StateFlow<Long?> = _dismissedUpdateVersion.asStateFlow()

    fun dismissUpdatePrompt(versionCode: Long) {
        _dismissedUpdateVersion.value = versionCode
    }

    val isFirebaseAiEnabled: Boolean
        get() = aiRepository.isFirebaseAiEnabled

    val firebaseAiNotice: String
        get() = aiRepository.firebaseRequiredNotice

    init {
        observeRemoteSession()
    }

    private fun observeRemoteSession() {
        viewModelScope.launch {
            val (wasAlreadyOnboardedLocally, restoredBio) = withContext(Dispatchers.IO) {
                val onboarded = repository.isOnboardingCompletedForUser(currentUserId)
                val savedEmail = repository.getSavedAccountEmail()
                val preferredName = if (!savedEmail.isNullOrBlank() && savedEmail.contains("@")) {
                    savedEmail.substringBefore("@")
                } else null

                val bio = if (onboarded) {
                    val cachedStateJson = repository.getCachedStateJsonForUser(currentUserId)
                    val rawBio = if (!cachedStateJson.isNullOrBlank()) {
                        try {
                            val root = JSONObject(cachedStateJson)
                            HunterBiometrics.fromJsonObject(root.optJSONObject("biometrics")).copy(onboardingCompleted = true)
                        } catch (e: Exception) {
                            HunterBiometrics(onboardingCompleted = true)
                        }
                    } else {
                        HunterBiometrics(onboardingCompleted = true)
                    }
                    val finalName = if ((rawBio.hunterName == "Trainer" || rawBio.hunterName.isBlank()) && !preferredName.isNullOrBlank()) {
                        preferredName
                    } else {
                        rawBio.hunterName
                    }
                    rawBio.copy(hunterName = finalName)
                } else null
                Pair(onboarded, bio)
            }
            if (restoredBio != null) {
                _systemState.value = _systemState.value.copy(biometrics = restoredBio)
            }

            repository.observeActiveSession()
                .catch { e ->
                    Log.w(TAG, "Error observing active session", e)
                    _isSessionLoaded.value = true
                }
                .collect { session ->
                    if (session != null) {
                        val evaluated = withContext(Dispatchers.IO) {
                            val loaded = session.toHunterSystemState()
                            val savedEmail = repository.getSavedAccountEmail()
                            val preferredName = if (!savedEmail.isNullOrBlank() && savedEmail.contains("@")) {
                                savedEmail.substringBefore("@")
                            } else null

                            val effectiveBiometrics = if (loaded.biometrics.onboardingCompleted || session.stateJson.isNotBlank()) {
                                loaded.biometrics.copy(onboardingCompleted = true)
                            } else {
                                loaded.biometrics
                            }
                            val finalHunterName = if ((effectiveBiometrics.hunterName == "Trainer" || effectiveBiometrics.hunterName.isBlank()) && !preferredName.isNullOrBlank()) {
                                preferredName
                            } else {
                                effectiveBiometrics.hunterName
                            }
                            val eval = loaded.copy(biometrics = effectiveBiometrics.copy(hunterName = finalHunterName)).evaluateStreakOnLogin()
                            if (effectiveBiometrics.onboardingCompleted) {
                                repository.setOnboardingCompletedForUser(currentUserId, true, eval.toStateJson())
                            }
                            eval
                        }
                        _systemState.value = evaluated
                        hasSeededInitialSession = true
                        _isSessionLoaded.value = true
                        seedWelcomeNutritionChatIfEmpty(evaluated)
                        if (evaluated.biometrics.onboardingCompleted && !hasAutoTriggeredCoachThisSession) {
                            hasAutoTriggeredCoachThisSession = true
                            generatePikaCoachTip(showAsPopup = true)
                        }
                    } else if (!hasSeededInitialSession) {
                        hasSeededInitialSession = true
                        if (wasAlreadyOnboardedLocally) {
                            val current = _systemState.value
                            val ensured = current.copy(biometrics = current.biometrics.copy(onboardingCompleted = true))
                            _systemState.value = ensured
                            _isSessionLoaded.value = true
                            seedWelcomeNutritionChatIfEmpty(ensured)
                            if (!hasAutoTriggeredCoachThisSession) {
                                hasAutoTriggeredCoachThisSession = true
                                generatePikaCoachTip(showAsPopup = true)
                            }
                        } else {
                            // Brand new Google Account with no Firestore session and no local cache -> show "Hey Trainer" onboarding!
                            val initial = HunterSystemState.createResetState()
                            _systemState.value = initial
                            _isSessionLoaded.value = true
                        }
                    }
                }
        }
    }

    private var tipVariationCounter: Int = 0

    fun buildContextAwareFallbackTip(
        state: HunterSystemState,
        variationIndex: Int = tipVariationCounter
    ): PikaCoachTip {
        val bio = state.biometrics
        val vit = state.statBreakdown.vitality
        val stam = state.statBreakdown.stamina
        val str = state.statBreakdown.strength
        val ps = state.playerStatus
        val calRemaining = (vit.caloriesTarget - vit.caloriesLogged).coerceAtLeast(0)
        val proTarget = bio.recommendedProteinG
        val proRemaining = (proTarget - vit.proteinG.toInt()).coerceAtLeast(0)
        val waterLeft = ((stam.targetLiters - stam.waterLiters) * 10).toInt() / 10.0

        val slot = (variationIndex.coerceAtLeast(0)) % 6

        return when {
            !ps.checkedInToday && vit.caloriesLogged == 0 && str.exercisesLogged.isEmpty() -> {
                val inactivePool = listOf(
                    PikaCoachTip(
                        headline = "Pika-Alert! Zero Activity Logged Today ⚡",
                        advice = "Hey Trainer ${bio.hunterName}! You haven't logged a meal or workout set yet today. Opening PikaFit alone doesn't count toward your ${ps.streakDays}-day streak — log your first meal or set now to spark Day ${ps.streakDays + 1} toward your ${bio.goalDisplayLabel} goal!",
                        actionLabel = "Log +350 kcal Starter Meal",
                        actionType = "LOG_FOOD",
                        coachMood = "ALERT_INACTIVE"
                    ),
                    PikaCoachTip(
                        headline = "Anabolic Window Open, ${bio.hunterName}! 🍳",
                        advice = "After overnight fasting, muscle protein breakdown exceeds synthesis until your first leucine-rich meal. Kickstart your ${vit.caloriesTarget} kcal ${bio.goalDisplayLabel} blueprint with 30g+ protein (eggs, paneer, or Greek yogurt) right now!",
                        actionLabel = "Log Morning Protein Meal",
                        actionType = "LOG_FOOD",
                        coachMood = "ENERGIZED"
                    ),
                    PikaCoachTip(
                        headline = "Quick-Attack Workout Challenge! 💪",
                        advice = "Don't wait for motivation, ${bio.hunterName} — action creates momentum! Knock out 3 sets of Pushups, Lateral Raises, or Squats right now to lock in your Day ${ps.streakDays + 1} streak and trigger mTOR hypertrophy signaling.",
                        actionLabel = "Start 3-Set Progressive Lift",
                        actionType = "LOG_WORKOUT",
                        coachMood = "TACTICAL"
                    ),
                    PikaCoachTip(
                        headline = "Cellular Hydration Ignition (${stam.targetLiters}L Goal) 💧",
                        advice = "Even 2% dehydration drops strength output by 10% and slows nutrient transport. Drink 500ml water right now while planning your first meal to prime your muscles for ${bio.goalDisplayLabel}!",
                        actionLabel = "Drink +0.5L Water Now",
                        actionType = "LOG_WATER",
                        coachMood = "ENERGIZED"
                    ),
                    PikaCoachTip(
                        headline = "Protein Pacing Strategy: ${proTarget}g Target 🎯",
                        advice = "At ${bio.weightKg} kg (BMI ${bio.bmi}), hitting ${proTarget}g protein is effortless when split into 4 feedings of ~${(proTarget / 4).coerceAtLeast(25)}g. Log your first meal in Smart Grams to get ahead of the curve!",
                        actionLabel = "Log First Meal (+350 kcal)",
                        actionType = "LOG_FOOD",
                        coachMood = "TACTICAL"
                    ),
                    PikaCoachTip(
                        headline = "100,000 Volts of Discipline Awaits! ⚡",
                        advice = "Every Gym Leader builds their physique one verified day at a time. You need ${vit.caloriesTarget} kcal and 1 workout session today — tap below to log your first entry and claim +100 XP!",
                        actionLabel = "Verify Day ${ps.streakDays + 1} Streak Now",
                        actionType = "LOG_FOOD",
                        coachMood = "ALERT_INACTIVE"
                    )
                )
                inactivePool[slot]
            }
            str.exercisesLogged.isEmpty() -> {
                val workoutPool = listOf(
                    PikaCoachTip(
                        headline = "Nutrition Flowing — Time to Lift, Pika! 💪",
                        advice = "Great job logging ${vit.caloriesLogged} kcal today, ${bio.hunterName}! To direct those calories into skeletal muscle rather than fat storage at ${bio.weightKg} kg, knock out 3 progressive overload sets now.",
                        actionLabel = "Log Workout Set Now",
                        actionType = "LOG_WORKOUT",
                        coachMood = "TACTICAL"
                    ),
                    PikaCoachTip(
                        headline = "Glycogen Primed (${vit.caloriesLogged} kcal Logged) ⚡",
                        advice = "Your bloodstream has active energy from today's meals! Use this pre-workout fuel to add +1 rep or +2.5kg over last session's numbers for maximum mechanical tension.",
                        actionLabel = "Open Workout Logger",
                        actionType = "LOG_WORKOUT",
                        coachMood = "ENERGIZED"
                    ),
                    PikaCoachTip(
                        headline = "Compound Lift Power Hour, ${bio.hunterName}! 🏋️",
                        advice = "Multi-joint movements (Pushups, Rows, Squats, Overhead Press) recruit the most motor units and burn the highest calories. Log 3 sets today to boost your ${bio.goalDisplayLabel} progress!",
                        actionLabel = "Log Compound Exercise",
                        actionType = "LOG_WORKOUT",
                        coachMood = "TACTICAL"
                    ),
                    PikaCoachTip(
                        headline = "Mind-Muscle Connection Tip 🧠",
                        advice = "Control the eccentric (lowering) phase for 2–3 full seconds on every rep today, ${bio.hunterName}. Slow eccentrics recruit high-threshold muscle fibers and accelerate lean mass gains!",
                        actionLabel = "Start Controlled Sets",
                        actionType = "LOG_WORKOUT",
                        coachMood = "ENERGIZED"
                    ),
                    PikaCoachTip(
                        headline = "Protein + Resistance = Hypertrophy 🔥",
                        advice = "You've logged ${vit.proteinG.toInt()}g protein so far! Pair that dietary amino acid pool with resistance training today so your muscles absorb every gram for growth.",
                        actionLabel = "Log Today's Workout",
                        actionType = "LOG_WORKOUT",
                        coachMood = "TACTICAL"
                    ),
                    PikaCoachTip(
                        headline = "Unleash Pikachu's Iron Tail Session! ⚡",
                        advice = "Even a focused 20-minute upper or lower body session spikes growth hormone and insulin sensitivity. Head to the Workout tab and record your sets!",
                        actionLabel = "Go to Workout Tab",
                        actionType = "LOG_WORKOUT",
                        coachMood = "ENERGIZED"
                    )
                )
                workoutPool[slot]
            }
            calRemaining > 400 -> {
                val fuelPool = listOf(
                    PikaCoachTip(
                        headline = "Fuel Gap: $calRemaining kcal & ${proRemaining}g Protein Left!",
                        advice = "Trainer ${bio.hunterName}, your workout is recorded, but you still need $calRemaining kcal and ~${proRemaining}g protein to hit your ${vit.caloriesTarget} kcal ${bio.goalDisplayLabel} target.",
                        actionLabel = "Quick Log +350 kcal (28g P)",
                        actionType = "LOG_FOOD",
                        coachMood = "ENERGIZED"
                    ),
                    PikaCoachTip(
                        headline = "Post-Workout Recovery Meal Needed 🍗",
                        advice = "After ${str.exercisesLogged.size} exercise(s), muscle insulin sensitivity is elevated! Eat a high-protein meal (150g chicken/paneer/eggs + rice or roti) to close your ${proRemaining}g protein gap.",
                        actionLabel = "Log Post-Workout Meal",
                        actionType = "LOG_FOOD",
                        coachMood = "TACTICAL"
                    ),
                    PikaCoachTip(
                        headline = "Protect Your Muscle Gains, ${bio.hunterName}! 🛡️",
                        advice = "Falling $calRemaining kcal short on a ${bio.goalDisplayLabel} plan can stall recovery. Add a nutrient-dense snack like Greek yogurt, bananas with peanut butter, or boiled eggs now!",
                        actionLabel = "Add +350 kcal Recovery Fuel",
                        actionType = "LOG_FOOD",
                        coachMood = "ENERGIZED"
                    ),
                    PikaCoachTip(
                        headline = "Leucine Threshold Check: ${vit.proteinG.toInt()}g / ${proTarget}g 🥩",
                        advice = "You're ${proRemaining}g away from your daily protein goal! Aim for 30–40g protein in your next meal to maximally stimulate muscle repair overnight.",
                        actionLabel = "Log High-Protein Meal",
                        actionType = "LOG_FOOD",
                        coachMood = "TACTICAL"
                    ),
                    PikaCoachTip(
                        headline = "Smart Grams Macro Tip ⚡",
                        advice = "Not sure what to eat for your remaining $calRemaining kcal? Ask PikaBot or use the Smart Grams converter in the Workout/Diet tab to log exact portions!",
                        actionLabel = "Quick Log +350 kcal",
                        actionType = "LOG_FOOD",
                        coachMood = "ENERGIZED"
                    ),
                    PikaCoachTip(
                        headline = "Nighttime Casein & Recovery Prep 🌙",
                        advice = "Having curd, paneer, milk, or lean protein in your evening meal supplies slow-releasing amino acids while you sleep. Close your $calRemaining kcal gap strong!",
                        actionLabel = "Log Evening Nutrition",
                        actionType = "LOG_FOOD",
                        coachMood = "TACTICAL"
                    )
                )
                fuelPool[slot]
            }
            stam.waterLiters < stam.targetLiters -> {
                val waterPool = listOf(
                    PikaCoachTip(
                        headline = "Hydration Check: ${stam.waterLiters}L / ${stam.targetLiters}L 💧",
                        advice = "Almost at peak output, ${bio.hunterName}! Drink ${waterLeft}L more water to support cellular glycogen storage and joint recovery.",
                        actionLabel = "Drink +0.5L Water",
                        actionType = "LOG_WATER",
                        coachMood = "ENERGIZED"
                    ),
                    PikaCoachTip(
                        headline = "Flush Metabolic Byproducts (${waterLeft}L Left) 🌊",
                        advice = "High-protein diets (${proTarget}g/day) and intense lifting require extra renal hydration to clear urea and lactate. Grab a 500ml glass of water now!",
                        actionLabel = "Add +0.5L Water",
                        actionType = "LOG_WATER",
                        coachMood = "TACTICAL"
                    ),
                    PikaCoachTip(
                        headline = "Muscle Fullness & Cell Volumization 💪",
                        advice = "Skeletal muscle is ~75% water! Hitting your ${stam.targetLiters}L water target drives intracellular hydration, keeping your pump full and joints cushioned.",
                        actionLabel = "Drink +0.5L Water",
                        actionType = "LOG_WATER",
                        coachMood = "ENERGIZED"
                    ),
                    PikaCoachTip(
                        headline = "Electrolyte & Focus Boost ⚡",
                        advice = "Feeling afternoon fatigue? Mild dehydration often masquerades as hunger or tiredness. Top up ${waterLeft}L water to finish today's targets at 100%!",
                        actionLabel = "Log +0.5L Water",
                        actionType = "LOG_WATER",
                        coachMood = "ENERGIZED"
                    ),
                    PikaCoachTip(
                        headline = "Nutrient Transport Optimization 💧",
                        advice = "You've logged ${vit.caloriesLogged} kcal today — proper hydration ensures amino acids and glucose are efficiently shuttled into recovering muscle fibers.",
                        actionLabel = "Drink +0.5L Water",
                        actionType = "LOG_WATER",
                        coachMood = "TACTICAL"
                    ),
                    PikaCoachTip(
                        headline = "Final Hydration Sprint, Trainer ${bio.hunterName}! 🏆",
                        advice = "Calories and workouts are on point! Clear the final ${waterLeft}L of water to achieve a 100% flawless PikaFit daily sweep.",
                        actionLabel = "Complete +0.5L Water",
                        actionType = "LOG_WATER",
                        coachMood = "PROUD"
                    )
                )
                waterPool[slot]
            }
            else -> {
                val proudPool = listOf(
                    PikaCoachTip(
                        headline = "100,000 Volts! All Daily Targets Crushed ⚡",
                        advice = "Phenomenal discipline, Trainer ${bio.hunterName}! You cleared ${vit.caloriesLogged} kcal, ${stam.waterLiters}L water, and ${str.exercisesLogged.size} exercises with an active ${ps.streakDays}-day streak!",
                        actionLabel = "Keep Up The Momentum!",
                        actionType = "KEEP_GOING",
                        coachMood = "PROUD"
                    ),
                    PikaCoachTip(
                        headline = "Deep Sleep = Peak Growth Hormone 🌙",
                        advice = "Your nutrition (${vit.caloriesLogged} kcal) and training (${str.exercisesLogged.size} exercises) are locked in! Aim for 7.5–8 hours of quality sleep tonight when 70% of muscle repair occurs.",
                        actionLabel = "Rest & Recover Strong",
                        actionType = "KEEP_GOING",
                        coachMood = "PROUD"
                    ),
                    PikaCoachTip(
                        headline = "Progressive Overload Blueprint Ready 📈",
                        advice = "Next session, challenge yourself to beat today's volume by +1 rep per set or +2.5kg on your main lift. Consistent micro-progression is the secret to elite body recomposition!",
                        actionLabel = "Review Biometric Charts",
                        actionType = "KEEP_GOING",
                        coachMood = "ENERGIZED"
                    ),
                    PikaCoachTip(
                        headline = "Active Recovery & Mobility Tip 🧘",
                        advice = "With all macro and workout goals complete today, spend 5 minutes stretching your chest, hips, and thoracic spine to reduce DOMS and improve posture.",
                        actionLabel = "Awesome Job, Pika!",
                        actionType = "KEEP_GOING",
                        coachMood = "PROUD"
                    ),
                    PikaCoachTip(
                        headline = "Tomorrow's Meal Prep Advantage 🍱",
                        advice = "Champion trainers win the next day the night before! Pre-plan tomorrow's breakfast so hitting ${bio.recommendedProteinG}g protein on Day ${ps.streakDays + 1} is automatic.",
                        actionLabel = "Stay Consistent!",
                        actionType = "KEEP_GOING",
                        coachMood = "TACTICAL"
                    ),
                    PikaCoachTip(
                        headline = "S-Rank Consistency Unlocked, ${bio.hunterName}! 🏆",
                        advice = "At ${bio.weightKg} kg (BMI ${bio.bmi}), stacking days like today with ${vit.proteinG.toInt()}g protein and ${stam.waterLiters}L water guarantees visible physique transformation!",
                        actionLabel = "Let's Go, Pika!",
                        actionType = "KEEP_GOING",
                        coachMood = "PROUD"
                    )
                )
                proudPool[slot]
            }
        }
    }

    fun generatePikaCoachTip(showAsPopup: Boolean = true) {
        val snapshot = _systemState.value
        val currentVariation = tipVariationCounter++
        val preview = buildContextAwareFallbackTip(snapshot, currentVariation)
        _pikaCoachState.value = PikaCoachUiState.Ready(tip = preview, showPopup = showAsPopup)

        viewModelScope.launch {
            try {
                val tip = aiRepository.generateCoachTip(
                    state = snapshot,
                    fallbackTip = preview,
                    variationIndex = currentVariation
                )
                val currentPopupVisible = when (val cur = _pikaCoachState.value) {
                    is PikaCoachUiState.Ready -> cur.showPopup
                    is PikaCoachUiState.Error -> cur.showPopup
                    else -> showAsPopup
                }
                _pikaCoachState.value = PikaCoachUiState.Ready(tip = tip, showPopup = currentPopupVisible)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Using calibrated Pika-Coach telemetry tip: ${e.message}")
            }
        }
    }

    fun dismissPikaCoachPopup() {
        when (val current = _pikaCoachState.value) {
            is PikaCoachUiState.Ready -> _pikaCoachState.value = current.copy(showPopup = false)
            is PikaCoachUiState.Error -> _pikaCoachState.value = current.copy(showPopup = false)
            else -> {}
        }
    }

    fun reopenPikaCoachPopup() {
        when (val current = _pikaCoachState.value) {
            is PikaCoachUiState.Ready -> _pikaCoachState.value = current.copy(showPopup = true)
            is PikaCoachUiState.Error -> _pikaCoachState.value = current.copy(showPopup = true)
            else -> generatePikaCoachTip(showAsPopup = true)
        }
    }

    private fun seedWelcomeNutritionChatIfEmpty(state: HunterSystemState) {
        if (_nutritionChatMessages.value.isNotEmpty()) return
        val bio = state.biometrics
        val vit = state.statBreakdown.vitality
        _nutritionChatMessages.value = listOf(
            NutritionChatMessage(
                role = "model",
                text = "Pika-Pika! I'm Pika-Dietitian, your All-India (South & North Indian) Nutrition & Custom Diet Plan Bot! ⚡\n\n" +
                    "1️⃣ Ask about ANY Indian food (e.g., \"chicken gravy\", \"150g poha\", \"3 dosa 4 idli\", \"ven pongal\", \"puttu kadala curry\", \"paneer butter masala\", \"dal tadka\", \"chicken biryani\") for exact macros.\n" +
                    "2️⃣ Ask me to create or change your Diet Plan (e.g., \"weight gain diet plan\", \"switch to South Indian fat loss plan\", \"North Indian vegetarian muscle gain plan\", or \"set 2500 kcal diet plan\") and apply it directly to your dashboard!",
                suggestedMealName = "Chicken Gravy / Curry (180g)",
                suggestedCalories = 302,
                suggestedProteinG = 31.5
            )
        )
    }

    fun applyDietPlanFromPikaBot(
        messageId: String? = null,
        goalKey: String,
        planTitle: String,
        caloriesTarget: Int,
        proteinTargetG: Int,
        waterTargetLiters: Double
    ) {
        viewModelScope.launch {
            val current = _systemState.value.evaluateStreakOnLogin()
            val cleanGoal = when (goalKey.uppercase()) {
                "WEIGHT_GAIN" -> "WEIGHT_GAIN"
                "WEIGHT_LOSS" -> "WEIGHT_LOSS"
                else -> "RECOMP"
            }
            val safeCalTarget = caloriesTarget.coerceIn(1200, 5500)
            val safeWaterTarget = round(waterTargetLiters.coerceIn(1.5, 8.0) * 10.0) / 10.0
            val updatedBio = current.biometrics.copy(fitnessGoal = cleanGoal)
            val curVit = current.statBreakdown.vitality
            val curStam = current.statBreakdown.stamina

            val updatedQuests = current.questLog.map { q ->
                when {
                    q.questName.contains("Protein", ignoreCase = true) ->
                        q.copy(
                            questName = "Hit ${proteinTargetG}g Daily Protein Target",
                            status = if (curVit.proteinG >= proteinTargetG * 0.9) "COMPLETED" else "IN_PROGRESS"
                        )
                    q.questName.contains("Calorie", ignoreCase = true) || q.questName.contains("kcal", ignoreCase = true) || q.questName.contains("Surplus", ignoreCase = true) ->
                        q.copy(
                            questName = "Clear $safeCalTarget kcal Daily Target (${curVit.caloriesLogged} / $safeCalTarget logged)",
                            status = if (curVit.caloriesLogged >= safeCalTarget * 0.9) "COMPLETED" else "IN_PROGRESS"
                        )
                    q.questName.contains("Water", ignoreCase = true) ->
                        q.copy(
                            questName = "Drink ${safeWaterTarget}L+ Water",
                            status = if (curStam.waterLiters >= safeWaterTarget) "COMPLETED" else "IN_PROGRESS"
                        )
                    else -> q
                }
            }

            val updatedState = current.copy(
                biometrics = updatedBio,
                statBreakdown = current.statBreakdown.copy(
                    vitality = curVit.copy(
                        caloriesTarget = safeCalTarget,
                        targetMet = curVit.caloriesLogged >= (safeCalTarget * 0.90).toInt()
                    ),
                    stamina = curStam.copy(
                        targetLiters = safeWaterTarget,
                        targetMet = curStam.waterLiters >= safeWaterTarget
                    )
                ),
                questLog = updatedQuests,
                tacticalBriefing = "PikaBot applied your new \"$planTitle\"! Daily targets updated to $safeCalTarget kcal, ${proteinTargetG}g Protein, and ${safeWaterTarget}L Water."
            )

            _systemState.value = updatedState
            repository.saveOrUpdateSession(updatedState)

            if (messageId != null) {
                _nutritionChatMessages.value = _nutritionChatMessages.value.map { msg ->
                    if (msg.id == messageId) msg.copy(planApplied = true) else msg
                }
            }

            _aiSyncState.value = AiSyncState.Success(
                "Applied $planTitle ($safeCalTarget kcal • ${proteinTargetG}g P • ${safeWaterTarget}L Water) ⚡"
            )
        }
    }

    fun sendNutritionChatMessage(userQuery: String) {
        val cleanQuery = userQuery.trim()
        if (cleanQuery.isBlank() || _isNutritionChatLoading.value) return

        val snapshot = _systemState.value.evaluateStreakOnLogin()
        val bio = snapshot.biometrics
        val vit = snapshot.statBreakdown.vitality
        val stam = snapshot.statBreakdown.stamina

        seedWelcomeNutritionChatIfEmpty(snapshot)

        val userMsg = NutritionChatMessage(role = "user", text = cleanQuery)
        val placeholderId = UUID.randomUUID().toString()
        val detectedDietPlan = SmartFoodNutritionEngine.detectAndBuildDietPlanForQuery(
            query = cleanQuery,
            bio = bio,
            currentCaloriesTarget = vit.caloriesTarget,
            caloriesLogged = vit.caloriesLogged,
            proteinLogged = vit.proteinG
        )

        val streamingPlaceholder = NutritionChatMessage(
            id = placeholderId,
            role = "model",
            text = if (detectedDietPlan != null) {
                "Pika-Dietitian is building your customized ${detectedDietPlan.planTitle} (${detectedDietPlan.caloriesTarget} kcal & ${detectedDietPlan.proteinTargetG}g protein)..."
            } else {
                "Pika-Dietitian is analyzing Indian & global nutrition facts for \"$cleanQuery\"..."
            },
            isStreaming = true
        )

        val previousHistory = _nutritionChatMessages.value
        _nutritionChatMessages.value = previousHistory + userMsg + streamingPlaceholder
        _isNutritionChatLoading.value = true

        viewModelScope.launch {
            // If the user asked for a Diet Plan or asked to change their Diet Plan, use the calibrated DietPlanRecommendation
            // so the caloriesTarget never gets +350 kcal added twice and we attach the interactive Apply Plan button!
            if (detectedDietPlan != null) {
                if (detectedDietPlan.shouldAutoApply) {
                    applyDietPlanFromPikaBot(
                        messageId = placeholderId,
                        goalKey = detectedDietPlan.goalKey,
                        planTitle = detectedDietPlan.planTitle,
                        caloriesTarget = detectedDietPlan.caloriesTarget,
                        proteinTargetG = detectedDietPlan.proteinTargetG,
                        waterTargetLiters = detectedDietPlan.waterTargetLiters
                    )
                }
                _nutritionChatMessages.value = _nutritionChatMessages.value.map { msg ->
                    if (msg.id == placeholderId) {
                        msg.copy(
                            text = detectedDietPlan.formattedPlanText,
                            isStreaming = false,
                            isError = false,
                            suggestedMealName = null,
                            suggestedCalories = null,
                            suggestedProteinG = null,
                            suggestedPlanGoalKey = detectedDietPlan.goalKey,
                            suggestedPlanTitle = detectedDietPlan.planTitle,
                            suggestedPlanCaloriesTarget = detectedDietPlan.caloriesTarget,
                            suggestedPlanProteinG = detectedDietPlan.proteinTargetG,
                            suggestedPlanWaterLiters = detectedDietPlan.waterTargetLiters,
                            planApplied = detectedDietPlan.shouldAutoApply
                        )
                    } else msg
                }
                _isNutritionChatLoading.value = false
                return@launch
            }

            try {
                val reply = aiRepository.generateNutritionChatReply(
                    query = cleanQuery,
                    bio = bio,
                    caloriesTarget = vit.caloriesTarget,
                    caloriesLogged = vit.caloriesLogged,
                    proteinLogged = vit.proteinG,
                    waterLogged = stam.waterLiters,
                    waterTarget = stam.targetLiters
                )
                _nutritionChatMessages.value = _nutritionChatMessages.value.map { msg ->
                    if (msg.id == placeholderId) {
                        msg.copy(
                            text = reply.replyText,
                            isStreaming = false,
                            isError = false,
                            suggestedMealName = reply.suggestedMealName,
                            suggestedCalories = reply.suggestedCalories,
                            suggestedProteinG = reply.suggestedProteinG
                        )
                    } else msg
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Nutrition chat generation notice: ${e.message}", e)
                val (fallbackReply, sugMeal, sugCal, sugPro) = buildSmartNutritionScientificReply(
                    query = cleanQuery,
                    bio = bio,
                    caloriesTarget = vit.caloriesTarget,
                    caloriesLogged = vit.caloriesLogged,
                    proteinLogged = vit.proteinG,
                    waterLogged = stam.waterLiters,
                    waterTarget = stam.targetLiters
                )
                _nutritionChatMessages.value = _nutritionChatMessages.value.map { msg ->
                    if (msg.id == placeholderId) {
                        msg.copy(
                            text = fallbackReply,
                            isStreaming = false,
                            isError = false,
                            suggestedMealName = sugMeal,
                            suggestedCalories = sugCal,
                            suggestedProteinG = sugPro
                        )
                    } else msg
                }
            } finally {
                _isNutritionChatLoading.value = false
            }
        }
    }

    private data class NutritionReplyResult(
        val replyText: String,
        val mealName: String,
        val calories: Int,
        val proteinG: Double
    )

    private fun buildSmartNutritionScientificReply(
        query: String,
        bio: HunterBiometrics,
        caloriesTarget: Int,
        caloriesLogged: Int,
        proteinLogged: Double,
        waterLogged: Double,
        waterTarget: Double
    ): NutritionReplyResult {
        val calc = SmartFoodNutritionEngine.formatChatbotResponseForQuery(
            query = query,
            bio = bio,
            caloriesTarget = caloriesTarget,
            caloriesLogged = caloriesLogged,
            proteinLogged = proteinLogged,
            waterLogged = waterLogged,
            waterTarget = waterTarget
        )
        val formattedText = SmartFoodNutritionEngine.buildFormattedChatReplyText(
            query = query,
            calc = calc,
            bio = bio,
            caloriesTarget = caloriesTarget,
            caloriesLogged = caloriesLogged,
            proteinLogged = proteinLogged
        )
        val cleanMealLabel = if (calc.foodName.contains("(${calc.grams}g)")) {
            calc.foodName
        } else {
            "${calc.foodName} (${calc.grams}g)"
        }
        return NutritionReplyResult(
            replyText = formattedText,
            mealName = cleanMealLabel,
            calories = calc.calories,
            proteinG = calc.proteinG
        )
    }

    fun clearNutritionChat() {
        _nutritionChatMessages.value = emptyList()
        seedWelcomeNutritionChatIfEmpty(_systemState.value)
    }

    fun completeTrainerOnboarding(
        trainerName: String,
        age: Int,
        biologicalSex: String,
        heightCm: Double,
        weightKg: Double,
        fitnessGoal: String,
        experienceLevel: String
    ) {
        viewModelScope.launch {
            val cleanName = trainerName.trim().take(60).ifBlank { "Trainer" }
            val cleanGoal = fitnessGoal.uppercase().let {
                if (it in setOf("WEIGHT_GAIN", "WEIGHT_LOSS", "MAINTENANCE")) it else "WEIGHT_GAIN"
            }
            val cleanExp = experienceLevel.uppercase().let {
                if (it in setOf("BEGINNER", "INTERMEDIATE", "PROFESSIONAL")) it else "BEGINNER"
            }
            val starterTier = TrainerRankProgression.tierForXp(0)
            val calibratedBio = HunterBiometrics(
                hunterName = cleanName,
                hunterTitle = "${starterTier.classTitle} • ${starterTier.pokemonName}",
                avatarBase64 = "",
                weightKg = weightKg.coerceIn(25.0, 350.0),
                heightCm = heightCm.coerceIn(100.0, 250.0),
                age = age.coerceIn(10, 120),
                biologicalSex = if (biologicalSex.uppercase() == "FEMALE") "FEMALE" else "MALE",
                fitnessGoal = cleanGoal,
                experienceLevel = cleanExp,
                onboardingCompleted = true
            )
            val freshState = HunterSystemState.createResetState(
                existingBiometrics = calibratedBio,
                caloriesTarget = calibratedBio.recommendedCaloriesTarget,
                waterTargetLiters = calibratedBio.recommendedWaterLiters
            )
            repository.setOnboardingCompletedForUser(currentUserId, true, freshState.toStateJson())
            _systemState.value = freshState
            _isSessionLoaded.value = true
            repository.resetAllUserProgress(
                resetState = freshState,
                clearJournalEntries = true
            )
            seedWelcomeNutritionChatIfEmpty(freshState)
            generatePikaCoachTip(showAsPopup = true)
            _aiSyncState.value = AiSyncState.Success(
                "Pika-Pika! Welcome Trainer $cleanName (${starterTier.classTitle})! Target: ${freshState.statBreakdown.vitality.caloriesTarget} kcal & ${freshState.statBreakdown.stamina.targetLiters}L water."
            )
        }
    }

    /**
     * Logs a new Weight, Body Fat %, and Muscle Mass % point directly to Firestore for the Recharts Data Visualization Screen.
     * Note: Biometric body-scale logging updates Firestore telemetry, whereas Daily Streak Check-In specifically requires
     * Food or Exercise input per the Hunter System rule.
     */
    fun logBiometricPointToFirestore(
        weightKg: Double,
        bodyFatPercent: Double?,
        muscleMassPercent: Double?,
        note: String = ""
    ) {
        viewModelScope.launch {
            val current = _systemState.value
            val prevBio = current.biometrics
            val updatedBio = prevBio.copy(
                weightKg = weightKg.coerceIn(25.0, 350.0),
                customBodyFatPercent = bodyFatPercent?.coerceIn(3.0, 65.0),
                customMuscleMassPercent = muscleMassPercent?.coerceIn(20.0, 90.0)
            )

            val (xpUpdatedState, scaledXpGain) = current.copy(biometrics = updatedBio).applyScaledXpProgress(50)

            val cleanNote = note.trim().ifBlank {
                "Weight ${updatedBio.weightKg} kg • Body Fat ${updatedBio.bodyFatPercent}% • Muscle Mass ${updatedBio.muscleMassPercent}% (${updatedBio.muscleMassKg} kg)"
            }
            val briefing = "New biometric point plotted to Firestore: ${updatedBio.weightKg} kg, ${updatedBio.bodyFatPercent}% Body Fat, ${updatedBio.muscleMassPercent}% Muscle Mass (${updatedBio.muscleMassKg} kg). Class: ${xpUpdatedState.playerStatus.rank} (+$scaledXpGain XP)."

            val updatedState = xpUpdatedState.copy(
                tacticalBriefing = briefing
            )

            _systemState.value = updatedState
            repository.saveOrUpdateSession(updatedState)

            repository.appendJournalEntry(
                entryType = "BIOMETRIC_UPDATE",
                title = "Telemetry Point: ${updatedBio.weightKg}kg • ${updatedBio.bodyFatPercent}% BF • ${updatedBio.muscleMassPercent}% Muscle",
                caption = cleanNote,
                poseTag = "Recharts Sync",
                photoBase64 = "",
                biometrics = updatedState.biometrics
            )

            repository.appendTacticalLog(
                commandText = "Plotted Biometrics: ${updatedBio.weightKg}kg, BF ${updatedBio.bodyFatPercent}%, Muscle ${updatedBio.muscleMassPercent}%",
                briefingResponse = briefing,
                xpDelta = scaledXpGain
            )

            _aiSyncState.value = AiSyncState.Success("Biometric point saved to Firestore & plotted (+$scaledXpGain XP • ${updatedState.playerStatus.rank})")
        }
    }

    fun updateProfileAndBiometrics(
        hunterName: String,
        hunterTitle: String,
        weightKg: Double,
        heightCm: Double,
        age: Int,
        biologicalSex: String,
        avatarBase64: String? = null,
        targetWeightKg: Double? = null,
        targetPhysique: String? = null
    ) {
        viewModelScope.launch {
            val current = _systemState.value
            val prevBio = current.biometrics
            val updatedBio = prevBio.copy(
                hunterName = hunterName.trim().take(60).ifBlank { prevBio.hunterName },
                avatarBase64 = avatarBase64?.take(18000) ?: prevBio.avatarBase64,
                weightKg = weightKg.coerceIn(25.0, 350.0),
                heightCm = heightCm.coerceIn(100.0, 250.0),
                age = age.coerceIn(10, 120),
                biologicalSex = if (biologicalSex.uppercase() == "FEMALE") "FEMALE" else "MALE",
                targetWeightKg = (targetWeightKg ?: prevBio.targetWeightKg).coerceIn(25.0, 350.0),
                targetPhysique = targetPhysique?.trim()?.take(60)?.ifBlank { prevBio.targetPhysique } ?: prevBio.targetPhysique,
                onboardingCompleted = true
            )

            val (xpUpdatedState, scaledXpGain) = current.copy(biometrics = updatedBio).applyScaledXpProgress(50)
            val calibratedBio = xpUpdatedState.biometrics

            val briefing = "Biometric telemetry calibrated for ${calibratedBio.hunterName} (${xpUpdatedState.playerStatus.rank}): ${calibratedBio.weightKg}kg -> Target ${calibratedBio.targetWeightKg}kg (${calibratedBio.targetPhysique}). BMI ${calibratedBio.bmi} • Body Fat ${calibratedBio.bodyFatPercent}% • Muscle ${calibratedBio.muscleMassPercent}%. Archived to Personal Journal (+$scaledXpGain XP)."

            val updatedState = xpUpdatedState.copy(
                tacticalBriefing = briefing
            )

            _systemState.value = updatedState
            repository.saveOrUpdateSession(updatedState)

            val summaryCaption = buildString {
                append("Weight: ${calibratedBio.weightKg} kg -> Target: ${calibratedBio.targetWeightKg} kg (${calibratedBio.targetPhysique})\n")
                append("Height: ${calibratedBio.heightCm.toInt()} cm • Age: ${calibratedBio.age} (${calibratedBio.biologicalSex}) • Class: ${updatedState.playerStatus.rank} (${updatedState.playerStatus.currentXp} XP)\n")
                append("BMI: ${calibratedBio.bmi} (${calibratedBio.bmiCategory}) • Body Fat: ${calibratedBio.bodyFatPercent}% (${calibratedBio.fatMassKg} kg) • Muscle: ${calibratedBio.muscleMassPercent}% (${calibratedBio.muscleMassKg} kg)")
            }

            repository.appendJournalEntry(
                entryType = "BIOMETRIC_UPDATE",
                title = "Biometric Profile Sync (${calibratedBio.weightKg} kg • BMI ${calibratedBio.bmi})",
                caption = summaryCaption,
                poseTag = "Biometric Sync",
                photoBase64 = avatarBase64 ?: "",
                biometrics = calibratedBio
            )

            repository.appendTacticalLog(
                commandText = "Updated Biometrics: ${calibratedBio.weightKg}kg, ${calibratedBio.heightCm.toInt()}cm, Age ${calibratedBio.age}",
                briefingResponse = briefing,
                xpDelta = scaledXpGain
            )

            _aiSyncState.value = AiSyncState.Success("Biometrics saved (+$scaledXpGain XP • Class: ${updatedState.playerStatus.rank})")
        }
    }

    fun logProgressPhotoToJournal(
        title: String,
        caption: String,
        poseTag: String,
        photoBase64: String,
        setAsAvatar: Boolean = false
    ) {
        if (photoBase64.isBlank()) return
        viewModelScope.launch {
            val current = _systemState.value
            val updatedBio = if (setAsAvatar) {
                current.biometrics.copy(avatarBase64 = photoBase64.take(18000))
            } else {
                current.biometrics
            }

            val (xpUpdatedState, scaledXpGain) = current.copy(biometrics = updatedBio).applyScaledXpProgress(85)
            val calibratedBio = xpUpdatedState.biometrics

            val cleanTitle = title.trim().ifBlank { "Physique Check-In ($poseTag)" }
            val cleanCaption = caption.trim().ifBlank {
                "Documented physique progress at ${calibratedBio.weightKg} kg (BMI ${calibratedBio.bmi}, BF ${calibratedBio.bodyFatPercent}%, Muscle ${calibratedBio.muscleMassPercent}%)."
            }

            val hasPhotoAchievement = xpUpdatedState.unlockedAchievements.any { it.title == "Visual Chronicler" }
            val updatedAchievements = if (!hasPhotoAchievement) {
                xpUpdatedState.unlockedAchievements + AchievementItem(
                    title = "Visual Chronicler",
                    description = "Uploaded and archived a physique progress photo to your Personal Hunter Journal.",
                    rarity = "EPIC"
                )
            } else {
                xpUpdatedState.unlockedAchievements
            }

            val briefing = "Progress photo \"$cleanTitle\" [$poseTag] archived in Personal Journal with full biometric telemetry (${calibratedBio.weightKg}kg, BF ${calibratedBio.bodyFatPercent}%, Muscle ${calibratedBio.muscleMassPercent}%). Awarded +$scaledXpGain XP (Class: ${xpUpdatedState.playerStatus.rank})."

            val updatedState = xpUpdatedState.copy(
                unlockedAchievements = updatedAchievements,
                tacticalBriefing = briefing
            )

            _systemState.value = updatedState
            repository.saveOrUpdateSession(updatedState)

            val res = repository.appendJournalEntry(
                entryType = "PHOTO_PROGRESS",
                title = cleanTitle,
                caption = cleanCaption,
                poseTag = poseTag,
                photoBase64 = photoBase64,
                biometrics = calibratedBio
            )

            repository.appendTacticalLog(
                commandText = "Journal Photo Logged: $cleanTitle [$poseTag]",
                briefingResponse = briefing,
                xpDelta = scaledXpGain
            )

            if (res.isSuccess) {
                _aiSyncState.value = AiSyncState.Success("Progress photo added to Personal Journal (+$scaledXpGain XP)")
            } else {
                _aiSyncState.value = AiSyncState.Error(
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to save journal entry."
                )
            }
        }
    }

    fun deleteJournalEntry(entryId: String) {
        if (entryId.isBlank()) return
        viewModelScope.launch {
            repository.deleteJournalEntry(entryId)
        }
    }

    /**
     * Logs a specific food/meal item and checks the user in for their Daily Productive Streak!
     */
    fun logFoodItem(
        mealName: String,
        calories: Int,
        proteinG: Double,
        carbsG: Double = (calories * 0.10),
        fatsG: Double = (calories * 0.03),
        fiberG: Double = 0.0,
        grams: Int = 100
    ) {
        if (calories < 0 || (calories == 0 && mealName.isBlank())) return
        viewModelScope.launch {
            val cleanMeal = mealName.trim()
                .replace(Regex("""\(\d+g\)\s*(\(\d+g\))"""), "$1")
                .ifBlank { "Tactical Ration" }
            val safeCals = calories.coerceIn(0, 10000)
            val safePro = round(proteinG.coerceIn(0.0, 500.0) * 10.0) / 10.0
            val safeCarbs = round(carbsG.coerceIn(0.0, 1000.0) * 10.0) / 10.0
            val safeFats = round(fatsG.coerceIn(0.0, 500.0) * 10.0) / 10.0
            val safeFiber = round(fiberG.coerceIn(0.0, 200.0) * 10.0) / 10.0
            val safeGrams = grams.coerceIn(1, 5000)

            val rawCurrent = _systemState.value
            val current = rawCurrent.evaluateStreakOnLogin()
            if (!current.playerStatus.checkedInToday && rawCurrent.playerStatus.checkedInToday) {
                _foodHistory.value = emptyList()
                _waterHistory.value = emptyList()
            }
            val vit = current.statBreakdown.vitality
            val newKcal = (vit.caloriesLogged + safeCals).coerceIn(0, 50000)
            val newProtein = round((vit.proteinG + safePro).coerceIn(0.0, 2000.0) * 10.0) / 10.0
            val newCarbs = round((vit.carbsG + safeCarbs).coerceIn(0.0, 5000.0) * 10.0) / 10.0
            val newFats = round((vit.fatsG + safeFats).coerceIn(0.0, 2000.0) * 10.0) / 10.0
            val newFiber = round((vit.fiberG + safeFiber).coerceIn(0.0, 500.0) * 10.0) / 10.0
            val targetMet = newKcal >= vit.caloriesTarget && newProtein >= 110.0

            // Apply productive input to check the user in for their Daily Streak (awards tier-scaled daily completion bonus)
            val (streakUpdatedState, newlyCheckedIn) = current.applyProductiveInput(
                isFoodInput = true,
                inputSummary = "$cleanMeal (+$safeCals kcal, ${safePro}g P, ${safeCarbs}g C, ${safeFats}g F)"
            )

            val baseXpGain = 45
            val (xpUpdatedState, scaledActionXp) = streakUpdatedState.applyScaledXpProgress(baseXpGain)
            val ps = xpUpdatedState.playerStatus
            val dailyBonusXp = if (newlyCheckedIn) TrainerRankProgression.tierForXp(current.playerStatus.currentXp).dailyCompletionXp else 0
            val totalXpGain = scaledActionXp + dailyBonusXp

            val updatedQuests = xpUpdatedState.questLog.map { q ->
                if (q.questName.contains("kcal", ignoreCase = true) || q.questName.contains("Surplus", ignoreCase = true)) {
                    QuestItem(
                        questName = "Clear ${vit.caloriesTarget} kcal Surplus Target ($newKcal / ${vit.caloriesTarget} logged)",
                        status = if (newKcal >= vit.caloriesTarget) "COMPLETED" else "IN_PROGRESS",
                        xpAwarded = 120
                    )
                } else {
                    q
                }
            }

            val streakNote = if (newlyCheckedIn) {
                "Daily Productive Streak Checked In (${ps.streakDays} Days • +$dailyBonusXp Daily XP)!"
            } else {
                "Productive Streak active (${ps.streakDays} Days • Class: ${ps.rank})."
            }

            val updatedBriefing = "Logged food \"$cleanMeal\" (+$safeCals kcal | P: ${safePro}g • C: ${safeCarbs}g • F: ${safeFats}g). Total intake: $newKcal / ${vit.caloriesTarget} kcal. $streakNote"

            val finalState = xpUpdatedState.copy(
                statBreakdown = xpUpdatedState.statBreakdown.copy(
                    vitality = vit.copy(
                        caloriesLogged = newKcal,
                        proteinG = newProtein,
                        carbsG = newCarbs,
                        fatsG = newFats,
                        fiberG = newFiber,
                        targetMet = targetMet
                    )
                ),
                questLog = updatedQuests,
                tacticalBriefing = updatedBriefing
            )

            _foodHistory.value = _foodHistory.value + FoodEntryRecord(
                mealName = cleanMeal,
                calories = safeCals,
                proteinG = safePro,
                carbsG = safeCarbs,
                fatsG = safeFats,
                fiberG = safeFiber,
                grams = safeGrams,
                xpAwarded = totalXpGain,
                triggeredDailyCheckIn = newlyCheckedIn,
                previousStreakDays = current.playerStatus.streakDays
            )

            _systemState.value = finalState
            repository.saveOrUpdateSession(finalState)
            repository.appendTacticalLog(
                commandText = "Food Input: $cleanMeal (+$safeCals kcal, ${safePro}g P, ${safeCarbs}g C, ${safeFats}g F)",
                briefingResponse = updatedBriefing,
                xpDelta = totalXpGain
            )

            _aiSyncState.value = AiSyncState.Success(
                if (newlyCheckedIn) {
                    "Daily Streak Checked In (${ps.streakDays}D) via $cleanMeal! (+$totalXpGain XP • ${ps.rank})"
                } else {
                    "Logged $cleanMeal: +$safeCals kcal (+$totalXpGain XP • ${ps.rank})"
                }
            )
        }
    }

    fun submitTacticalCommand(command: String) {
        val cleanCmd = command.trim()
        if (cleanCmd.isBlank()) return

        viewModelScope.launch {
            _aiSyncState.value = AiSyncState.Analyzing(cleanCmd)
            try {
                val beforeState = _systemState.value
                val oldXp = beforeState.playerStatus.currentXp + (beforeState.playerStatus.level * 1000)
                val evaluatedState = aiRepository.evaluateTacticalCommand(
                    command = cleanCmd,
                    currentState = beforeState
                )

                val lowerCmd = cleanCmd.lowercase()
                val isExerciseCmd = evaluatedState.statBreakdown.strength.exercisesLogged.size > beforeState.statBreakdown.strength.exercisesLogged.size ||
                    listOf("set", "rep", "pushup", "pull", "squat", "bench", "row", "curl", "raise", "workout", "lift", "press", "run").any { lowerCmd.contains(it) }
                val isFoodCmd = evaluatedState.statBreakdown.vitality.caloriesLogged > beforeState.statBreakdown.vitality.caloriesLogged ||
                    evaluatedState.statBreakdown.vitality.proteinG > beforeState.statBreakdown.vitality.proteinG ||
                    listOf("kcal", "cal", "protein", "meal", "ate", "had", "shake", "chicken", "egg", "rice", "food", "eat").any { lowerCmd.contains(it) }

                val (finalState, newlyCheckedIn) = when {
                    isExerciseCmd -> evaluatedState.applyProductiveInput(isFoodInput = false, inputSummary = cleanCmd.take(40))
                    isFoodCmd -> evaluatedState.applyProductiveInput(isFoodInput = true, inputSummary = cleanCmd.take(40))
                    else -> Pair(evaluatedState, false)
                }

                val newXp = finalState.playerStatus.currentXp + (finalState.playerStatus.level * 1000)
                val xpDelta = (newXp - oldXp).coerceAtLeast(25)

                _systemState.value = finalState
                val saveRes = repository.saveOrUpdateSession(finalState)
                repository.appendTacticalLog(
                    commandText = cleanCmd,
                    briefingResponse = finalState.tacticalBriefing,
                    xpDelta = xpDelta
                )

                if (saveRes.isSuccess) {
                    val msg = if (newlyCheckedIn) {
                        "Daily Streak Checked In (${finalState.playerStatus.streakDays}D) & Gemini AI Updated (+${xpDelta} XP)"
                    } else {
                        "Gemini AI evaluated report (+${xpDelta} XP)"
                    }
                    _aiSyncState.value = AiSyncState.Success(msg)
                } else {
                    _aiSyncState.value = AiSyncState.Error(
                        saveRes.exceptionOrNull()?.localizedMessage ?: "Saved locally; cloud sync error."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Tactical evaluation processed locally: ${e.message}")
                _aiSyncState.value = AiSyncState.Success("Tactical report logged: $cleanCmd (+35 XP)")
            }
        }
    }

    fun quickAdjustCalories(deltaKcal: Int, deltaProteinG: Double = 0.0) {
        viewModelScope.launch {
            val rawCurrent = _systemState.value
            val current = rawCurrent.evaluateStreakOnLogin()
            if (!current.playerStatus.checkedInToday && rawCurrent.playerStatus.checkedInToday) {
                _foodHistory.value = emptyList()
                _waterHistory.value = emptyList()
            }
            val vit = current.statBreakdown.vitality
            val newKcal = (vit.caloriesLogged + deltaKcal).coerceIn(0, 50000)
            val newProtein = (vit.proteinG + deltaProteinG).coerceIn(0.0, 2000.0)
            val newCarbs = (vit.carbsG + (deltaKcal * 0.11)).coerceIn(0.0, 5000.0)
            val targetMet = newKcal >= vit.caloriesTarget && newProtein >= 110.0

            // Positive food/calorie input checks the user in for their Daily Productive Streak!
            val (streakState, newlyCheckedIn) = if (deltaKcal > 0) {
                current.applyProductiveInput(
                    isFoodInput = true,
                    inputSummary = "+$deltaKcal kcal (${deltaProteinG.toInt()}g protein)"
                )
            } else {
                Pair(current, false)
            }

            val baseXpGain = if (deltaKcal > 0) 40 else 0
            val (xpUpdatedState, scaledActionXp) = streakState.applyScaledXpProgress(baseXpGain)
            val ps = xpUpdatedState.playerStatus
            val dailyBonusXp = if (newlyCheckedIn) TrainerRankProgression.tierForXp(current.playerStatus.currentXp).dailyCompletionXp else 0
            val totalXpGain = scaledActionXp + dailyBonusXp

            val updatedQuests = xpUpdatedState.questLog.map { q ->
                if (q.questName.contains("kcal", ignoreCase = true) || q.questName.contains("Surplus", ignoreCase = true)) {
                    QuestItem(
                        questName = "Clear ${vit.caloriesTarget} kcal Surplus Target ($newKcal / ${vit.caloriesTarget} logged)",
                        status = if (newKcal >= vit.caloriesTarget) "COMPLETED" else "IN_PROGRESS",
                        xpAwarded = 120
                    )
                } else {
                    q
                }
            }

            val streakSuffix = if (newlyCheckedIn) " Daily Productive Streak Checked In (${ps.streakDays}D)!" else ""
            val updatedBriefing = if (targetMet) {
                "Caloric surplus and protein synthesis targets achieved at $newKcal kcal (${newProtein.toInt()}g protein). Anabolic recovery window is locked in.$streakSuffix"
            } else {
                val remaining = (vit.caloriesTarget - newKcal).coerceAtLeast(0)
                "Vitality updated to $newKcal kcal (${newProtein.toInt()}g protein). $remaining kcal remaining to clear your daily hypertrophy surplus.$streakSuffix"
            }

            val updated = xpUpdatedState.copy(
                statBreakdown = xpUpdatedState.statBreakdown.copy(
                    vitality = vit.copy(
                        caloriesLogged = newKcal,
                        proteinG = newProtein,
                        carbsG = newCarbs,
                        targetMet = targetMet
                    )
                ),
                questLog = updatedQuests,
                tacticalBriefing = updatedBriefing
            )

            if (deltaKcal > 0) {
                _foodHistory.value = _foodHistory.value + FoodEntryRecord(
                    mealName = "Quick +$deltaKcal kcal",
                    calories = deltaKcal,
                    proteinG = deltaProteinG,
                    carbsG = deltaKcal * 0.11,
                    fatsG = 0.0,
                    xpAwarded = totalXpGain,
                    triggeredDailyCheckIn = newlyCheckedIn,
                    previousStreakDays = current.playerStatus.streakDays
                )
            }

            _systemState.value = updated
            repository.saveOrUpdateSession(updated)
            repository.appendTacticalLog(
                commandText = "Logged ${if (deltaKcal >= 0) "+$deltaKcal" else "$deltaKcal"} kcal (${newKcal} kcal total)",
                briefingResponse = updatedBriefing,
                xpDelta = totalXpGain
            )

            if (newlyCheckedIn) {
                _aiSyncState.value = AiSyncState.Success("Daily Streak Checked In (${ps.streakDays}D) via Food Input! (+$totalXpGain XP • ${ps.rank})")
            } else if (deltaKcal > 0) {
                _aiSyncState.value = AiSyncState.Success("Logged +$deltaKcal kcal (+${deltaProteinG.toInt()}g protein • +$totalXpGain XP)")
            }
        }
    }

    fun quickAddWater(litersDelta: Double, customLabel: String? = null) {
        if (litersDelta == 0.0) return
        viewModelScope.launch {
            val safeDelta = round(litersDelta.coerceIn(-10.0, 15.0) * 100.0) / 100.0
            val rawCurrent = _systemState.value
            val current = rawCurrent.evaluateStreakOnLogin()
            if (!current.playerStatus.checkedInToday && rawCurrent.playerStatus.checkedInToday) {
                _foodHistory.value = emptyList()
                _waterHistory.value = emptyList()
            }
            val stam = current.statBreakdown.stamina
            val newWater = round((stam.waterLiters + safeDelta) * 100.0) / 100.0
            val clampedWater = newWater.coerceIn(0.0, 50.0)
            val targetMet = clampedWater >= stam.targetLiters

            val baseXpGain = if (safeDelta > 0) 20 else 0
            val (xpUpdatedState, scaledXpGain) = current.applyScaledXpProgress(baseXpGain)

            val mlEquivalent = (safeDelta * 1000).toInt()
            val entryLabel = customLabel?.takeIf { it.isNotBlank() }
                ?: "+${safeDelta}L (${mlEquivalent}ml) Water"

            val briefing = "Hydration reserves at ${clampedWater}L / ${stam.targetLiters}L ($entryLabel). Cellular nutrient transport and joint lubrication optimized."
            val updatedQuests = xpUpdatedState.questLog.map { q ->
                if (q.questName.contains("Water", ignoreCase = true)) {
                    QuestItem(
                        questName = "Drink ${stam.targetLiters}L Water (${clampedWater}L / ${stam.targetLiters}L)",
                        status = if (targetMet) "COMPLETED" else "IN_PROGRESS",
                        xpAwarded = q.xpAwarded
                    )
                } else {
                    q
                }
            }
            val updated = xpUpdatedState.copy(
                statBreakdown = xpUpdatedState.statBreakdown.copy(
                    stamina = stam.copy(
                        waterLiters = clampedWater,
                        targetMet = targetMet
                    )
                ),
                questLog = updatedQuests,
                tacticalBriefing = briefing
            )
            if (safeDelta > 0) {
                _waterHistory.value = _waterHistory.value + WaterEntryRecord(
                    label = entryLabel,
                    litersDelta = safeDelta,
                    xpAwarded = scaledXpGain
                )
            }
            _systemState.value = updated
            repository.saveOrUpdateSession(updated)
            repository.appendTacticalLog(
                commandText = "Hydration $entryLabel (${clampedWater}L total)",
                briefingResponse = briefing,
                xpDelta = scaledXpGain
            )
            if (safeDelta > 0) {
                _aiSyncState.value = AiSyncState.Success("Logged $entryLabel (${clampedWater}L / ${stam.targetLiters}L • +$scaledXpGain XP)")
            }
        }
    }

    /**
     * Undoes the most recent Food/Calorie entry (or removes a specific FoodEntryRecord by id),
     * reverting calories, protein, carbs, fats, fiber, XP, and daily food input counters if mistakenly entered.
     */
    fun undoLastFoodEntry(entryId: String? = null) {
        viewModelScope.launch {
            val current = _systemState.value
            val vit = current.statBreakdown.vitality
            val history = _foodHistory.value

            val targetRecord = if (entryId != null) {
                history.find { it.id == entryId }
            } else {
                history.lastOrNull()
            }

            if (targetRecord == null && vit.caloriesLogged <= 0 && vit.proteinG <= 0.0) {
                _aiSyncState.value = AiSyncState.Error("No food entries left to undo today.")
                return@launch
            }

            val removeCals = targetRecord?.calories ?: vit.caloriesLogged.coerceAtMost(350)
            val removePro = targetRecord?.proteinG ?: vit.proteinG.coerceAtMost(28.0)
            val removeCarbs = targetRecord?.carbsG ?: (removeCals * 0.10)
            val removeFats = targetRecord?.fatsG ?: (removeCals * 0.03)
            val removeFiber = targetRecord?.fiberG ?: 0.0
            val removeLabel = targetRecord?.mealName ?: "${removeCals} kcal entry"
            val removeXp = targetRecord?.xpAwarded ?: 40

            if (targetRecord != null) {
                _foodHistory.value = history.filterNot { it.id == targetRecord.id }
            }

            val newKcal = (vit.caloriesLogged - removeCals).coerceAtLeast(0)
            val newProtein = round((vit.proteinG - removePro).coerceAtLeast(0.0) * 10.0) / 10.0
            val newCarbs = round((vit.carbsG - removeCarbs).coerceAtLeast(0.0) * 10.0) / 10.0
            val newFats = round((vit.fatsG - removeFats).coerceAtLeast(0.0) * 10.0) / 10.0
            val newFiber = round((vit.fiberG - removeFiber).coerceAtLeast(0.0) * 10.0) / 10.0
            val targetMet = newKcal >= vit.caloriesTarget && newProtein >= 110.0

            val ps = current.playerStatus
            val newFoodInputs = (ps.todayFoodInputs - 1).coerceAtLeast(0)
            val stillHasActivityToday = newFoodInputs > 0 || ps.todayExerciseInputs > 0 || newKcal > 0
            val newCheckedIn = if (!stillHasActivityToday) false else ps.checkedInToday
            val newStreakDays = if (!stillHasActivityToday && targetRecord?.triggeredDailyCheckIn == true) {
                targetRecord.previousStreakDays
            } else {
                ps.streakDays
            }

            val (xpRevertedState, _) = current.applyScaledXpProgress(-removeXp, isRawAlreadyScaled = true)

            val updatedQuests = xpRevertedState.questLog.map { q ->
                if (q.questName.contains("kcal", ignoreCase = true) || q.questName.contains("Surplus", ignoreCase = true)) {
                    QuestItem(
                        questName = "Clear ${vit.caloriesTarget} kcal Surplus Target ($newKcal / ${vit.caloriesTarget} logged)",
                        status = if (newKcal >= vit.caloriesTarget) "COMPLETED" else "IN_PROGRESS",
                        xpAwarded = 120
                    )
                } else {
                    q
                }
            }

            val updatedBriefing = "Undid food entry \"$removeLabel\" (-$removeCals kcal, -${removePro}g protein). Updated total: $newKcal / ${vit.caloriesTarget} kcal."

            val updatedState = xpRevertedState.copy(
                playerStatus = xpRevertedState.playerStatus.copy(
                    todayFoodInputs = newFoodInputs,
                    checkedInToday = newCheckedIn,
                    streakDays = newStreakDays
                ),
                statBreakdown = xpRevertedState.statBreakdown.copy(
                    vitality = vit.copy(
                        caloriesLogged = newKcal,
                        proteinG = newProtein,
                        carbsG = newCarbs,
                        fatsG = newFats,
                        fiberG = newFiber,
                        targetMet = targetMet
                    )
                ),
                questLog = updatedQuests,
                tacticalBriefing = updatedBriefing
            )

            _systemState.value = updatedState
            repository.saveOrUpdateSession(updatedState)
            repository.appendTacticalLog(
                commandText = "Undo Food Entry: $removeLabel (-$removeCals kcal)",
                briefingResponse = updatedBriefing,
                xpDelta = -removeXp
            )
            _aiSyncState.value = AiSyncState.Success("Undid food entry: $removeLabel (-$removeCals kcal, -${removePro}g P)")
        }
    }

    /**
     * Undoes the most recent Water/Hydration entry (or removes a specific WaterEntryRecord by id),
     * reverting waterLiters, targetMet, and XP if mistakenly entered.
     */
    fun undoLastWaterEntry(entryId: String? = null) {
        viewModelScope.launch {
            val current = _systemState.value
            val stam = current.statBreakdown.stamina
            val history = _waterHistory.value

            val targetRecord = if (entryId != null) {
                history.find { it.id == entryId }
            } else {
                history.lastOrNull()
            }

            if (targetRecord == null && stam.waterLiters <= 0.0) {
                _aiSyncState.value = AiSyncState.Error("No water entries left to undo today.")
                return@launch
            }

            val removeLiters = targetRecord?.litersDelta ?: stam.waterLiters.coerceAtMost(0.5)
            val removeXp = targetRecord?.xpAwarded ?: 20

            if (targetRecord != null) {
                _waterHistory.value = history.filterNot { it.id == targetRecord.id }
            }

            val newWater = (round((stam.waterLiters - removeLiters) * 100.0) / 100.0).coerceAtLeast(0.0)
            val targetMet = newWater >= stam.targetLiters

            val (xpRevertedState, _) = current.applyScaledXpProgress(-removeXp, isRawAlreadyScaled = true)

            val updatedBriefing = "Undid water entry (-${removeLiters}L). Hydration reserves updated to ${newWater}L / ${stam.targetLiters}L."

            val updatedState = xpRevertedState.copy(
                statBreakdown = xpRevertedState.statBreakdown.copy(
                    stamina = stam.copy(
                        waterLiters = newWater,
                        targetMet = targetMet
                    )
                ),
                tacticalBriefing = updatedBriefing
            )

            _systemState.value = updatedState
            repository.saveOrUpdateSession(updatedState)
            repository.appendTacticalLog(
                commandText = "Undo Water Entry (-${removeLiters}L)",
                briefingResponse = updatedBriefing,
                xpDelta = -removeXp
            )
            _aiSyncState.value = AiSyncState.Success("Undid water entry (-${removeLiters}L). Now at ${newWater}L / ${stam.targetLiters}L")
        }
    }

    fun addExerciseSet(name: String, sets: Int, reps: String, overload: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val current = _systemState.value
            val existingExercises = current.statBreakdown.strength.exercisesLogged
            val newEx = ExerciseLogged(
                exerciseName = name.trim(),
                setsCompleted = sets.coerceIn(1, 20),
                repsPerSet = reps.trim().ifBlank { "10" },
                progressiveOverloadHit = overload
            )
            val updatedList = existingExercises + newEx

            // Logging any exercise data checks the user in for their Daily Productive Streak!
            val (streakState, newlyCheckedIn) = current.applyProductiveInput(
                isFoodInput = false,
                inputSummary = "${newEx.exerciseName} (${newEx.setsCompleted}x${newEx.repsPerSet})"
            )

            val baseXpGain = if (overload) 75 else 45
            val (xpUpdatedState, scaledActionXp) = streakState.applyScaledXpProgress(baseXpGain)
            val ps = xpUpdatedState.playerStatus
            val dailyBonusXp = if (newlyCheckedIn) TrainerRankProgression.tierForXp(current.playerStatus.currentXp).dailyCompletionXp else 0
            val totalXpGain = scaledActionXp + dailyBonusXp

            val streakSuffix = if (newlyCheckedIn) " Daily Productive Streak Checked In (${ps.streakDays}D)!" else ""
            val briefing = "Logged ${newEx.exerciseName} (${newEx.setsCompleted}x${newEx.repsPerSet}). Progressive overload ${if (overload) "CONFIRMED" else "maintained"} — mechanical tension stimulus recorded.$streakSuffix"
            val updated = xpUpdatedState.copy(
                statBreakdown = xpUpdatedState.statBreakdown.copy(
                    strength = xpUpdatedState.statBreakdown.strength.copy(
                        workoutCompleted = true,
                        exercisesLogged = updatedList
                    )
                ),
                tacticalBriefing = briefing
            )
            _systemState.value = updated
            repository.saveOrUpdateSession(updated)
            repository.appendTacticalLog(
                commandText = "Strength Log: ${newEx.exerciseName} ${newEx.setsCompleted}x${newEx.repsPerSet}",
                briefingResponse = briefing,
                xpDelta = totalXpGain
            )

            _aiSyncState.value = AiSyncState.Success(
                if (newlyCheckedIn) {
                    "Daily Streak Checked In (${ps.streakDays}D) via Exercise Input! (+$totalXpGain XP • ${ps.rank})"
                } else {
                    "Exercise logged: ${newEx.exerciseName} (+$totalXpGain XP • ${ps.rank})"
                }
            )
        }
    }

    fun toggleQuestStatus(index: Int) {
        viewModelScope.launch {
            val current = _systemState.value
            val quests = current.questLog.toMutableList()
            if (index !in quests.indices) return@launch
            val item = quests[index]
            val nextStatus = if (item.status == "COMPLETED") "IN_PROGRESS" else "COMPLETED"
            quests[index] = item.copy(status = nextStatus)

            val xpDelta = if (nextStatus == "COMPLETED") item.xpAwarded else -item.xpAwarded
            val (xpUpdatedState, _) = current.applyScaledXpProgress(xpDelta)

            val updated = xpUpdatedState.copy(
                questLog = quests
            )
            _systemState.value = updated
            repository.saveOrUpdateSession(updated)
        }
    }

    fun updateSettingsProfileAndTargets(
        hunterName: String,
        hunterTitle: String,
        weightKg: Double,
        heightCm: Double,
        age: Int,
        biologicalSex: String,
        customBodyFatPercent: Double?,
        customMuscleMassPercent: Double?,
        caloriesTarget: Int,
        waterTargetLiters: Double,
        avatarBase64: String? = null,
        targetWeightKg: Double? = null,
        targetPhysique: String? = null
    ) {
        viewModelScope.launch {
            val current = _systemState.value
            val prevBio = current.biometrics
            val updatedBio = prevBio.copy(
                hunterName = hunterName.trim().take(60).ifBlank { prevBio.hunterName },
                avatarBase64 = avatarBase64?.take(18000) ?: prevBio.avatarBase64,
                weightKg = weightKg.coerceIn(25.0, 350.0),
                heightCm = heightCm.coerceIn(100.0, 250.0),
                age = age.coerceIn(10, 120),
                biologicalSex = if (biologicalSex.uppercase() == "FEMALE") "FEMALE" else "MALE",
                targetWeightKg = (targetWeightKg ?: prevBio.targetWeightKg).coerceIn(25.0, 350.0),
                targetPhysique = targetPhysique?.trim()?.take(60)?.ifBlank { prevBio.targetPhysique } ?: prevBio.targetPhysique,
                customBodyFatPercent = customBodyFatPercent?.coerceIn(3.0, 65.0),
                customMuscleMassPercent = customMuscleMassPercent?.coerceIn(20.0, 90.0),
                onboardingCompleted = true
            )

            val safeCalTarget = caloriesTarget.coerceIn(800, 15000)
            val safeWaterTarget = ((waterTargetLiters.coerceIn(0.5, 20.0) * 10.0).toInt() / 10.0)
            val vit = current.statBreakdown.vitality
            val stam = current.statBreakdown.stamina

            val (xpUpdatedState, scaledXpGain) = current.copy(biometrics = updatedBio).applyScaledXpProgress(50)
            val calibratedBio = xpUpdatedState.biometrics

            val briefing = "Profile & Daily Targets updated in Settings for ${calibratedBio.hunterName} (${xpUpdatedState.playerStatus.rank}): ${calibratedBio.weightKg}kg -> Target ${calibratedBio.targetWeightKg}kg (${calibratedBio.targetPhysique}), Daily Targets ${safeCalTarget} kcal & ${safeWaterTarget}L water."

            val updatedQuests = xpUpdatedState.questLog.map { q ->
                if (q.questName.contains("kcal", ignoreCase = true) || q.questName.contains("Surplus", ignoreCase = true)) {
                    QuestItem(
                        questName = "Clear ${safeCalTarget} kcal Surplus Target (${vit.caloriesLogged} / ${safeCalTarget} logged)",
                        status = if (vit.caloriesLogged >= safeCalTarget) "COMPLETED" else "IN_PROGRESS",
                        xpAwarded = 120
                    )
                } else {
                    q
                }
            }

            val updatedState = xpUpdatedState.copy(
                statBreakdown = xpUpdatedState.statBreakdown.copy(
                    vitality = vit.copy(
                        caloriesTarget = safeCalTarget,
                        targetMet = vit.caloriesLogged >= safeCalTarget && vit.proteinG >= 110.0
                    ),
                    stamina = stam.copy(
                        targetLiters = safeWaterTarget,
                        targetMet = stam.waterLiters >= safeWaterTarget
                    )
                ),
                questLog = updatedQuests,
                tacticalBriefing = briefing
            )

            _systemState.value = updatedState
            repository.saveOrUpdateSession(updatedState)

            repository.appendJournalEntry(
                entryType = "BIOMETRIC_UPDATE",
                title = "Settings Profile Sync (${calibratedBio.weightKg} kg -> ${calibratedBio.targetWeightKg} kg)",
                caption = "Updated profile & targets: ${calibratedBio.hunterName} (${updatedState.playerStatus.rank}) • Weight ${calibratedBio.weightKg} kg • Target ${calibratedBio.targetWeightKg} kg (${calibratedBio.targetPhysique}) • Targets: ${safeCalTarget} kcal / ${safeWaterTarget}L.",
                poseTag = "Settings Sync",
                photoBase64 = avatarBase64 ?: "",
                biometrics = calibratedBio
            )

            repository.appendTacticalLog(
                commandText = "Settings Profile Updated: ${calibratedBio.hunterName} (${safeCalTarget} kcal, ${safeWaterTarget}L)",
                briefingResponse = briefing,
                xpDelta = scaledXpGain
            )

            _aiSyncState.value = AiSyncState.Success("Profile & Daily Targets saved (+$scaledXpGain XP • ${updatedState.playerStatus.rank})")
        }
    }

    fun resetHunterProgress(clearJournalHistory: Boolean) {
        viewModelScope.launch {
            val current = _systemState.value
            _foodHistory.value = emptyList()
            _waterHistory.value = emptyList()
            val resetState = HunterSystemState.createResetState(
                existingBiometrics = current.biometrics.copy(onboardingCompleted = false),
                caloriesTarget = current.statBreakdown.vitality.caloriesTarget,
                waterTargetLiters = current.statBreakdown.stamina.targetLiters
            )
            repository.setOnboardingCompletedForUser(currentUserId, false, null)
            _systemState.value = resetState
            val res = repository.resetAllUserProgress(
                resetState = resetState,
                clearJournalEntries = clearJournalHistory
            )
            if (res.isSuccess) {
                _aiSyncState.value = AiSyncState.Success(
                    if (clearJournalHistory) {
                        "All progress & journal history reset to Beginner (0 XP, 0D Streak)."
                    } else {
                        "Hunter progress reset to Beginner (0 XP, 0D Streak). Biometrics preserved."
                    }
                )
            } else {
                _aiSyncState.value = AiSyncState.Error(
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to reset progress in cloud."
                )
            }
        }
    }

    fun submitPublicFeedback(
        category: String,
        rating: Int,
        message: String
    ) {
        val cleanMsg = message.trim()
        if (cleanMsg.isBlank()) return
        viewModelScope.launch {
            val current = _systemState.value
            val hunterName = current.biometrics.hunterName.ifBlank { "Shadow Vanguard" }
            val res = repository.submitPublicFeedback(
                hunterName = hunterName,
                category = category,
                rating = rating,
                message = cleanMsg
            )
            if (res.isSuccess) {
                val (xpUpdatedState, scaledXpGain) = current.applyScaledXpProgress(35)
                val updated = xpUpdatedState.copy(
                    tacticalBriefing = "Public feedback ($category • ${rating}★) transmitted to the Creator Feedback Board. Thank you for helping evolve the Hunter System!"
                )
                _systemState.value = updated
                repository.saveOrUpdateSession(updated)
                repository.appendTacticalLog(
                    commandText = "Public Feedback Submitted [$category • ${rating}★]",
                    briefingResponse = updated.tacticalBriefing,
                    xpDelta = scaledXpGain
                )
                _aiSyncState.value = AiSyncState.Success("Public feedback received by Creator Board (+$scaledXpGain XP)")
            } else {
                _aiSyncState.value = AiSyncState.Error(
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to submit feedback."
                )
            }
        }
    }

    fun updateFeedbackStatus(feedbackId: String, newStatus: String) {
        if (feedbackId.isBlank()) return
        viewModelScope.launch {
            val res = repository.updateFeedbackStatus(feedbackId, newStatus)
            if (res.isSuccess) {
                _aiSyncState.value = AiSyncState.Success("Feedback status updated to $newStatus")
            } else {
                _aiSyncState.value = AiSyncState.Error(
                    res.exceptionOrNull()?.localizedMessage ?: "Unable to update feedback status."
                )
            }
        }
    }

    fun deleteFeedback(feedbackId: String) {
        if (feedbackId.isBlank()) return
        viewModelScope.launch {
            val res = repository.deleteFeedback(feedbackId)
            if (res.isSuccess) {
                _aiSyncState.value = AiSyncState.Success("Feedback entry removed")
            } else {
                _aiSyncState.value = AiSyncState.Error(
                    res.exceptionOrNull()?.localizedMessage ?: "Unable to delete feedback."
                )
            }
        }
    }

    fun dismissAiSyncBanner() {
        _aiSyncState.value = AiSyncState.Idle
    }

    fun logExerciseSet(
        exerciseName: String,
        setsCompleted: Int,
        repsPerSet: String,
        progressiveOverloadHit: Boolean
    ) {
        addExerciseSet(
            name = exerciseName,
            sets = setsCompleted,
            reps = repsPerSet,
            overload = progressiveOverloadHit
        )
    }

    fun logBiometricDataPoint(
        weightKg: Double,
        bodyFatPercent: Double?,
        muscleMassKg: Double?,
        note: String
    ) {
        val computedMusclePercent = if (muscleMassKg != null && weightKg > 0.0) {
            ((muscleMassKg / weightKg) * 100.0).coerceIn(20.0, 90.0)
        } else {
            null
        }
        logBiometricPointToFirestore(
            weightKg = weightKg,
            bodyFatPercent = bodyFatPercent,
            muscleMassPercent = computedMusclePercent,
            note = note
        )
    }

    fun logFoodIntake(mealName: String, calories: Int, proteinG: Double) {
        logFoodItem(mealName = mealName, calories = calories, proteinG = proteinG)
    }

    fun undoLastFoodIntake() {
        undoLastFoodEntry()
    }

    fun undoLastWaterIntake() {
        undoLastWaterEntry()
    }

    fun applyRecommendedDietPlan(
        messageId: String,
        goalKey: String,
        planTitle: String,
        caloriesTarget: Int,
        proteinTargetG: Int,
        waterTargetLiters: Double
    ) {
        applyDietPlanFromPikaBot(
            messageId = messageId,
            goalKey = goalKey,
            planTitle = planTitle,
            caloriesTarget = caloriesTarget,
            proteinTargetG = proteinTargetG,
            waterTargetLiters = waterTargetLiters
        )
    }

    fun updateBiometrics(
        hunterName: String,
        hunterTitle: String,
        weightKg: Double,
        heightCm: Double,
        age: Int,
        biologicalSex: String,
        avatarImageBase64: String?
    ) {
        updateProfileAndBiometrics(
            hunterName = hunterName,
            hunterTitle = hunterTitle,
            weightKg = weightKg,
            heightCm = heightCm,
            age = age,
            biologicalSex = biologicalSex,
            avatarBase64 = avatarImageBase64
        )
    }

    fun updateBiometricsWithGoal(
        hunterName: String,
        hunterTitle: String,
        weightKg: Double,
        heightCm: Double,
        age: Int,
        biologicalSex: String,
        avatarImageBase64: String?,
        targetWeightKg: Double,
        physiqueGoalType: String
    ) {
        updateProfileAndBiometrics(
            hunterName = hunterName,
            hunterTitle = hunterTitle,
            weightKg = weightKg,
            heightCm = heightCm,
            age = age,
            biologicalSex = biologicalSex,
            avatarBase64 = avatarImageBase64,
            targetWeightKg = targetWeightKg,
            targetPhysique = physiqueGoalType
        )
    }

    fun logProgressPhoto(
        title: String,
        caption: String,
        poseTag: String,
        photoBase64: String,
        setAsAvatar: Boolean
    ) {
        logProgressPhotoToJournal(
            title = title,
            caption = caption,
            poseTag = poseTag,
            photoBase64 = photoBase64,
            setAsAvatar = setAsAvatar
        )
    }

    private companion object {
        const val TAG = "HunterViewModel"
    }
}

class HunterViewModelFactory(
    private val context: android.content.Context,
    private val currentUserId: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HunterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HunterViewModel(
                repository = HunterRepository(context.applicationContext),
                currentUserId = currentUserId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

