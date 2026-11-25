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
import com.davidzhu.wantnote.ui.components.WantFab
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.material3.MaterialTheme
import com.davidzhu.wantnote.ui.components.WantNavigationBar
import com.davidzhu.wantnote.ui.components.WantNavigationBarItem
import com.davidzhu.wantnote.ui.components.WantScaffold
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.davidzhu.wantnote.data.EditorMode
import com.davidzhu.wantnote.ui.calendar.CalendarScreen
import com.davidzhu.wantnote.ui.editor.EditorScreen
import com.davidzhu.wantnote.ui.editor.NoteDetailScreen
import com.davidzhu.wantnote.ui.home.HomeScreen
import com.davidzhu.wantnote.ui.insights.InsightsScreen
import com.davidzhu.wantnote.ui.migration.MigrationGate
import com.davidzhu.wantnote.ui.search.SearchScreen
import com.davidzhu.wantnote.ui.settings.AboutScreen
import com.davidzhu.wantnote.ui.settings.AiSettingsScreen
import com.davidzhu.wantnote.ui.settings.AttachmentSettingsScreen
import com.davidzhu.wantnote.ui.settings.BackupSyncScreen
import com.davidzhu.wantnote.ui.settings.BoxManagementScreen
import com.davidzhu.wantnote.ui.settings.EditorSettingsScreen
import com.davidzhu.wantnote.ui.settings.GeneralSettingsScreen
import com.davidzhu.wantnote.ui.settings.ImportExportScreen
import com.davidzhu.wantnote.ui.settings.SettingsScreen
import com.davidzhu.wantnote.ui.settings.TagManagementScreen
import com.davidzhu.wantnote.ui.settings.ThemeEditorScreen
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

    // 0.9.0 强制迁移闸门：待迁移笔记 > 0 时先迁移，再进主界面。
    var migrationGate by remember { mutableStateOf(true) }
    if (migrationGate) {
        MigrationGate(onDone = { migrationGate = false })
        return
    }

    fun openDetail(id: String) {
        nav.navigate("note/$id")
    }

    fun openEditor(id: String, mode: EditorMode = EditorMode.WYSIWYG) {
        nav.navigate("editor/$id?mode=${mode.name}")
    }

    fun createAndOpen() {
        scope.launch {
            val note = notes.createBlank()
            openEditor(note.id, EditorMode.WYSIWYG)
        }
    }

    WantScaffold(
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
                    onOpenNote = { openDetail(it) },
                    onEditNote = { openEditor(it, EditorMode.WYSIWYG) },
                    onSearch = { nav.navigate("search") },
                    onCreate = { createAndOpen() },
                )
            }
            composable("calendar") {
                CalendarScreen(onOpenNote = { openDetail(it) })
            }
            composable("insights") {
                InsightsScreen(
                    onOpenNote = { openDetail(it) },
                    onOpenAiSettings = { nav.navigate("settings/ai") },
                )
            }
            composable("settings") {
                SettingsScreen(onOpen = { nav.navigate(it) })
            }
            composable("settings/general") {
                GeneralSettingsScreen(onBack = { nav.popBackStack() })
            }
            composable("settings/themes") {
                ThemeEditorScreen(onBack = { nav.popBackStack() })
            }
            composable("settings/editor") {
                EditorSettingsScreen(onBack = { nav.popBackStack() })
            }
            composable("settings/boxes") {
                BoxManagementScreen(onBack = { nav.popBackStack() })
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
                route = "note/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
            ) { entry ->
                val noteId = entry.arguments?.getString("noteId") ?: return@composable
                NoteDetailScreen(
                    noteId = noteId,
                    onBack = { nav.popBackStack() },
                    onEdit = { mode -> openEditor(noteId, mode) },
                    onOpenNote = { targetId -> openDetail(targetId) },
                )
            }
            composable(
                route = "editor/{noteId}?mode={mode}",
                arguments = listOf(
                    navArgument("noteId") { type = NavType.StringType },
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = EditorMode.WYSIWYG.name
                    },
                ),
            ) { entry ->
                val noteId = entry.arguments?.getString("noteId") ?: return@composable
                val initialMode = EditorMode.fromStorage(entry.arguments?.getString("mode"))
                EditorScreen(
                    noteId = noteId,
                    initialMode = initialMode,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("search") {
                SearchScreen(
                    onOpenNote = { openDetail(it) },
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
    WantNavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        WantNavigationBarItem(
            selected = currentRoute == MainTab.Home.route,
            onClick = { onTab(MainTab.Home.route) },
            icon = MainTab.Home.icon,
            label = MainTab.Home.label,
        )
        WantNavigationBarItem(
            selected = currentRoute == MainTab.Calendar.route,
            onClick = { onTab(MainTab.Calendar.route) },
            icon = MainTab.Calendar.icon,
            label = MainTab.Calendar.label,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            WantFab(
                onClick = onPlus,
                modifier = Modifier
                    .size(52.dp)
                    .offset(y = (-6).dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "新建笔记")
            }
        }
        WantNavigationBarItem(
            selected = currentRoute == MainTab.Insights.route,
            onClick = { onTab(MainTab.Insights.route) },
            icon = MainTab.Insights.icon,
            label = MainTab.Insights.label,
        )
        WantNavigationBarItem(
            selected = currentRoute == MainTab.Settings.route,
            onClick = { onTab(MainTab.Settings.route) },
            icon = MainTab.Settings.icon,
            label = MainTab.Settings.label,
        )
    }
}
