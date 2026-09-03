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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.aibook.android.feature.importer.ImportBooksScreen
import com.aibook.android.feature.reader.ReaderScreen
import com.aibook.android.feature.reader.BookReaderRoute
import com.aibook.android.feature.settings.AboutScreen
import com.aibook.android.feature.settings.BackupRestoreScreen
import com.aibook.android.feature.settings.PrivacyPermissionsScreen
import com.aibook.android.feature.settings.ScanDirectoryScreen
import com.aibook.android.feature.settings.LocalScanScreen
import com.aibook.android.feature.settings.ScanResultScreen
import com.aibook.android.feature.settings.SettingsScreen
import com.aibook.android.feature.settings.ReadingSettingsScreen
import com.aibook.android.feature.settings.AppThemeSettingsScreen
import com.aibook.android.feature.settings.ShelfSettingsScreen
import com.aibook.android.feature.settings.StorageCacheScreen
import com.aibook.android.feature.settings.SyncConnectionSettingsScreen
import com.aibook.android.feature.shelf.ShelfScreen
import com.aibook.android.feature.shelf.BookDetailScreen
import com.aibook.android.feature.shelf.BookSourcesScreen
import com.aibook.android.feature.shelf.CreateShelfFolderScreen
import com.aibook.android.feature.shelf.RecentReadingScreen
import com.aibook.android.feature.shelf.ShelfFolderDetailScreen
import com.aibook.android.feature.shelf.ShelfFoldersScreen
import com.aibook.android.feature.shelf.ShelfSortFilterScreen
import com.aibook.android.feature.store.BookStoreScreen
import com.aibook.android.feature.store.StoreRemoteBookDetailScreen
import com.aibook.android.feature.store.StoreCategoryScreen
import com.aibook.android.feature.store.StoreSearchScreen
import com.aibook.android.feature.server.ServerLibraryScreen
import com.aibook.android.feature.server.ServerLibrarySection
import com.aibook.android.feature.server.BookListEditorScreen
import com.aibook.android.feature.server.BackendBookDetailScreen
import com.aibook.android.feature.server.BackendCollectionScreen
import com.aibook.android.feature.server.BackendBooklistsScreen
import com.aibook.android.feature.downloads.DownloadManagerScreen
import com.aibook.android.feature.downloads.DownloadDetailScreen
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
    val navigateDiscoverySource: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(Screen.Store.route) { inclusive = false }
            launchSingleTop = true
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val selectedBottomRoute = when (currentRoute) {
        Screen.ShelfFolders.route,
        Screen.ShelfList.route,
        Screen.ShelfFolderDetail.route,
        Screen.NewShelfFolder.route,
        Screen.ShelfBatch.route,
        Screen.RecentReading.route,
        Screen.ShelfSortFilter.route -> Screen.Shelf.route
        Screen.StoreCategory.route,
        Screen.StoreSearch.route,
        Screen.StoreOpds.route,
        Screen.ServerLibrary.route,
        Screen.BackendRecent.route,
        Screen.BackendFavorites.route,
        Screen.BackendBooklists.route,
        Screen.NewBookList.route,
        Screen.EditBookList.route -> Screen.Store.route
        Screen.StoreRemoteBookDetail.route -> Screen.Store.route
        Screen.OpdsAddSource.route -> Screen.Opds.route
        Screen.ImportBooks.route -> Screen.Opds.route
        Screen.ScanDirectories.route -> Screen.Settings.route
        Screen.ShelfSettings.route -> Screen.Settings.route
        Screen.SyncConnectionSettings.route -> Screen.Settings.route
        Screen.StorageCache.route -> Screen.Settings.route
        Screen.Downloads.route -> Screen.Settings.route
        Screen.PrivacyPermissions.route -> Screen.Settings.route
        Screen.About.route -> Screen.Settings.route
        Screen.BackupRestore.route -> Screen.Settings.route
        Screen.ReadingSettings.route -> Screen.Settings.route
        else -> currentRoute
    }
    val bottomBarRoutes = bottomTabs.map { it.screen.route } +
        listOf(
            Screen.StoreCategory.route,
            Screen.StoreSearch.route,
            Screen.StoreOpds.route,
            Screen.OpdsServiceDetail.route,
            Screen.StoreRemoteBookDetail.route,
            Screen.ServerLibrary.route,
            Screen.BackendRecent.route,
            Screen.BackendFavorites.route,
            Screen.BackendBooklists.route,
            Screen.Opds.route,
            Screen.OpdsAddSource.route,
            Screen.ImportBooks.route,
            Screen.ShelfSettings.route,
            Screen.ScanDirectories.route,
            Screen.SyncConnectionSettings.route,
            Screen.StorageCache.route,
            Screen.Downloads.route,
            Screen.PrivacyPermissions.route,
            Screen.About.route,
            Screen.ShelfFolders.route,
            Screen.ShelfFolderDetail.route,
            Screen.NewShelfFolder.route,
            Screen.ShelfList.route,
            Screen.ShelfBatch.route,
            Screen.RecentReading.route,
            Screen.ShelfSortFilter.route,
            Screen.NewBookList.route,
            Screen.EditBookList.route,
            Screen.BackupRestore.route,
            Screen.ReadingSettings.route
        )
    val showBottomBar = currentRoute in bottomBarRoutes ||
        currentRoute?.startsWith("bookstore/opds/add") == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier
                        .height(DesignTokens.BottomNavigationHeight)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp)),
                    containerColor = MaterialTheme.colorScheme.surface,
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
                                    .height(DesignTokens.BottomNavigationHeight)
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
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                            RoundedCornerShape(DesignTokens.RadiusLarge)
                                        )
                                        .padding(horizontal = 18.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(tab.icon, contentDescription = tab.label, tint = color)
                                }
                                Text(tab.label, color = color, style = MaterialTheme.typography.labelSmall)
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
                        },
                        onRemoteReadClick = { bookId ->
                            navController.navigate(Screen.RemoteReader.createRoute(bookId))
                        },
                        onFoldersClick = { navController.navigate(Screen.ShelfFolders.route) },
                        onRecentReadingClick = { navController.navigate(Screen.RecentReading.route) },
                        onSortClick = { navController.navigate(Screen.ShelfSortFilter.route) }
                    )
                }
            }
            composable(Screen.ShelfList.route) {
                PaddedScreen(paddingValues) {
                    ShelfScreen(
                        onBookClick = { navController.navigate(Screen.BookDetail.createRoute(it)) },
                        onReadClick = { navController.navigate(Screen.Reader.createRoute(it)) },
                        onRemoteReadClick = { navController.navigate(Screen.RemoteReader.createRoute(it)) },
                        onFoldersClick = { navController.navigate(Screen.ShelfFolders.route) },
                        onRecentReadingClick = { navController.navigate(Screen.RecentReading.route) },
                        onSortClick = { navController.navigate(Screen.ShelfSortFilter.route) },
                        initialViewMode = 1
                    )
                }
            }
            composable(Screen.ShelfBatch.route) {
                PaddedScreen(paddingValues) {
                    ShelfScreen(
                        onBookClick = { navController.navigate(Screen.BookDetail.createRoute(it)) },
                        onReadClick = { navController.navigate(Screen.Reader.createRoute(it)) },
                        onRemoteReadClick = { navController.navigate(Screen.RemoteReader.createRoute(it)) },
                        initialManagementMode = true
                    )
                }
            }
            composable(Screen.ShelfFolders.route) {
                PaddedScreen(paddingValues) {
                    ShelfFoldersScreen(
                        onBack = { navController.popBackStack() },
                        onFolderClick = { navController.navigate(Screen.ShelfFolderDetail.createRoute(it)) },
                        onCreateClick = { navController.navigate(Screen.NewShelfFolder.route) }
                    )
                }
            }
            composable(
                route = Screen.ShelfFolderDetail.route,
                arguments = listOf(navArgument("folderId") { type = NavType.StringType })
            ) { entry ->
                val folderId = entry.arguments?.getString("folderId").orEmpty()
                PaddedScreen(paddingValues) {
                    ShelfFolderDetailScreen(
                        folderId = folderId,
                        onBack = { navController.popBackStack() },
                        onBookClick = { navController.navigate(Screen.BookDetail.createRoute(it)) }
                    )
                }
            }
            composable(Screen.NewShelfFolder.route) {
                PaddedScreen(paddingValues) {
                    CreateShelfFolderScreen(
                        onBack = { navController.popBackStack() },
                        onCreated = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.ShelfSortFilter.route) {
                PaddedScreen(paddingValues) {
                    ShelfSortFilterScreen(
                        onBack = { navController.popBackStack() },
                        onApplied = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.RecentReading.route) {
                PaddedScreen(paddingValues) {
                    RecentReadingScreen(
                        onBack = { navController.popBackStack() },
                        onBookClick = { navController.navigate(Screen.BookDetail.createRoute(it)) }
                    )
                }
            }
            composable(Screen.Opds.route) {
                PaddedScreen(paddingValues) {
                    OpdsScreen(
                        onAddSourceClick = { navController.navigate(Screen.OpdsAddSource.route) },
                        onScanDirectoriesClick = { navController.navigate(Screen.ScanDirectories.route) },
                        onImportBooksClick = { navController.navigate(Screen.ImportBooks.route) }
                    )
                }
            }
            composable(Screen.ImportBooks.route) {
                PaddedScreen(paddingValues) {
                    ImportBooksScreen(
                        onBack = { navController.popBackStack() },
                        onFolderImport = { navController.navigate(Screen.ScanDirectories.route) }
                    )
                }
            }
            composable(Screen.Store.route) {
                PaddedScreen(paddingValues) {
                    BookStoreScreen(
                        onServerLibraryClick = { navigateDiscoverySource(Screen.ServerLibrary.route) },
                        onCategoryClick = { navController.navigate(Screen.StoreCategory.route) },
                        onSearchClick = { navController.navigate(Screen.StoreSearch.route) },
                        onDownloadsClick = { navController.navigate(Screen.Downloads.route) },
                        onBookClick = { bookId ->
                            navController.navigate(Screen.BookDetail.createRoute(bookId))
                        },
                        onRemoteBookClick = { bookId ->
                            navController.navigate(Screen.StoreRemoteBookDetail.createRoute(bookId))
                        }
                    )
                }
            }
            composable(Screen.StoreOpds.route) {
                PaddedScreen(paddingValues) {
                    OpdsScreen(
                        servicesOnly = true,
                        onAddSourceClick = { navController.navigate(Screen.OpdsAddSource.route) },
                        onConnectionClick = { navController.navigate(Screen.OpdsServiceDetail.createRoute(it)) }
                    )
                }
            }
            composable(
                route = Screen.OpdsServiceDetail.route,
                arguments = listOf(navArgument("serviceId") { type = NavType.StringType })
            ) { entry ->
                PaddedScreen(paddingValues) {
                    OpdsScreen(
                        servicesOnly = true,
                        initialConnectionId = entry.arguments?.getString("serviceId"),
                        onAddSourceClick = { navController.navigate(Screen.OpdsAddSource.route) },
                        onCategoriesClick = { navController.navigate(Screen.OpdsCategories.createRoute(it)) },
                        onCategoryClick = { serviceId, href ->
                            navController.navigate(Screen.OpdsCategoryBooks.createRoute(serviceId, href))
                        }
                    )
                }
            }
            composable(
                route = Screen.OpdsCategories.route,
                arguments = listOf(navArgument("serviceId") { type = NavType.StringType })
            ) { entry ->
                PaddedScreen(paddingValues) {
                    OpdsScreen(
                        servicesOnly = true,
                        pageTitle = "全部分类",
                        initialConnectionId = entry.arguments?.getString("serviceId"),
                        categoriesOnly = true,
                        onCategoryClick = { serviceId, href ->
                            navController.navigate(Screen.OpdsCategoryBooks.createRoute(serviceId, href))
                        }
                    )
                }
            }
            composable(
                route = Screen.OpdsCategoryBooks.route,
                arguments = listOf(
                    navArgument("serviceId") { type = NavType.StringType },
                    navArgument("categoryId") { type = NavType.StringType }
                )
            ) { entry ->
                PaddedScreen(paddingValues) {
                    OpdsScreen(
                        servicesOnly = true,
                        pageTitle = "分类书籍",
                        initialConnectionId = entry.arguments?.getString("serviceId"),
                        initialHref = entry.arguments?.getString("categoryId"),
                        booksOnly = true
                    )
                }
            }
            composable(Screen.ServerLibrary.route) {
                PaddedScreen(paddingValues) {
                    ServerLibraryScreen(
                        onLocalLibraryClick = { navigateDiscoverySource(Screen.Store.route) },
                        onOpdsClick = { navigateDiscoverySource(Screen.StoreOpds.route) },
                        onReadBook = { bookId -> navController.navigate(Screen.RemoteReader.createRoute(bookId)) },
                        onSectionClick = { section ->
                            val route = when (section) {
                                ServerLibrarySection.ALL -> Screen.BackendRecent.route
                                ServerLibrarySection.FAVORITES -> Screen.BackendFavorites.route
                                ServerLibrarySection.SHELF -> Screen.BackendRecent.route
                                ServerLibrarySection.LISTS -> Screen.BackendBooklists.route
                            }
                            navController.navigate(route)
                        }
                    )
                }
            }
            composable(Screen.BackendRecent.route) {
                PaddedScreen(paddingValues) {
                    BackendCollectionScreen(
                        section = ServerLibrarySection.ALL,
                        title = "最近加入",
                        onBack = { navController.popBackStack() },
                        onBookClick = { navController.navigate(Screen.RemoteBookDetail.createRoute(it)) }
                    )
                }
            }
            composable(Screen.BackendFavorites.route) {
                PaddedScreen(paddingValues) {
                    BackendCollectionScreen(
                        section = ServerLibrarySection.FAVORITES,
                        title = "收藏",
                        listMode = true,
                        onBack = { navController.popBackStack() },
                        onBookClick = { navController.navigate(Screen.RemoteBookDetail.createRoute(it)) }
                    )
                }
            }
            composable(Screen.BackendBooklists.route) {
                PaddedScreen(paddingValues) {
                    BackendBooklistsScreen(
                        onBack = { navController.popBackStack() },
                        onBooklistClick = { navController.navigate(Screen.BackendBookListDetail.createRoute(it)) },
                        onCreate = { navController.navigate(Screen.NewBookList.route) }
                    )
                }
            }
            composable(
                route = Screen.BackendBookListDetail.route,
                arguments = listOf(navArgument("listId") { type = NavType.LongType })
            ) { entry ->
                PaddedScreen(paddingValues) {
                    BackendCollectionScreen(
                        section = ServerLibrarySection.LISTS,
                        title = "书单详情",
                        listId = entry.arguments?.getLong("listId"),
                        columns = 2,
                        onBack = { navController.popBackStack() },
                        onBookClick = { navController.navigate(Screen.RemoteBookDetail.createRoute(it)) }
                    )
                }
            }
            composable(Screen.NewBookList.route) {
                PaddedScreen(paddingValues) {
                    BookListEditorScreen(
                        listId = null,
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() }
                    )
                }
            }
            composable(
                route = Screen.EditBookList.route,
                arguments = listOf(navArgument("listId") { type = NavType.LongType })
            ) { entry ->
                PaddedScreen(paddingValues) {
                    BookListEditorScreen(
                        listId = entry.arguments?.getLong("listId"),
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() }
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
            composable(Screen.StoreSearch.route) {
                PaddedScreen(paddingValues) {
                    StoreSearchScreen(
                        onBack = { navController.popBackStack() },
                        onBookClick = { bookId -> navController.navigate(Screen.BookDetail.createRoute(bookId)) },
                        onRemoteBookClick = { bookId ->
                            navController.navigate(Screen.StoreRemoteBookDetail.createRoute(bookId))
                        }
                    )
                }
            }
            composable(
                route = Screen.StoreSearchResults.route,
                arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                PaddedScreen(paddingValues) {
                    StoreSearchScreen(
                        initialQuery = entry.arguments?.getString("query"),
                        onBack = { navController.popBackStack() },
                        onBookClick = { navController.navigate(Screen.BookDetail.createRoute(it)) },
                        onRemoteBookClick = { navController.navigate(Screen.StoreRemoteBookDetail.createRoute(it)) }
                    )
                }
            }
            composable(Screen.Settings.route) {
                PaddedScreen(paddingValues) {
                    SettingsScreen(
                        onThemeClick = { navController.navigate(Screen.ThemeSettings.route) },
                        onReadingSettingsClick = { navController.navigate(Screen.ReadingSettings.route) },
                        onShelfSettingsClick = { navController.navigate(Screen.ShelfSettings.route) },
                        onScanDirectoriesClick = { navController.navigate(Screen.ScanDirectories.route) },
                        onSyncConnectionClick = { navController.navigate(Screen.SyncConnectionSettings.route) },
                        onStorageClick = { navController.navigate(Screen.StorageCache.route) },
                        onDownloadsClick = { navController.navigate(Screen.Downloads.route) },
                        onRecentReadingClick = { navController.navigate(Screen.RecentReading.route) },
                        onBackupClick = { navController.navigate(Screen.BackupRestore.route) },
                        onPrivacyClick = { navController.navigate(Screen.PrivacyPermissions.route) },
                        onAboutClick = { navController.navigate(Screen.About.route) }
                    )
                }
            }
            composable(Screen.ThemeSettings.route) {
                PaddedScreen(paddingValues) {
                    AppThemeSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.ReadingSettings.route) {
                PaddedScreen(paddingValues) { ReadingSettingsScreen(onBack = { navController.popBackStack() }) }
            }
            composable(Screen.ShelfSettings.route) {
                PaddedScreen(paddingValues) {
                    ShelfSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.ScanDirectories.route) {
                PaddedScreen(paddingValues) {
                    ScanDirectoryScreen(
                        onBack = { navController.popBackStack() },
                        onStartScan = { navController.navigate(Screen.LocalScan.route) }
                    )
                }
            }
            composable(Screen.LocalScan.route) {
                PaddedScreen(paddingValues) {
                    LocalScanScreen(
                        onBack = { navController.popBackStack() },
                        onComplete = { navController.navigate(Screen.ScanResult.route) }
                    )
                }
            }
            composable(Screen.ScanResult.route) {
                PaddedScreen(paddingValues) {
                    ScanResultScreen(
                        onBack = { navController.popBackStack() },
                        onImport = { navController.navigate(Screen.ImportBooks.route) }
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
                        onBack = { navController.popBackStack() },
                        onDownloadsClick = { navController.navigate(Screen.Downloads.route) }
                    )
                }
            }
            composable(Screen.Downloads.route) {
                PaddedScreen(paddingValues) {
                    DownloadManagerScreen(
                        onBack = { navController.popBackStack() },
                        onTaskClick = { navController.navigate(Screen.DownloadDetail.createRoute(it)) }
                    )
                }
            }
            composable(
                route = Screen.DownloadDetail.route,
                arguments = listOf(navArgument("taskId") { type = NavType.StringType })
            ) { entry ->
                PaddedScreen(paddingValues) {
                    DownloadDetailScreen(
                        taskId = entry.arguments?.getString("taskId").orEmpty(),
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
            composable(Screen.BackupRestore.route) {
                PaddedScreen(paddingValues) { BackupRestoreScreen(onBack = { navController.popBackStack() }) }
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
                route = Screen.BookSources.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
                PaddedScreen(paddingValues) {
                    BookSourcesScreen(
                        bookId = bookId,
                        onBack = { navController.popBackStack() },
                        onSelect = { selectedId -> navController.navigate(Screen.BookDetail.createRoute(selectedId)) }
                    )
                }
            }
            composable(
                route = Screen.RemoteBookDetail.route,
                arguments = listOf(navArgument("bookId") { type = NavType.LongType })
            ) { entry ->
                PaddedScreen(paddingValues) {
                    BackendBookDetailScreen(
                        bookId = entry.arguments?.getLong("bookId") ?: 0L,
                        onBack = { navController.popBackStack() },
                        onRead = { navController.navigate(Screen.RemoteReader.createRoute(it)) }
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
                        onBack = { navController.popBackStack() },
                        onRelatedBookClick = { relatedId ->
                            navController.navigate(Screen.BookDetail.createRoute(relatedId))
                        },
                        onSourcesClick = { navController.navigate(Screen.BookSources.createRoute(bookId)) }
                    )
                }
            }
            composable(
                route = Screen.Reader.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
                BookReaderRoute(
                    bookId = bookId,
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
