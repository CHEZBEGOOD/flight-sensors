package app.flightsensors

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/** A basic artificial horizon: sky and ground split by a line that banks and moves with pitch. */
class AttitudeView(context: Context) : View(context) {

    private var pitch = 0f
    private var roll = 0f
    private val density = resources.displayMetrics.density

    private val sky = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 116, 186) }
    private val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(128, 82, 44) }
    private val horizonLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f * density
    }
    private val ladder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 1.5f * density
        textSize = 12f * density
    }
    private val airplane = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 193, 7)
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }

    fun setAttitude(pitchDeg: Float, rollDeg: Float) {
        if (pitchDeg == pitch && rollDeg == roll) return
        pitch = pitchDeg
        roll = rollDeg
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val cy = h / 2
        val pxPerDegree = min(w, h) / 50f  // roughly +/-25 degrees of pitch fits on screen
        val big = w + h                    // enough to cover the view at any bank angle

        canvas.save()
        canvas.clipRect(0f, 0f, w, h)
        canvas.rotate(-roll, cx, cy)           // bank right and the horizon tilts the other way
        val horizonY = cy + pitch * pxPerDegree  // nose up pushes the horizon down
        canvas.drawRect(cx - big, horizonY - big, cx + big, horizonY, sky)
        canvas.drawRect(cx - big, horizonY, cx + big, horizonY + big, ground)
        canvas.drawLine(cx - big, horizonY, cx + big, horizonY, horizonLine)

        for (deg in intArrayOf(-20, -10, 10, 20)) {
            val y = horizonY - deg * pxPerDegree
            val half = if (abs(deg) == 20) w * 0.12f else w * 0.08f
            canvas.drawLine(cx - half, y, cx + half, y, ladder)
            canvas.drawText(abs(deg).toString(), cx + half + 6f * density, y + 4f * density, ladder)
        }
        canvas.restore()

        // the little airplane stays fixed in the middle
        canvas.drawLine(cx - w * 0.18f, cy, cx - w * 0.05f, cy, airplane)
        canvas.drawLine(cx + w * 0.05f, cy, cx + w * 0.18f, cy, airplane)
        canvas.drawCircle(cx, cy, 3f * density, airplane)
    }
}
