package com.gerko.fourenote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderFileTest {
    @Test
    fun survivesTheRoundTrip() {
        val folders = listOf(Folder("a", "School", null, 10), Folder("b", "Maths", "a", 20, deleted = true))
        assertEquals(folders.sortedBy { it.id }, FolderFile.decode(FolderFile.encode(folders)))
    }

    @Test
    fun mergeKeepsBothSidesAndTheNewerChange() {
        val here = listOf(Folder("a", "School", null, 10), Folder("b", "Maths", "a", 30))
        val there = listOf(Folder("a", "Uni", null, 20), Folder("c", "Art", null, 5))
        val merged = FolderFile.merge(here, there).associateBy { it.id }
        assertEquals(setOf("a", "b", "c"), merged.keys)
        assertEquals("Uni", merged.getValue("a").name) // renamed later over there
        assertEquals("Maths", merged.getValue("b").name)
    }

    @Test
    fun aDeletionMadeLaterWins() {
        val merged = FolderFile.merge(listOf(Folder("a", "School", null, 10)), listOf(Folder("a", "School", null, 11, deleted = true)))
        assertTrue(merged.single().deleted)
    }

    @Test
    fun damagedTextGivesNoFolders() {
        assertTrue(FolderFile.decode("not json").isEmpty())
    }
}
