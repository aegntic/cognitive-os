package com.thresholdinc.luxe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import com.thresholdinc.luxe.core.AnitaCore
import com.thresholdinc.luxe.data.EncryptedVault
import com.thresholdinc.luxe.domain.Inquiry
import com.thresholdinc.luxe.domain.MemoryEntry
import com.thresholdinc.luxe.domain.Client
import com.thresholdinc.luxe.ui.components.InsidherBackground
import com.thresholdinc.luxe.ui.screens.*
import com.thresholdinc.luxe.ui.theme.InsidherTheme
import kotlinx.coroutines.launch

val gson = Gson()

enum class AppScreen {
    Splash, Onboarding, Login, SignUp, ForgotPassword, PrivacyPolicy, TermsOfService, MainApp, Settings, LogoutConfirmation
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuxeThresholdApp() {
    InsidherTheme {
        val context = LocalContext.current
        val vault = remember { EncryptedVault(context) }
        var currentScreen by remember { mutableStateOf(AppScreen.Splash) }
        val screenHistory = remember { mutableStateListOf<AppScreen>() }

        var activeUserEmail by remember { mutableStateOf("") }
        var selectedTab by remember { mutableStateOf(0) }
        var clients by remember { mutableStateOf(listOf<Client>()) }
        var inquiries by remember { mutableStateOf(listOf<Inquiry>()) }
        var log by remember { mutableStateOf(listOf<String>()) }
        var useOnDevice by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun navigateTo(screen: AppScreen) {
            screenHistory.add(currentScreen)
            currentScreen = screen
        }

        fun navigateBack() {
            if (screenHistory.isNotEmpty()) currentScreen = screenHistory.removeLast() else currentScreen = AppScreen.Login
        }

        fun clearHistoryAndNavigate(screen: AppScreen) {
            screenHistory.clear()
            currentScreen = screen
        }

        LaunchedEffect(Unit) {
            val loaded = vault.getAllInquiries()
            if (loaded.isNotEmpty()) {
                inquiries = loaded
            } else {
                inquiries = listOf(
                    Inquiry("1", "Professional inquiry regarding Q3 coordination", "client@firm.com", "2026-07-09"),
                    Inquiry("2", "Vague note about possible alignment next week", "prospect@co.com", "2026-07-08"),
                    Inquiry("3", "Urgent: need time this afternoon for project sync", "partner@studio.com", "2026-07-08"),
                    Inquiry("4", "Pushy demand to meet immediately without context", "sales@vendor.com", "2026-07-08")
                )
                inquiries.forEach { vault.storeInquiry(it) }
            }
            log = vault.getRecentLogs()
            clients = listOf(
                vault.getOrCreateClient("client@firm.com"),
                vault.getOrCreateClient("prospect@co.com"),
                vault.getOrCreateClient("partner@studio.com")
            )
        }

        when (currentScreen) {
            AppScreen.Splash -> SplashScreen(vault = vault) { isLoggedIn ->
                if (isLoggedIn) {
                    val activeUser = vault.getConfig("active_user") ?: ""
                    activeUserEmail = activeUser
                    clearHistoryAndNavigate(AppScreen.MainApp)
                } else clearHistoryAndNavigate(AppScreen.Onboarding)
            }
            AppScreen.Onboarding -> OnboardingScreen(onNavigateToLogin = { navigateTo(AppScreen.Login) }, onNavigateToSignUp = { navigateTo(AppScreen.SignUp) })
            AppScreen.Login -> LoginScreen(vault = vault, onNavigateToRegister = { navigateTo(AppScreen.SignUp) }, onNavigateToForgotPassword = { navigateTo(AppScreen.ForgotPassword) }, onLoginSuccess = { email -> activeUserEmail = email; clearHistoryAndNavigate(AppScreen.MainApp) })
            AppScreen.SignUp -> SignUpScreen(vault = vault, onNavigateToLogin = { navigateTo(AppScreen.Login) }, onNavigateToPrivacyPolicy = { navigateTo(AppScreen.PrivacyPolicy) }, onNavigateToTerms = { navigateTo(AppScreen.TermsOfService) }, onSignUpSuccess = { email -> activeUserEmail = email; clearHistoryAndNavigate(AppScreen.MainApp) })
            AppScreen.ForgotPassword -> ForgotPasswordScreen(vault = vault, onNavigateToLogin = { navigateTo(AppScreen.Login) })
            AppScreen.PrivacyPolicy -> PrivacyPolicyScreen(onNavigateBack = { navigateBack() })
            AppScreen.TermsOfService -> TermsOfServiceScreen(onNavigateBack = { navigateBack() })
            AppScreen.Settings -> SettingsScreen(vault = vault, activeUserEmail = activeUserEmail, onNavigateBack = { navigateBack() }, onNavigateToPrivacy = { navigateTo(AppScreen.PrivacyPolicy) }, onNavigateToTerms = { navigateTo(AppScreen.TermsOfService) }, onNavigateToLogout = { navigateTo(AppScreen.LogoutConfirmation) })
            AppScreen.LogoutConfirmation -> LogoutConfirmationScreen(vault = vault, onConfirmLogout = { activeUserEmail = ""; clearHistoryAndNavigate(AppScreen.Onboarding) }, onCancel = { navigateBack() })
            AppScreen.MainApp -> {
                InsidherBackground {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Insidher", fontWeight = FontWeight.Light, fontSize = 20.sp, color = com.thresholdinc.luxe.ui.theme.InsidherTokens.TextPrimary) },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = com.thresholdinc.luxe.ui.theme.InsidherTokens.TextPrimary),
                                actions = {
                                    TextButton(onClick = { useOnDevice = !useOnDevice }) {
                                        Text(if (useOnDevice) "On-Device" else "Cloud", color = com.thresholdinc.luxe.ui.theme.InsidherTokens.AmberGold, fontSize = 12.sp)
                                    }
                                    IconButton(onClick = { navigateTo(AppScreen.Settings) }) {
                                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = com.thresholdinc.luxe.ui.theme.InsidherTokens.AmberGold)
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            NavigationBar(containerColor = Color(0xFF141210)) {
                                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text("Onboard") }, icon = { })
                                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text("Clients") }, icon = { })
                                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, label = { Text("Bookings") }, icon = { })
                                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, label = { Text("Profile") }, icon = { })
                                NavigationBarItem(selected = selectedTab == 4, onClick = { selectedTab = 4 }, label = { Text("Active Ads") }, icon = { })
                                NavigationBarItem(selected = selectedTab == 5, onClick = { selectedTab = 5 }, label = { Text("Draft Ads") }, icon = { })
                            }
                        }
                    ) { padding ->
                        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                            when (selectedTab) {
                                0 -> OnboardScreen(vault = vault, activeUserEmail = activeUserEmail)
                                1 -> ClientsScreen(
                                    clients = clients,
                                    inquiries = inquiries,
                                    log = log,
                                    vault = vault,
                                    scope = scope,
                                    useOnDevice = useOnDevice,
                                    onAddClient = { name, email ->
                                        scope.launch {
                                            val c = vault.getOrCreateClient(email)
                                            clients = clients + c
                                            vault.appendLog("Client added: $email")
                                        }
                                    },
                                    onLog = { msg ->
                                        scope.launch {
                                            log = log + msg
                                            vault.appendLog(msg)
                                        }
                                    }
                                )
                                2 -> BookingsScreen(vault = vault, activeUserEmail = activeUserEmail)
                                3 -> ProfileScreen(vault = vault, activeUserEmail = activeUserEmail, onNavigateToSettings = { navigateTo(AppScreen.Settings) })
                                4 -> AdsScreen(activeUserEmail = activeUserEmail)
                                5 -> AdsScreen(activeUserEmail = activeUserEmail)
                            }
                        }
                    }
                }
            }
        }
    }
}