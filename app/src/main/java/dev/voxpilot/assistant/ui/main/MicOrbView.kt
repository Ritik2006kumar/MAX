package dev.voxpilot.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The glowing mic button from the design, with a distinct animation per voice state:
 *  - IDLE:      slow breathing glow, calm ring.
 *  - LISTENING: soft ripples travel outward; the ring swells with the user's voice level.
 *  - THINKING:  mic dims, two arcs spin in opposite directions with orbiting sparks (magenta).
 *  - SPEAKING:  an equalizer crown of bars around the ring dances to the AI's voice level.
 * The view is drawn at 2x the ring so ripples and bars have room; the ring is half the view width.
 */
class MicOrbView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    private var state = State.IDLE
    private var level = 0f          // smoothed 0..1 voice level
    private var targetLevel = 0f
    private var t = 0f              // seconds, drives every animation
    private var blend = 0f          // 0 = red palette, 1 = thinking palette (smoothly eased)
    private var micFade = 1f        // mic icon opacity (dims while thinking)

    private val red = Color.rgb(255, 45, 85)
    private val pink = Color.rgb(255, 120, 150)
    private val magenta = Color.rgb(213, 60, 255)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val micPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val micPath = Path()

    private var lastNanos = 0L
    private val clock = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { tick() }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // BlurMaskFilter glow needs software rendering
    }

    fun setState(newState: State) {
        if (newState == state) return
        state = newState
        invalidate()
    }

    /** Voice level 0..1 (mic level while listening, playback level while speaking). */
    fun setLevel(value: Float) {
        targetLevel = value.coerceIn(0f, 1f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastNanos = 0L
        if (!clock.isStarted) clock.start()
    }

    override fun onDetachedFromWindow() {
        clock.cancel()
        super.onDetachedFromWindow()
    }

    private fun tick() {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceAtMost(0.05f)
        lastNanos = now
        t += dt
        // Fast attack, slower release: bars jump with the voice and settle smoothly.
        level += (targetLevel - level) * (if (targetLevel > level) 0.45f else 0.12f)
        if (state != State.LISTENING && state != State.SPEAKING) targetLevel *= 0.9f
        blend += ((if (state == State.THINKING) 1f else 0f) - blend) * 0.08f
        micFade += ((if (state == State.THINKING) 0.35f else 1f) - micFade) * 0.1f
        invalidate()
    }

    private fun mix(a: Int, b: Int, f: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
    )

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val ringR = min(width, height) * 0.25f            // ring radius (ring diameter = half the view)
        val d = resources.displayMetrics.density
        val main = mix(red, magenta, blend)
        val soft = mix(pink, Color.rgb(230, 150, 255), blend)

        val breathe = 0.5f + 0.5f * sin(t * (if (state == State.IDLE) 1.6f else 2.4f))
        val swell = when (state) {
            State.LISTENING -> 1f + 0.05f * level
            State.SPEAKING -> 1f + 0.04f * level
            else -> 1f + 0.012f * breathe
        }
        val r = ringR * swell

        // Ambient glow behind everything
        val glowA = when (state) {
            State.IDLE -> 0.35f + 0.15f * breathe
            State.LISTENING -> 0.5f + 0.4f * level
            State.THINKING -> 0.5f + 0.15f * breathe
            State.SPEAKING -> 0.55f + 0.4f * level
        }
        discPaint.shader = RadialGradient(cx, cy, r * 2.1f,
            intArrayOf(withAlpha(main, (110 * glowA).toInt()), withAlpha(main, (40 * glowA).toInt()), Color.TRANSPARENT),
            floatArrayOf(0.35f, 0.62f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 2.1f, discPaint)

        // State-specific effects that live outside the ring
        when (state) {
            State.LISTENING -> drawRipples(canvas, cx, cy, r, main)
            State.SPEAKING -> drawEqualizer(canvas, cx, cy, r, main, soft, d)
            State.THINKING -> drawThinking(canvas, cx, cy, r, main, soft, d)
            State.IDLE -> drawRipples(canvas, cx, cy, r, main, strength = 0.35f, speed = 0.18f)
        }

        // Outer ring: blurred glow pass + crisp pass
        glowPaint.strokeWidth = 7f * d
        glowPaint.color = withAlpha(main, 200)
        glowPaint.maskFilter = BlurMaskFilter(10f * d, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, r, glowPaint)
        ringPaint.strokeWidth = 2.6f * d
        ringPaint.color = mix(soft, Color.WHITE, 0.25f)
        canvas.drawCircle(cx, cy, r, ringPaint)

        // Inner thin ring (as in the design)
        ringPaint.strokeWidth = 1.2f * d
        ringPaint.color = withAlpha(soft, 150)
        canvas.drawCircle(cx, cy, r * 0.86f, ringPaint)

        // Dark glass disc with a soft top-left highlight
        discPaint.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 0.9f,
            intArrayOf(Color.rgb(58, 44, 50), Color.rgb(24, 17, 20), Color.rgb(10, 7, 9)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 0.8f, discPaint)
        discPaint.shader = null

        drawMic(canvas, cx, cy, r * 0.8f, soft, d)
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    /** Concentric rings drifting outward and fading. */
    private fun drawRipples(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strength: Float = 1f, speed: Float = 0.45f) {
        val d = resources.displayMetrics.density
        for (i in 0 until 3) {
            val phase = ((t * speed + i / 3f) % 1f)
            val rr = r * (1.05f + phase * (0.55f + 0.5f * level))
            val a = (1f - phase) * (0.35f + 0.5f * level) * strength
            ringPaint.strokeWidth = (2.2f - phase * 1.4f) * d
            ringPaint.color = withAlpha(color, (255 * a).toInt())
            canvas.drawCircle(cx, cy, rr, ringPaint)
        }
    }

    /** Crown of radial bars around the ring that dance to the playback level. */
    private fun drawEqualizer(canvas: Canvas, cx: Float, cy: Float, r: Float, main: Int, soft: Int, d: Float) {
        val bars = 56
        barPaint.strokeWidth = 3.2f * d
        for (i in 0 until bars) {
            val ang = (i.toFloat() / bars) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val wobble = 0.5f + 0.5f * sin(t * 9f + i * 0.9f) * cos(t * 5.3f + i * 0.37f)
            val len = (4f + (6f + 38f * level) * wobble) * d
            val r0 = r + 8f * d
            val r1 = r0 + len
            barPaint.color = mix(main, soft, wobble)
            barPaint.alpha = (110 + 145 * wobble).toInt()
            canvas.drawLine(cx + cos(ang) * r0, cy + sin(ang) * r0, cx + cos(ang) * r1, cy + sin(ang) * r1, barPaint)
        }
        drawRipples(canvas, cx, cy, r, main, strength = 0.5f, speed = 0.6f)
    }

    /** Two counter-rotating gradient arcs plus orbiting sparks while the AI is working. */
    private fun drawThinking(canvas: Canvas, cx: Float, cy: Float, r: Float, main: Int, soft: Int, d: Float) {
        arcPaint.strokeWidth = 4f * d
        val rr = r + 16f * d
        oval.set(cx - rr, cy - rr, cx + rr, cy + rr)
        val a1 = (t * 220f) % 360f
        arcPaint.color = main
        arcPaint.maskFilter = BlurMaskFilter(6f * d, BlurMaskFilter.Blur.SOLID)
        canvas.drawArc(oval, a1, 95f, false, arcPaint)
        canvas.drawArc(oval, a1 + 180f, 55f, false, arcPaint)
        arcPaint.maskFilter = null

        val r2 = r + 30f * d
        oval.set(cx - r2, cy - r2, cx + r2, cy + r2)
        val a2 = -(t * 140f) % 360f
        arcPaint.strokeWidth = 2.4f * d
        arcPaint.color = withAlpha(soft, 200)
        canvas.drawArc(oval, a2, 120f, false, arcPaint)
        canvas.drawArc(oval, a2 + 200f, 40f, false, arcPaint)

        // Orbiting sparks with comet tails
        for (i in 0 until 6) {
            val ang = Math.toRadians((t * 180.0 + i * 60.0))
            val orbit = r + (22f + 10f * sin(t * 2f + i)) * d
            val x = cx + cos(ang).toFloat() * orbit
            val y = cy + sin(ang).toFloat() * orbit
            sparkPaint.color = withAlpha(soft, 230)
            canvas.drawCircle(x, y, (2.2f + (i % 3)) * d * 0.8f, sparkPaint)
        }
    }

    private fun drawMic(canvas: Canvas, cx: Float, cy: Float, discR: Float, color: Int, d: Float) {
        val s = discR * 0.5f // icon scale
        val a = (255 * micFade).toInt()
        micPaint.strokeWidth = s * 0.115f

        // glow under the glyph
        micPaint.color = withAlpha(color, (170 * micFade).toInt())
        micPaint.maskFilter = BlurMaskFilter(7f * d, BlurMaskFilter.Blur.NORMAL)
        buildMicPath(cx, cy, s)
        canvas.drawPath(micPath, micPaint)
        micPaint.maskFilter = null
        micPaint.color = withAlpha(mix(color, Color.WHITE, 0.35f), a)
        canvas.drawPath(micPath, micPaint)
    }

    private fun buildMicPath(cx: Float, cy: Float, s: Float) {
        micPath.reset()
        val capW = s * 0.36f
        val capTop = cy - s * 0.78f
        val capBottom = cy + s * 0.12f
        // capsule
        oval.set(cx - capW, capTop, cx + capW, capBottom)
        micPath.addRoundRect(oval, capW, capW, Path.Direction.CW)
        // U-shaped holder
        val hr = s * 0.62f
        oval.set(cx - hr, cy - s * 0.42f, cx + hr, cy + s * 0.5f)
        micPath.moveTo(cx - hr, cy + s * 0.04f)
        micPath.arcTo(oval, 180f, -180f, false)
        // stem + base
        micPath.moveTo(cx, cy + s * 0.5f)
        micPath.lineTo(cx, cy + s * 0.82f)
        micPath.moveTo(cx - s * 0.3f, cy + s * 0.82f)
        micPath.lineTo(cx + s * 0.3f, cy + s * 0.82f)
    }
}
