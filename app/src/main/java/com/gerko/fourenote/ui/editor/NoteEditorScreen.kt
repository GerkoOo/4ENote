package com.gerko.fourenote.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.brush.Brush
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gerko.fourenote.data.NoteRepository

/** Preset ink colors (ARGB), picked for contrast against the white canvas background. */
private val InkColors = listOf(
    0xFF1A1A1A.toInt(), // near-black
    0xFFD32F2F.toInt(), // red
    0xFF1976D2.toInt(), // blue
    0xFF2E7D32.toInt(), // green
)

private const val DEFAULT_BRUSH_SIZE = 6f
private const val DEFAULT_BRUSH_EPSILON = 0.1f

/**
 * The handwriting/drawing editor for a single note. Loads [noteId] via [repository], shows a
 * title field, and renders a stylus/finger-friendly ink canvas backed by androidx.ink:
 * [InProgressStrokes] handles low-latency wet-ink input and prediction while it's being drawn,
 * and finished strokes are re-drawn dry via [CanvasStrokeRenderer] from then on. Strokes and
 * title are auto-saved through [NoteEditorViewModel]; see that class for the save strategy.
 */
@Composable
fun NoteEditorScreen(
    noteId: String,
    repository: NoteRepository,
    onNavigateBack: () -> Unit,
) {
    val viewModel: NoteEditorViewModel = viewModel(
        factory = NoteEditorViewModel.Factory(repository, noteId),
    )

    fun saveAndGoBack() {
        viewModel.saveNow()
        onNavigateBack()
    }

    BackHandler(onBack = ::saveAndGoBack)

    var selectedColor by rememberSaveable { mutableStateOf(InkColors.first()) }
    val currentBrush = remember(selectedColor) {
        Brush.createWithColorIntArgb(
            family = InkSerialization.brushFamily,
            colorIntArgb = selectedColor,
            size = DEFAULT_BRUSH_SIZE,
            epsilon = DEFAULT_BRUSH_EPSILON,
        )
    }
    val canvasStrokeRenderer = remember { CanvasStrokeRenderer.create() }
    val identityTransform = remember { android.graphics.Matrix() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = viewModel.title,
                        onValueChange = viewModel::onTitleChanged,
                        placeholder = { Text("Untitled") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::saveAndGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InkColors.forEach { argb ->
                            val color = Color(argb)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (argb == selectedColor) 3.dp else 1.dp,
                                        color = if (argb == selectedColor) Color.Gray else Color.LightGray,
                                        shape = CircleShape,
                                    )
                                    .clickable { selectedColor = argb },
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = viewModel::undoLastStroke) { Text("Undo") }
                        TextButton(onClick = viewModel::clearCanvas) { Text("Clear") }
                    }
                }
            }
        },
    ) { padding ->
        if (!viewModel.isLoaded) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val strokes = viewModel.strokes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White),
        ) {
            // Dry layer: finished strokes, redrawn whenever the list changes.
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    for (stroke in strokes) {
                        canvasStrokeRenderer.draw(canvas.nativeCanvas, stroke, identityTransform)
                    }
                }
            }
            // Wet layer: low-latency in-progress stroke rendering + input prediction.
            InProgressStrokes(
                defaultBrush = currentBrush,
                onStrokesFinished = { finished -> viewModel.onStrokesFinished(finished) },
            )
        }
    }
}
