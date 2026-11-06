package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.LanguageMode
import com.example.ui.CompanionViewModel
import com.example.ui.brain.BrainScreen
import com.example.ui.home.HomeScreen
import com.example.ui.learn.LearnScreen
import com.example.ui.library.LibraryScreen
import com.example.ui.memory.MemoryScreen
import com.example.ui.navigation.Screen
import com.example.ui.personality.PersonalityScreen
import com.example.ui.reader.PdfReaderScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.CompanionAiTheme
import com.example.ui.theme.CompanionBackground
import com.example.ui.theme.CompanionBorder
import com.example.ui.theme.CompanionCyan
import com.example.ui.theme.CompanionCyanDark
import com.example.ui.theme.CompanionSurface
import com.example.ui.theme.CompanionTextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompanionAiTheme {
                val viewModel: CompanionViewModel = viewModel()
                CompanionAppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun CompanionAppRoot(viewModel: CompanionViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var previousScreen by remember { mutableStateOf(Screen.HOME) }
    val language by viewModel.languageMode.collectAsState()
    val isArabic = language != LanguageMode.ENGLISH

    // Hardware back button navigation
    BackHandler(enabled = currentScreen != Screen.HOME) {
        currentScreen = if (currentScreen == Screen.READER) previousScreen else Screen.HOME
    }

    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                // Hide bottom navigation bar when viewing document in full reader
                if (currentScreen != Screen.READER) {
                    NavigationBar(
                        containerColor = CompanionSurface,
                        contentColor = CompanionCyan,
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .background(CompanionSurface)
                            .navigationBarsPadding()
                            .testTag("main_bottom_nav_bar")
                    ) {
                        val navScreens = listOf(
                            Screen.HOME,
                            Screen.LEARN,
                            Screen.LIBRARY,
                            Screen.MEMORY,
                            Screen.PERSONALITY,
                            Screen.BRAIN,
                            Screen.SETTINGS
                        )

                        navScreens.forEach { screen ->
                            val isSelected = currentScreen == screen
                            val label = if (isArabic) screen.titleAr else screen.titleEn
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (currentScreen != screen) {
                                        previousScreen = currentScreen
                                        currentScreen = screen
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CompanionCyan,
                                    selectedTextColor = CompanionCyan,
                                    unselectedIconColor = CompanionTextSecondary,
                                    unselectedTextColor = CompanionTextSecondary,
                                    indicatorColor = CompanionCyanDark
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(CompanionBackground)
            ) {
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(viewModel = viewModel)
                    Screen.LEARN -> LearnScreen(
                        viewModel = viewModel,
                        onOpenReader = { docId ->
                            viewModel.documents.value.find { it.id == docId }?.let { doc ->
                                viewModel.openDocumentInReader(doc)
                                previousScreen = currentScreen
                                currentScreen = Screen.READER
                            }
                        }
                    )
                    Screen.LIBRARY -> LibraryScreen(
                        viewModel = viewModel,
                        onOpenReader = { doc ->
                            viewModel.openDocumentInReader(doc)
                            previousScreen = currentScreen
                            currentScreen = Screen.READER
                        }
                    )
                    Screen.READER -> PdfReaderScreen(
                        viewModel = viewModel,
                        onBack = {
                            currentScreen = previousScreen
                        }
                    )
                    Screen.MEMORY -> MemoryScreen(viewModel = viewModel)
                    Screen.PERSONALITY -> PersonalityScreen(viewModel = viewModel)
                    Screen.BRAIN -> BrainScreen(viewModel = viewModel)
                    Screen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
