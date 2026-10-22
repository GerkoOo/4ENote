package com.gerko.fourenote.data.scan

/**
 * Finds the four corners of a sheet of paper in a photo, so the scanner can straighten it.
 * Works on a small greyscale copy of the photo: the paper is told apart from what it lies on
 * by brightness (Otsu's threshold), the patch of paper-coloured pixels around the middle of
 * the photo is taken as the sheet, and its corners are the points furthest out diagonally.
 */
object PageFinder {
    /** A quadrilateral in the photo's coordinates: top-left, top-right, bottom-right, bottom-left. */
    data class Corners(val points: FloatArray) {
        init {
            require(points.size == 8)
        }

        fun scaled(factor: Float) = Corners(FloatArray(8) { points[it] * factor })

        override fun equals(other: Any?) = other is Corners && points.contentEquals(other.points)
        override fun hashCode() = points.contentHashCode()
    }

    /** The paper must cover at least this share of the photo, and not all of it. */
    private const val MIN_AREA = 0.12f
    private const val MAX_AREA = 0.97f

    /**
     * The sheet's corners in [luma] (one byte of brightness per pixel, row by row, [width] x
     * [height]), or null when no clear sheet stands out from its background.
     */
    fun find(luma: IntArray, width: Int, height: Int): Corners? {
        if (width < 8 || height < 8) return null
        val smooth = blur(luma, width, height)
        val threshold = otsu(smooth)
        // Paper is usually the brighter side; whichever side fills the middle of the photo is the paper.
        var bright = 0
        var total = 0
        for (y in height * 2 / 5 until height * 3 / 5) for (x in width * 2 / 5 until width * 3 / 5) {
            total++
            if (smooth[y * width + x] > threshold) bright++
        }
        val paperIsBright = bright * 2 >= total
        fun isPaper(i: Int) = (smooth[i] > threshold) == paperIsBright

        // Grow the sheet from the middle.
        val inSheet = BooleanArray(width * height)
        val queue = IntArray(width * height)
        var head = 0
        var tail = 0
        val start = (height / 2) * width + width / 2
        if (!isPaper(start)) return null
        inSheet[start] = true
        queue[tail++] = start
        var area = 0
        while (head < tail) {
            val i = queue[head++]
            area++
            val x = i % width
            val y = i / width
            fun visit(j: Int) {
                if (!inSheet[j] && isPaper(j)) {
                    inSheet[j] = true
                    queue[tail++] = j
                }
            }
            if (x > 0) visit(i - 1)
            if (x < width - 1) visit(i + 1)
            if (y > 0) visit(i - width)
            if (y < height - 1) visit(i + width)
        }
        val share = area.toFloat() / (width * height)
        if (share < MIN_AREA || share > MAX_AREA) return null

        // Corners: the sheet's pixels furthest towards each corner of the photo.
        var tl = start; var tr = start; var br = start; var bl = start
        fun sum(i: Int) = i % width + i / width
        fun diff(i: Int) = i % width - i / width
        for (i in inSheet.indices) {
            if (!inSheet[i]) continue
            if (sum(i) < sum(tl)) tl = i
            if (sum(i) > sum(br)) br = i
            if (diff(i) > diff(tr)) tr = i
            if (diff(i) < diff(bl)) bl = i
        }
        val corners = intArrayOf(tl, tr, br, bl)
        return Corners(FloatArray(8) { k -> val i = corners[k / 2]; (if (k % 2 == 0) i % width else i / width).toFloat() })
    }

    // A 5 x 5 box blur, so paper texture and writing don't break the sheet into pieces.
    private fun blur(luma: IntArray, width: Int, height: Int): IntArray {
        val horizontal = IntArray(luma.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0
            var count = 0
            for (dx in -2..2) {
                val xx = x + dx
                if (xx in 0 until width) { sum += luma[y * width + xx]; count++ }
            }
            horizontal[y * width + x] = sum / count
        }
        val out = IntArray(luma.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0
            var count = 0
            for (dy in -2..2) {
                val yy = y + dy
                if (yy in 0 until height) { sum += horizontal[yy * width + x]; count++ }
            }
            out[y * width + x] = sum / count
        }
        return out
    }

    /** Otsu's method: the brightness that best splits the pixels into two groups. */
    fun otsu(luma: IntArray): Int {
        val histogram = IntArray(256)
        for (v in luma) histogram[v.coerceIn(0, 255)]++
        val total = luma.size.toDouble()
        var sumAll = 0.0
        for (v in 0..255) sumAll += v * histogram[v].toDouble()
        var sumBelow = 0.0
        var countBelow = 0.0
        var best = 0.0
        var threshold = 127
        for (v in 0..255) {
            countBelow += histogram[v]
            if (countBelow == 0.0) continue
            val countAbove = total - countBelow
            if (countAbove == 0.0) break
            sumBelow += v * histogram[v].toDouble()
            val meanBelow = sumBelow / countBelow
            val meanAbove = (sumAll - sumBelow) / countAbove
            val between = countBelow * countAbove * (meanBelow - meanAbove) * (meanBelow - meanAbove)
            if (between > best) {
                best = between
                threshold = v
            }
        }
        return threshold
    }
}
