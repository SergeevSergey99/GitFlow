package com.gitflow.android

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gitflow.android.data.settings.AppSettingsManager
import com.gitflow.android.ui.screens.*
import com.gitflow.android.ui.auth.AuthScreen
import com.gitflow.android.ui.repositories.RemoteRepositoriesScreen
import com.gitflow.android.ui.theme.GitFlowTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateBaseContextLocale(newBase))
    }

    private fun updateBaseContextLocale(context: Context): Context {
        val settingsManager = AppSettingsManager(context)
        val language = settingsManager.getLanguage()

        if (language == AppSettingsManager.LANGUAGE_SYSTEM) {
            // Use system default locale
            return context
        }

        val locale = when (language) {
            AppSettingsManager.LANGUAGE_ENGLISH -> Locale("en")
            AppSettingsManager.LANGUAGE_RUSSIAN -> Locale("ru")
            else -> Locale("ru") // Fallback to Russian
        }

        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GitFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GitFlowApp()
                }
            }
        }
    }
}

@Composable
fun GitFlowApp() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(navController)
        }
        composable("auth") {
            AuthScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("remote_repositories") {
            RemoteRepositoriesScreen(
                onNavigateBack = { navController.popBackStack() },
                onRepositoryCloned = { 
                    navController.popBackStack()
                }
            )
        }
    }
}