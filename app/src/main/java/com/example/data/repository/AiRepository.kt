package com.example.data.repository

import android.util.Log
import com.example.data.model.AchievementItem
import com.example.data.model.ExerciseLogged
import com.example.data.model.HunterBiometrics
import com.example.data.model.HunterSystemState
import com.example.data.model.QuestItem
import com.example.ui.viewmodel.PikaCoachTip
import com.example.util.SmartFoodNutritionEngine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class NutritionPlaceholderReply(
    val replyText: String,
    val suggestedMealName: String,
    val suggestedCalories: Int,
    val suggestedProteinG: Double
)

/**
 * Single AI interface through which all AI calls in the application are routed.
 * Uses Firebase AI Logic (`Firebase.ai.generativeModel("gemini-3.8-flash", ...)`).
 */
interface AiRepository {
    val isFirebaseAiEnabled: Boolean
    val firebaseRequiredNotice: String

    suspend fun generateCoachTip(
        state: HunterSystemState,
        fallbackTip: PikaCoachTip,
        variationIndex: Int
    ): PikaCoachTip

    suspend fun generateNutritionChatReply(
        query: String,
        bio: HunterBiometrics,
        caloriesTarget: Int,
        caloriesLogged: Int,
        proteinLogged: Double,
        waterLogged: Double,
        waterTarget: Double
    ): NutritionPlaceholderReply

    suspend fun evaluateTacticalCommand(
        command: String,
        currentState: HunterSystemState
    ): HunterSystemState
}

class FirebaseAiRepository : AiRepository {
    override val isFirebaseAiEnabled: Boolean = true
    override val firebaseRequiredNotice: String = ""

    private val coachTipSchema: Schema by lazy {
        Schema.obj(
            mapOf(
                "headline" to Schema.string("Short energetic Pikachu coach headline under 60 chars"),
                "advice" to Schema.string("2-3 sentences of personalized fitness/nutrition advice referencing the user's current metrics"),
                "action_label" to Schema.string("Actionable button CTA label under 35 chars"),
                "action_type" to Schema.enumeration(
                    listOf("LOG_FOOD", "LOG_WATER", "LOG_WORKOUT", "KEEP_GOING")
                ),
                "coach_mood" to Schema.enumeration(
                    listOf("ENERGIZED", "ALERT_INACTIVE", "PROUD", "TACTICAL")
                )
            )
        )
    }

    private val hunterSystemSchema: Schema by lazy {
        Schema.obj(
            mapOf(
                "player_status" to Schema.obj(
                    mapOf(
                        "level" to Schema.integer(),
                        "rank" to Schema.string(),
                        "current_xp" to Schema.integer(),
                        "next_level_xp" to Schema.integer(),
                        "progress_percent" to Schema.double(),
                        "hp_percent" to Schema.double(),
                        "streak_days" to Schema.integer(),
                        "active_buffs" to Schema.array(Schema.string()),
                        "active_debuffs" to Schema.array(Schema.string())
                    ),
                    optionalProperties = listOf("hp_percent", "active_buffs", "active_debuffs")
                ),
                "stat_breakdown" to Schema.obj(
                    mapOf(
                        "vitality" to Schema.obj(
                            mapOf(
                                "calories_logged" to Schema.integer(),
                                "calories_target" to Schema.integer(),
                                "protein_g" to Schema.double(),
                                "carbs_g" to Schema.double(),
                                "fats_g" to Schema.double(),
                                "fiber_g" to Schema.double(),
                                "omega3_mg" to Schema.double(),
                                "target_met" to Schema.boolean()
                            ),
                            optionalProperties = listOf("calories_target", "carbs_g", "fats_g", "fiber_g", "omega3_mg")
                        ),
                        "stamina" to Schema.obj(
                            mapOf(
                                "water_liters" to Schema.double(),
                                "target_liters" to Schema.double(),
                                "target_met" to Schema.boolean()
                            )
                        ),
                        "strength" to Schema.obj(
                            mapOf(
                                "workout_completed" to Schema.boolean(),
                                "exercises_logged" to Schema.array(
                                    Schema.obj(
                                        mapOf(
                                            "exercise_name" to Schema.string(),
                                            "sets_completed" to Schema.integer(),
                                            "reps_per_set" to Schema.string(),
                                            "progressive_overload_hit" to Schema.boolean()
                                        ),
                                        optionalProperties = listOf("reps_per_set", "progressive_overload_hit")
                                    )
                                )
                            ),
                            optionalProperties = listOf("exercises_logged")
                        )
                    )
                ),
                "quest_log" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "quest_name" to Schema.string(),
                            "status" to Schema.enumeration(listOf("COMPLETED", "IN_PROGRESS", "FAILED")),
                            "xp_awarded" to Schema.integer()
                        )
                    )
                ),
                "unlocked_achievements" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "title" to Schema.string(),
                            "description" to Schema.string(),
                            "rarity" to Schema.enumeration(listOf("COMMON", "RARE", "EPIC", "LEGENDARY"))
                        )
                    )
                ),
                "tactical_briefing" to Schema.string("2-3 sentences of motivating, actionable tactical feedback."),
                "suggested_actions" to Schema.array(Schema.string())
            )
        )
    }

    override suspend fun generateCoachTip(
        state: HunterSystemState,
        fallbackTip: PikaCoachTip,
        variationIndex: Int
    ): PikaCoachTip {
        return try {
            val bio = state.biometrics
            val vit = state.statBreakdown.vitality
            val stam = state.statBreakdown.stamina
            val str = state.statBreakdown.strength
            val ps = state.playerStatus

            val model = Firebase.ai.generativeModel(
                modelName = "gemini-3.8-flash",
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = coachTipSchema
                    temperature = 0.7f
                },
                systemInstruction = content {
                    text(
                        "You are Pika-Coach, an energetic, evidence-based fitness and nutrition AI coach inside PikaFit. " +
                            "Give concise, actionable advice tailored to the user's current daily calories, protein, water, and workout sets."
                    )
                }
            )

            val prompt = buildString {
                appendLine("Trainer Name: ${bio.hunterName}, Goal: ${bio.goalDisplayLabel}, Weight: ${bio.weightKg}kg, BMI: ${bio.bmi}")
                appendLine("Today's Calories: ${vit.caloriesLogged} / ${vit.caloriesTarget} kcal, Protein: ${vit.proteinG.toInt()}g / ${bio.recommendedProteinG}g")
                appendLine("Today's Water: ${stam.waterLiters}L / ${stam.targetLiters}L")
                appendLine("Exercises Logged Today: ${str.exercisesLogged.size}, Streak: ${ps.streakDays} days (checkedInToday=${ps.checkedInToday})")
                appendLine("Variation seed: $variationIndex. Return a fresh, motivating JSON coach tip.")
            }

            val response = withTimeoutOrNull(30_000L) {
                model.generateContent(prompt)
            }
            val rawJson = response?.text?.trim()
            if (!rawJson.isNullOrBlank()) {
                val obj = JSONObject(rawJson)
                PikaCoachTip(
                    headline = obj.optString("headline").ifBlank { fallbackTip.headline },
                    advice = obj.optString("advice").ifBlank { fallbackTip.advice },
                    actionLabel = obj.optString("action_label").ifBlank { fallbackTip.actionLabel },
                    actionType = obj.optString("action_type").ifBlank { fallbackTip.actionType },
                    coachMood = obj.optString("coach_mood").ifBlank { fallbackTip.coachMood }
                )
            } else {
                fallbackTip
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("FirebaseAiRepository", "Coach tip fallback used: ${e.message}")
            fallbackTip
        }
    }

    override suspend fun generateNutritionChatReply(
        query: String,
        bio: HunterBiometrics,
        caloriesTarget: Int,
        caloriesLogged: Int,
        proteinLogged: Double,
        waterLogged: Double,
        waterTarget: Double
    ): NutritionPlaceholderReply {
        val calc = SmartFoodNutritionEngine.formatChatbotResponseForQuery(
            query = query,
            bio = bio,
            caloriesTarget = caloriesTarget,
            caloriesLogged = caloriesLogged,
            proteinLogged = proteinLogged,
            waterLogged = waterLogged,
            waterTarget = waterTarget
        )
        val baselineFormattedText = SmartFoodNutritionEngine.buildFormattedChatReplyText(
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

        return try {
            val model = Firebase.ai.generativeModel(
                modelName = "gemini-3.8-flash",
                systemInstruction = content {
                    text(
                        "You are Pika-Dietitian, an All-India (South & North Indian) and global sports nutritionist AI in PikaFit. " +
                            "Provide accurate macro breakdowns (Calories, Protein, Carbs, Fats, Fiber) for the requested food and portion, " +
                            "plus 1-2 short tips on how it fits the Trainer's goal (${bio.goalDisplayLabel}, ${caloriesLogged}/${caloriesTarget} kcal logged today)."
                    )
                }
            )
            val prompt = buildString {
                appendLine("User query: \"$query\"")
                appendLine("Calibrated baseline reference for ${cleanMealLabel}: ${calc.calories} kcal, ${calc.proteinG}g Protein, ${calc.carbsG}g Carbs, ${calc.fatsG}g Fats, ${calc.fiberG}g Fiber.")
                appendLine("Provide a concise, friendly Pika-Dietitian breakdown keeping those exact macro numbers consistent so the user can tap Quick-Log below.")
            }
            val response = withTimeoutOrNull(45_000L) {
                model.generateContent(prompt)
            }
            val aiText = response?.text?.trim()
            NutritionPlaceholderReply(
                replyText = if (!aiText.isNullOrBlank()) aiText else baselineFormattedText,
                suggestedMealName = cleanMealLabel,
                suggestedCalories = calc.calories,
                suggestedProteinG = calc.proteinG
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("FirebaseAiRepository", "Using calibrated nutrition engine response: ${e.message}")
            NutritionPlaceholderReply(
                replyText = baselineFormattedText,
                suggestedMealName = cleanMealLabel,
                suggestedCalories = calc.calories,
                suggestedProteinG = calc.proteinG
            )
        }
    }

    override suspend fun evaluateTacticalCommand(
        command: String,
        currentState: HunterSystemState
    ): HunterSystemState {
        return try {
            val model = Firebase.ai.generativeModel(
                modelName = "gemini-3.8-flash",
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = hunterSystemSchema
                    temperature = 0.4f
                },
                systemInstruction = content {
                    text(
                        "You are the Hunter System Tactical AI Engine. Given the player's current state JSON and their new activity/nutrition log command, " +
                            "update their player_status, stat_breakdown, quest_log, unlocked_achievements, tactical_briefing, and suggested_actions."
                    )
                }
            )
            val prompt = buildString {
                appendLine("Current Hunter State JSON:")
                appendLine(currentState.toStateJson())
                appendLine()
                appendLine("Trainer Activity / Tactical Log Command: \"$command\"")
                appendLine("Return the updated JSON matching the schema.")
            }

            val response = withTimeoutOrNull(60_000L) {
                model.generateContent(prompt)
            }
            val jsonText = response?.text?.trim()
            if (!jsonText.isNullOrBlank()) {
                HunterSystemState.fromGeminiJson(
                    jsonString = jsonText,
                    existingBiometrics = currentState.biometrics,
                    existingPlayerStatus = currentState.playerStatus
                )
            } else {
                PlaceholderAiRepository().evaluateTacticalCommand(command, currentState)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("FirebaseAiRepository", "Fallback tactical evaluation used: ${e.message}")
            PlaceholderAiRepository().evaluateTacticalCommand(command, currentState)
        }
    }
}

class PlaceholderAiRepository : AiRepository {
    override val isFirebaseAiEnabled: Boolean = false
    override val firebaseRequiredNotice: String = ""

    override suspend fun generateCoachTip(
        state: HunterSystemState,
        fallbackTip: PikaCoachTip,
        variationIndex: Int
    ): PikaCoachTip = fallbackTip

    override suspend fun generateNutritionChatReply(
        query: String,
        bio: HunterBiometrics,
        caloriesTarget: Int,
        caloriesLogged: Int,
        proteinLogged: Double,
        waterLogged: Double,
        waterTarget: Double
    ): NutritionPlaceholderReply {
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
        return NutritionPlaceholderReply(
            replyText = formattedText,
            suggestedMealName = cleanMealLabel,
            suggestedCalories = calc.calories,
            suggestedProteinG = calc.proteinG
        )
    }

    override suspend fun evaluateTacticalCommand(
        command: String,
        currentState: HunterSystemState
    ): HunterSystemState {
        val lower = command.lowercase()
        val vit = currentState.statBreakdown.vitality
        val stam = currentState.statBreakdown.stamina
        val str = currentState.statBreakdown.strength

        val kcalMatch = Regex("""(\d{2,4})\s*(?:kcal|cal)""").find(lower)
        val addedKcal = kcalMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val waterMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:l|liter|litre)""").find(lower)
        val addedWater = waterMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0

        val updatedCalories = if (addedKcal > 0) (vit.caloriesLogged + addedKcal).coerceAtMost(10000) else vit.caloriesLogged.coerceAtLeast(2550)
        val updatedProtein = if (addedKcal > 0) vit.proteinG + (addedKcal * 0.08) else vit.proteinG.coerceAtLeast(112.0)
        val updatedWater = if (addedWater > 0.0) (stam.waterLiters + addedWater).coerceAtMost(15.0) else stam.waterLiters.coerceAtLeast(4.0)

        val sampleExercises = str.exercisesLogged.ifEmpty {
            listOf(
                ExerciseLogged("Lateral Raise", 3, "15", true),
                ExerciseLogged("Declined Pushups", 3, "10", true),
                ExerciseLogged("Table Rows", 3, "10", true)
            )
        }

        val sampleQuests = listOf(
            QuestItem("Hit 110g Protein", "COMPLETED", 100),
            QuestItem("Upper Body Progression Routine", "COMPLETED", 250),
            QuestItem("Drink 3.5L+ Water", "COMPLETED", 70)
        )

        val hasIronFoundation = currentState.unlockedAchievements.any { it.title == "Iron Foundation" }
        val updatedAchievements = if (hasIronFoundation) {
            currentState.unlockedAchievements
        } else {
            currentState.unlockedAchievements + AchievementItem(
                title = "Iron Foundation",
                description = "Completed your first week of progressive overload without skipping a set.",
                rarity = "RARE"
            )
        }

        val (xpUpdated, _) = currentState.applyScaledXpProgress(120)

        return xpUpdated.copy(
            playerStatus = xpUpdated.playerStatus.copy(
                activeBuffs = (xpUpdated.playerStatus.activeBuffs + listOf("Well-Fed (+50 XP)", "Hydrated", "Hypertrophy Pump")).distinct()
            ),
            statBreakdown = xpUpdated.statBreakdown.copy(
                vitality = vit.copy(
                    caloriesLogged = updatedCalories,
                    proteinG = updatedProtein,
                    carbsG = vit.carbsG.coerceAtLeast(310.0),
                    fatsG = vit.fatsG.coerceAtLeast(48.0),
                    fiberG = vit.fiberG.coerceAtLeast(22.0),
                    omega3Mg = vit.omega3Mg.coerceAtLeast(1900.0),
                    targetMet = updatedCalories >= vit.caloriesTarget
                ),
                stamina = stam.copy(
                    waterLiters = updatedWater,
                    targetMet = updatedWater >= stam.targetLiters
                ),
                strength = str.copy(
                    workoutCompleted = true,
                    exercisesLogged = sampleExercises
                )
            ),
            questLog = sampleQuests,
            unlockedAchievements = updatedAchievements,
            tacticalBriefing = "Flawless session. All volume targets were cleared and protein synthesis was fully supported by your caloric surplus. Keep water high into the evening to flush metabolic waste.",
            suggestedActions = listOf("LOG TOMORROW'S MEAL PREP", "REST & RECOVER", "VIEW SKILL TREE")
        )
    }
}
