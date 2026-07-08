package com.example.netconnect_tool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.netconnect_tool.data.CampusNetworkClient
import com.example.netconnect_tool.data.CredentialStore
import com.example.netconnect_tool.ui.DashboardScreen
import com.example.netconnect_tool.ui.DashboardViewModel
import com.example.netconnect_tool.ui.LoginScreen
import com.example.netconnect_tool.ui.LoginViewModel
import com.example.netconnect_tool.ui.currentVersionName
import com.example.netconnect_tool.ui.theme.Netconnect_toolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val client = CampusNetworkClient()
        val credentialStore = CredentialStore(this)
        val versionName = currentVersionName(this)
        setContent {
            Netconnect_toolTheme {
                AppNavigation(
                    client = client,
                    credentialStore = credentialStore,
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
    currentVersion: String
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "dashboard") {
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
                    credentialStore = credentialStore,
                    currentVersion = currentVersion
                )
            }
            DashboardScreen(
                viewModel = viewModel,
                onLoggedOut = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onNeedLogin = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }
    }
}
