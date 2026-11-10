package com.davidzhu.wantnote.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.ui.editor.EditorScreen
import com.davidzhu.wantnote.ui.search.SearchScreen
import com.davidzhu.wantnote.ui.timeline.TimelineScreen

val LocalNotes = staticCompositionLocalOf<NoteRepository> {
    error("NoteRepository not provided")
}

@Composable
fun WantNoteApp() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "timeline") {
        composable("timeline") {
            TimelineScreen(
                onOpenNote = { id -> nav.navigate("editor/$id") },
                onSearch = { nav.navigate("search") },
            )
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
                onOpenNote = { id -> nav.navigate("editor/$id") },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
