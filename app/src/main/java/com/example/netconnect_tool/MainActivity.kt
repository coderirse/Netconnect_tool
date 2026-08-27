package com.example.netconnect_tool

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.netconnect_tool.data.AppSettings
import com.example.netconnect_tool.data.BillingStore
import com.example.netconnect_tool.data.CampusNetworkClient
import com.example.netconnect_tool.data.CredentialStore
import com.example.netconnect_tool.data.Notifier
import com.example.netconnect_tool.data.TrafficHistoryStore
import com.example.netconnect_tool.ui.DashboardScreen
import com.example.netconnect_tool.ui.DashboardViewModel
import com.example.netconnect_tool.ui.LoginScreen
import com.example.netconnect_tool.ui.LoginViewModel
import com.example.netconnect_tool.ui.SettingsScreen
import com.example.netconnect_tool.ui.SettingsViewModel
import com.example.netconnect_tool.ui.currentVersionName
import com.example.netconnect_tool.ui.theme.Netconnect_toolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val client = CampusNetworkClient()
        val credentialStore = CredentialStore(this)
        val appSettings = AppSettings(this)
        val trafficHistoryStore = TrafficHistoryStore(this)
        val notifier = Notifier(this)
        val billingStore = BillingStore(this)
        val versionName = currentVersionName(this)
        setContent {
            Netconnect_toolTheme {
                AppNavigation(
                    client = client,
                    credentialStore = credentialStore,
                    appSettings = appSettings,
                    trafficHistoryStore = trafficHistoryStore,
                    notifier = notifier,
                    billingStore = billingStore,
                    currentVersion = versionName
                )
            }
        }
    }
}

@Composable
private fun AppNavigation(
    client: CampusNetworkClient,
    credentialStore: CredentialStore,
    appSettings: AppSettings,
    trafficHistoryStore: TrafficHistoryStore,
    notifier: Notifier,
    billingStore: BillingStore,
    currentVersion: String
) {
    val navController = rememberNavController()
    // 首次启动请求通知权限（Android 13+ 必须，否则通知不显示）
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = android.Manifest.permission.POST_NOTIFICATIONS
            val check = context.checkSelfPermission(granted) == PackageManager.PERMISSION_GRANTED
            if (!check) permissionLauncher.launch(granted)
        }
    }
    NavHost(
        navController = navController,
        startDestination = "dashboard",
        // 淡入 + 水平滑动过渡，流畅舒适
        enterTransition = {
            fadeIn(animationSpec = tween(220)) + slideInHorizontally(animationSpec = tween(220)) { it / 12 }
        },
        exitTransition = {
            fadeOut(animationSpec = tween(180)) + slideOutHorizontally(animationSpec = tween(180)) { -it / 16 }
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(220)) + slideInHorizontally(animationSpec = tween(220)) { -it / 12 }
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(180)) + slideOutHorizontally(animationSpec = tween(180)) { it / 16 }
        }
    ) {
        composable("login") {
            val viewModel: LoginViewModel = viewModel {
                LoginViewModel(client = client, credentialStore = credentialStore)
            }
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") {
            val viewModel: DashboardViewModel = viewModel {
                DashboardViewModel(
                    client = client,
                    currentVersion = currentVersion,
                    appSettings = appSettings,
                    credentialStore = credentialStore,
                    trafficHistoryStore = trafficHistoryStore,
                    notifier = notifier,
                    billingStore = billingStore
                )
            }
            val navigateToLogin: () -> Unit = {
                navController.navigate("login") {
                    popUpTo("dashboard") { inclusive = true }
                }
            }
            DashboardScreen(
                viewModel = viewModel,
                onLoggedOut = navigateToLogin,
                onNeedLogin = navigateToLogin,
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            val viewModel: SettingsViewModel = viewModel {
                SettingsViewModel(appSettings = appSettings, billingStore = billingStore)
            }
            SettingsScreen(
                viewModel = viewModel,
                currentVersion = currentVersion,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
