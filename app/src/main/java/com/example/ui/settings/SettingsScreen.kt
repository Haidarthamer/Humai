package com.example.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.LanguageMode
import com.example.ui.CompanionViewModel
import com.example.ui.components.FuturisticCard
import com.example.ui.theme.CompanionBackground
import com.example.ui.theme.CompanionBorder
import com.example.ui.theme.CompanionCyan
import com.example.ui.theme.CompanionCyanBright
import com.example.ui.theme.CompanionCyanDark
import com.example.ui.theme.CompanionError
import com.example.ui.theme.CompanionSuccess
import com.example.ui.theme.CompanionSurface
import com.example.ui.theme.CompanionSurfaceElevated
import com.example.ui.theme.CompanionSurfaceVariant
import com.example.ui.theme.CompanionTextPrimary
import com.example.ui.theme.CompanionTextSecondary

@Composable
fun SettingsScreen(
    viewModel: CompanionViewModel,
    modifier: Modifier = Modifier
) {
    val language by viewModel.languageMode.collectAsState()
    val isArabic = language != LanguageMode.ENGLISH

    var showClearDataDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CompanionBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = if (isArabic) "الإعدادات والخصوصية" else "Settings & Privacy Vault",
            color = CompanionCyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isArabic) "إعدادات اللغة، الأمان المحلي التام، والتحكم بالبيانات" else "Language configuration, zero-cloud privacy & data governance",
            color = CompanionTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Language Preferences
        Text(
            text = if (isArabic) "لغة التواصل المعرفي:" else "Primary Communication Dialect:",
            color = CompanionCyanBright,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        listOf(
            Triple(LanguageMode.ENGLISH, "English", "Native English dialect and technical vocabulary"),
            Triple(LanguageMode.ARABIC_MSA, "العربية الفصحى (Modern Standard Arabic)", "صياغة فصحى دقيقة وبلاغية لدراسة الأبحاث والكتب"),
            Triple(LanguageMode.ARABIC_IRAQI, "اللهجة العراقية (Iraqi Arabic)", "محادثة عفوية وقريبة بالعامية العراقية اليومية")
        ).forEach { (mode, name, desc) ->
            val isSelected = language == mode
            FuturisticCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                borderColor = if (isSelected) CompanionCyan else CompanionBorder,
                onClick = { viewModel.setLanguage(mode) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) CompanionCyanDark else CompanionSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = if (isSelected) CompanionCyan else CompanionTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            color = CompanionTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = desc,
                            color = CompanionTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Privacy Guarantee Card
        FuturisticCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = CompanionSuccess.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = CompanionSuccess)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "ضمان الخصوصية والأمان الأوفلاين" else "Strict Offline Privacy Guarantee",
                        color = CompanionSuccess,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isArabic) """
                        • لا توجد أي اتصالات بسيرفرات خارجية أو واجهات ذكاء اصطناعي سحابية.
                        • جميع الوثائق والكتب والذاكرة ومقاطع الفيديو تُعالج محلياً 100% داخل مساحة التخزين الآمنة بتطبيقك.
                        • لا يتم جمع أو تتبع أي بيانات استخدام أو تحليلات.
                    """.trimIndent() else """
                        • Zero cloud telemetry, no remote API calls, no third-party tracking.
                        • All PDF embeddings, video frames, and 4-tier memories are stored strictly in local device SQLite Room.
                        • Full data sovereignity: you can wipe all local storage anytime.
                    """.trimIndent(),
                    color = CompanionTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Clear All Data Section
        Button(
            onClick = { showClearDataDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = CompanionError.copy(alpha = 0.2f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CompanionError, RoundedCornerShape(12.dp))
                .testTag("clear_all_data_button")
        ) {
            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = CompanionError)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isArabic) "مسح جميع البيانات والذكريات المخزنة" else "Erase All Cognitive Data & Memories",
                color = CompanionError,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Erase All Data?", color = CompanionError) },
            text = {
                Text(
                    "This action permanently clears all conversations, imported PDFs, documents, knowledge chunks, and 4-tier memories from your device.",
                    color = CompanionTextPrimary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CompanionError)
                ) {
                    Text("Confirm Erase", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel", color = CompanionTextSecondary)
                }
            },
            containerColor = CompanionSurface
        )
    }
}
