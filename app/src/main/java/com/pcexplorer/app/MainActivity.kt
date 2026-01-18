package com.pcexplorer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pcexplorer.core.domain.model.ConnectionState
import com.pcexplorer.features.browser.BrowserScreen
import com.pcexplorer.features.connection.ConnectionScreen
import com.pcexplorer.features.connection.ConnectionViewModel
import com.pcexplorer.features.settings.SettingsScreen
import com.pcexplorer.features.settings.SettingsViewModel
import com.pcexplorer.features.settings.ThemeMode
import com.pcexplorer.features.transfer.TransferScreen
import com.pcexplorer.features.transfer.TransferViewModel
import com.pcexplorer.shared.theme.PcExplorerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()

            val isDarkTheme = when (settingsState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            PcExplorerTheme(darkTheme = isDarkTheme) {
                MainApp()
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Connection : Screen("connection", "Connection", Icons.Filled.Usb)
    data object Browser : Screen("browser", "Files", Icons.Filled.Folder)
    data object Transfer : Screen("transfer", "Transfers", Icons.Filled.SwapVert)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val connectionViewModel: ConnectionViewModel = hiltViewModel()
    val transferViewModel: TransferViewModel = hiltViewModel()

    val connectionState by connectionViewModel.connectionState.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected

    val screens = listOf(
        Screen.Connection,
        Screen.Browser,
        Screen.Transfer,
        Screen.Settings
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                screens.forEach { screen ->
                    val enabled = screen == Screen.Connection ||
                            screen == Screen.Settings ||
                            isConnected

                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        enabled = enabled,
                        onClick = {
                            if (enabled && currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Connection.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Connection.route) {
                ConnectionScreen(
                    onConnected = {
                        navController.navigate(Screen.Browser.route) {
                            popUpTo(Screen.Connection.route) { inclusive = false }
                        }
                    },
                    viewModel = connectionViewModel
                )
            }
            composable(Screen.Browser.route) {
                BrowserScreen(
                    onDownloadFile = { file ->
                        // Download to app's external files directory
                        val downloadPath = "/storage/emulated/0/Download/${file.name}"
                        transferViewModel.downloadFile(file.path, downloadPath)
                    }
                )
            }
            composable(Screen.Transfer.route) {
                TransferScreen(viewModel = transferViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
