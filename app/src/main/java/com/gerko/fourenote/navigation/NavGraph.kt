package com.gerko.fourenote.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    const val SUPPORT = "support"
    const val TRASH = "trash"
    const val SECURITY = "security"
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
            // Back at the list, the way back along followed links is over.
            androidx.compose.runtime.LaunchedEffect(Unit) { com.gerko.fourenote.ui.editor.LinkTrail.clear() }
            val viewModel: NoteListViewModel = viewModel(
                factory = NoteListViewModel.Factory(
                    repository,
                    syncManager::requestSync,
                    (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.gerko.fourenote.FourENoteApp).scanInbox,
                    (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.gerko.fourenote.FourENoteApp).updateWatch,
                ),
            )
            NoteListScreen(
                viewModel = viewModel,
                syncManager = syncManager,
                backgrounds = backgrounds,
                darkTheme = darkTheme,
                onToggleTheme = onToggleTheme,
                onOpenNote = { noteId -> navController.navigate(Routes.noteEditor(noteId)) },
                onOpenCloudSettings = { navController.navigate(Routes.CLOUD_SETTINGS) },
                onOpenSupport = { navController.navigate(Routes.SUPPORT) },
                onOpenTrash = { navController.navigate(Routes.TRASH) },
                onOpenSecurity = { navController.navigate(Routes.SECURITY) },
            )
        }
        composable(Routes.SECURITY) {
            val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.gerko.fourenote.FourENoteApp
            com.gerko.fourenote.ui.settings.SecurityScreen(
                appLock = app.appLock,
                noteLock = app.noteLock,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TRASH) {
            val viewModel: com.gerko.fourenote.ui.notelist.TrashViewModel = viewModel(
                factory = com.gerko.fourenote.ui.notelist.TrashViewModel.Factory(repository, syncManager::requestSync),
            )
            com.gerko.fourenote.ui.notelist.TrashScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.SUPPORT) {
            com.gerko.fourenote.ui.settings.SupportScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CLOUD_SETTINGS) {
            CloudSettingsScreen(syncManager = syncManager, onBack = { navController.popBackStack() })
        }
        composable(Routes.NOTE_EDITOR) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
            val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.gerko.fourenote.FourENoteApp
            val noteLock = app.noteLock
            // The note's lock, read once per note; null while it loads. A note not saved yet (a new
            // one is written in the background while the editor opens) can't be locked.
            val note by androidx.compose.runtime.produceState<com.gerko.fourenote.data.Note?>(null, noteId) {
                value = repository.getById(noteId) ?: com.gerko.fourenote.data.Note(id = noteId)
            }
            val unlockedIds by noteLock.unlocked.collectAsState()
            val allUnlocked by noteLock.allUnlocked.collectAsState()
            val open = note?.let { !it.locked || allUnlocked || it.id in unlockedIds } == true
            // Closing the note (or leaving the app) makes it ask again next time.
            androidx.compose.runtime.DisposableEffect(noteId) {
                onDispose { noteLock.relock(noteId) }
            }
            when {
                note == null -> Unit
                open -> {
                    // An open locked note mustn't show in the recent-apps thumbnail either.
                    if (note?.locked == true && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
                        androidx.compose.runtime.DisposableEffect(activity) {
                            activity?.setRecentsScreenshotEnabled(false)
                            onDispose { activity?.setRecentsScreenshotEnabled(!app.appLock.enabled) }
                        }
                    }
                    val viewModel: NoteEditorViewModel = viewModel(
                        key = noteId,
                        factory = NoteEditorViewModel.Factory(
                            noteId, repository, attachments, toolPreferences, syncManager::requestSync, syncManager::requestSync,
                            deviceLayer = deviceLayer(androidx.compose.ui.platform.LocalContext.current),
                        ),
                    )
                    NoteEditorScreen(
                        viewModel = viewModel,
                        backgrounds = backgrounds,
                        onBack = {
                            // Leaving with the tool bar's arrow: forget jumps within this note, and go
                            // back to the exact spot in the note before it (unless that is already set).
                            val trail = com.gerko.fourenote.ui.editor.LinkTrail
                            if (trail.restore == null) {
                                while (trail.stops.lastOrNull()?.noteId == noteId) trail.stops.removeAt(trail.stops.lastIndex)
                                trail.stops.removeLastOrNull()?.let { trail.restore = it }
                            }
                            navController.popBackStack()
                        },
                        onOpenNote = { id ->
                            val trail = com.gerko.fourenote.ui.editor.LinkTrail
                            val earlier = trail.stops.indexOfFirst { it.noteId == id }
                            if (earlier < 0) {
                                navController.navigate(Routes.noteEditor(id))
                            } else {
                                // That note is open further back: go back to it rather than open it twice,
                                // since two editors of one note would overwrite each other's ink.
                                while (trail.stops.size > earlier) trail.stops.removeAt(trail.stops.lastIndex)
                                while (navController.currentBackStackEntry?.arguments?.getString("noteId") != id &&
                                    navController.previousBackStackEntry != null
                                ) {
                                    navController.popBackStack()
                                }
                            }
                        },
                    )
                }
                else -> com.gerko.fourenote.ui.settings.NoteLockedScreen(
                    title = note?.title.orEmpty(),
                    onUnlocked = { noteLock.unlocked(noteId) },
                    onBack = {
                        // A link led to a locked note: leaving it still goes back to where the reader was.
                        if (com.gerko.fourenote.ui.editor.LinkTrail.stops.isNotEmpty()) {
                            com.gerko.fourenote.ui.editor.LinkTrail.stops.removeLastOrNull()?.let { com.gerko.fourenote.ui.editor.LinkTrail.restore = it }
                        }
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}

/** The layer this device writes on in every note, named after the device (see DeviceIdentity). */
private fun deviceLayer(context: android.content.Context) = com.gerko.fourenote.data.ink.InkLayer(
    id = com.gerko.fourenote.data.sync.DeviceIdentity.layerId(com.gerko.fourenote.data.sync.DeviceIdentity.id(context)),
    name = com.gerko.fourenote.data.sync.DeviceIdentity.name(),
)
