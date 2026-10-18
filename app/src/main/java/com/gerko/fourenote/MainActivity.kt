package com.gerko.fourenote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gerko.fourenote.navigation.FourENoteNavGraph
import com.gerko.fourenote.sync.DriveSyncManager
import com.gerko.fourenote.ui.theme.FourENoteTheme

class MainActivity : ComponentActivity() {
    // Constructed here (not lazily) because DriveSyncManager registers an ActivityResultLauncher,
    // which must happen before this Activity reaches the STARTED lifecycle state.
    private lateinit var driveSyncManager: DriveSyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as FourENoteApp).repository
        driveSyncManager = DriveSyncManager(this, repository)

        setContent {
            FourENoteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FourENoteNavGraph(repository = repository, driveSyncManager = driveSyncManager)
                }
            }
        }
    }
}
