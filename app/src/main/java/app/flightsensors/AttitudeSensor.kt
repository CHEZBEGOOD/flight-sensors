package app.flightsensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pitch, roll and heading from the rotation vector sensor (gyro, accelerometer and compass
 * already fused by Android).
 *
 * Assumes the phone is mounted upright and facing you, so "forward" is out the back of the
 * phone. Tap Level in straight-and-level flight to zero out however the mount is tilted.
 */
class AttitudeSensor(context: Context, private val screenRotation: () -> Int) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val matrix = FloatArray(9)

    val available: Boolean get() = sensor != null

    @Volatile var ready = false
        private set
    @Volatile var pitchDeg = 0f
        private set
    @Volatile var rollDeg = 0f
        private set
    @Volatile var headingDeg = 0f
        private set

    private var smoothPitch = 0f
    private var smoothRoll = 0f
    // heading is smoothed as a unit vector so 359 and 001 don't average out to 180
    private var headingEast = 0f
    private var headingNorth = 1f

    fun start() {
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    fun level() {
        pitchOffset = smoothPitch
        rollOffset = smoothRoll
    }

    override fun onSensorChanged(event: SensorEvent) {
        // matrix turns phone coordinates into world coordinates (x east, y north, z up)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)

        // which way "right" on the screen points, in phone coordinates
        val (rightX, rightY) = when (screenRotation()) {
            Surface.ROTATION_90 -> 0f to -1f   // landscape, top of the phone to the left
            Surface.ROTATION_180 -> -1f to 0f
            Surface.ROTATION_270 -> 0f to 1f   // landscape, top of the phone to the right
            else -> 1f to 0f
        }

        // forward = out the back of the phone = (0, 0, -1)
        val forwardEast = -matrix[2]
        val forwardNorth = -matrix[5]
        val forwardUp = -matrix[8]
        // how far the right wing (screen right) points up or down
        val rightUp = matrix[6] * rightX + matrix[7] * rightY

        val pitch = Math.toDegrees(asin(forwardUp.coerceIn(-1f, 1f).toDouble())).toFloat()
        val roll = Math.toDegrees(-asin(rightUp.coerceIn(-1f, 1f).toDouble())).toFloat()  // right wing down = positive
        val heading = atan2(forwardEast.toDouble(), forwardNorth.toDouble())

        // light smoothing, the fused sensor is already pretty clean
        val k = if (ready) 0.15f else 1f
        smoothPitch += (pitch - smoothPitch) * k
        smoothRoll += (roll - smoothRoll) * k
        headingEast += (sin(heading).toFloat() - headingEast) * k
        headingNorth += (cos(heading).toFloat() - headingNorth) * k

        pitchDeg = smoothPitch - pitchOffset
        rollDeg = smoothRoll - rollOffset
        headingDeg = ((Math.toDegrees(atan2(headingEast.toDouble(), headingNorth.toDouble())) + 360.0) % 360.0).toFloat()
        ready = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        // kept outside the instance so Level survives a screen rotation (the activity gets rebuilt)
        private var pitchOffset = 0f
        private var rollOffset = 0f
    }
}
