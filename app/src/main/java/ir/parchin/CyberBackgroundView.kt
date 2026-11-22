package ir.parchin

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/** A restrained HUD backdrop. There is no animator or timer while the activity is hidden. */
class CyberBackgroundView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var running = false
    private var startedAt = 0L
    private var elapsed = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (!running || !isAttachedToWindow || windowVisibility != VISIBLE) return
            invalidate()
            postDelayed(this, 50L) // 20 fps is sufficient for the slow ambient movement
        }
    }

    fun setRunning(active: Boolean) {
        val shouldRun = active && ValueAnimator.areAnimatorsEnabled()
        if (shouldRun == running) return
        if (running) elapsed += SystemClock.uptimeMillis() - startedAt
        running = shouldRun
        removeCallbacks(tick)
        if (running) {
            startedAt = SystemClock.uptimeMillis()
            post(tick)
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        setRunning(false)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val time = (elapsed + if (running) SystemClock.uptimeMillis() - startedAt else 0L) / 1000f
        paint.shader = LinearGradient(0f, 0f, w, h, 0xFF07131B.toInt(), 0xFF090C18.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val d = resources.displayMetrics.density
        paint.strokeWidth = d
        paint.color = 0x123B9D9D
        val step = 36f * d
        var x = 0f
        while (x < w) { canvas.drawLine(x, 0f, x, h, paint); x += step }
        var y = 0f
        while (y < h) { canvas.drawLine(0f, y, w, y, paint); y += step }
        // Fine scanner line and orbital arcs: low contrast behind the content.
        val scanY = ((time * 11f * d) % (h + 120f * d)) - 60f * d
        paint.shader = LinearGradient(0f, scanY - 35f * d, 0f, scanY + 1f,
            Color.TRANSPARENT, 0x253EE0D1, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, scanY - 35f * d, w, scanY + 1f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * d
        paint.color = 0x2859D3C9
        val cx = w * .77f
        val cy = h * .72f
        val r = w * (.30f + .012f * sin(time * .6f))
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = 0x268A80D0
        canvas.drawArc(cx-r-12*d, cy-r-12*d, cx+r+12*d, cy+r+12*d, time * 5f, 110f, false, paint)
        paint.style = Paint.Style.FILL
    }
}
