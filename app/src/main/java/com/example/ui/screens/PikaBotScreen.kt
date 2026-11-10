package com.example.ui.screens

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.HunterSystemState
import com.example.ui.theme.*
import com.example.ui.viewmodel.NutritionChatMessage
import com.example.util.SmartFoodNutritionEngine

@Composable
fun PikaBotScreen(
    state: HunterSystemState,
    messages: List<NutritionChatMessage>,
    isLoading: Boolean,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onLogRecommendedFood: (mealName: String, calories: Int, proteinG: Double) -> Unit,
    onApplyDietPlan: (
        messageId: String,
        goalKey: String,
        planTitle: String,
        caloriesTarget: Int,
        proteinTargetG: Int,
        waterTargetLiters: Double
    ) -> Unit = { _, _, _, _, _, _ -> }
) {
    val bio = state.biometrics
    val vit = state.statBreakdown.vitality
    val stam = state.statBreakdown.stamina
    val currentTier = state.playerStatus.currentTier

    var queryInput by rememberSaveable { mutableStateOf("") }
    var showSmartCalculator by rememberSaveable { mutableStateOf(false) }
    var calcFoodQuery by rememberSaveable { mutableStateOf("") }
    var calcAmountText by rememberSaveable { mutableStateOf("150") }
    var isQuantityMode by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // 100% Real user telemetry (no fake hardcoded minimums)
    val calLeft = (vit.caloriesTarget - vit.caloriesLogged).coerceAtLeast(0)
    val proLeft = (bio.recommendedProteinG - vit.proteinG.toInt()).coerceAtLeast(0)
    val waterCurrent = stam.waterLiters

    val calculatedPreview = remember(calcFoodQuery, calcAmountText, isQuantityMode) {
        val query = calcFoodQuery.trim().ifBlank { "Idli" }
        val amt = calcAmountText.toIntOrNull()?.coerceIn(1, 3000) ?: if (isQuantityMode) 3 else 150
        SmartFoodNutritionEngine.calculateForGramsOrQuantity(
            foodInput = query,
            amountValue = amt,
            isQuantityMode = isQuantityMode
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("pikabot_screen")
    ) {
        // Top App Title Bar + Smart Grams Calculator Toggle + Clear Chat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { showSmartCalculator = !showSmartCalculator },
                color = if (showSmartCalculator) NeonAmber.copy(alpha = 0.2f) else ObsidianCard,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.6f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Calculate,
                        contentDescription = "Smart Grams Calculator",
                        tint = NeonAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showSmartCalculator) "Hide Grams Calc" else "Smart Grams Calc",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = NeonAmber
                    )
                }
            }

            Text(
                text = "PikaBot AI",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = TextPrimary
            )

            IconButton(
                onClick = onClearChat,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(1.dp, NeonAmber.copy(alpha = 0.4f), CircleShape)
                    .testTag("pikabot_clear_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Clear Chat",
                    tint = NeonAmber,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Collapsible Smart Grams / Pieces Food Calculator Drawer
        AnimatedVisibility(visible = showSmartCalculator) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                border = BorderStroke(1.dp, NeonAmber),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SMART INDIAN & GLOBAL PORTION CALCULATOR",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = PikaYellow
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !isQuantityMode,
                                onClick = {
                                    isQuantityMode = false
                                    calcAmountText = "150"
                                },
                                label = { Text("Grams (g)", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = isQuantityMode,
                                onClick = {
                                    isQuantityMode = true
                                    calcAmountText = "3"
                                },
                                label = { Text("Pieces / Qty", fontSize = 10.sp) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = calcFoodQuery,
                            onValueChange = { calcFoodQuery = it },
                            label = { Text("Food Name") },
                            placeholder = { Text("e.g., Idli, Dosa, Chicken Gravy, Paneer") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                        OutlinedTextField(
                            value = calcAmountText,
                            onValueChange = { calcAmountText = it.filter { ch -> ch.isDigit() } },
                            label = { Text(if (isQuantityMode) "Pieces" else "Grams") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${calculatedPreview.foodName} (${calculatedPreview.servingLabel}): ${calculatedPreview.calories} kcal • P: ${calculatedPreview.proteinG}g • C: ${calculatedPreview.carbsG}g • F: ${calculatedPreview.fatsG}g",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onLogRecommendedFood(
                                "${calculatedPreview.foodName} (${calculatedPreview.servingLabel})",
                                calculatedPreview.calories,
                                calculatedPreview.proteinG
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonAmber,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "LOG ${calculatedPreview.calories} KCAL (${calculatedPreview.proteinG}g P) TO DASHBOARD",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Hero Header Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardSurfaceGradient)
                .border(1.dp, BorderAmberGlow, RoundedCornerShape(22.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ObsidianBg)
                        .border(2.5.dp, NeonAmber, CircleShape)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pika_logo_hi),
                        contentDescription = "PikaBot Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "PIKABOT AI",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            letterSpacing = 0.6.sp
                        ),
                        color = NeonAmber
                    )
                    Text(
                        text = "NUTRITIONIST",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            letterSpacing = 0.6.sp
                        ),
                        color = PikaYellow
                    )
                    Text(
                        text = "South & North Indian Food Database • Custom Diet Plans",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2x2 Real Status Pill Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatusPill(
                    icon = Icons.Filled.Bolt,
                    prefix = "KCAL LEFT: ",
                    highlight = "$calLeft / ${vit.caloriesTarget}",
                    borderColor = PikaYellow,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatusPill(
                    icon = Icons.Filled.FitnessCenter,
                    prefix = "PROTEIN LEFT: ",
                    highlight = "${proLeft}g / ${bio.recommendedProteinG}g",
                    borderColor = NeonAmber,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatusPill(
                    icon = Icons.Filled.WaterDrop,
                    prefix = "WATER: ",
                    highlight = "${waterCurrent}L / ${stam.targetLiters}L",
                    borderColor = HunterCyan,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatusPill(
                    icon = Icons.Filled.EmojiEvents,
                    prefix = "CLASS: ",
                    highlight = currentTier.classTitle.uppercase(),
                    borderColor = NeonOrange,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Prompt Chips Row 1 & Row 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FoodPromptChip(
                text = "3 Dosa 4 Idli ⚡",
                selected = true,
                onClick = { onSendMessage("I had 3 Dosa and 4 Idli for breakfast.") }
            )
            FoodPromptChip(
                text = "150g Poha 🥟",
                selected = false,
                onClick = { onSendMessage("150g poha") }
            )
            FoodPromptChip(
                text = "180g Chicken Gravy 🍗",
                selected = false,
                onClick = { onSendMessage("180g chicken gravy") }
            )
            FoodPromptChip(
                text = "Chicken Biryani 🍲",
                selected = false,
                onClick = { onSendMessage("1 plate chicken biryani") }
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FoodPromptChip(
                text = "South Indian Diet Plan 🍛",
                selected = false,
                onClick = { onSendMessage("Switch to South Indian diet plan") }
            )
            FoodPromptChip(
                text = "🍱 Weight Loss Plan",
                selected = false,
                onClick = { onSendMessage("Weight loss diet plan") }
            )
            FoodPromptChip(
                text = "💪 Muscle Gain Plan",
                selected = false,
                onClick = { onSendMessage("Weight gain muscle bulk diet plan") }
            )
            FoodPromptChip(
                text = "🥗 Veg High-Protein Plan",
                selected = false,
                onClick = { onSendMessage("Indian vegetarian high protein diet plan") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Real Dynamic Chat Stream (No fake pre-populated user messages)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.isUser
                if (isUser) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Surface(
                            color = ObsidianCardElevated,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.5f)),
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.pika_logo_hi),
                            contentDescription = "PikaBot",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, NeonAmber, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                            border = BorderStroke(1.dp, BorderAmberGlow),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = PikaYellow, fontWeight = FontWeight.Bold)) {
                                            append("PikaBot AI: ")
                                        }
                                        withStyle(SpanStyle(color = TextPrimary)) {
                                            append(msg.text)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )

                                // Rich Nutrient Breakdown Card when a food item was analyzed
                                if (msg.suggestedCalories != null && msg.suggestedCalories > 0) {
                                    val mealTitle = msg.suggestedMealName?.ifBlank { "Analyzed Meal" } ?: "Analyzed Meal"
                                    val cals = msg.suggestedCalories
                                    val pro = msg.suggestedProteinG ?: 20.0
                                    val estCarbs = ((cals - (pro * 4.0)) * 0.65 / 4.0).toInt().coerceAtLeast(5)
                                    val estFats = ((cals - (pro * 4.0)) * 0.35 / 9.0).toInt().coerceAtLeast(2)

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = ObsidianBg.copy(alpha = 0.75f),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, BorderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "$mealTitle ⚡",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = TextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                progress = { (cals / 900f).coerceIn(0.15f, 1f) },
                                                color = PikaYellow,
                                                trackColor = BorderSubtle,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Protein: ${pro}g",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "$cals kcal",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = PikaYellow
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { (pro.toFloat() / 60f).coerceIn(0.12f, 1f) },
                                                color = NeonAmber,
                                                trackColor = BorderSubtle,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Carbs: ~${estCarbs}g",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary
                                                )
                                                Text(
                                                    text = "Fats: ~${estFats}g",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = {
                                                    onLogRecommendedFood(mealTitle, cals, pro)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = NeonAmber,
                                                    contentColor = ObsidianBg
                                                ),
                                                shape = RoundedCornerShape(50),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(40.dp)
                                            ) {
                                                Text(
                                                    text = "LOG MEAL (+$cals KCAL • +50 XP)",
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Interactive Diet Plan Application Card when PikaBot builds a Diet Plan
                                if (msg.suggestedPlanCaloriesTarget != null && msg.suggestedPlanProteinG != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = ObsidianBg.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (msg.planApplied) HunterEmerald else PikaYellow
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = msg.suggestedPlanTitle ?: "Custom PikaFit Diet Plan",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = PikaYellow
                                            )
                                            Text(
                                                text = "Target: ${msg.suggestedPlanCaloriesTarget} kcal • ${msg.suggestedPlanProteinG}g Protein • ${msg.suggestedPlanWaterLiters ?: 3.5}L Water",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    onApplyDietPlan(
                                                        msg.id,
                                                        msg.suggestedPlanGoalKey ?: "WEIGHT_GAIN",
                                                        msg.suggestedPlanTitle ?: "Custom Diet Plan",
                                                        msg.suggestedPlanCaloriesTarget,
                                                        msg.suggestedPlanProteinG,
                                                        msg.suggestedPlanWaterLiters ?: 3.5
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (msg.planApplied) HunterEmerald else PikaYellow,
                                                    contentColor = ObsidianBg
                                                ),
                                                shape = RoundedCornerShape(50),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (msg.planApplied) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "DIET PLAN APPLIED TO DASHBOARD",
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                } else {
                                                    Text(
                                                        text = "⚡ APPLY THIS DIET PLAN TO MY DASHBOARD",
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = queryInput,
                onValueChange = { queryInput = it },
                placeholder = {
                    Text(
                        text = "Enter your Indian meal or ask for a diet plan...",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonAmber,
                    unfocusedBorderColor = NeonAmber.copy(alpha = 0.5f),
                    focusedContainerColor = ObsidianCard,
                    unfocusedContainerColor = ObsidianCard,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("pikabot_input_field")
            )

            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(PikaYellow, NeonOrange)
                        )
                    )
                    .clickable {
                        if (queryInput.isNotBlank()) {
                            onSendMessage(queryInput)
                            queryInput = ""
                        }
                    }
                    .testTag("pikabot_send_button"),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = ObsidianBg,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = ObsidianBg,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryStatusPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    prefix: String,
    highlight: String,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianBg.copy(alpha = 0.65f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) {
                        append(prefix)
                    }
                    withStyle(SpanStyle(color = borderColor, fontWeight = FontWeight.ExtraBold)) {
                        append(highlight)
                    }
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FoodPromptChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) NeonAmber.copy(alpha = 0.18f) else ObsidianCard,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (selected) PikaYellow else BorderAmberGlow)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            color = if (selected) PikaYellow else TextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}