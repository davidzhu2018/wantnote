package com.davidzhu.wantnote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.davidzhu.wantnote.ui.calendar.CalendarScreen
import com.davidzhu.wantnote.ui.editor.EditorScreen
import com.davidzhu.wantnote.ui.home.HomeScreen
import com.davidzhu.wantnote.ui.insights.InsightsScreen
import com.davidzhu.wantnote.ui.search.SearchScreen
import com.davidzhu.wantnote.ui.settings.AboutScreen
import com.davidzhu.wantnote.ui.settings.AiSettingsScreen
import com.davidzhu.wantnote.ui.settings.AttachmentSettingsScreen
import com.davidzhu.wantnote.ui.settings.BackupSyncScreen
import com.davidzhu.wantnote.ui.settings.EditorSettingsScreen
import com.davidzhu.wantnote.ui.settings.GeneralSettingsScreen
import com.davidzhu.wantnote.ui.settings.ImportExportScreen
import com.davidzhu.wantnote.ui.settings.SettingsScreen
import com.davidzhu.wantnote.ui.settings.TagManagementScreen
import kotlinx.coroutines.launch

private enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "主页", Icons.Outlined.Home),
    Calendar("calendar", "日历", Icons.Outlined.CalendarMonth),
    Insights("insights", "洞察", Icons.Outlined.Insights),
    Settings("settings", "设置", Icons.Outlined.Settings),
}

@Composable
fun WantNoteApp() {
    val nav = rememberNavController()
    val notes = LocalNotes.current
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in setOf("home", "calendar", "insights", "settings")

    fun openEditor(id: String) {
        nav.navigate("editor/$id")
    }

    fun createAndOpen() {
        scope.launch {
            val note = notes.createBlank()
            openEditor(note.id)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                WantNoteBottomBar(
                    currentRoute = currentRoute,
                    onTab = { route ->
                        nav.navigate(route) {
                            popUpTo(nav.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onPlus = { createAndOpen() },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable("home") {
                HomeScreen(
                    onOpenNote = { openEditor(it) },
                    onSearch = { nav.navigate("search") },
                    onCreate = { createAndOpen() },
                )
            }
            composable("calendar") {
                CalendarScreen(onOpenNote = { openEditor(it) })
            }
            composable("insights") {
                InsightsScreen(
                    onOpenNote = { openEditor(it) },
                    onOpenAiSettings = { nav.navigate("settings/ai") },
                )
            }
            composable("settings") {
                SettingsScreen(onOpen = { nav.navigate(it) })
            }
            composable("settings/general") {
                GeneralSettingsScreen(onBack = { nav.popBackStack() })
            }
            composable("settings/editor") {
                EditorSettingsScreen(onBack = { nav.popBackStack() })
            }
            composable("settings/tags") {
                TagManagementScreen(onBack = { nav.popBackStack() })
            }
            composable("settings/attachments") {
                AttachmentSettingsScreen(onBack = { nav.popBackStack() })
            }
            composable("settings/sync") {
                BackupSyncScreen(onBack = { nav.popBackStack() })
            }
            composable("settings/importexport") {
                ImportExportScreen(onBack = { nav.popBackStack() })
            }
            composable("settings/ai") {
                AiSettingsScreen(onBack = { nav.popBackStack() })
            }
            composable("settings/about") {
                AboutScreen(onBack = { nav.popBackStack() })
            }
            composable(
                route = "editor/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
            ) { entry ->
                val noteId = entry.arguments?.getString("noteId") ?: return@composable
                EditorScreen(
                    noteId = noteId,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("search") {
                SearchScreen(
                    onOpenNote = { openEditor(it) },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun WantNoteBottomBar(
    currentRoute: String?,
    onTab: (String) -> Unit,
    onPlus: () -> Unit,
) {
    val colors = NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        NavigationBarItem(
            selected = currentRoute == MainTab.Home.route,
            onClick = { onTab(MainTab.Home.route) },
            icon = { Icon(MainTab.Home.icon, contentDescription = MainTab.Home.label) },
            label = { Text(MainTab.Home.label) },
            colors = colors,
        )
        NavigationBarItem(
            selected = currentRoute == MainTab.Calendar.route,
            onClick = { onTab(MainTab.Calendar.route) },
            icon = { Icon(MainTab.Calendar.icon, contentDescription = MainTab.Calendar.label) },
            label = { Text(MainTab.Calendar.label) },
            colors = colors,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            FloatingActionButton(
                onClick = onPlus,
                modifier = Modifier
                    .size(52.dp)
                    .offset(y = (-6).dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "新建笔记")
            }
        }
        NavigationBarItem(
            selected = currentRoute == MainTab.Insights.route,
            onClick = { onTab(MainTab.Insights.route) },
            icon = { Icon(MainTab.Insights.icon, contentDescription = MainTab.Insights.label) },
            label = { Text(MainTab.Insights.label) },
            colors = colors,
        )
        NavigationBarItem(
            selected = currentRoute == MainTab.Settings.route,
            onClick = { onTab(MainTab.Settings.route) },
            icon = { Icon(MainTab.Settings.icon, contentDescription = MainTab.Settings.label) },
            label = { Text(MainTab.Settings.label) },
            colors = colors,
        )
    }
}
