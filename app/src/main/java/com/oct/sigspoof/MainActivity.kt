package com.oct.sigspoof

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.ui.Modifier
import com.oct.sigspoof.ui.EditSpoofScreen
import com.oct.sigspoof.ui.MainScreen
import com.oct.sigspoof.ui.MainViewModel
import com.oct.sigspoof.ui.theme.SigspoofhelperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val viewModel by viewModels<MainViewModel>()
        
        setContent {
            SigspoofhelperTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            if (targetState is Screen.Edit) {
                                slideInHorizontally { it } + fadeIn() togetherWith
                                        slideOutHorizontally { -it / 2 } + fadeOut()
                            } else {
                                slideInHorizontally { -it / 2 } + fadeIn() togetherWith
                                        slideOutHorizontally { it } + fadeOut()
                            }.using(SizeTransform(clip = false))
                        },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            is Screen.Main -> {
                                MainScreen(
                                    viewModel = viewModel,
                                    onEditApp = { pkg: String -> currentScreen = Screen.Edit(pkg) }
                                )
                            }
                            is Screen.Edit -> {
                                EditSpoofScreen(
                                    packageName = screen.packageName,
                                    onNavigateBack = { currentScreen = Screen.Main },
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed class Screen {
    object Main : Screen()
    data class Edit(val packageName: String) : Screen()
}