package com.example.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.FileType
import com.example.core.model.LanguageMode
import com.example.ui.CompanionViewModel
import com.example.ui.components.FuturisticCard
import com.example.ui.theme.CompanionBackground
import com.example.ui.theme.CompanionBorder
import com.example.ui.theme.CompanionCyan
import com.example.ui.theme.CompanionCyanDark
import com.example.ui.theme.CompanionSurface
import com.example.ui.theme.CompanionSurfaceElevated
import com.example.ui.theme.CompanionSurfaceVariant
import com.example.ui.theme.CompanionTextPrimary
import com.example.ui.theme.CompanionTextSecondary
import java.io.File

@Composable
fun PdfReaderScreen(
    viewModel: CompanionViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDoc by viewModel.activeReaderDocument.collectAsState()
    val currentPage by viewModel.readerCurrentPage.collectAsState()
    val pageBitmap by viewModel.readerPageBitmap.collectAsState()
    val bookmarks by viewModel.readerBookmarks.collectAsState()
    val language by viewModel.languageMode.collectAsState()
    val isArabic = language != LanguageMode.ENGLISH

    val aiResponse by viewModel.pdfAssistantResponse.collectAsState()
    val isAiLoading by viewModel.pdfAssistantLoading.collectAsState()

    var showAiAssistant by remember { mutableStateOf(false) }
    var aiQueryInput by remember { mutableStateOf("") }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    val totalPages = (activeDoc?.pageCount ?: 1).coerceAtLeast(1)
    val isBookmarked = bookmarks.any { it.pageNumber == currentPage }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CompanionBackground)
    ) {
        // Reader Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CompanionSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CompanionCyan
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = activeDoc?.title ?: "Document Viewer",
                        color = CompanionTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "Page $currentPage of $totalPages • ${(zoomScale * 100).toInt()}% Zoom",
                        color = CompanionTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Zoom Controls
                IconButton(onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.75f) }) {
                    Icon(imageVector = Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = CompanionTextSecondary)
                }
                IconButton(onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3.0f) }) {
                    Icon(imageVector = Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = CompanionCyan)
                }

                // Bookmark toggle
                IconButton(onClick = {
                    if (isBookmarked) {
                        bookmarks.find { it.pageNumber == currentPage }?.let { viewModel.deleteBookmark(it.id) }
                    } else {
                        viewModel.addBookmark("Bookmark on page $currentPage")
                    }
                }) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Bookmark Page",
                        tint = if (isBookmarked) CompanionCyan else CompanionTextSecondary
                    )
                }

                // AI Assistant Toggle
                IconButton(
                    onClick = { showAiAssistant = !showAiAssistant },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (showAiAssistant) CompanionCyan else CompanionCyanDark)
                        .testTag("pdf_ai_assistant_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI PDF Assistant",
                        tint = if (showAiAssistant) Color(0xFF001F29) else CompanionCyan
                    )
                }
            }
        }

        // Main Document Content Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF090D18)),
            contentAlignment = Alignment.Center
        ) {
            val doc = activeDoc
            if (doc != null && doc.fileType == FileType.PDF) {
                if (pageBitmap != null) {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page $currentPage",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale
                            )
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(0.75f, 3.5f)
                                }
                            }
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CompanionCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Rendering page $currentPage...", color = CompanionTextSecondary, fontSize = 12.sp)
                    }
                }
            } else if (doc != null) {
                // Non-PDF Document (TXT, MD, DOCX) text view
                val cachedFile = doc.localCachedPath?.let { File(it) }
                val textContent = if (cachedFile != null && cachedFile.exists()) {
                    cachedFile.readText()
                } else {
                    doc.summary.ifBlank { "Document content loaded." }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = textContent,
                        color = CompanionTextPrimary,
                        fontSize = (14 * zoomScale).sp,
                        lineHeight = (22 * zoomScale).sp
                    )
                }
            } else {
                Text("No document selected.", color = CompanionTextSecondary)
            }
        }

        // Embedded AI PDF Assistant Drawer / Panel
        AnimatedVisibility(visible = showAiAssistant) {
            FuturisticCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                borderColor = CompanionCyan
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = CompanionCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArabic) "المساعد الذكي لمستند PDF" else "AI PDF Document Assistant",
                                color = CompanionCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { showAiAssistant = false }, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = CompanionTextSecondary)
                        }
                    }

                    // Quick Assistant Prompts
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val actions = if (isArabic) {
                            listOf(
                                "لخص هذه الصفحة بالتفصيل",
                                "اشرح المصطلحات المعقدة هنا",
                                "استخرج ملخصاً دراسياً",
                                "اسأل بالعربية عن المحتوى"
                            )
                        } else {
                            listOf(
                                "Summarize this page",
                                "Explain key concepts",
                                "Extract study notes",
                                "Translate to Arabic"
                            )
                        }
                        items(actions) { action ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CompanionSurfaceVariant)
                                    .border(1.dp, CompanionCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.askAiAboutCurrentDocument(action)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = action, color = CompanionCyan, fontSize = 11.sp)
                            }
                        }
                    }

                    // Assistant Response Box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CompanionSurfaceElevated)
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (isAiLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CompanionCyan, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing page $currentPage...", color = CompanionTextSecondary, fontSize = 12.sp)
                            }
                        } else if (aiResponse.isNotBlank()) {
                            Text(text = aiResponse, color = CompanionTextPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                        } else {
                            Text(
                                text = if (isArabic) "اضغط على أمر سريع أعلاه أو اكتب سؤالك عن الصفحة الحالية." else "Select a quick action above or ask any question about this document page.",
                                color = CompanionTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Input field inside PDF Assistant
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = aiQueryInput,
                            onValueChange = { aiQueryInput = it },
                            placeholder = { Text("Ask about page $currentPage...", fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CompanionCyan,
                                unfocusedBorderColor = CompanionBorder,
                                focusedTextColor = CompanionTextPrimary,
                                unfocusedTextColor = CompanionTextPrimary
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (aiQueryInput.isNotBlank()) {
                                    val q = aiQueryInput
                                    aiQueryInput = ""
                                    viewModel.askAiAboutCurrentDocument(q)
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CompanionCyan)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Ask", tint = Color(0xFF001F29), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Bottom Page Navigation Bar & Thumbnail Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CompanionSurface)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (currentPage > 1) viewModel.renderReaderPage(currentPage - 1)
                    },
                    enabled = currentPage > 1
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Page",
                        tint = if (currentPage > 1) CompanionCyan else CompanionTextSecondary.copy(alpha = 0.3f)
                    )
                }

                Slider(
                    value = currentPage.toFloat(),
                    onValueChange = { viewModel.renderReaderPage(it.toInt()) },
                    valueRange = 1f..totalPages.toFloat(),
                    steps = if (totalPages > 2) totalPages - 2 else 0,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = CompanionCyan,
                        activeTrackColor = CompanionCyan,
                        inactiveTrackColor = CompanionBorder
                    )
                )

                IconButton(
                    onClick = {
                        if (currentPage < totalPages) viewModel.renderReaderPage(currentPage + 1)
                    },
                    enabled = currentPage < totalPages
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Page",
                        tint = if (currentPage < totalPages) CompanionCyan else CompanionTextSecondary.copy(alpha = 0.3f)
                    )
                }
            }

            // Quick Page Thumbnails Strip
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items((1..totalPages).toList()) { p ->
                    val isCurrent = p == currentPage
                    val hasBookmark = bookmarks.any { it.pageNumber == p }
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCurrent) CompanionCyanDark else CompanionSurfaceVariant)
                            .border(
                                1.5.dp,
                                if (isCurrent) CompanionCyan else CompanionBorder,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { viewModel.renderReaderPage(p) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (hasBookmark) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = CompanionCyan,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            Text(
                                text = "$p",
                                color = if (isCurrent) CompanionCyan else CompanionTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
