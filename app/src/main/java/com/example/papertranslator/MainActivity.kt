package com.example.papertranslator

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.papertranslator.data.AppDatabase
import com.example.papertranslator.ui.*
import com.example.papertranslator.ui.theme.PaperTranslatorTheme
import java.io.File

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaperTranslatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = navController()
                    var paperContent by remember { mutableStateOf("") }
                    var translationResult by remember { mutableStateOf("") }
                    var interpretationResult by remember { mutableStateOf("") }
                    var currentPdfFile by remember { mutableStateOf<File?>(null) }

                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(
                                onSettingsClick = { navController.navigate("settings") },
                                onTranslateClick = { 
                                    Log.d(TAG, "Translate button clicked")
                                    navController.navigate("input/translate") 
                                },
                                onInterpretClick = { navController.navigate("input/interpret") },
                                onHistoryClick = { navController.navigate("history") }
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                onBackClick = { navController.popBackStack() },
                                onRecordClick = { record ->
                                    if (record.filePath != null) {
                                        currentPdfFile = File(record.filePath)
                                        navController.navigate("reader")
                                    }
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("input/{type}") { backStackEntry ->
                            val type = backStackEntry.arguments?.getString("type") ?: "translate"
                            Log.d(TAG, "Navigating to input screen with type: $type")
                            PaperInputScreen(
                                type = type,
                                onBackClick = { navController.popBackStack() },
                                onConfirmClick = { content, result ->
                                    paperContent = content
                                    if (type == "translate") {
                                        translationResult = result
                                        navController.navigate("result/translate")
                                    } else {
                                        interpretationResult = result
                                        navController.navigate("result/interpret")
                                    }
                                },
                                onReaderNavigate = { file ->
                                    currentPdfFile = file
                                    navController.navigate("reader")
                                }
                            )
                        }
                        composable("result/{type}") { backStackEntry ->
                            val type = backStackEntry.arguments?.getString("type") ?: "translate"
                            ResultScreen(
                                type = type,
                                content = paperContent,
                                result = if (type == "translate") translationResult else interpretationResult,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("reader") {
                            currentPdfFile?.let { file ->
                                PaperReaderScreen(
                                    pdfFile = file,
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun navController() = rememberNavController()
}
