package com.example.ui.memory

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.core.model.MemoryTier
import com.example.database.entities.MemoryEntity
import com.example.ui.CompanionViewModel
import com.example.ui.components.FuturisticCard
import com.example.ui.theme.CompanionBackground
import com.example.ui.theme.CompanionBorder
import com.example.ui.theme.CompanionCyan
import com.example.ui.theme.CompanionCyanBright
import com.example.ui.theme.CompanionCyanDark
import com.example.ui.theme.CompanionError
import com.example.ui.theme.CompanionSurface
import com.example.ui.theme.CompanionSurfaceVariant
import com.example.ui.theme.CompanionTextPrimary
import com.example.ui.theme.CompanionTextSecondary

@Composable
fun MemoryScreen(
    viewModel: CompanionViewModel,
    modifier: Modifier = Modifier
) {
    val memories by viewModel.memories.collectAsState()
    val language by viewModel.languageMode.collectAsState()
    val isArabic = language != LanguageMode.ENGLISH

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val tiers = listOf(
        MemoryTier.WORKING to if (isArabic) "العاملة" else "Working",
        MemoryTier.EPISODIC to if (isArabic) "العرضية (الأحداث)" else "Episodic",
        MemoryTier.SEMANTIC to if (isArabic) "الدلالية (المعرفة)" else "Semantic",
        MemoryTier.PERSONALITY to if (isArabic) "الشخصية" else "Personality"
    )

    val activeTier = tiers[selectedTabIndex].first

    val filteredMemories = memories.filter { m ->
        val matchesTier = m.tier == activeTier
        val matchesQuery = searchQuery.isBlank() ||
                m.topic.contains(searchQuery, ignoreCase = true) ||
                m.content.contains(searchQuery, ignoreCase = true)
        matchesTier && matchesQuery
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CompanionBackground)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isArabic) "مركز إدارة الذاكرة الرباعي" else "4-Tier Memory Center",
                    color = CompanionCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isArabic) "الاسترجاع الدلالي، التكرار، وتتبع مصادر المعلومات" else "Persistent semantic retention, deduplication & source tracking",
                    color = CompanionTextSecondary,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CompanionCyan)
                    .size(36.dp)
                    .testTag("add_memory_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Memory",
                    tint = Color(0xFF001F29)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(if (isArabic) "ابحث في الذاكرة..." else "Search cognitive memories...", color = CompanionTextSecondary, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CompanionCyan) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("memory_search_field"),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CompanionCyan,
                unfocusedBorderColor = CompanionBorder,
                focusedContainerColor = CompanionSurface,
                unfocusedContainerColor = CompanionSurface,
                focusedTextColor = CompanionTextPrimary,
                unfocusedTextColor = CompanionTextPrimary
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4-Tier Memory Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = CompanionSurface,
            contentColor = CompanionCyan,
            edgePadding = 0.dp,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, CompanionBorder, RoundedCornerShape(12.dp))
        ) {
            tiers.forEachIndexed { index, pair ->
                val isSelected = selectedTabIndex == index
                val count = memories.count { it.tier == pair.first }
                Tab(
                    selected = isSelected,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = "${pair.second} ($count)",
                            color = if (isSelected) CompanionCyan else CompanionTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tier Description & Clear Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val desc = when (activeTier) {
                MemoryTier.WORKING -> if (isArabic) "السياق الآني والحوار النشط" else "Active short-term buffer & context window"
                MemoryTier.EPISODIC -> if (isArabic) "التجارب والمواقف التفاعلية الهامة" else "Significant user interactions and milestones"
                MemoryTier.SEMANTIC -> if (isArabic) "المعرفة والحقائق المستخلصة من الملفات" else "Extracted facts & knowledge from imported media"
                MemoryTier.PERSONALITY -> if (isArabic) "تفضيلات المستخدم واهتماماته المتراكمة" else "User communication habits and preferences"
            }
            Text(text = desc, color = CompanionTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))

            if (filteredMemories.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearMemories(activeTier) }) {
                    Text(if (isArabic) "مسح الفئة" else "Clear Tier", color = CompanionError, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Memories List
        if (filteredMemories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isArabic) "لا توجد ذكريات في هذه الفئة حالياً.\nأضف ذاكرة جديدة أو استورد ملفات في قسم التعلم!" else "No memories recorded in this tier yet.\nAdd one above or ingest materials in Learn!",
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
                items(filteredMemories, key = { it.id }) { mem ->
                    MemoryCardItem(
                        memory = mem,
                        onEdit = { editingMemory = mem },
                        onDelete = { viewModel.deleteMemory(mem.id) }
                    )
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showAddDialog || editingMemory != null) {
        val isEditing = editingMemory != null
        var topicInput by remember { mutableStateOf(editingMemory?.topic ?: "") }
        var contentInput by remember { mutableStateOf(editingMemory?.content ?: "") }
        var importanceInput by remember { mutableFloatStateOf(editingMemory?.importanceScore ?: 0.7f) }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingMemory = null
            },
            title = {
                Text(
                    text = if (isEditing) "Edit Cognitive Memory" else "Record New Memory",
                    color = CompanionCyan
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        label = { Text("Memory Topic / Keyword") },
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
                        value = contentInput,
                        onValueChange = { contentInput = it },
                        label = { Text("Memory Content / Fact") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CompanionCyan,
                            unfocusedBorderColor = CompanionBorder,
                            focusedTextColor = CompanionTextPrimary,
                            unfocusedTextColor = CompanionTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Importance Weight: ${(importanceInput * 100).toInt()}%",
                        color = CompanionCyanBright,
                        fontSize = 11.sp
                    )
                    androidx.compose.material3.Slider(
                        value = importanceInput,
                        onValueChange = { importanceInput = it },
                        valueRange = 0.1f..1.0f,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = CompanionCyan,
                            activeTrackColor = CompanionCyan
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (topicInput.isNotBlank() && contentInput.isNotBlank()) {
                            if (isEditing) {
                                editingMemory?.let {
                                    viewModel.updateMemory(
                                        it.copy(
                                            topic = topicInput,
                                            content = contentInput,
                                            importanceScore = importanceInput
                                        )
                                    )
                                }
                            } else {
                                viewModel.addMemory(activeTier, topicInput, contentInput, importanceInput)
                            }
                            showAddDialog = false
                            editingMemory = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CompanionCyan)
                ) {
                    Text(if (isEditing) "Save" else "Record", color = Color(0xFF001F29), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    editingMemory = null
                }) {
                    Text("Cancel", color = CompanionTextSecondary)
                }
            },
            containerColor = CompanionSurface
        )
    }
}

@Composable
fun MemoryCardItem(
    memory: MemoryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    FuturisticCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = CompanionBorder
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CompanionCyanDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = CompanionCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = memory.topic,
                            color = CompanionCyanBright,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "Importance: ${(memory.importanceScore * 100).toInt()}% • Recalled: ${memory.accessCount}x",
                            color = CompanionTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = CompanionTextSecondary, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = CompanionError, modifier = Modifier.size(15.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = memory.content,
                color = CompanionTextPrimary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            if (memory.sourceDetails != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Source: ${memory.sourceDetails}",
                    color = CompanionTextSecondary,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { memory.importanceScore },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = CompanionCyan,
                trackColor = CompanionSurfaceVariant
            )
        }
    }
}
