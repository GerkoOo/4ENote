package com.gerko.fourenote.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gerko.fourenote.data.NoteRepository
import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.data.attachments.BackgroundLoader
import com.gerko.fourenote.data.sync.SyncManager
import com.gerko.fourenote.ui.editor.NoteEditorScreen
import com.gerko.fourenote.ui.editor.NoteEditorViewModel
import com.gerko.fourenote.ui.editor.ToolPreferences
import com.gerko.fourenote.ui.notelist.NoteListScreen
import com.gerko.fourenote.ui.notelist.NoteListViewModel
import com.gerko.fourenote.ui.settings.CloudSettingsScreen

private object Routes {
    const val NOTE_LIST = "noteList"
    const val NOTE_EDITOR = "noteEditor/{noteId}"
    const val CLOUD_SETTINGS = "cloudSettings"
    fun noteEditor(noteId: String) = "noteEditor/$noteId"
}

@Composable
fun FourENoteNavGraph(
    repository: NoteRepository,
    syncManager: SyncManager,
    attachments: AttachmentStore,
    backgrounds: BackgroundLoader,
    toolPreferences: ToolPreferences,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.NOTE_LIST) {
        composable(Routes.NOTE_LIST) {
            val viewModel: NoteListViewModel = viewModel(
                factory = NoteListViewModel.Factory(repository, syncManager::requestSync),
            )
            NoteListScreen(
                viewModel = viewModel,
                syncManager = syncManager,
                backgrounds = backgrounds,
                darkTheme = darkTheme,
                onToggleTheme = onToggleTheme,
                onOpenNote = { noteId -> navController.navigate(Routes.noteEditor(noteId)) },
                onOpenCloudSettings = { navController.navigate(Routes.CLOUD_SETTINGS) },
            )
        }
        composable(Routes.CLOUD_SETTINGS) {
            CloudSettingsScreen(syncManager = syncManager, onBack = { navController.popBackStack() })
        }
        composable(Routes.NOTE_EDITOR) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
            val viewModel: NoteEditorViewModel = viewModel(
                key = noteId,
                factory = NoteEditorViewModel.Factory(
                    noteId, repository, attachments, toolPreferences, syncManager::requestSync, syncManager::requestSync,
                ),
            )
            NoteEditorScreen(viewModel = viewModel, backgrounds = backgrounds, onBack = { navController.popBackStack() })
        }
    }
}
