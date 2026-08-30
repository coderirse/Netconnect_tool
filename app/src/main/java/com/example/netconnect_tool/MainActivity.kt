package com.example.netconnect_tool

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
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
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val client = CampusNetworkClient(applicationContext)
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
    // 启动时请求通知权限（Android 13+ 必须，否则通知不显示）；仅在提醒开关开启时打扰用户，
    // 同一进程最多问一次（拒绝后可到设置页重新打开开关触发请求）
    val context = androidx.compose.ui.platform.LocalContext.current
    val askedThisProcess = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedThisProcess.value) {
            askedThisProcess.value = true
            val perm = android.Manifest.permission.POST_NOTIFICATIONS
            val enabled = try {
                appSettings.notifyEnabled.first()
            } catch (_: Exception) {
                true
            }
            if (enabled && context.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(perm)
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = "dashboard",
        // 书页式转场：新页从右往左整页盖入，旧页带 1/4 视差左移并微暗；
        // 返回时反向（当前页向右翻走，前页从左复位）
        enterTransition = {
            slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { it } +
                fadeIn(animationSpec = tween(300), initialAlpha = 0.6f)
        },
        exitTransition = {
            slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { -it / 4 } +
                fadeOut(animationSpec = tween(300), targetAlpha = 0.7f)
        },
        popEnterTransition = {
            slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { -it / 4 } +
                fadeIn(animationSpec = tween(300), initialAlpha = 0.7f)
        },
        popExitTransition = {
            slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { it } +
                fadeOut(animationSpec = tween(300), targetAlpha = 0.6f)
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
                    context = context.applicationContext,
                    client = client,
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
                SettingsViewModel(
                    appSettings = appSettings,
                    billingStore = billingStore,
                    credentialStore = credentialStore,
                    currentVersion = currentVersion
                )
            }
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
