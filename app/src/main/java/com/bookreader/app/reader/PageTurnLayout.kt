package com.bookreader.app.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.bookreader.app.data.PageAnimation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Holds the book's navigator and animates its page turns, whether by drag, tap or volume key:
 * [PageAnimation.Curl] curls the page over like paper and [PageAnimation.Cover] slides it off the page beneath,
 * while [PageAnimation.Slide] leaves turns to the navigator's own animation and [PageAnimation.None] turns at once
 *
 * Readium draws the pages itself (web views, PDF and image views), so an effect plays on a screenshot: the page is captured,
 * the navigator turns underneath without animation, and the screenshot is curled or slid off the real page below.
 * The screenshot needs Android 8; before that, or while the menu covers the page, turns slide.
 */
@SuppressLint("ViewConstructor")
internal class PageTurnLayout(context: Context, private val window: Window) : FrameLayout(context) {

    var animation = PageAnimation.Curl

    /** Color of the paper's back as it curls over */
    var pageColor = Color.WHITE

    /** Whether the next page is to the left, as in manga and right-to-left books */
    var rightToLeft: () -> Boolean = { false }

    /** Off while the menu covers the page, which a screenshot would carry along; turns slide meanwhile */
    var effectsEnabled: () -> Boolean = { true }

    /** Off while the page needs drags itself: extending a text selection, panning a zoomed page */
    var dragTurnsEnabled: () -> Boolean = { true }

    /** Turns the navigator's page, returning false when it can't; some navigators can't tell, see [pageChanged] */
    var navigate: (forward: Boolean, animated: Boolean) -> Boolean = { _, _ -> false }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val density = resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The turn on screen: following a drag, playing, or settling back after the page was let go */
    private var current: Turn? = null
    private var screenshot: Bitmap? = null

    // Touch that may become a page-turning drag
    private var watching = false
    private var downX = 0f
    private var downY = 0f
    private var velocity: VelocityTracker? = null

    // Drawing, reused from frame to frame
    private var geometry: CurlGeometry? = null
    private val lifted = Path()
    private val backOutline = Path()
    private val strip = Path()
    private val mirror = Matrix()
    private val reflection = Matrix()
    private val reflect = Matrix()
    private val placement = Matrix()
    private val gradientMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val throughPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = SHOW_THROUGH_ALPHA }
    private val pagePaint = Paint()

    // One shader per shade drawn in a frame: a shader's matrix is read when the frame is drawn, not when it's recorded
    private val rollShade = shadePaint(ROLL_SHADE)
    private val backShade = shadePaint(BACK_SHADE)
    private val edgeShade1 = shadePaint(EDGE_SHADE)
    private val edgeShade2 = shadePaint(EDGE_SHADE)
    private val coverShade = shadePaint(EDGE_SHADE)

    private val navigatorView: View? get() = getChildAt(0)

    /** Turns a page for a tap or key */
    fun turn(forward: Boolean) {
        current?.let {
            if (it.settling || it.fingerDown || !it.ready) return
            // One still playing ends at once, so quick taps keep up
            it.skipToEnd()
            if (current != null) return
        }
        val effect = effect()
        if (effect == null) {
            navigate(forward, animation != PageAnimation.None)
            return
        }
        val turn = Turn(forward, rightToLeft(), effect, fromBottom = true, level = false)
        current = turn
        turn.begin(onFailed = { navigate(forward, true) }) { turn.play() }
    }

    /** The navigator reports a new location: a turn waiting to learn whether its page changed now knows */
    fun pageChanged() {
        current?.takeIf { it.ready }?.onLanding(true)
    }

    /** The effect a turn plays now, or null when the navigator animates it (Slide), turns at once (None), or can't be captured */
    private fun effect(): PageAnimation? = animation.takeIf {
        (it == PageAnimation.Curl || it == PageAnimation.Cover) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            width > 0 && height > 0 && effectsEnabled()
    }

    /** Drags are taken over for an effect, or with None to turn at once; Slide leaves them to the navigator */
    private fun dragEffect(): PageAnimation? = if (animation == PageAnimation.None) PageAnimation.None else effect()

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // A turn still playing ends at once so the touch acts on the page that's coming;
                // one settling back keeps the page to itself for the few frames that takes.
                // A drag whose finger never came up (its gesture ended elsewhere) is let go first
                current?.let { if (it.fingerDown) it.release(0f) }
                current?.let { if (it.ready && !it.settling) it.skipToEnd() }
                if (current != null) return true
                downX = event.x
                downY = event.y
                watching = dragEffect() != null && dragTurnsEnabled()
                if (watching) velocity = VelocityTracker.obtain().apply { addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> if (watching) {
                velocity?.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                    stopWatching()
                } else if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    watching = false
                    val effect = dragEffect()
                    if (effect != null && dragTurnsEnabled()) {
                        startDrag(effect, event)
                        return true
                    }
                    stopWatching()
                }
            }
            // A second finger is a pinch, not a page turn
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopWatching()
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val turn = current ?: return false
        // Busy settling: the touch is swallowed
        if (!turn.fingerDown) return true
        velocity?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> turn.follow(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                velocity?.computeCurrentVelocity(1000)
                turn.release(velocity?.xVelocity ?: 0f)
                stopWatching()
            }
            MotionEvent.ACTION_CANCEL -> {
                turn.release(0f)
                stopWatching()
            }
        }
        return true
    }

    private fun startDrag(effect: PageAnimation, event: MotionEvent) {
        val rtl = rightToLeft()
        // Dragging toward the start of the line turns forward; a drag from the middle third turns the page straight across
        val forward = (event.x < downX) != rtl
        val level = downY > height / 3f && downY < height * 2 / 3f
        val turn = Turn(forward, rtl, effect, fromBottom = downY > height / 2f, level = level)
        current = turn
        turn.startDrag(downX, downY)
        turn.follow(event.x, event.y)
    }

    private fun stopWatching() {
        watching = false
        velocity?.recycle()
        velocity = null
    }

    override fun dispatchDraw(canvas: Canvas) {
        val turn = current
        val page = screenshot
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            super.dispatchDraw(canvas)
            return
        }
        if (turn == null || !turn.ready || page == null || turn.effect == PageAnimation.None) {
            super.dispatchDraw(canvas)
            return
        }
        val child = navigatorView
        if (turn.effect == PageAnimation.Cover && !turn.forward && child != null) {
            // Turning back with Cover, the page coming back slides in over the screenshot;
            // it's backed with the page color, since a comic or PDF page doesn't fill its view
            canvas.drawBitmap(page, 0f, 0f, null)
            pagePaint.color = pageColor
            canvas.drawRect(child.left + child.translationX, child.top.toFloat(), child.right + child.translationX, child.bottom.toFloat(), pagePaint)
        }
        super.dispatchDraw(canvas)
        val saved = canvas.save()
        // The effects are drawn in the page's own coordinates, running in reading direction
        if (turn.rtl) canvas.scale(-1f, 1f, width / 2f, 0f)
        if (turn.effect == PageAnimation.Curl) drawCurl(canvas, page, turn) else drawCover(canvas, page, turn)
        canvas.restoreToCount(saved)
    }

    /** Cover: forward, the screenshot slides off to reveal the next page; back, the page coming back slides in over it */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun drawCover(canvas: Canvas, page: Bitmap, turn: Turn) {
        if (turn.forward) {
            placement.set(mirror)
            placement.postTranslate(turn.x - width, 0f)
            canvas.drawBitmap(page, placement, bitmapPaint)
        }
        // Shadow beside the moving page's edge, once it's moving
        if (turn.x == turn.startX || turn.x == turn.endX) return
        val reach = COVER_SHADE_DP * density
        aim(coverShade, turn.x, 0f, reach, 0f)
        canvas.drawRect(turn.x, 0f, turn.x + reach, height.toFloat(), coverShade)
    }

    /** Curl: see [CurlGeometry] for the parts of the page */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun drawCurl(canvas: Canvas, page: Bitmap, turn: Turn) {
        // Nothing lifted yet (both directions start from the screenshot as the page was), or all of it gone
        if (turn.x == turn.startX) {
            canvas.drawBitmap(page, mirror, bitmapPaint)
            return
        }
        if (turn.x == turn.endX) return
        val g = geometry?.takeIf { it.width == width.toDouble() && it.height == height.toDouble() }
            ?: CurlGeometry(width.toDouble(), height.toDouble()).also { geometry = it }
        g.layout(turn.x.toDouble(), turn.y.toDouble(), turn.fromBottom)

        lifted.rewind()
        lifted.moveTo(g.start1)
        lifted.quadTo(g.control1, g.end1)
        lifted.lineTo(g.touch)
        lifted.lineTo(g.end2)
        lifted.quadTo(g.control2, g.start2)
        lifted.lineTo(g.corner)
        lifted.close()
        backOutline.rewind()
        backOutline.moveTo(g.vertex2)
        backOutline.lineTo(g.vertex1)
        backOutline.lineTo(g.end1)
        backOutline.lineTo(g.touch)
        backOutline.lineTo(g.end2)
        backOutline.close()

        // Toward the corner, at right angles to the crease
        val toCornerX = g.corner.x - g.middle.x
        val toCornerY = g.corner.y - g.middle.y
        val toCorner = hypot(toCornerX, toCornerY)
        val nx = (toCornerX / toCorner).toFloat()
        val ny = (toCornerY / toCorner).toFloat()
        // A quarter of how far the corner has been pulled
        val reach = (toCorner / 2).toFloat().coerceIn(MIN_ROLL_SHADE_DP * density, width / 4f)

        // The screenshot: turning forward, it's the page lifting away, left where it isn't lifted;
        // turning back, it's the page being covered again, left where the returning page hasn't landed yet
        var saved = canvas.save()
        if (turn.forward) {
            canvas.clipOutPath(lifted)
        } else {
            canvas.clipPath(lifted)
            canvas.clipOutPath(backOutline)
        }
        canvas.drawBitmap(page, mirror, bitmapPaint)
        canvas.restoreToCount(saved)

        // Shade on the page underneath, darkest along the roll
        saved = canvas.save()
        canvas.clipPath(lifted)
        canvas.clipOutPath(backOutline)
        aim(rollShade, g.vertex1.x.toFloat(), g.vertex1.y.toFloat(), nx * reach, ny * reach)
        canvas.drawPaint(rollShade)
        canvas.restoreToCount(saved)

        // Shadow the lifted paper casts beside its two edges
        saved = canvas.save()
        canvas.clipOutPath(lifted)
        edgeShadows(canvas, g)
        canvas.restoreToCount(saved)

        // The paper's back; turning forward it's this page's, whose text faintly shows through
        saved = canvas.save()
        canvas.clipPath(lifted)
        canvas.clipPath(backOutline)
        canvas.drawColor(pageColor)
        if (turn.forward) {
            reflectAcrossCrease(g)
            canvas.drawBitmap(page, reflection, throughPaint)
        }
        aim(backShade, g.vertex1.x.toFloat(), g.vertex1.y.toFloat(), -nx * reach / 2, -ny * reach / 2)
        canvas.drawPaint(backShade)
        canvas.restoreToCount(saved)
    }

    /**
     * Shadow strips outside the lifted paper's two edges, which meet at the touch point: the page's corner folded over,
     * so always at a right angle, where the two strips join along the diagonal. Each runs on along its edge's line to
     * the crease, so that where the edge turns into the roll the shadow goes under the roll instead of stopping short
     */
    private fun edgeShadows(canvas: Canvas, g: CurlGeometry) {
        val reach = EDGE_SHADE_DP * density
        val (n1x, n1y) = outward(g.control1, g.touch, g.end2) ?: return
        val (n2x, n2y) = outward(g.control2, g.touch, g.end1) ?: return
        val cornerX = g.touch.x.toFloat() + (n1x + n2x) * reach
        val cornerY = g.touch.y.toFloat() + (n1y + n2y) * reach
        edgeStrip(canvas, edgeShade1, g.control1, g.touch, n1x * reach, n1y * reach, cornerX, cornerY)
        edgeStrip(canvas, edgeShade2, g.control2, g.touch, n2x * reach, n2y * reach, cornerX, cornerY)
    }

    /** Unit normal of the edge from [from] to [to], on the side away from [other]; null for an edge too short to have one */
    private fun outward(from: CurlGeometry.Point, to: CurlGeometry.Point, other: CurlGeometry.Point): Pair<Float, Float>? {
        val vx = to.x - from.x
        val vy = to.y - from.y
        val length = hypot(vx, vy)
        if (length < 1) return null
        val nx = -vy / length
        val ny = vx / length
        val sign = if (nx * (other.x - from.x) + ny * (other.y - from.y) > 0) -1 else 1
        return (sign * nx).toFloat() to (sign * ny).toFloat()
    }

    /** One edge's strip, fading out over ([dx], [dy]) and ending at the touch point in the corner shared with the other strip */
    private fun edgeStrip(
        canvas: Canvas,
        paint: Paint,
        from: CurlGeometry.Point,
        touch: CurlGeometry.Point,
        dx: Float,
        dy: Float,
        cornerX: Float,
        cornerY: Float,
    ) {
        strip.rewind()
        strip.moveTo(from)
        strip.lineTo(touch)
        strip.lineTo(cornerX, cornerY)
        strip.lineTo(from.x.toFloat() + dx, from.y.toFloat() + dy)
        strip.close()
        aim(paint, from.x.toFloat(), from.y.toFloat(), dx, dy)
        canvas.drawPath(strip, paint)
    }

    /** [reflection]: the screenshot as it lies in page coordinates, mirrored across the crease */
    private fun reflectAcrossCrease(g: CurlGeometry) {
        val dx = g.control2.x - g.control1.x
        val dy = g.control2.y - g.control1.y
        val length = hypot(dx, dy)
        val ux = dx / length
        val uy = dy / length
        val a = ux * ux - uy * uy
        val b = 2 * ux * uy
        val x = g.control1.x
        val y = g.control1.y
        matrixValues[0] = a.toFloat()
        matrixValues[1] = b.toFloat()
        matrixValues[2] = (x - (a * x + b * y)).toFloat()
        matrixValues[3] = b.toFloat()
        matrixValues[4] = (-a).toFloat()
        matrixValues[5] = (y - (b * x - a * y)).toFloat()
        matrixValues[6] = 0f
        matrixValues[7] = 0f
        matrixValues[8] = 1f
        reflect.setValues(matrixValues)
        reflection.set(mirror)
        reflection.postConcat(reflect)
    }

    /** Points [paint]'s gradient, which runs along x from 0 to 1, from ([x], [y]) along ([dx], [dy]) */
    private fun aim(paint: Paint, x: Float, y: Float, dx: Float, dy: Float) {
        matrixValues[0] = dx
        matrixValues[1] = -dy
        matrixValues[2] = x
        matrixValues[3] = dy
        matrixValues[4] = dx
        matrixValues[5] = y
        matrixValues[6] = 0f
        matrixValues[7] = 0f
        matrixValues[8] = 1f
        gradientMatrix.setValues(matrixValues)
        paint.shader.setLocalMatrix(gradientMatrix)
    }

    /** Captures the page as it is now into [screenshot] */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun capture(onCaptured: () -> Unit, onFailed: () -> Unit) {
        val bitmap = screenshot?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                screenshot?.recycle()
                screenshot = it
            }
        val location = IntArray(2)
        getLocationInWindow(location)
        val area = Rect(location[0], location[1], location[0] + width, location[1] + height)
        try {
            PixelCopy.request(window, area, bitmap, { result -> if (result == PixelCopy.SUCCESS) onCaptured() else onFailed() }, mainHandler)
        } catch (_: IllegalArgumentException) {
            // The window has no surface to copy from
            onFailed()
        }
    }

    override fun onDetachedFromWindow() {
        current?.done()
        stopWatching()
        super.onDetachedFromWindow()
        screenshot?.recycle()
        screenshot = null
    }

    /**
     * One page turn. Its position is the touch point in page coordinates (x running in reading direction):
     * for a curl, where the corner has been pulled; for a cover, the moving page's leading edge
     */
    private inner class Turn(
        val forward: Boolean,
        val rtl: Boolean,
        val effect: PageAnimation,
        val fromBottom: Boolean,
        /** Turned straight across, from a drag that started mid-height */
        val level: Boolean,
    ) {
        private val w = width.toFloat()
        private val h = height.toFloat()
        private val curl = effect == PageAnimation.Curl
        private val cornerY = if (fromBottom) h else 0f

        /** Positions with the page as it was, and fully turned */
        val startX = if (curl) (if (forward) w else -w) else (if (forward) w else 0f)
        val endX = if (curl) (if (forward) -w else w) else (if (forward) 0f else w)
        var x = startX
        var y = cornerY

        var fingerDown = false
        private var fingerX = 0f
        private var fingerY = 0f

        /** Screenshot taken and the navigator turned underneath: only then is anything drawn */
        var ready = false

        /** Letting go of the page after the turn, it settles back and the navigator turns back */
        var settling = false

        /** Whether the navigator reached another page; null until it reports a location or [LANDING_TIMEOUT_MS] passes */
        private var landed: Boolean? = null
        private var afterLanding: (() -> Unit)? = null
        private val timeout = Runnable { onLanding(false) }

        /** Let go before the screenshot was taken: whether to complete the turn */
        private var releasedCommit: Boolean? = null
        private var animator: ValueAnimator? = null

        private val progress get() = (x - startX) / (endX - startX)

        init {
            if (rtl) mirror.setScale(-1f, 1f, w / 2, 0f) else mirror.reset()
        }

        fun begin(onFailed: () -> Unit = {}, then: () -> Unit) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || effect == PageAnimation.None) {
                ready = true
                then()
                return
            }
            capture(
                onCaptured = captured@{
                    if (current !== this) return@captured
                    // Ready first: the navigator may report its new location before navigate() returns
                    ready = true
                    mainHandler.postDelayed(timeout, LANDING_TIMEOUT_MS)
                    if (!navigate(forward, false)) {
                        done()
                        return@captured
                    }
                    moved()
                    then()
                },
                onFailed = {
                    done()
                    onFailed()
                },
            )
        }

        fun startDrag(viewX: Float, viewY: Float) {
            fingerDown = true
            fingerX = pageX(viewX)
            fingerY = viewY
            begin { releasedCommit?.let(::settle) }
        }

        fun follow(viewX: Float, viewY: Float) {
            val dx = pageX(viewX) - fingerX
            // A curl's corner follows the finger; turning back, the returning page starts off the page, so it moves twice as fast.
            // A cover's edge follows the finger
            x = when {
                curl && !forward -> -w + 2 * dx
                curl || forward -> w + dx
                else -> dx
            }.coerceIn(minOf(startX, endX), maxOf(startX, endX))
            y = if (curl && !level) cornerY + viewY - fingerY else cornerY
            moved()
        }

        /** Let go with the finger moving at [velocityX] px/s: a fling decides the turn, otherwise how far it got */
        fun release(velocityX: Float) {
            fingerDown = false
            val toward = if (rtl) -velocityX else velocityX
            val commit = if (abs(toward) > minFlingVelocity) (toward < 0) == forward else progress >= COMMIT_PROGRESS
            if (ready) settle(commit) else releasedCommit = commit
        }

        /** Tap or key: the whole turn, the curl's corner lifting in an arc */
        fun play() {
            val lift = if (fromBottom) -ARC * h else ARC * h
            animate(if (curl) CURL_MS else COVER_MS, AccelerateDecelerateInterpolator()) { t ->
                x = lerp(startX, endX, t)
                y = if (curl && t < 1f) cornerY + lift * sin(PI * t).toFloat() else cornerY
            }.onEnd { finish(true) }
        }

        /** Completes the turn, or takes it back when [commit] is false or the navigator didn't reach another page */
        private fun settle(commit: Boolean) {
            if (effect == PageAnimation.None) {
                if (commit) navigate(forward, false)
                done()
                return
            }
            val back = !commit || landed == false
            val fromX = x
            val fromY = y
            val toX = if (back) startX else endX
            val share = abs(toX - fromX) / abs(endX - startX)
            animate((SETTLE_MS * share).toLong().coerceAtLeast(MIN_SETTLE_MS), DecelerateInterpolator()) { t ->
                x = lerp(fromX, toX, t)
                y = lerp(fromY, cornerY, t)
            }.onEnd { finish(!back) }
        }

        private fun finish(turned: Boolean) {
            if (turned) {
                done()
                return
            }
            // The screenshot covers the page as it was again; once the navigator has said whether it turned,
            // it turns back underneath, and the page is uncovered when it's been drawn
            settling = true
            whenLanded {
                if (landed == true) {
                    landed = null
                    navigate(!forward, false)
                    mainHandler.postDelayed(timeout, LANDING_TIMEOUT_MS)
                    whenLanded(::uncover)
                } else {
                    uncover()
                }
            }
        }

        private fun uncover() {
            postOnAnimation { postOnAnimation { done() } }
        }

        fun onLanding(turned: Boolean) {
            if (landed != null) return
            mainHandler.removeCallbacks(timeout)
            landed = turned
            afterLanding?.let {
                afterLanding = null
                it()
                return
            }
            // The book has no page that way: the page springs back
            if (!turned && !fingerDown && !settling) {
                stopAnimation()
                settle(false)
            }
        }

        private fun whenLanded(action: () -> Unit) {
            if (landed != null) action() else afterLanding = action
        }

        fun skipToEnd() {
            animator?.end()
        }

        fun done() {
            stopAnimation()
            mainHandler.removeCallbacks(timeout)
            afterLanding = null
            navigatorView?.translationX = 0f
            if (current === this) current = null
            invalidate()
        }

        private fun moved() {
            if (!ready) return
            if (effect == PageAnimation.Cover && !forward) navigatorView?.translationX = if (rtl) w - x else x - w
            invalidate()
        }

        private fun pageX(viewX: Float) = if (rtl) w - viewX else viewX

        /** Exactly [to] at the end, which the drawing tells apart from a turn under way */
        private fun lerp(from: Float, to: Float, t: Float) = if (t >= 1f) to else from + (to - from) * t

        private fun animate(duration: Long, interpolator: TimeInterpolator, step: (Float) -> Unit): ValueAnimator {
            stopAnimation()
            return ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                this.interpolator = interpolator
                addUpdateListener {
                    step(it.animatedValue as Float)
                    moved()
                }
            }.also { animator = it }
        }

        private fun ValueAnimator.onEnd(action: () -> Unit) {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator === this@onEnd) animator = null
                    action()
                }
            })
            start()
        }

        private fun stopAnimation() {
            animator?.run {
                removeAllListeners()
                removeAllUpdateListeners()
                cancel()
            }
            animator = null
        }
    }

    private fun Path.moveTo(point: CurlGeometry.Point) = moveTo(point.x.toFloat(), point.y.toFloat())

    private fun Path.lineTo(point: CurlGeometry.Point) = lineTo(point.x.toFloat(), point.y.toFloat())

    private fun Path.quadTo(control: CurlGeometry.Point, end: CurlGeometry.Point) =
        quadTo(control.x.toFloat(), control.y.toFloat(), end.x.toFloat(), end.y.toFloat())

    private companion object {
        /** A slow drag let go this far through completes the turn */
        const val COMMIT_PROGRESS = 0.25f
        const val CURL_MS = 450L
        const val COVER_MS = 300L
        const val SETTLE_MS = 400L
        const val MIN_SETTLE_MS = 120L

        /** How long the navigator gets to report the page it turned to; without a report, there was no page to turn to */
        const val LANDING_TIMEOUT_MS = 600L

        /** How high a tap's curl lifts the corner, as a share of the page height */
        const val ARC = 0.12f

        const val MIN_ROLL_SHADE_DP = 6f
        const val EDGE_SHADE_DP = 12f
        const val COVER_SHADE_DP = 16f
        const val SHOW_THROUGH_ALPHA = 30

        // Shade colors, from where the shade starts to where it fades out
        val ROLL_SHADE = intArrayOf(0x66000000, 0)
        val BACK_SHADE = intArrayOf(0x33000000, 0)
        val EDGE_SHADE = intArrayOf(0x4D000000, 0)

        fun shadePaint(colors: IntArray) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 1f, 0f, colors, null, Shader.TileMode.CLAMP)
        }
    }
}
