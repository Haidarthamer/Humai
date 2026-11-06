package com.example.ui.personality

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CommunicationStyle
import com.example.core.model.LanguageMode
import com.example.core.model.PersonalityTraits
import com.example.ui.CompanionViewModel
import com.example.ui.components.CompanionNeuralAvatar
import com.example.ui.components.FuturisticCard
import com.example.ui.components.getEmotionColor
import com.example.ui.theme.CompanionBackground
import com.example.ui.theme.CompanionBorder
import com.example.ui.theme.CompanionCyan
import com.example.ui.theme.CompanionCyanBright
import com.example.ui.theme.CompanionCyanDark
import com.example.ui.theme.CompanionSurface
import com.example.ui.theme.CompanionSurfaceElevated
import com.example.ui.theme.CompanionSurfaceVariant
import com.example.ui.theme.CompanionTextPrimary
import com.example.ui.theme.CompanionTextSecondary

@Composable
fun PersonalityScreen(
    viewModel: CompanionViewModel,
    modifier: Modifier = Modifier
) {
    val traits by viewModel.personalityEngine.traits.collectAsState()
    val emotionState by viewModel.personalityEngine.emotion.collectAsState()
    val evolutionLog by viewModel.personalityEngine.evolutionHistory.collectAsState()
    val language by viewModel.languageMode.collectAsState()
    val isArabic = language != LanguageMode.ENGLISH

    val emotionColor = getEmotionColor(emotionState.current)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CompanionBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isArabic) "محرك الشخصية والمشاعر المحاكاة" else "Adaptive Persona & Emotion Engine",
                    color = CompanionCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isArabic) "سمات متطورة ومصفوفة عاطفية حسابية مستقلة" else "Computational psychological state & evolutionary traits",
                    color = CompanionTextSecondary,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = { viewModel.resetPersonality() },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CompanionSurfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Reset Matrix",
                    tint = CompanionCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Emotion Gauge Card
        FuturisticCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = emotionColor.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompanionNeuralAvatar(
                    emotionState = emotionState,
                    size = 64.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isArabic) "الحالة العاطفية المحاكاة:" else "Simulated Emotional State:",
                        color = CompanionTextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = if (isArabic) "${emotionState.current.arabicName} (${(emotionState.intensity * 100).toInt()}%)" else "${emotionState.current.displayName} (${(emotionState.intensity * 100).toInt()}%)",
                        color = emotionColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Trigger: ${emotionState.lastTrigger}",
                        color = CompanionTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 2
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Consciousness Disclaimer Note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CompanionSurfaceElevated)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = CompanionCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Notice: Emotional states are computational simulations influencing response tone, not biological consciousness.",
                color = CompanionTextSecondary,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Communication Style Selector
        Text(
            text = if (isArabic) "أسلوب التواصل المفضل:" else "Calibrated Communication Style:",
            color = CompanionCyanBright,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                CommunicationStyle.EMPATHETIC to if (isArabic) "ودود ومتعاطف" else "Empathetic",
                CommunicationStyle.ANALYTICAL to if (isArabic) "تحليلي دقيق" else "Analytical"
            ).forEach { (style, label) ->
                val isSelected = traits.communicationStyle == style
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) CompanionCyanDark else CompanionSurface)
                        .border(1.dp, if (isSelected) CompanionCyan else CompanionBorder, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updatePersonalityTraits(traits.copy(communicationStyle = style)) }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) CompanionCyan else CompanionTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                CommunicationStyle.PLAYFUL to if (isArabic) "مرح ومبتكر" else "Playful",
                CommunicationStyle.DIRECT to if (isArabic) "موجز ومباشر" else "Direct"
            ).forEach { (style, label) ->
                val isSelected = traits.communicationStyle == style
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) CompanionCyanDark else CompanionSurface)
                        .border(1.dp, if (isSelected) CompanionCyan else CompanionBorder, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updatePersonalityTraits(traits.copy(communicationStyle = style)) }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) CompanionCyan else CompanionTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Trait Calibration Sliders
        Text(
            text = if (isArabic) "معايرة السمات الشخصية:" else "Cognitive Trait Calibration:",
            color = CompanionCyanBright,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        FuturisticCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = CompanionBorder
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                TraitSliderRow(
                    label = if (isArabic) "الفضول والاستكشاف (Curiosity)" else "Curiosity",
                    value = traits.curiosity,
                    onValueChange = { viewModel.updatePersonalityTraits(traits.copy(curiosity = it)) }
                )
                TraitSliderRow(
                    label = if (isArabic) "روح الدعابة (Humor)" else "Humor",
                    value = traits.humor,
                    onValueChange = { viewModel.updatePersonalityTraits(traits.copy(humor = it)) }
                )
                TraitSliderRow(
                    label = if (isArabic) "الود والتقارب (Friendliness)" else "Friendliness",
                    value = traits.friendliness,
                    onValueChange = { viewModel.updatePersonalityTraits(traits.copy(friendliness = it)) }
                )
                TraitSliderRow(
                    label = if (isArabic) "الصبر والشرح المتأني (Patience)" else "Patience",
                    value = traits.patience,
                    onValueChange = { viewModel.updatePersonalityTraits(traits.copy(patience = it)) }
                )
                TraitSliderRow(
                    label = if (isArabic) "الإبداع والتخيل (Creativity)" else "Creativity",
                    value = traits.creativity,
                    onValueChange = { viewModel.updatePersonalityTraits(traits.copy(creativity = it)) }
                )
                TraitSliderRow(
                    label = if (isArabic) "الحماسة والدافعية (Enthusiasm)" else "Enthusiasm",
                    value = traits.enthusiasm,
                    onValueChange = { viewModel.updatePersonalityTraits(traits.copy(enthusiasm = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Evolution Log
        Text(
            text = if (isArabic) "سجل التطور التكيفي:" else "Adaptive Evolution Diary:",
            color = CompanionTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CompanionSurface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            evolutionLog.take(6).forEach { entry ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(text = "• ", color = CompanionCyan, fontSize = 12.sp)
                    Text(text = entry, color = CompanionTextPrimary, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
fun TraitSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = CompanionTextPrimary, fontSize = 12.sp)
            Text(text = "${(value * 100).toInt()}%", color = CompanionCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.05f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = CompanionCyan,
                activeTrackColor = CompanionCyan,
                inactiveTrackColor = CompanionSurfaceVariant
            )
        )
    }
}
