package com.aibook.android

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aibook.android.feature.opds.OpdsScreen
import com.aibook.android.feature.reader.ReaderScreen
import com.aibook.android.feature.settings.SettingsScreen
import com.aibook.android.feature.shelf.ShelfScreen
import com.aibook.android.feature.shelf.BookDetailScreen
import com.aibook.android.navigation.Screen

private data class BottomTab(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab(Screen.Shelf, "书架", Icons.Default.Book),
    BottomTab(Screen.Opds, "书库", Icons.Default.CloudDownload),
    BottomTab(Screen.Settings, "设置", Icons.Default.Settings)
)

@Composable
fun AiBookApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomTabs.map { it.screen.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.screen.route,
                            onClick = {
                                navController.navigate(tab.screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Shelf.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Shelf.route) {
                ShelfScreen(
                    onBookClick = { bookId ->
                        navController.navigate(Screen.BookDetail.createRoute(bookId))
                    }
                )
            }
            composable(Screen.Opds.route) {
                OpdsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = Screen.BookDetail.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
                BookDetailScreen(
                    bookId = bookId,
                    onReadClick = {
                        navController.navigate(Screen.Reader.createRoute(bookId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.Reader.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
                ReaderScreen(
                    bookId = bookId,
                    isRemote = false,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.RemoteReader.route,
                arguments = listOf(navArgument("bookId") { type = NavType.LongType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L
                ReaderScreen(
                    bookId = bookId.toString(),
                    isRemote = true,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
