package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkLayer
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStroke

/**
 * Three-way merge of a note edited on two devices since their last common
 * sync ([base]). Pages and strokes are identified by id, and strokes are
 * immutable, so both merge as sets:
 *  - anything added on either side is kept;
 *  - anything that was in [base] but is gone on either side is removed
 *    (a stroke erased, moved or recolored there arrives again as an add).
 * Title and paper are single values: a side that changed them wins, and if
 * both changed them the more recent edit wins.
 */
object NoteMerger {
    fun merge(base: Note?, local: Note, remote: Note): Note {
        val baseDoc = base?.let { InkSerializer.decode(it.inkData) }
        val localDoc = InkSerializer.decode(local.inkData)
        val remoteDoc = InkSerializer.decode(remote.inkData)

        val basePages = baseDoc?.pages.orEmpty().associateBy { it.id }
        val localPages = localDoc.pages.associateBy { it.id }
        val remotePages = remoteDoc.pages.associateBy { it.id }

        val pages = mergeById(baseDoc?.pages.orEmpty(), localDoc.pages, remoteDoc.pages) { it.id }
            .map { page ->
                val inBoth = localPages[page.id] != null && remotePages[page.id] != null
                if (!inBoth) {
                    page
                } else {
                    val basePage = basePages[page.id]
                    val localPage = localPages.getValue(page.id)
                    val remotePage = remotePages.getValue(page.id)
                    val localIsNewer = local.updatedAt >= remote.updatedAt
                    page.copy(
                        strokes = mergeById(basePage?.strokes.orEmpty(), localPage.strokes, remotePage.strokes, InkStroke::id),
                        // Pictures can be moved/resized in place, so for one edited on both sides pick per picture.
                        images = mergeById(basePage?.images.orEmpty(), localPage.images, remotePage.images) { it.id }
                            .map { image ->
                                pick(
                                    basePage?.images?.firstOrNull { it.id == image.id },
                                    localPage.images.firstOrNull { it.id == image.id } ?: image,
                                    remotePage.images.firstOrNull { it.id == image.id } ?: image,
                                    localIsNewer,
                                )
                            },
                        placement = pick(basePage?.placement, localPage.placement, remotePage.placement, localIsNewer),
                    )
                }
            }

        // Layers merge as a set too; a layer hidden/renamed on one side keeps that side's version.
        val layers = mergeById(baseDoc?.layers.orEmpty(), localDoc.layers, remoteDoc.layers) { it.id }
            .map { layer ->
                val base = baseDoc?.layers?.firstOrNull { it.id == layer.id }
                val l = localDoc.layers.firstOrNull { it.id == layer.id } ?: layer
                val r = remoteDoc.layers.firstOrNull { it.id == layer.id } ?: layer
                pick(base, l, r, local.updatedAt >= remote.updatedAt)
            }

        val localIsNewer = local.updatedAt >= remote.updatedAt
        val paper = pick(baseDoc?.paper, localDoc.paper, remoteDoc.paper, localIsNewer)
        val title = pick(base?.title, local.title, remote.title, localIsNewer)

        return local.copy(
            title = title,
            inkData = InkSerializer.encode(
                InkDocument(pages.ifEmpty { listOf(InkPage()) }, paper, layers.ifEmpty { listOf(InkLayer.BASE) }),
            ),
            createdAt = minOf(local.createdAt, remote.createdAt),
            updatedAt = maxOf(local.updatedAt, remote.updatedAt),
        )
    }

    /** Local order first, then items only the other side added, in its order. */
    private fun <T> mergeById(base: List<T>, local: List<T>, remote: List<T>, id: (T) -> String): List<T> {
        val baseIds = base.mapTo(HashSet(), id)
        val localIds = local.mapTo(HashSet(), id)
        val remoteIds = remote.mapTo(HashSet(), id)
        return local.filter { id(it) !in baseIds || id(it) in remoteIds } +
            remote.filter { id(it) !in localIds && id(it) !in baseIds }
    }

    private fun <T> pick(base: T?, local: T, remote: T, localIsNewer: Boolean): T = when {
        local == remote -> local
        local == base -> remote
        remote == base -> local
        localIsNewer -> local
        else -> remote
    }
}
