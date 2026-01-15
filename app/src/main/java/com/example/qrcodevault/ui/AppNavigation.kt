package com.example.qrcodevault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.qrcodevault.data.QRCodeRepository
import com.example.qrcodevault.ui.generator.GeneratorScreen
import com.example.qrcodevault.ui.scanner.ScannerScreen
import com.example.qrcodevault.ui.settings.SettingsScreen
import com.example.qrcodevault.ui.vault.VaultScreen
import com.example.qrcodevault.ui.theme.*
import com.example.qrcodevault.utils.ExportUtils
import kotlinx.coroutines.launch

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Vault : Screen("vault", "Vault", Icons.Filled.Lock, Icons.Outlined.Lock)
    object Generator : Screen("generator", "Create", Icons.Filled.Add, Icons.Outlined.Add)
    object Scanner : Screen("scanner", "Scan", Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainNavigation(repository: QRCodeRepository) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val bottomNavScreens = listOf(Screen.Vault, Screen.Generator, Screen.Scanner)
    val showBottomBar = currentRoute in bottomNavScreens.map { it.route }

    Scaffold(
        containerColor = GlassBackground,
        bottomBar = {
            if (showBottomBar) {
                GlassBottomNavigation(
                    screens = bottomNavScreens,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (route != currentRoute) {
                            navController.navigate(route) {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Vault.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Vault.route) {
                VaultScreen(
                    repository = repository,
                    onNavigateToScanner = { navController.navigate(Screen.Scanner.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Generator.route) {
                GeneratorScreen(repository = repository)
            }
            composable(Screen.Scanner.route) {
                ScannerScreen(
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun GlassBottomNavigation(
    screens: List<Screen>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    screens.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { onNavigate(screen.route) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GlassPrimary,
                                selectedTextColor = GlassPrimary,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = GlassPrimary.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }
        }
    }
}
