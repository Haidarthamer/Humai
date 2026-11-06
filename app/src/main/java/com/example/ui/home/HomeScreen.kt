package com.example.ui.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.model.LanguageMode
import com.example.database.entities.MessageEntity
import com.example.ui.CompanionViewModel
import com.example.ui.components.CompanionNeuralAvatar
import com.example.ui.components.EmotionIndicatorPill
import com.example.ui.components.FuturisticCard
import com.example.ui.theme.CompanionBackground
import com.example.ui.theme.CompanionBorder
import com.example.ui.theme.CompanionCyan
import com.example.ui.theme.CompanionCyanDark
import com.example.ui.theme.CompanionError
import com.example.ui.theme.CompanionSurface
import com.example.ui.theme.CompanionSurfaceElevated
import com.example.ui.theme.CompanionSurfaceVariant
import com.example.ui.theme.CompanionTextPrimary
import com.example.ui.theme.CompanionTextSecondary

@Composable
fun HomeScreen(
    viewModel: CompanionViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.currentMessages.collectAsState()
    val streamingText by viewModel.streamingMessageText.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val language by viewModel.languageMode.collectAsState()
    val emotionState by viewModel.personalityEngine.emotion.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val activeConvId by viewModel.currentConversationId.collectAsState()
    val activeModel by viewModel.modelManager.activeModel.collectAsState()
    val modelLoadState by viewModel.modelManager.modelLoadState.collectAsState()
    val attachedImageUri by viewModel.attachedImageUri.collectAsState()

    val isArabic = language == LanguageMode.ARABIC_MSA || language == LanguageMode.ARABIC_IRAQI
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var showConvMenu by remember { mutableStateOf(false) }

    // Image picker launcher for chat visual understanding
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.attachImage(uri)
    }

    // Auto-scroll on new message or stream update
    LaunchedEffect(messages.size, streamingText) {
        if (messages.isNotEmpty() || streamingText.isNotBlank()) {
            val target = messages.size + (if (streamingText.isNotBlank()) 1 else 0)
            if (target > 0) {
                listState.animateScrollToItem(target - 1)
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(CompanionBackground)
        ) {
            // Top Companion HUD Bar with genuine Model Status
            FuturisticCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                borderColor = CompanionCyan.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        CompanionNeuralAvatar(
                            emotionState = emotionState,
                            size = 46.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "COMPANION AI",
                                    color = CompanionCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CompanionCyanDark)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "QWEN3-4B",
                                        color = CompanionCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${activeModel?.name ?: "Qwen3-4B Instruct"} • ${(activeConvId ?: 1L).let { id -> conversations.find { it.id == id }?.title ?: "Session" }}",
                                color = CompanionTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EmotionIndicatorPill(
                            emotionState = emotionState,
                            isArabic = isArabic
                        )

                        // Conversation switcher dropdown trigger
                        Box {
                            IconButton(onClick = { showConvMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Switch or Rename Session",
                                    tint = CompanionCyan
                                )
                            }

                            DropdownMenu(
                                expanded = showConvMenu,
                                onDismissRequest = { showConvMenu = false },
                                modifier = Modifier.background(CompanionSurfaceElevated)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("＋ New Neural Session", color = CompanionCyan) },
                                    onClick = {
                                        viewModel.createNewConversation()
                                        showConvMenu = false
                                    }
                                )
                                conversations.forEach { conv ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = conv.title,
                                                    color = if (conv.id == activeConvId) CompanionCyan else CompanionTextPrimary,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (conversations.size > 1) {
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.deleteConversation(conv.id)
                                                            showConvMenu = false
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete",
                                                            tint = CompanionError,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectConversation(conv.id)
                                            showConvMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Language Switcher Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = CompanionTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                listOf(
                    LanguageMode.ENGLISH to "EN",
                    LanguageMode.ARABIC_MSA to "فصحى",
                    LanguageMode.ARABIC_IRAQI to "عراقي"
                ).forEach { (mode, label) ->
                    val isSelected = language == mode
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) CompanionCyan.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) CompanionCyan else CompanionBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.setLanguage(mode) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) CompanionCyan else CompanionTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isArabic = isArabic
                    )
                }

                if (streamingText.isNotBlank()) {
                    item {
                        AssistantStreamingBubble(
                            text = streamingText,
                            emotionState = emotionState,
                            isArabic = isArabic
                        )
                    }
                }
            }

            // Quick Prompt Suggestions (shown if few messages)
            if (messages.size <= 2 && !isGenerating) {
                val suggestions = when (language) {
                    LanguageMode.ENGLISH -> listOf(
                        "Summarize my imported files",
                        "What is in my semantic memory?",
                        "Who are you and what makes you private?",
                        "How do you simulate emotions?"
                    )
                    LanguageMode.ARABIC_MSA -> listOf(
                        "لخص لي الوثائق المحفوظة في ذاكرتك",
                        "ما هي طبيعة نظام الذاكرة الرباعي؟",
                        "من أنت وما هي قدراتك دون إنترنت؟"
                    )
                    LanguageMode.ARABIC_IRAQI -> listOf(
                        "شلونك اليوم؟ شكو ماكو بذاكرتك؟",
                        "لخصلي الكتب اللي ضفتها",
                        "احجيلي شلون تفهم وتتذكر السوالف؟"
                    )
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions) { sug ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(CompanionSurfaceVariant)
                                .border(1.dp, CompanionCyan.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                .clickable {
                                    viewModel.sendMessage(sug)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = sug,
                                color = CompanionCyan,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Attached Image Preview Chip (if image selected for Qwen3-VL analysis)
            AnimatedVisibility(visible = attachedImageUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CompanionSurfaceVariant)
                        .border(1.dp, CompanionCyan, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = attachedImageUri,
                            contentDescription = "Attached preview",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Image attached for Qwen3-VL analysis",
                                color = CompanionCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Supports OCR, screenshots, diagrams, and charts",
                                color = CompanionTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.attachImage(null) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove attached image",
                            tint = CompanionError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Input Bar with Image Attachment Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach image button (Qwen3-VL vision upload)
                IconButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CompanionSurfaceVariant)
                        .border(1.dp, CompanionBorder, CircleShape)
                        .testTag("attach_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Upload Image for Qwen3-VL Analysis",
                        tint = CompanionCyan
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = if (isArabic) "اكتب سؤالك أو استفسارك هنا..." else "Ask Qwen3-4B or discuss documents...",
                            color = CompanionTextSecondary,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CompanionCyan,
                        unfocusedBorderColor = CompanionBorder,
                        focusedContainerColor = CompanionSurface,
                        unfocusedContainerColor = CompanionSurface,
                        focusedTextColor = CompanionTextPrimary,
                        unfocusedTextColor = CompanionTextPrimary
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (isGenerating) {
                    IconButton(
                        onClick = { viewModel.stopGeneration() },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(CompanionError.copy(alpha = 0.2f))
                            .border(1.dp, CompanionError, CircleShape)
                            .testTag("stop_generation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Generation",
                            tint = CompanionError
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() || attachedImageUri != null) {
                                val textToSend = inputText
                                inputText = ""
                                viewModel.sendMessage(textToSend)
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(CompanionCyan)
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = Color(0xFF001F29)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isArabic: Boolean
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(if (isUser) CompanionCyanDark else CompanionSurface)
                .border(
                    1.dp,
                    if (isUser) CompanionCyan.copy(alpha = 0.6f) else CompanionBorder,
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                // If message has an attached image, render with Coil
                if (message.imageUri != null) {
                    AsyncImage(
                        model = message.imageUri,
                        contentDescription = "Uploaded image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .padding(bottom = 8.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                if (!isUser && message.emotionTag != null) {
                    Text(
                        text = "• ${message.emotionTag}",
                        color = CompanionCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = message.content,
                    color = CompanionTextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun AssistantStreamingBubble(
    text: String,
    emotionState: com.example.core.model.EmotionState,
    isArabic: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                .background(CompanionSurface)
                .border(1.dp, CompanionCyan, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Qwen3-4B streaming response...",
                        color = CompanionCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = text,
                    color = CompanionTextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
