package com.gerko.fourenote.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gerko.fourenote.data.NoteRepository
import com.gerko.fourenote.ui.editor.NoteEditorScreen
import com.gerko.fourenote.ui.notelist.NoteListScreen
import com.gerko.fourenote.ui.notelist.NoteListViewModel

private object Routes {
    const val NOTE_LIST = "noteList"
    const val NOTE_EDITOR = "noteEditor/{noteId}"
    fun noteEditor(noteId: String) = "noteEditor/$noteId"
}

@Composable
fun FourENoteNavGraph(
    repository: NoteRepository,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.NOTE_LIST) {
        composable(Routes.NOTE_LIST) {
            val viewModel: NoteListViewModel = viewModel(
                factory = NoteListViewModel.Factory(repository),
            )
            NoteListScreen(
                viewModel = viewModel,
                onOpenNote = { noteId -> navController.navigate(Routes.noteEditor(noteId)) },
            )
        }
        composable(Routes.NOTE_EDITOR) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
            NoteEditorScreen(
                noteId = noteId,
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
