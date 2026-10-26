package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkLayer
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.StickyNote
import com.gerko.fourenote.data.ink.withoutDeadLinks

/**
 * Three-way merge of a note edited on two devices since their last common
 * sync ([base]). Pages and strokes are identified by id, and strokes are
 * immutable, so both merge as sets:
 *  - anything added on either side is kept;
 *  - anything that was in [base] but is gone on either side is removed
 *    (a stroke erased, moved or recolored there arrives again as an add).
 * Title and paper are single values: a side that changed them wins, and if
 * both changed them the later edit by edit counter ([Note.rev]) wins.
 */
object NoteMerger {
    /**
     * [remoteFirst] breaks ties in ordering (see [mergeById]): devices merging each other's files
     * pass opposite values, so they agree.
     */
    fun merge(base: Note?, local: Note, remote: Note, remoteFirst: Boolean = false): Note {
        val baseDoc = base?.let { InkSerializer.decode(it.inkData) }
        val localDoc = InkSerializer.decode(local.inkData)
        val remoteDoc = InkSerializer.decode(remote.inkData)

        // Of two changes to one value, the later one by edit counter wins (see Note.rev); a tie
        // (made without seeing each other) goes the same way on every device.
        val localIsNewer = local.rev > remote.rev || (local.rev == remote.rev && !remoteFirst)

        val basePages = baseDoc?.pages.orEmpty().associateBy { it.id }
        val localPages = localDoc.pages.associateBy { it.id }
        val remotePages = remoteDoc.pages.associateBy { it.id }

        val pages = mergeById(remoteFirst, baseDoc?.pages.orEmpty(), localDoc.pages, remoteDoc.pages) { it.id }
            .map { page ->
                val inBoth = localPages[page.id] != null && remotePages[page.id] != null
                if (!inBoth) {
                    page
                } else {
                    val basePage = basePages[page.id]
                    val localPage = localPages.getValue(page.id)
                    val remotePage = remotePages.getValue(page.id)
                    page.copy(
                        strokes = oneEditPerStroke(
                            mergeById(remoteFirst, basePage?.strokes.orEmpty(), localPage.strokes, remotePage.strokes, InkStroke::id),
                            basePage?.strokes.orEmpty(), localPage.strokes, remotePage.strokes, localIsNewer,
                        ),
                        // Pictures can be moved/resized in place, so for one edited on both sides pick per picture.
                        images = mergeById(remoteFirst, basePage?.images.orEmpty(), localPage.images, remotePage.images) { it.id }
                            .map { image ->
                                val local = localPage.images.firstOrNull { it.id == image.id } ?: image
                                val remote = remotePage.images.firstOrNull { it.id == image.id } ?: image
                                when {
                                    // A frame filled with a photo on one side always beats the same frame still empty.
                                    local.waitingForPhoto && !remote.waitingForPhoto -> remote
                                    remote.waitingForPhoto && !local.waitingForPhoto -> local
                                    else -> pick(basePage?.images?.firstOrNull { it.id == image.id }, local, remote, localIsNewer)
                                }
                            },
                        // Text boxes are edited in place too: per box, the newer side's version.
                        texts = mergeById(remoteFirst, basePage?.texts.orEmpty(), localPage.texts, remotePage.texts) { it.id }
                            .map { text ->
                                pick(
                                    basePage?.texts?.firstOrNull { it.id == text.id },
                                    localPage.texts.firstOrNull { it.id == text.id } ?: text,
                                    remotePage.texts.firstOrNull { it.id == text.id } ?: text,
                                    localIsNewer,
                                )
                            },
                        placement = pick(basePage?.placement, localPage.placement, remotePage.placement, localIsNewer),
                        // Links are edited in place too: per link, the newer side's version.
                        links = mergeById(remoteFirst, basePage?.links.orEmpty(), localPage.links, remotePage.links) { it.id }
                            .map { link ->
                                pick(
                                    basePage?.links?.firstOrNull { it.id == link.id },
                                    localPage.links.firstOrNull { it.id == link.id } ?: link,
                                    remotePage.links.firstOrNull { it.id == link.id } ?: link,
                                    localIsNewer,
                                )
                            },
                        stickies = mergeById(remoteFirst, basePage?.stickies.orEmpty(), localPage.stickies, remotePage.stickies) { it.id }
                            .map { sticky ->
                                mergeSticky(
                                    basePage?.stickies?.firstOrNull { it.id == sticky.id },
                                    localPage.stickies.firstOrNull { it.id == sticky.id } ?: sticky,
                                    remotePage.stickies.firstOrNull { it.id == sticky.id } ?: sticky,
                                    localIsNewer,
                                    remoteFirst,
                                )
                            },
                    ).withoutDeadLinks()
                }
            }

        // Layers merge as a set too; a layer hidden/renamed on one side keeps that side's version.
        val layers = mergeById(remoteFirst, baseDoc?.layers.orEmpty(), localDoc.layers, remoteDoc.layers) { it.id }
            .map { layer ->
                val base = baseDoc?.layers?.firstOrNull { it.id == layer.id }
                val l = localDoc.layers.firstOrNull { it.id == layer.id } ?: layer
                val r = remoteDoc.layers.firstOrNull { it.id == layer.id } ?: layer
                pick(base, l, r, localIsNewer)
            }

        val paper = pick(baseDoc?.paper, localDoc.paper, remoteDoc.paper, localIsNewer)
        val title = pick(base?.title, local.title, remote.title, localIsNewer)

        return local.copy(
            title = title,
            // Moved to a folder on one side (or both: the more recent move).
            folderId = pick(base?.folderId, local.folderId, remote.folderId, localIsNewer),
            // Locking is a single value like the title: the side that changed it wins.
            locked = pick(base?.locked, local.locked, remote.locked, localIsNewer),
            inkData = InkSerializer.encode(
                InkDocument(pages.ifEmpty { listOf(InkPage()) }, paper, layers.ifEmpty { listOf(InkLayer.BASE) }),
            ),
            createdAt = minOf(local.createdAt, remote.createdAt),
            updatedAt = maxOf(local.updatedAt, remote.updatedAt),
            rev = maxOf(local.rev, remote.rev),
        )
    }

    /**
     * A sticky present on both sides: its frame (position, size, colour, folded) is a single value
     * like the title, while its ink and text merge as sets, so writing in the same sticky on two
     * devices keeps both.
     */
    private fun mergeSticky(base: StickyNote?, local: StickyNote, remote: StickyNote, localIsNewer: Boolean, remoteFirst: Boolean): StickyNote {
        val frame = pick(base?.withoutContent(), local.withoutContent(), remote.withoutContent(), localIsNewer)
        return frame.copy(
            strokes = oneEditPerStroke(
                mergeById(remoteFirst, base?.strokes.orEmpty(), local.strokes, remote.strokes, InkStroke::id),
                base?.strokes.orEmpty(), local.strokes, remote.strokes, localIsNewer,
            ),
            texts = mergeById(remoteFirst, base?.texts.orEmpty(), local.texts, remote.texts) { it.id }
                .map { text ->
                    pick(
                        base?.texts?.firstOrNull { it.id == text.id },
                        local.texts.firstOrNull { it.id == text.id } ?: text,
                        remote.texts.firstOrNull { it.id == text.id } ?: text,
                        localIsNewer,
                    )
                },
        )
    }

    private fun StickyNote.withoutContent() = copy(strokes = emptyList(), texts = emptyList())

    /**
     * A stroke edited on both sides (moved here, recoloured there) is replaced by new strokes on
     * each, and [merged] holds both: it would show twice. Like any single value, the newer side's
     * edit is kept and the other side's replacement strokes go. A copy (the stroke is still there
     * on its side) is not a replacement and always stays.
     */
    private fun oneEditPerStroke(
        merged: List<InkStroke>,
        base: List<InkStroke>,
        local: List<InkStroke>,
        remote: List<InkStroke>,
        localIsNewer: Boolean,
    ): List<InkStroke> {
        val baseIds = base.mapTo(HashSet()) { it.id }
        fun replacements(side: List<InkStroke>, other: List<InkStroke>): Map<String, List<InkStroke>> {
            val sideIds = side.mapTo(HashSet()) { it.id }
            val otherIds = other.mapTo(HashSet()) { it.id }
            return side.filter { s ->
                val original = s.replaces
                original != null && original in baseIds && original !in sideIds && s.id !in baseIds && s.id !in otherIds
            }.groupBy { it.replaces!! }
        }
        val fromLocal = replacements(local, remote)
        val fromRemote = replacements(remote, local)
        val both = fromLocal.keys intersect fromRemote.keys
        if (both.isEmpty()) return merged
        val losing = if (localIsNewer) fromRemote else fromLocal
        val drop = both.flatMap { losing.getValue(it) }.mapTo(HashSet()) { it.id }
        return merged.filter { it.id !in drop }
    }

    /**
     * The items kept on both sides, in the order of the side that rearranged them (or, if neither
     * or both did, of the side [remoteFirst] picks), with the other side's additions placed after
     * the item they followed there. Every device merging the same files so ends up with the same
     * order.
     */
    private fun <T> mergeById(remoteFirst: Boolean, base: List<T>, local: List<T>, remote: List<T>, id: (T) -> String): List<T> {
        val baseIds = base.mapTo(HashSet(), id)
        val localIds = local.mapTo(HashSet(), id)
        val remoteIds = remote.mapTo(HashSet(), id)
        val keptLocal = local.filter { id(it) !in baseIds || id(it) in remoteIds }
        val keptRemote = remote.filter { id(it) !in baseIds || id(it) in localIds }
        // Whether a side changed the order of the items all three share.
        val shared = baseIds.filterTo(HashSet()) { it in localIds && it in remoteIds }
        val baseOrder = base.map(id).filter { it in shared }
        val localMoved = local.map(id).filter { it in shared } != baseOrder
        val remoteMoved = remote.map(id).filter { it in shared } != baseOrder
        val remoteLeads = if (localMoved != remoteMoved) remoteMoved else remoteFirst
        val (lead, other) = if (remoteLeads) keptRemote to keptLocal else keptLocal to keptRemote

        // One pass (a page can hold tens of thousands of strokes): each of the other side's own
        // items is filed under the nearest item before it that both have (or the start), and goes
        // in after the leading side's own items there.
        val leadIds = lead.mapTo(HashSet(), id)
        val otherIds = other.mapTo(HashSet(), id)
        val head = mutableListOf<T>()
        val following = HashMap<String, MutableList<T>>()
        var anchor: String? = null
        for (item in other) {
            val itemId = id(item)
            if (itemId in leadIds) anchor = itemId
            else (anchor?.let { following.getOrPut(it) { mutableListOf() } } ?: head) += item
        }
        if (head.isEmpty() && following.isEmpty()) return lead
        val result = ArrayList<T>(lead.size + other.size)
        var pending: List<T> = head
        for (item in lead) {
            val itemId = id(item)
            if (itemId in otherIds) {
                result += pending
                pending = following[itemId].orEmpty()
            }
            result += item
        }
        result += pending
        return result
    }

    /**
     * Everything in either note; where both hold the same item, [first]'s version. The base for
     * merging a file from a device that had merged several files this device has too.
     */
    fun union(first: Note, second: Note): Note =
        // With no base, every single value goes to the newer side: [first] is made the newer.
        merge(null, first.copy(rev = Long.MAX_VALUE), second).copy(rev = maxOf(first.rev, second.rev))

    private fun <T> pick(base: T?, local: T, remote: T, localIsNewer: Boolean): T = when {
        local == remote -> local
        local == base -> remote
        remote == base -> local
        localIsNewer -> local
        else -> remote
    }
}
