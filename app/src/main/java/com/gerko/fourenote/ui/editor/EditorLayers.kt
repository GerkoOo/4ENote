package com.gerko.fourenote.ui.editor

import com.gerko.fourenote.data.ink.InkLayer
import com.gerko.fourenote.data.ink.InkStroke
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

// Part of NoteEditorViewModel: Layers: choosing, adding, hiding, deleting, copying and merging them.
// Extension functions, so callers keep writing viewModel.x(); the state stays in the class.

fun NoteEditorViewModel.selectLayer(layerId: String) {
    activeLayerId = layerId
    clearSelection()
    val index = layers.indexOfFirst { it.id == layerId }
    if (index >= 0 && !layers[index].visible) setLayerVisible(layerId, true)
}

fun NoteEditorViewModel.addLayer() {
    recordUndo()
    val layer = InkLayer(name = "Layer ${layers.size + 1}")
    layers.add(layer)
    activeLayerId = layer.id
    clearSelection()
    persist()
}

fun NoteEditorViewModel.setLayerVisible(layerId: String, visible: Boolean) {
    val index = layers.indexOfFirst { it.id == layerId }
    if (index < 0) return
    recordUndo()
    layers[index] = layers[index].copy(visible = visible)
    if (!visible && selectionPageId != null) clearSelection()
    persist()
}

fun NoteEditorViewModel.deleteLayer(layerId: String) = deleteLayers(setOf(layerId))

fun NoteEditorViewModel.deleteLayers(layerIds: Set<String>) {
    if (layerIds.isEmpty()) return
    if (layers.all { it.id in layerIds }) {
        message = "A note needs at least one layer"
        return
    }
    recordUndo()
    layers.removeAll { it.id in layerIds }
    for (page in pages) page.strokes.removeAll { it.layerId in layerIds }
    if (activeLayerId in layerIds) activeLayerId = layers.last().id
    clearSelection()
    persist()
}

fun NoteEditorViewModel.duplicateLayers(layerIds: Set<String>) {
    val originals = layers.filter { it.id in layerIds }
    if (originals.isEmpty()) return
    recordUndo()
    for (original in originals) {
        val copy = InkLayer(name = "${original.name} copy", visible = original.visible)
        layers.add(layers.indexOfFirst { it.id == original.id } + 1, copy)
        for (page in pages) {
            page.strokes.addAll(
                page.strokes.filter { it.layerId == original.id }.map {
                    InkStroke(it.color, it.width, it.highlighter, it.points, it.widths, copy.id, nibAngle = it.nibAngle, squareEnds = it.squareEnds)
                },
            )
        }
    }
    clearSelection()
    persist()
}

fun NoteEditorViewModel.mergeLayers(layerIds: Set<String>) {
    val chosen = layers.filter { it.id in layerIds }
    if (chosen.size < 2) return
    val target = chosen.first()
    val merged = chosen.drop(1).mapTo(HashSet()) { it.id }
    recordUndo()
    for (page in pages) {
        for (i in page.strokes.indices) {
            val stroke = page.strokes[i]
            if (stroke.layerId in merged) {
                page.strokes[i] = InkStroke(
                    stroke.color, stroke.width, stroke.highlighter, stroke.points, stroke.widths, target.id, stroke.id, stroke.nibAngle, stroke.squareEnds,
                )
            }
        }
    }
    layers.removeAll { it.id in merged }
    if (activeLayerId in merged) activeLayerId = target.id
    clearSelection()
    persist()
}
