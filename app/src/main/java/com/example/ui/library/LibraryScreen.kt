package com.example.ui.library

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.example.core.model.FileType
import com.example.core.model.IngestionStatus
import com.example.core.model.LanguageMode
import com.example.database.entities.DocumentEntity
import com.example.ui.CompanionViewModel
import com.example.ui.components.FuturisticCard
import com.example.ui.theme.CompanionBackground
import com.example.ui.theme.CompanionBorder
import com.example.ui.theme.CompanionCyan
import com.example.ui.theme.CompanionCyanDark
import com.example.ui.theme.CompanionError
import com.example.ui.theme.CompanionSuccess
import com.example.ui.theme.CompanionSurface
import com.example.ui.theme.CompanionSurfaceVariant
import com.example.ui.theme.CompanionTextPrimary
import com.example.ui.theme.CompanionTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    viewModel: CompanionViewModel,
    onOpenReader: (DocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.documents.collectAsState()
    val language by viewModel.languageMode.collectAsState()
    val isArabic = language != LanguageMode.ENGLISH

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<FileType?>(null) }

    val filtered = documents.filter { doc ->
        val matchesType = selectedTypeFilter == null || doc.fileType == selectedTypeFilter
        val matchesQuery = searchQuery.isBlank() ||
                doc.title.contains(searchQuery, ignoreCase = true) ||
                doc.summary.contains(searchQuery, ignoreCase = true)
        matchesType && matchesQuery
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CompanionBackground)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = if (isArabic) "مكتبة المعرفة والوثائق" else "Neural Knowledge Library",
            color = CompanionCyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isArabic) "استعراض والبحث في جميع المواد والكتب المفهرسة محلياً" else "Browse and search all locally indexed materials and documents",
            color = CompanionTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = if (isArabic) "ابحث في المستندات والملخصات..." else "Search documents, summaries, and topics...",
                    color = CompanionTextSecondary,
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = CompanionCyan
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_field"),
            shape = RoundedCornerShape(16.dp),
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

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                TypeFilterChip(
                    label = if (isArabic) "الكل (${documents.size})" else "All (${documents.size})",
                    isSelected = selectedTypeFilter == null,
                    onClick = { selectedTypeFilter = null }
                )
            }
            items(
                listOf(
                    FileType.PDF to "PDF",
                    FileType.TXT to "TXT",
                    FileType.DOCX to "DOCX",
                    FileType.VIDEO to "Video",
                    FileType.IMAGE to "Image",
                    FileType.AUDIO to "Audio"
                )
            ) { (type, label) ->
                val count = documents.count { it.fileType == type }
                TypeFilterChip(
                    label = "$label ($count)",
                    isSelected = selectedTypeFilter == type,
                    onClick = { selectedTypeFilter = type }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Document Cards List
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isArabic) "لا توجد مستندات تطابق البحث." else "No documents match the current filter.",
                    color = CompanionTextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(filtered, key = { it.id }) { doc ->
                    LibraryDocumentCard(
                        doc = doc,
                        onOpen = { onOpenReader(doc) },
                        onDelete = { viewModel.deleteDocument(doc.id) },
                        isArabic = isArabic
                    )
                }
            }
        }
    }
}

@Composable
fun TypeFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) CompanionCyanDark else CompanionSurface)
            .border(
                1.dp,
                if (isSelected) CompanionCyan else CompanionBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) CompanionCyan else CompanionTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun LibraryDocumentCard(
    doc: DocumentEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    isArabic: Boolean
) {
    val dateStr = remember(doc.importedAt) {
        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(doc.importedAt))
    }

    val icon = when (doc.fileType) {
        FileType.PDF -> Icons.Default.PictureAsPdf
        FileType.VIDEO -> Icons.Default.Movie
        FileType.IMAGE -> Icons.Default.Image
        FileType.AUDIO -> Icons.Default.Audiotrack
        else -> Icons.Default.Description
    }

    FuturisticCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = CompanionBorder,
        onClick = onOpen
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CompanionSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = CompanionCyan)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = doc.title,
                            color = CompanionTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "$dateStr • ${doc.pageCount} ${if (isArabic) "صفحة/مقطع" else "pages/segments"}",
                            color = CompanionTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (doc.status == IngestionStatus.COMPLETED) CompanionSuccess.copy(alpha = 0.15f) else CompanionError.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (doc.status == IngestionStatus.COMPLETED) "INDEXED" else doc.status.name,
                            color = if (doc.status == IngestionStatus.COMPLETED) CompanionSuccess else CompanionError,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Document",
                            tint = CompanionTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (doc.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = doc.summary,
                    color = CompanionTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${doc.wordCount} words • Last read: page ${doc.lastReadPage}",
                    color = CompanionTextSecondary,
                    fontSize = 11.sp
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CompanionCyanDark)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = CompanionCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isArabic) "فتح القارئ" else "Open in Reader",
                        color = CompanionCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
