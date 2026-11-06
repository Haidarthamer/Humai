package com.example.ui.learn

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.FileType
import com.example.core.model.IngestionStatus
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
import com.example.ui.theme.CompanionSurfaceVariant
import com.example.ui.theme.CompanionTextPrimary
import com.example.ui.theme.CompanionTextSecondary
import java.io.File
import java.io.FileOutputStream

@Composable
fun LearnScreen(
    viewModel: CompanionViewModel,
    onOpenReader: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ingestionState by viewModel.ingestionState.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val language by viewModel.languageMode.collectAsState()
    val isArabic = language != LanguageMode.ENGLISH

    var showManualNoteDialog by remember { mutableStateOf(false) }
    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }

    // SAF Document Picker
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = queryFileName(context, it) ?: "imported_doc.pdf"
            val fileType = if (fileName.endsWith(".pdf", true)) {
                FileType.PDF
            } else if (fileName.endsWith(".docx", true) || fileName.endsWith(".doc", true)) {
                FileType.DOCX
            } else if (fileName.endsWith(".md", true)) {
                FileType.MARKDOWN
            } else {
                FileType.TXT
            }
            viewModel.ingestFile(it, fileName, fileType)
        }
    }

    // Media Picker for Video
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = queryFileName(context, it) ?: "video_${System.currentTimeMillis()}.mp4"
            viewModel.ingestFile(it, fileName, FileType.VIDEO)
        }
    }

    // Media Picker for Image
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = queryFileName(context, it) ?: "image_${System.currentTimeMillis()}.jpg"
            viewModel.ingestFile(it, fileName, FileType.IMAGE)
        }
    }

    // Media Picker for Audio
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = queryFileName(context, it) ?: "audio_${System.currentTimeMillis()}.m4a"
            viewModel.ingestFile(it, fileName, FileType.AUDIO)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CompanionBackground)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (isArabic) "مركز الاستيعاب والتعلم" else "Cognitive Assimilation Hub",
                    color = CompanionCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isArabic) "استيراد ملفات PDF، الفيديو، والمستندات لبناء الذاكرة" else "Import PDFs, media, and documents into local memory",
                    color = CompanionTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Ingestion Progress HUD (Active Job)
        AnimatedVisibility(visible = ingestionState.isIngesting) {
            FuturisticCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                borderColor = CompanionCyan
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { ingestionState.progress },
                                modifier = Modifier.size(24.dp),
                                color = CompanionCyan,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = ingestionState.fileName,
                                    color = CompanionTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = ingestionState.statusText,
                                    color = CompanionCyanBright,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.cancelIngestion() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Ingestion",
                                tint = CompanionError
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { ingestionState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = CompanionCyan,
                        trackColor = CompanionBorder
                    )
                }
            }
        }

        // Import Action Buttons Grid
        Text(
            text = if (isArabic) "اختر وسيط المعرفة للاستيراد:" else "Select Knowledge Media to Ingest:",
            color = CompanionTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ImportMediumButton(
                icon = Icons.Default.PictureAsPdf,
                title = "PDF File",
                subtitle = "Multi-page RAG",
                onClick = { docPickerLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_pdf_button")
            )
            ImportMediumButton(
                icon = Icons.Default.Description,
                title = "Document",
                subtitle = "TXT, DOCX, MD",
                onClick = { docPickerLauncher.launch(arrayOf("text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/markdown")) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_doc_button")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ImportMediumButton(
                icon = Icons.Default.Movie,
                title = "Video Media",
                subtitle = "Scene Timeline",
                onClick = { videoPickerLauncher.launch("video/*") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_video_button")
            )
            ImportMediumButton(
                icon = Icons.Default.Image,
                title = "Image / OCR",
                subtitle = "Diagrams & Text",
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_image_button")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ImportMediumButton(
                icon = Icons.Default.Audiotrack,
                title = "Audio Track",
                subtitle = "Speech Transcribe",
                onClick = { audioPickerLauncher.launch("audio/*") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_audio_button")
            )
            ImportMediumButton(
                icon = Icons.Default.EditNote,
                title = "Manual Note",
                subtitle = "Direct Input",
                onClick = { showManualNoteDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_manual_note_button")
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Pre-loaded Knowledge Demo Button
        FuturisticCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            borderColor = CompanionCyanDark,
            onClick = {
                // Ingest starter knowledge pack
                val sampleText = """
                    Quantum Computing and Superposition Principles:
                    Quantum computing harnesses the phenomena of quantum mechanics such as superposition and entanglement.
                    Unlike classical bits that are strictly 0 or 1, a qubit can exist in a linear combination of states |0⟩ and |1⟩.
                    Major applications include cryptography, molecular modeling, combinatorial optimization, and quantum machine learning.
                    Page 1 covers qubits and Bloch spheres.
                    Page 2 covers Shor's algorithm and quantum error correction.
                """.trimIndent()
                val tempFile = File(context.cacheDir, "Quantum_Computing_Overview.txt")
                tempFile.writeText(sampleText)
                viewModel.ingestFile(Uri.fromFile(tempFile), "Quantum_Computing_Overview.txt", FileType.TXT)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = CompanionCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isArabic) "تحميل حزمة معرفية تجريبية (مبادئ الحوسبة الكمومية)" else "Load Starter Knowledge Pack (Quantum Principles)",
                        color = CompanionCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isArabic) "يضيف فوراً وثيقة نموذجية مع اقتباسات لاختبار البحث الدلالي" else "Instantly adds a sample document with page citations to test RAG",
                        color = CompanionTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Ingested Materials List
        Text(
            text = if (isArabic) "المواد المستوعبة حديثاً (${documents.size}):" else "Ingested Materials (${documents.size}):",
            color = CompanionTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isArabic) "لم يتم استيراد أي مستندات حتى الآن.\nاختر ملفاً أعلاه لتبدأ المعالجة العصبية!" else "No documents ingested yet.\nChoose a file above to begin local assimilation!",
                    color = CompanionTextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    DocumentRowItem(
                        document = doc,
                        onOpen = { onOpenReader(doc.id) },
                        onDelete = { viewModel.deleteDocument(doc.id) }
                    )
                }
            }
        }
    }

    // Manual Note Dialog
    if (showManualNoteDialog) {
        AlertDialog(
            onDismissRequest = { showManualNoteDialog = false },
            title = { Text("Ingest Manual Note", color = CompanionCyan) },
            text = {
                Column {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Title / Topic") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CompanionCyan,
                            unfocusedBorderColor = CompanionBorder,
                            focusedTextColor = CompanionTextPrimary,
                            unfocusedTextColor = CompanionTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Content / Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CompanionCyan,
                            unfocusedBorderColor = CompanionBorder,
                            focusedTextColor = CompanionTextPrimary,
                            unfocusedTextColor = CompanionTextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteTitle.isNotBlank() && noteContent.isNotBlank()) {
                            val tempFile = File(context.cacheDir, "${noteTitle.replace(" ", "_")}.txt")
                            tempFile.writeText(noteContent)
                            viewModel.ingestFile(Uri.fromFile(tempFile), "${noteTitle}.txt", FileType.NOTE)
                            showManualNoteDialog = false
                            noteTitle = ""
                            noteContent = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CompanionCyan)
                ) {
                    Text("Ingest Note", color = Color(0xFF001F29), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualNoteDialog = false }) {
                    Text("Cancel", color = CompanionTextSecondary)
                }
            },
            containerColor = CompanionSurface
        )
    }
}

@Composable
fun ImportMediumButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FuturisticCard(
        modifier = modifier,
        onClick = onClick,
        borderColor = CompanionBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CompanionCyanDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CompanionCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = CompanionTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = CompanionTextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun DocumentRowItem(
    document: com.example.database.entities.DocumentEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    FuturisticCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen
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
                val icon = when (document.fileType) {
                    FileType.PDF -> Icons.Default.PictureAsPdf
                    FileType.VIDEO -> Icons.Default.Movie
                    FileType.IMAGE -> Icons.Default.Image
                    FileType.AUDIO -> Icons.Default.Audiotrack
                    else -> Icons.Default.Description
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CompanionSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = CompanionCyan)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = document.title,
                        color = CompanionTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${document.fileType.name} • ${document.pageCount} pages • ${if (document.status == IngestionStatus.COMPLETED) "Indexed" else document.status.name}",
                        color = if (document.status == IngestionStatus.COMPLETED) CompanionSuccess else CompanionTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = CompanionTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun queryFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
    }
    return name ?: uri.path?.substringAfterLast('/')
}
