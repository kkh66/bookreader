package com.bookreader.app.reader

import kotlin.math.abs

/**
 * Where the parts of a curling page lie, for a [width] × [height] page turned from a corner of its right edge
 * (x to the right, y down); a right-to-left book uses it mirrored
 *
 * The corner is pulled to the touch point and the paper folds along the crease, the perpendicular bisector of the two,
 * which meets the corner's edge at [control1] and the right edge at [control2]. The paper rolls over in two quadratic
 * curves controlled by those points: from [start1] on the corner's edge through [vertex1] to [end1], and from [start2]
 * on the right edge through [vertex2] to [end2]. The lifted part runs from the curves to the corner; its back shows
 * between the line through the vertices and the touch point. This is the paper curl reader apps commonly draw.
 * Worked in doubles: when the corner is pulled almost straight across, the right edge's points lie far off the page.
 */
internal class CurlGeometry(val width: Double, val height: Double) {

    /** A point updated in place, so following a drag doesn't allocate */
    class Point(var x: Double = 0.0, var y: Double = 0.0) {
        fun set(x: Double, y: Double) {
            this.x = x
            this.y = y
        }
    }

    val corner = Point()
    val touch = Point()
    val middle = Point()
    val control1 = Point()
    val control2 = Point()
    val start1 = Point()
    val start2 = Point()
    val vertex1 = Point()
    val vertex2 = Point()
    val end1 = Point()
    val end2 = Point()

    /**
     * Lays the curl out for the bottom ([fromBottom]) or top right corner pulled to ([x], [y])
     * The touch point is kept off the corner's two edges, which the construction divides by. While it's over the page,
     * the paper stays attached along the left edge, as a book's page is to its spine: pulled further, it's brought back
     * along the line to the corner. Past the left edge (the end of a turn) the paper leaves the page.
     */
    fun layout(x: Double, y: Double, fromBottom: Boolean) {
        corner.set(width, if (fromBottom) height else 0.0)
        touch.set(
            minOf(x, width - EDGE_GAP),
            if (fromBottom) minOf(y, height - EDGE_GAP) else maxOf(y, EDGE_GAP),
        )
        crease()
        if (touch.x > 0 && start1.x < 0) {
            val pulled = corner.x - touch.x
            val kept = pulled * width / (width - start1.x)
            touch.set(corner.x - kept, corner.y + (touch.y - corner.y) * kept / pulled)
            crease()
        }
        intersection(touch, control1, start1, start2, end1)
        intersection(touch, control2, start1, start2, end2)
        // Midpoints of the two curves
        vertex1.set((start1.x + 2 * control1.x + end1.x) / 4, (start1.y + 2 * control1.y + end1.y) / 4)
        vertex2.set((start2.x + 2 * control2.x + end2.x) / 4, (start2.y + 2 * control2.y + end2.y) / 4)
    }

    /** The crease's ends, and the curves' starts half as far again from the corner */
    private fun crease() {
        middle.set((touch.x + corner.x) / 2, (touch.y + corner.y) / 2)
        val dx = corner.x - middle.x
        val dy = corner.y - middle.y
        control1.set(middle.x - dy * dy / dx, corner.y)
        control2.set(corner.x, middle.y - dx * dx / dy)
        start1.set(control1.x - (corner.x - control1.x) / 2, corner.y)
        start2.set(corner.x, control2.y - (corner.y - control2.y) / 2)
    }

    /** Sets [out] to where the line through [p1] and [p2] meets the line through [p3] and [p4] */
    private fun intersection(p1: Point, p2: Point, p3: Point, p4: Point, out: Point) {
        val d = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x)
        if (abs(d) < 1e-9) {
            // Parallel only when nothing is lifted
            out.set(p3.x, p3.y)
            return
        }
        val a = p1.x * p2.y - p1.y * p2.x
        val b = p3.x * p4.y - p3.y * p4.x
        out.set((a * (p3.x - p4.x) - (p1.x - p2.x) * b) / d, (a * (p3.y - p4.y) - (p1.y - p2.y) * b) / d)
    }

    private companion object {
        /** Closest the touch point gets to the corner's edges, in px */
        const val EDGE_GAP = 0.5
    }
}
