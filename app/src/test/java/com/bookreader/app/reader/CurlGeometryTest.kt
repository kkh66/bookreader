package com.bookreader.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CurlGeometryTest {

    private val width = 400.0
    private val height = 700.0
    private val geometry = CurlGeometry(width, height)

    @Test
    fun creaseIsThePerpendicularBisectorOfCornerAndTouch() {
        geometry.layout(250.0, 600.0, fromBottom = true)
        with(geometry) {
            assertEquals(0.0, dot(control1, middle, corner, touch), 1e-6)
            assertEquals(0.0, dot(control2, middle, corner, touch), 1e-6)
            assertEquals(height, control1.y, 0.0)
            assertEquals(width, control2.x, 0.0)
        }
    }

    @Test
    fun paperStaysAttachedToTheLeftEdgeWhilePulledOverThePage() {
        // Pulled this far, the curl would start left of the page; the touch point is brought back toward the corner
        geometry.layout(40.0, 250.0, fromBottom = true)
        assertEquals(0.0, geometry.start1.x, 1e-6)
        assertTrue(geometry.touch.x > 40.0)
        // Still on the line from the corner through where the finger is
        val slope = (height - 250.0) / (width - 40.0)
        assertEquals(slope, (height - geometry.touch.y) / (width - geometry.touch.x), 1e-9)
    }

    @Test
    fun paperLeavesThePagePastItsLeftEdge() {
        geometry.layout(-150.0, 650.0, fromBottom = true)
        assertEquals(-150.0, geometry.touch.x, 0.0)
        assertTrue(geometry.start1.x < 0)
    }

    @Test
    fun topCornerMirrorsTheBottomOne() {
        val bottom = CurlGeometry(width, height).apply { layout(250.0, 600.0, fromBottom = true) }
        val top = CurlGeometry(width, height).apply { layout(250.0, 100.0, fromBottom = false) }
        listOf(
            bottom.start1 to top.start1, bottom.end1 to top.end1, bottom.vertex1 to top.vertex1,
            bottom.start2 to top.start2, bottom.end2 to top.end2, bottom.vertex2 to top.vertex2,
        ).forEach { (b, t) ->
            assertEquals(b.x, t.x, 1e-6)
            assertEquals(height - b.y, t.y, 1e-6)
        }
    }

    @Test
    fun touchOnTheCornersEdgeTurnsThePageStraightAcross() {
        geometry.layout(150.0, height, fromBottom = true)
        with(geometry) {
            // Kept just off the bottom edge, so the crease and the roll come out upright
            assertTrue(touch.y < height)
            assertTrue(abs(vertex1.x - vertex2.x) / abs(vertex1.y - vertex2.y) < 0.01)
            assertEquals(275.0, control1.x, 0.5)
        }
    }

    @Test
    fun edgesMeetAtTheTouchPointAtARightAngle() {
        // The lifted corner is the page's own corner folded over
        geometry.layout(250.0, 600.0, fromBottom = true)
        with(geometry) {
            assertEquals(0.0, dot(end1, touch, end2, touch), 1e-6)
        }
    }

    /** Dot product of a − b and c − d */
    private fun dot(a: CurlGeometry.Point, b: CurlGeometry.Point, c: CurlGeometry.Point, d: CurlGeometry.Point) =
        (a.x - b.x) * (c.x - d.x) + (a.y - b.y) * (c.y - d.y)
}
