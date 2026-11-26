package dev.voxpilot.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * The flowing red ribbons that run out from both sides of the mic button. Their height follows the
 * voice state: calm when idle, livelier while listening, a steady fast ripple while thinking,
 * and big swells that follow the AI's voice while speaking.
 */
class RibbonWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private var energy = 0.3f
    private var targetEnergy = 0.3f
    private var speed = 0.6f
    private var targetSpeed = 0.6f
    private var level = 0f
    private var t = 0f
    private var lastNanos = 0L
    private var thinking = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    private val clock = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            val now = System.nanoTime()
            val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceAtMost(0.05f)
            lastNanos = now
            t += dt * speed
            energy += (targetEnergy + level * 0.5f - energy) * 0.1f
            speed += (targetSpeed - speed) * 0.05f
            invalidate()
        }
    }

    fun setState(state: MicOrbView.State) {
        when (state) {
            MicOrbView.State.IDLE -> { targetEnergy = 0.32f; targetSpeed = 0.5f; thinking = 0f }
            MicOrbView.State.LISTENING -> { targetEnergy = 0.45f; targetSpeed = 1.0f; thinking = 0f }
            MicOrbView.State.THINKING -> { targetEnergy = 0.35f; targetSpeed = 2.4f; thinking = 1f }
            MicOrbView.State.SPEAKING -> { targetEnergy = 0.6f; targetSpeed = 1.6f; thinking = 0f }
        }
    }

    fun setLevel(value: Float) {
        level = value.coerceIn(0f, 1f)
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

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val mid = h / 2f
        val d = resources.displayMetrics.density
        val tint = if (thinking > 0.5f) Color.rgb(213, 80, 255) else Color.rgb(255, 50, 90)

        // Fade out toward both screen edges and toward the centre (the orb covers it).
        paint.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(Color.TRANSPARENT, withA(tint, 200), withA(tint, 230), withA(tint, 90), withA(tint, 230), withA(tint, 200), Color.TRANSPARENT),
            floatArrayOf(0f, 0.18f, 0.36f, 0.5f, 0.64f, 0.82f, 1f), Shader.TileMode.CLAMP,
        )

        val lines = 7
        for (i in 0 until lines) {
            val f = i / (lines - 1f)                 // 0..1 across the bundle
            val phase = t * 2.2f + f * 1.6f
            val amp = h * 0.38f * energy * (0.45f + 0.55f * (1f - kotlin.math.abs(f - 0.5f) * 1.2f))
            paint.strokeWidth = (if (i == lines / 2) 1.6f else 0.9f) * d
            paint.alpha = 255
            path.reset()
            var x = 0f
            val step = 6f * d
            while (x <= w + step) {
                val nx = x / w
                // Envelope: ribbons are tall mid-way between the edge and the centre, flat at both ends.
                val env = sin(PI * nx).toFloat().let { it * it }
                val y = mid + env * amp * sin(nx * 9f * PI.toFloat() * 0.5f * (1f + f * 0.25f) + phase) +
                    env * amp * 0.35f * sin(nx * 17f + phase * 1.3f + f * 3f) + (f - 0.5f) * h * 0.1f * env
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun withA(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
}
