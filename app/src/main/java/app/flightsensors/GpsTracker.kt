package app.flightsensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper

data class GpsFix(
    val groundSpeedKt: Double?,
    val trackDeg: Double?,
    val altitudeFt: Double?,
    val altitudeIsMsl: Boolean,
    val accuracyFt: Double?
)

/** Ground speed, track, altitude and a smoothed vertical speed from the phone's GPS. */
class GpsTracker(context: Context) : LocationListener {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var running = false

    @Volatile var fix: GpsFix? = null
        private set

    @Volatile var verticalSpeedFpm: Double? = null
        private set

    private var lastAltitudeFt: Double? = null
    private var lastTimeMs = 0L

    @SuppressLint("MissingPermission")  // MainActivity checks the permission before calling this
    fun start() {
        if (running) return
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this, Looper.getMainLooper())
        running = true
    }

    fun stop() {
        if (!running) return
        manager.removeUpdates(this)
        running = false
    }

    override fun onLocationChanged(location: Location) {
        // Android 14 can report real MSL altitude. Older versions only give height above the
        // WGS84 ellipsoid, which is off from MSL by the geoid height (around -20 m in Oregon).
        val altitudeM: Double?
        val isMsl: Boolean
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude()) {
            altitudeM = location.mslAltitudeMeters
            isMsl = true
        } else {
            altitudeM = if (location.hasAltitude()) location.altitude else null
            isMsl = false
        }
        val altitudeFt = altitudeM?.times(M_TO_FT)

        val nowMs = location.elapsedRealtimeNanos / 1_000_000
        val previous = lastAltitudeFt
        if (altitudeFt != null && previous != null && nowMs > lastTimeMs) {
            val minutes = (nowMs - lastTimeMs) / 60_000.0
            val raw = (altitudeFt - previous) / minutes
            // GPS altitude jumps around a lot, so lean on the running average
            verticalSpeedFpm = verticalSpeedFpm?.let { it * 0.75 + raw * 0.25 } ?: raw
        }
        if (altitudeFt != null) {
            lastAltitudeFt = altitudeFt
            lastTimeMs = nowMs
        }

        fix = GpsFix(
            groundSpeedKt = if (location.hasSpeed()) location.speed * MS_TO_KT else null,
            trackDeg = if (location.hasBearing()) location.bearing.toDouble() else null,
            altitudeFt = altitudeFt,
            altitudeIsMsl = isMsl,
            accuracyFt = if (location.hasAccuracy()) location.accuracy * M_TO_FT else null
        )
    }

    // These became default methods in Android 11. Phones older than that crash with
    // AbstractMethodError if they're missing, so keep the empty versions.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    companion object {
        const val M_TO_FT = 3.28084
        const val MS_TO_KT = 1.943844
    }
}
