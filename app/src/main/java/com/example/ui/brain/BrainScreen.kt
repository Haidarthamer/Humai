package com.example.ui.brain

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ai.LlamaBridge
import com.example.core.model.GGUFModelInfo
import com.example.core.model.LanguageMode
import com.example.ui.CompanionViewModel
import com.example.ui.components.FuturisticCard
import com.example.ui.theme.CompanionBackground
import com.example.ui.theme.CompanionBorder
import com.example.ui.theme.CompanionCyan
import com.example.ui.theme.CompanionCyanBright
import com.example.ui.theme.CompanionCyanDark
import com.example.ui.theme.CompanionSuccess
import com.example.ui.theme.CompanionSurface
import com.example.ui.theme.CompanionSurfaceElevated
import com.example.ui.theme.CompanionSurfaceVariant
import com.example.ui.theme.CompanionTextPrimary
import com.example.ui.theme.CompanionTextSecondary
import java.io.File
import java.io.FileOutputStream

@Composable
fun BrainScreen(
    viewModel: CompanionViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val models by viewModel.modelManager.availableModels.collectAsState()
    val activeModel by viewModel.modelManager.activeModel.collectAsState()
    val temperature by viewModel.modelManager.temperature.collectAsState()
    val contextWindow by viewModel.modelManager.contextWindow.collectAsState()
    val gpuEnabled by viewModel.modelManager.gpuAcceleration.collectAsState()
    val language by viewModel.languageMode.collectAsState()
    val isArabic = language != LanguageMode.ENGLISH

    val report = viewModel.getHardwareDiagnostics()
    val isNativeLoaded = LlamaBridge.isNativeAvailable()

    val customModelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val displayName = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "imported_model.gguf"
            val fileName = displayName.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destFile = File(context.filesDir, "models/${System.currentTimeMillis()}_${fileName}")
            try {
                destFile.parentFile?.mkdirs()
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open selected file.")
                input.use { inp ->
                    FileOutputStream(destFile).use { out -> inp.copyTo(out) }
                }
                viewModel.modelManager.registerImportedModel(destFile)
                Toast.makeText(context, "Model imported and validated.", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                destFile.delete()
                Toast.makeText(context, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CompanionBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = if (isArabic) "المخ الاصطناعي وإدارة النماذج (AI Brain)" else "Local AI Brain & Model Management",
            color = CompanionCyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isArabic) "إدارة نماذج GGUF وذاكرة الوصول العشوائي ومحددات الاستدلال" else "Configure local GGUF models, RAM budgets, and inference metrics",
            color = CompanionTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Hardware Diagnostics HUD
        FuturisticCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = CompanionCyanDark
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Memory, contentDescription = null, tint = CompanionCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "تشخيص عتاد الجهاز (Hardware Specs)" else "Hardware Diagnostics & RAM",
                            color = CompanionCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isNativeLoaded) CompanionSuccess.copy(alpha = 0.2f) else CompanionCyanDark)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isNativeLoaded) "NATIVE RUNTIME READY" else "CMAKE BUILD REQUIRED",
                            color = if (isNativeLoaded) CompanionSuccess else CompanionCyanBright,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Total RAM: ${report.totalRamMb} MB", color = CompanionTextPrimary, fontSize = 12.sp)
                        Text(text = "Available: ${report.availableRamMb} MB", color = CompanionSuccess, fontSize = 11.sp)
                    }
                    Column {
                        Text(text = "Storage: ${"%.1f".format(report.availableStorageGb)} GB free", color = CompanionTextPrimary, fontSize = 12.sp)
                        Text(text = "CPU: ${report.cpuCores} cores (${report.supportedAbi})", color = CompanionTextSecondary, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Recommended Model: ${report.recommendedModelId.uppercase()}",
                    color = CompanionCyanBright,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Provisioning Progress HUD (shown when copying or if there's an error)
        val provProgress by viewModel.provisioningProgress.collectAsState()
        if (provProgress.isInstalling || provProgress.error != null) {
            FuturisticCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                borderColor = if (provProgress.error != null) Color(0xFFFF5252) else CompanionCyan
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (provProgress.error != null) "Model Weights Not Bundled" else "Preparing Local Models",
                            color = if (provProgress.error != null) CompanionCyanBright else CompanionCyanBright,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(provProgress.progress * 100).toInt()}%",
                            color = CompanionCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (provProgress.isInstalling) provProgress.progress else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = CompanionCyan,
                        trackColor = CompanionSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = provProgress.statusMessage,
                        color = CompanionTextSecondary,
                        fontSize = 11.sp
                    )
                    if (provProgress.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { customModelPicker.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = CompanionCyanDark)
                        ) {
                            Text(if (isArabic) "استيراد موديل GGUF" else "Import a GGUF model", color = CompanionCyanBright, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pre-Installed AI Models Section (Default text, vision, and projector)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isArabic) "كتالوج النماذج المحلية" else "Local Model Catalog",
                        color = CompanionCyanBright,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                }
                Text(
                    text = if (isArabic) "ملفات النماذج مو مرفقة بالتطبيق؛ استورد ملف GGUF حقيقي من جهازك." else "Model weights are not bundled. Import a genuine GGUF file from your device.",
                    color = CompanionTextSecondary,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = { customModelPicker.launch(arrayOf("*/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = CompanionSurfaceVariant),
                modifier = Modifier.testTag("import_custom_gguf_button")
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, tint = CompanionCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isArabic) "استيراد" else "Import", color = CompanionCyan, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Group into Preinstalled vs Optional
        val preinstalledModels = models.filter { it.isPreinstalled }
        val optionalModels = models.filter { !it.isPreinstalled }

        preinstalledModels.forEach { model ->
            val isSelected = activeModel?.id == model.id
            FuturisticCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                borderColor = if (isSelected) CompanionCyan else CompanionBorder,
                onClick = { viewModel.selectModel(model) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
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
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (model.integrityVerified) CompanionSuccess else CompanionCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = model.name,
                                    color = CompanionTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val roleTag = when (model.modelRole) {
                                    com.example.core.model.ModelRole.TEXT_DEFAULT -> "DEFAULT TEXT"
                                    com.example.core.model.ModelRole.VISION_MULTIMODAL -> "VISION MODEL"
                                    com.example.core.model.ModelRole.VISION_PROJECTOR -> "MMPROJ ADAPTER"
                                    com.example.core.model.ModelRole.OPTIONAL_TEXT -> "OPTIONAL"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(CompanionCyanDark)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(roleTag, color = CompanionCyanBright, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = "${model.architecture} • ${model.parameterCount} • ${model.quantization} • ${"%.2f".format(model.fileSizeBytes / (1024f * 1024f * 1024f))} GB",
                                color = CompanionTextSecondary,
                                fontSize = 11.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusText = if (model.isInstalled) {
                                    if (model.integrityVerified) "Installed & Verified (GGUF v3)" else "Installed"
                                } else "Not imported"
                                Text(
                                    text = "Status: $statusText • ${model.compatibility}",
                                    color = if (model.integrityVerified) CompanionSuccess else CompanionCyanBright,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (isSelected) {
                                val statusDetail by viewModel.modelManager.modelStatusDetail.collectAsState()
                                Text(
                                    text = "Active Engine: $statusDetail",
                                    color = CompanionCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CompanionCyan else CompanionSurfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isSelected) "ACTIVE" else "SELECT",
                            color = if (isSelected) Color(0xFF001F29) else CompanionTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (optionalModels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (isArabic) "النماذج المحلية المتاحة للاستيراد" else "Available Local Models (Manual Import)",
                color = CompanionCyanBright,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            optionalModels.forEach { model ->
                val isSelected = activeModel?.id == model.id
                FuturisticCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    borderColor = if (isSelected) CompanionCyan else CompanionBorder,
                    onClick = { viewModel.selectModel(model) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
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
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = CompanionCyan
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = model.name,
                                    color = CompanionTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${model.architecture} • ${model.parameterCount} • ${model.quantization} • ${model.fileSizeBytes / (1024 * 1024)} MB",
                                    color = CompanionTextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = if (model.isInstalled) "Installed on disk" else "Not installed (Optional)",
                                    color = if (model.isInstalled) CompanionSuccess else CompanionTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CompanionCyan else CompanionSurfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isSelected) "ACTIVE" else "SELECT",
                                color = if (isSelected) Color(0xFF001F29) else CompanionTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Inference Tuning Parameters
        Text(
            text = if (isArabic) "محددات التوليد والاستدلال:" else "Inference & Generation Parameters:",
            color = CompanionCyanBright,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        FuturisticCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = CompanionBorder
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Temperature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Temperature (Randomness / Creativity)", color = CompanionTextPrimary, fontSize = 12.sp)
                    Text(text = "%.2f".format(temperature), color = CompanionCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = temperature,
                    onValueChange = { viewModel.modelManager.updateTemperature(it) },
                    valueRange = 0.1f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = CompanionCyan, activeTrackColor = CompanionCyan)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Context Window
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Context Window (Tokens)", color = CompanionTextPrimary, fontSize = 12.sp)
                    Text(text = "$contextWindow tokens", color = CompanionCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = contextWindow.toFloat(),
                    onValueChange = { viewModel.modelManager.updateContextWindow(it.toInt()) },
                    valueRange = 1024f..8192f,
                    steps = 6,
                    colors = SliderDefaults.colors(thumbColor = CompanionCyan, activeTrackColor = CompanionCyan)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // GPU / NPU Acceleration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "GPU / NPU Acceleration", color = CompanionTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Unavailable in this CPU-only build", color = CompanionTextSecondary, fontSize = 10.sp)
                    }
                    Switch(
                        checked = false,
                        onCheckedChange = { },
                        enabled = false,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CompanionCyan,
                            checkedTrackColor = CompanionCyanDark
                        )
                    )
                }
            }
        }
    }
}
