package app.flightsensors

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * One screen: an artificial horizon plus a grid of big numbers.
 * Everything redraws 5 times a second from whatever the sensors last reported.
 */
class MainActivity : Activity() {

    private lateinit var gps: GpsTracker
    private lateinit var attitude: AttitudeSensor
    private lateinit var sound: SoundMeter
    private lateinit var horizon: AttitudeView
    private lateinit var status: TextView
    private val values = mutableMapOf<String, TextView>()

    private val handler = Handler(Looper.getMainLooper())
    private val redraw = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 200)
        }
    }
    private var askedForPermissions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gps = GpsTracker(this)
        attitude = AttitudeSensor(this) { screenRotation() }
        sound = SoundMeter(this)
        setContentView(buildLayout())
    }

    override fun onResume() {
        super.onResume()
        startSensors()
        handler.post(redraw)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(redraw)
        gps.stop()
        attitude.stop()
        sound.stop()
    }

    // ---------- permissions ----------

    private fun has(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun startSensors() {
        attitude.start()
        val wanted = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,  // Android 12+ wants both in the same request
            Manifest.permission.RECORD_AUDIO
        )
        val missing = wanted.filterNot(::has)
        if (missing.isNotEmpty() && !askedForPermissions) {
            askedForPermissions = true  // only ask once per launch, otherwise a "deny" loops forever
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
        if (has(Manifest.permission.ACCESS_FINE_LOCATION)) gps.start()
        if (has(Manifest.permission.RECORD_AUDIO)) sound.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        if (has(Manifest.permission.ACCESS_FINE_LOCATION)) gps.start()
        if (has(Manifest.permission.RECORD_AUDIO)) sound.start()
    }

    @Suppress("DEPRECATION")
    private fun screenRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.rotation ?: Surface.ROTATION_0
        else windowManager.defaultDisplay.rotation

    // ---------- layout (built in code, no XML) ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun buildLayout(): View {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        horizon = AttitudeView(this)

        val grid = GridLayout(this).apply { columnCount = 3 }
        for ((key, label) in TILES) {
            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(tile(key, label), params)
        }

        status = TextView(this).apply {
            setTextColor(MUTED)
            textSize = 12f
        }
        val level = Button(this).apply {
            text = "Level"
            setOnClickListener {
                attitude.level()
                Toast.makeText(this@MainActivity, "Pitch and roll zeroed", Toast.LENGTH_SHORT).show()
            }
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), 0)
            addView(status, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(level)
        }

        val numbers = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        return LinearLayout(this).apply {
            setBackgroundColor(BACKGROUND)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            if (landscape) {
                orientation = LinearLayout.HORIZONTAL
                addView(horizon, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { rightMargin = dp(8) })
                addView(numbers, LinearLayout.LayoutParams(0, MATCH_PARENT, 1.3f))
            } else {
                orientation = LinearLayout.VERTICAL
                addView(horizon, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { bottomMargin = dp(8) })
                addView(numbers, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
        }
    }

    private fun tile(key: String, label: String): View {
        val value = TextView(this).apply {
            text = "--"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        values[key] = value
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(TILE)
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(10), dp(6), dp(10), dp(8))
            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(MUTED)
                textSize = 12f
            })
            addView(value)
        }
    }

    // ---------- updating the screen ----------

    private fun show(key: String, text: String?) {
        values[key]?.text = text ?: "--"
    }

    private fun render() {
        val fix = gps.fix
        show("gs", fix?.groundSpeedKt?.let { "%.0f".format(it) })
        show("alt", fix?.altitudeFt?.let { "%,d".format(it.roundToInt()) })
        show("vs", gps.verticalSpeedFpm?.let { fpm ->
            val rounded = (fpm / 10).roundToInt() * 10  // GPS can't do better than tens of fpm anyway
            if (rounded > 0) "+$rounded" else "$rounded"
        })
        show("trk", fix?.trackDeg?.let { "%03d".format(it.roundToInt() % 360) })
        show("acc", fix?.accuracyFt?.let { "%.0f".format(it) })

        if (attitude.ready) {
            show("pitch", "%+.0f".format(attitude.pitchDeg))
            show("roll", "%+.0f".format(attitude.rollDeg))
            show("hdg", "%03d".format(attitude.headingDeg.roundToInt() % 360))
            horizon.setAttitude(attitude.pitchDeg, attitude.rollDeg)
        }

        val db = sound.levelDbfs
        show("noise", if (db.isNaN()) null else "%.0f".format(db))

        status.text = statusLine(fix)
    }

    private fun statusLine(fix: GpsFix?): String {
        val parts = mutableListOf<String>()
        parts += when {
            !has(Manifest.permission.ACCESS_FINE_LOCATION) -> "GPS off (no location permission)"
            fix == null -> "Waiting for GPS, needs a view of the sky"
            fix.altitudeIsMsl -> "GPS altitude is MSL"
            else -> "GPS altitude is above the WGS84 ellipsoid, not MSL"
        }
        if (!attitude.available) parts += "no rotation sensor on this phone"
        if (!has(Manifest.permission.RECORD_AUDIO)) parts += "mic off"
        parts += "Not a flight instrument"
        return parts.joinToString(" · ")
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1

        private val BACKGROUND = Color.rgb(12, 16, 20)
        private val TILE = Color.rgb(28, 34, 41)
        private val MUTED = Color.rgb(139, 148, 158)

        private val TILES = listOf(
            "gs" to "GS kt",
            "alt" to "GPS ALT ft",
            "vs" to "VS fpm",
            "trk" to "TRACK °",
            "hdg" to "HDG ° (mag)",
            "acc" to "GPS ± ft",
            "pitch" to "PITCH °",
            "roll" to "ROLL °",
            "noise" to "NOISE dBFS"
        )
    }
}
