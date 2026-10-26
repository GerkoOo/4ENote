package com.gerko.fourenote.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class InkCacheBudgetTest {
    @Test
    fun pagesShareTheBudget() {
        val budget = InkCacheBudget(12_000_000f)
        budget.register()
        assertEquals(12_000_000f, budget.pixelsPerPage(), 0f)
        budget.register()
        budget.register()
        assertEquals(4_000_000f, budget.pixelsPerPage(), 0f)
        budget.unregister()
        assertEquals(6_000_000f, budget.pixelsPerPage(), 0f)
    }

    @Test
    fun onePageNeverGetsMoreThanTheOldCap() {
        val budget = InkCacheBudget.forMemoryClass(2048)
        budget.register()
        assertEquals(InkCacheBudget.MAX_PAGE_PIXELS, budget.pixelsPerPage(), 0f)
    }

    @Test
    fun aQuarterOfTheMemoryClass() {
        // 256 MB: 64 MB of pictures, 16 million pixels for all pages together.
        val budget = InkCacheBudget.forMemoryClass(256)
        budget.register()
        budget.register()
        assertEquals(8_388_608f, budget.pixelsPerPage(), 1f)
    }

    @Test
    fun unregisteringMoreThanRegisteredIsHarmless() {
        val budget = InkCacheBudget(1_000_000f)
        budget.unregister()
        budget.register()
        assertEquals(1_000_000f, budget.pixelsPerPage(), 0f)
    }

    @Test
    fun resolutionSnapsDownToStepsOfRootTwo() {
        assertEquals(1f, snappedResolution(0.5f), 0f)
        assertEquals(1f, snappedResolution(1.3f), 1e-4f)
        assertEquals(1.4142f, snappedResolution(1.5f), 1e-3f)
        assertEquals(2f, snappedResolution(2f), 1e-3f)
        assertEquals(2f, snappedResolution(2.7f), 1e-3f)
        assertEquals(2.8284f, snappedResolution(2.9f), 1e-3f)
        // Small zoom changes within a step keep the same size: no new picture.
        assertEquals(snappedResolution(2.05f), snappedResolution(2.6f), 0f)
    }
}
