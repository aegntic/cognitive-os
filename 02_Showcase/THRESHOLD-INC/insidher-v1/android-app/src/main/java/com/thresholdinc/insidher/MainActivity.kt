package com.thresholdinc.insidher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thresholdinc.insidher.ui.Routes
import com.thresholdinc.insidher.ui.screens.ConversationDetailScreen
import com.thresholdinc.insidher.ui.screens.ConversationListScreen
import com.thresholdinc.insidher.ui.screens.OnboardingScreen
import com.thresholdinc.insidher.ui.theme.InsidherTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* runtime grant; receiver still registered */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestSmsPermissionsIfNeeded()

        setContent {
            InsidherTheme {
                val app = application as InsidherApp
                val nav = rememberNavController()
                var start by remember {
                    mutableStateOf(
                        if (app.prefs.onboarded) Routes.THREADS else Routes.ONBOARDING,
                    )
                }
                NavHost(navController = nav, startDestination = start) {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(
                            onDone = {
                                start = Routes.THREADS
                                nav.navigate(Routes.THREADS) {
                                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(Routes.THREADS) {
                        ConversationListScreen(
                            onOpenThread = { id -> nav.navigate(Routes.thread(id)) },
                        )
                    }
                    composable(
                        Routes.THREAD,
                        arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
                    ) { entry ->
                        val id = entry.arguments?.getString("threadId") ?: return@composable
                        ConversationDetailScreen(
                            threadId = id,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    private fun requestSmsPermissionsIfNeeded() {
        val needed = listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
