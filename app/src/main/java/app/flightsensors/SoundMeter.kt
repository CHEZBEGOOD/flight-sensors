package app.flightsensors

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Rough cabin noise from the microphone in dBFS (0 is the loudest the mic can record).
 * It isn't calibrated to real dB SPL, but it's fine for comparing, like cruise vs. climb power.
 */
class SoundMeter(context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile var levelDbfs = Double.NaN
        private set

    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")  // MainActivity checks RECORD_AUDIO before calling this
    fun start() {
        if (running) return
        val rate = 44100
        val minBytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return

        // UNPROCESSED skips automatic gain control on phones that support it,
        // otherwise the phone "helpfully" turns loud noise down and the reading lies
        val source = if (audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.MIC
        }

        val recorder = try {
            AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBytes * 2)
        } catch (e: Exception) {
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }

        running = true
        thread = Thread({
            val samples = ShortArray(minBytes / 2)  // 16-bit samples, 2 bytes each
            try {
                recorder.startRecording()
                while (running) {
                    val n = recorder.read(samples, 0, samples.size)
                    if (n < 0) break  // error code from the driver
                    if (n == 0) continue
                    var sum = 0.0
                    for (i in 0 until n) {
                        val s = samples[i].toDouble()
                        sum += s * s
                    }
                    val rms = sqrt(sum / n)
                    val db = if (rms > 0) 20 * log10(rms / 32768.0) else -90.0
                    levelDbfs = if (levelDbfs.isNaN()) db else levelDbfs * 0.8 + db * 0.2
                }
            } catch (e: IllegalStateException) {
                // mic is busy or got taken away (phone call, another app)
            } finally {
                try { recorder.stop() } catch (e: IllegalStateException) { }
                recorder.release()
                running = false
            }
        }, "sound-meter").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }
}
