package com.aibook.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aibook.android.feature.opds.OpdsScreen
import com.aibook.android.feature.opds.OpdsAddSourceScreen
import com.aibook.android.feature.reader.ReaderScreen
import com.aibook.android.feature.reader.ReaderThemeSettingsScreen
import com.aibook.android.feature.settings.AboutScreen
import com.aibook.android.feature.settings.PrivacyPermissionsScreen
import com.aibook.android.feature.settings.ScanDirectoryScreen
import com.aibook.android.feature.settings.SettingsScreen
import com.aibook.android.feature.settings.StorageCacheScreen
import com.aibook.android.feature.settings.SyncConnectionSettingsScreen
import com.aibook.android.feature.shelf.ShelfScreen
import com.aibook.android.feature.shelf.BookDetailScreen
import com.aibook.android.feature.store.BookStoreScreen
import com.aibook.android.feature.store.StoreRemoteBookDetailScreen
import com.aibook.android.feature.store.StoreCategoryScreen
import com.aibook.android.navigation.Screen
import com.aibook.android.ui.design.DesignTokens

private data class BottomTab(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab(Screen.Shelf, "书架", Icons.Default.Book),
    BottomTab(Screen.Store, "书城", Icons.Default.LocalMall),
    BottomTab(Screen.Opds, "发现", Icons.Default.Explore),
    BottomTab(Screen.Settings, "设置", Icons.Default.Settings)
)

@Composable
fun AiBookApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val selectedBottomRoute = when (currentRoute) {
        Screen.StoreCategory.route -> Screen.Store.route
        Screen.StoreRemoteBookDetail.route -> Screen.Store.route
        Screen.OpdsAddSource.route -> Screen.Opds.route
        Screen.ScanDirectories.route -> Screen.Settings.route
        Screen.SyncConnectionSettings.route -> Screen.Settings.route
        Screen.StorageCache.route -> Screen.Settings.route
        Screen.PrivacyPermissions.route -> Screen.Settings.route
        Screen.About.route -> Screen.Settings.route
        else -> currentRoute
    }
    val bottomBarRoutes = bottomTabs.map { it.screen.route } +
        listOf(
            Screen.StoreCategory.route,
            Screen.StoreRemoteBookDetail.route,
            Screen.OpdsAddSource.route,
            Screen.ScanDirectories.route,
            Screen.SyncConnectionSettings.route,
            Screen.StorageCache.route,
            Screen.PrivacyPermissions.route,
            Screen.About.route
        )
    val showBottomBar = currentRoute in bottomBarRoutes ||
        currentRoute?.startsWith("opds-add-source") == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.height(64.dp),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bottomTabs.forEach { tab ->
                            val selected = selectedBottomRoute == tab.screen.route
                            val color = if (selected) DesignTokens.Accent else MaterialTheme.colorScheme.onSurfaceVariant
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        navController.navigate(tab.screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(tab.icon, contentDescription = tab.label, tint = color)
                                Text(tab.label, color = color)
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Shelf.route
        ) {
            composable(Screen.Shelf.route) {
                PaddedScreen(paddingValues) {
                    ShelfScreen(
                        onBookClick = { bookId ->
                            navController.navigate(Screen.BookDetail.createRoute(bookId))
                        },
                        onReadClick = { bookId ->
                            navController.navigate(Screen.Reader.createRoute(bookId))
                        }
                    )
                }
            }
            composable(Screen.Opds.route) {
                PaddedScreen(paddingValues) {
                    OpdsScreen(
                        onAddSourceClick = { navController.navigate(Screen.OpdsAddSource.route) },
                        onScanDirectoriesClick = { navController.navigate(Screen.ScanDirectories.route) }
                    )
                }
            }
            composable(Screen.Store.route) {
                PaddedScreen(paddingValues) {
                    BookStoreScreen(
                        onCategoryClick = { navController.navigate(Screen.StoreCategory.route) },
                        onBookClick = { bookId ->
                            navController.navigate(Screen.BookDetail.createRoute(bookId))
                        },
                        onRemoteBookClick = { bookId ->
                            navController.navigate(Screen.StoreRemoteBookDetail.createRoute(bookId))
                        }
                    )
                }
            }
            composable(Screen.StoreCategory.route) {
                PaddedScreen(paddingValues) {
                    StoreCategoryScreen(
                        onBack = { navController.popBackStack() },
                        onBookClick = { bookId ->
                            navController.navigate(Screen.BookDetail.createRoute(bookId))
                        },
                        onRemoteBookClick = { bookId ->
                            navController.navigate(Screen.StoreRemoteBookDetail.createRoute(bookId))
                        }
                    )
                }
            }
            composable(Screen.Settings.route) {
                PaddedScreen(paddingValues) {
                    SettingsScreen(
                        onThemeClick = { navController.navigate(Screen.ThemeSettings.route) },
                        onScanDirectoriesClick = { navController.navigate(Screen.ScanDirectories.route) },
                        onSyncConnectionClick = { navController.navigate(Screen.SyncConnectionSettings.route) },
                        onStorageClick = { navController.navigate(Screen.StorageCache.route) },
                        onPrivacyClick = { navController.navigate(Screen.PrivacyPermissions.route) },
                        onAboutClick = { navController.navigate(Screen.About.route) }
                    )
                }
            }
            composable(Screen.ThemeSettings.route) {
                PaddedScreen(paddingValues) {
                    ReaderThemeSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.ScanDirectories.route) {
                PaddedScreen(paddingValues) {
                    ScanDirectoryScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.SyncConnectionSettings.route) {
                PaddedScreen(paddingValues) {
                    SyncConnectionSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.StorageCache.route) {
                PaddedScreen(paddingValues) {
                    StorageCacheScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.PrivacyPermissions.route) {
                PaddedScreen(paddingValues) {
                    PrivacyPermissionsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.About.route) {
                PaddedScreen(paddingValues) {
                    AboutScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.OpdsAddSource.route) {
                PaddedScreen(paddingValues) {
                    OpdsAddSourceScreen(
                        connectionId = null,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                route = "${Screen.OpdsAddSource.route}?connectionId={connectionId}",
                arguments = listOf(navArgument("connectionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val connectionId = backStackEntry.arguments?.getString("connectionId")
                PaddedScreen(paddingValues) {
                    OpdsAddSourceScreen(
                        connectionId = connectionId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                route = Screen.StoreRemoteBookDetail.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
                PaddedScreen(paddingValues) {
                    StoreRemoteBookDetailScreen(
                        bookId = bookId,
                        onBack = { navController.popBackStack() },
                        onOpenLocalBook = { localBookId ->
                            navController.navigate(Screen.BookDetail.createRoute(localBookId))
                        }
                    )
                }
            }
            composable(
                route = Screen.BookDetail.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
                PaddedScreen(paddingValues) {
                    BookDetailScreen(
                        bookId = bookId,
                        onReadClick = {
                            navController.navigate(Screen.Reader.createRoute(bookId))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
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

@Composable
private fun PaddedScreen(
    paddingValues: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.padding(paddingValues)) {
        content()
    }
}
