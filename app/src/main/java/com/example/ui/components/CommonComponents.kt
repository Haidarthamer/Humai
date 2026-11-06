package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Citation
import com.example.core.model.EmotionState
import com.example.core.model.SimulatedEmotion
import com.example.ui.theme.CompanionBorder
import com.example.ui.theme.CompanionCyan
import com.example.ui.theme.CompanionCyanBright
import com.example.ui.theme.CompanionSurface
import com.example.ui.theme.CompanionSurfaceVariant
import com.example.ui.theme.MoodCalmness
import com.example.ui.theme.MoodConfusion
import com.example.ui.theme.MoodCuriosity
import com.example.ui.theme.MoodExcitement
import com.example.ui.theme.MoodFrustration
import com.example.ui.theme.MoodHappiness
import com.example.ui.theme.MoodInterest
import com.example.ui.theme.MoodSadness

@Composable
fun getEmotionColor(emotion: SimulatedEmotion): Color {
    return when (emotion) {
        SimulatedEmotion.HAPPINESS -> MoodHappiness
        SimulatedEmotion.CURIOSITY -> MoodCuriosity
        SimulatedEmotion.EXCITEMENT -> MoodExcitement
        SimulatedEmotion.CALMNESS -> MoodCalmness
        SimulatedEmotion.CONFUSION -> MoodConfusion
        SimulatedEmotion.SADNESS -> MoodSadness
        SimulatedEmotion.FRUSTRATION -> MoodFrustration
        SimulatedEmotion.INTEREST -> MoodInterest
    }
}

@Composable
fun CompanionNeuralAvatar(
    emotionState: EmotionState,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val emotionColor = getEmotionColor(emotionState.current)
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .size(size)
            .testTag("companion_avatar"),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring
        Canvas(modifier = Modifier.size(size)) {
            val radius = (this.size.minDimension / 2f) * pulseScale * 0.95f
            drawCircle(
                color = emotionColor.copy(alpha = 0.22f),
                radius = radius,
                center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(emotionColor.copy(alpha = 0.45f), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                color = emotionColor.copy(alpha = 0.65f),
                radius = radius * 0.85f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Inner core
        Box(
            modifier = Modifier
                .size(size * 0.62f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(CompanionCyanBright, emotionColor, Color(0xFF04101E))
                    )
                )
                .border(1.5.dp, emotionColor, CircleShape)
        )
    }
}

@Composable
fun FuturisticCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CompanionBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = if (onClick != null) {
            modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() }
        } else {
            modifier
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CompanionSurface
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        content()
    }
}

@Composable
fun EmotionIndicatorPill(
    emotionState: EmotionState,
    isArabic: Boolean = false,
    modifier: Modifier = Modifier
) {
    val color = getEmotionColor(emotionState.current)
    val label = if (isArabic) emotionState.current.arabicName else emotionState.current.displayName

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.FiberManualRecord,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(10.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label ${(emotionState.intensity * 100).toInt()}%",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun CitationBadge(
    citation: Citation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pageStr = if (citation.pageNumber != null) " p.${citation.pageNumber}" else ""
    val timeStr = if (citation.timestampSeconds != null) " ${citation.timestampSeconds}s" else ""

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CompanionSurfaceVariant)
            .border(1.dp, CompanionCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = CompanionCyan,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${citation.documentTitle}$pageStr$timeStr",
            color = CompanionCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
