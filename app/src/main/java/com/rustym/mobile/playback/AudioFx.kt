package com.rustym.mobile.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Session-scoped sound effects, the way premium players do it:
 * 5-band equalizer, bass boost, surround (virtualizer) and 8D rotation.
 * Created against the player's audio session and driven from Compose state
 * so the Settings page and the Now Playing popover stay in sync.
 */
object AudioFx {
    private const val PREFS = "rustym_sound"
    private var prefs: SharedPreferences? = null

    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    var eqEnabled by mutableStateOf(false)
        private set
    var bandCount by mutableStateOf(0)
        private set
    var bandGains by mutableStateOf<List<Int>>(emptyList()) // band level in milli-bel (-1500..1500)
        private set
    var bandRange by mutableStateOf(-1500 to 1500)
        private set
    var bandLabels by mutableStateOf<List<String>>(emptyList())
        private set
    var bassPercent by mutableStateOf(0) // 0..100
        private set
    var surroundPercent by mutableStateOf(0) // 0..100
        private set
    var eightD by mutableStateOf(false)
        private set

    /** Read on every audio buffer by [EightDAudioProcessor]; must stay plain, no Compose. */
    @Volatile
    @JvmField
    var eightDActive: Boolean = false

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        eqEnabled = prefs?.getBoolean("eq_enabled", false) ?: false
        eightD = prefs?.getBoolean("eight_d", false) ?: false
        eightDActive = eightD
        bassPercent = prefs?.getInt("bass", 0) ?: 0
        surroundPercent = prefs?.getInt("surround", 0) ?: 0
    }

    /** Bind the effects to the player's audio session; called on session creation / change. */
    fun attach(sessionId: Int) {
        if (sessionId == 0) return
        releaseFx()
        runCatching {
            equalizer = Equalizer(0, sessionId).apply {
                bandRange = (getBandLevelRange().getOrNull(0)?.toInt() ?: -1500) to
                    (getBandLevelRange().getOrNull(1)?.toInt() ?: 1500)
                bandCount = numberOfBands.toInt()
                val saved = prefs?.getString("eq_gains", null)
                    ?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
                if (saved.size >= numberOfBands) {
                    for (b in 0 until numberOfBands) setBandLevel(b.toShort(), saved[b].toShort())
                }
                bandGains = (0 until numberOfBands).map { getBandLevel(it.toShort()).toInt() }
                bandLabels = (0 until numberOfBands).map { b ->
                    val hz = getCenterFreq(b.toShort()) / 1000 // mHz -> Hz
                    if (hz >= 1000) "%.1fk".format(hz / 1000f) else "${hz}Hz"
                }
                enabled = eqEnabled
            }
        }
        runCatching {
            bass = BassBoost(0, sessionId).apply {
                if (strengthSupported) setStrength((bassPercent * 10).coerceIn(0, 1000).toShort())
            }
        }
        runCatching {
            virtualizer = Virtualizer(0, sessionId).apply {
                if (strengthSupported) setStrength((surroundPercent * 10).coerceIn(0, 1000).toShort())
            }
        }
    }

    fun setEqEnabled(on: Boolean) {
        eqEnabled = on
        runCatching { equalizer?.enabled = on }
        prefs?.edit()?.putBoolean("eq_enabled", on)?.apply()
    }

    fun setBandGain(band: Int, levelMb: Int) {
        runCatching { equalizer?.setBandLevel(band.toShort(), levelMb.toShort()) }
        val gains = bandGains.toMutableList()
        if (band < gains.size) {
            gains[band] = levelMb
            bandGains = gains
            prefs?.edit()?.putString("eq_gains", gains.joinToString(","))?.apply()
        }
    }

    fun setBass(percent: Int) {
        bassPercent = percent.coerceIn(0, 100)
        runCatching { bass?.setStrength((bassPercent * 10).coerceIn(0, 1000).toShort()) }
        prefs?.edit()?.putInt("bass", bassPercent)?.apply()
    }

    fun setSurround(percent: Int) {
        surroundPercent = percent.coerceIn(0, 100)
        runCatching { virtualizer?.setStrength((surroundPercent * 10).coerceIn(0, 1000).toShort()) }
        prefs?.edit()?.putInt("surround", surroundPercent)?.apply()
    }

    fun set8D(on: Boolean) {
        eightD = on
        eightDActive = on
        prefs?.edit()?.putBoolean("eight_d", on)?.apply()
    }

    private fun releaseFx() {
        runCatching { equalizer?.enabled = false; equalizer?.release() }
        runCatching { bass?.enabled = false; bass?.release() }
        runCatching { virtualizer?.enabled = false; virtualizer?.release() }
        equalizer = null; bass = null; virtualizer = null
    }

    fun release() {
        releaseFx()
        eightDActive = false
    }
}
