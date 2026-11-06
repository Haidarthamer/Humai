package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val titleEn: String,
    val titleAr: String,
    val icon: ImageVector
) {
    HOME("Home", "الرئيسية", Icons.Default.ChatBubble),
    LEARN("Learn", "التعلم", Icons.Default.UploadFile),
    LIBRARY("Library", "المكتبة", Icons.Default.Folder),
    READER("Reader", "القارئ", Icons.Default.AutoAwesome),
    MEMORY("Memory", "الذاكرة", Icons.Default.Memory),
    PERSONALITY("Personality", "الشخصية", Icons.Default.Psychology),
    BRAIN("Brain", "المعالج", Icons.Default.AutoAwesome),
    SETTINGS("Settings", "الإعدادات", Icons.Default.Settings)
}
