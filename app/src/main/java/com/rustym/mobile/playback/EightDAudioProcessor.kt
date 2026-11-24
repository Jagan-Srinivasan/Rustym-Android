package com.rustym.mobile.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * The 8D effect: the stereo image rotates around the listener's head roughly
 * once every six seconds. Implemented as an ExoPlayer PCM processor so it works
 * on any stream; when the effect is off it is a byte-for-byte passthrough.
 */
class EightDAudioProcessor : BaseAudioProcessor() {
    private var phase = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val format = inputAudioFormat
        val canRotate = AudioFx.eightDActive &&
            format.channelCount == 2 &&
            format.encoding == C.ENCODING_PCM_16BIT

        val out = replaceOutputBuffer(remaining)
        if (!canRotate) {
            out.put(inputBuffer)
        } else {
            val sampleRate = format.sampleRate.coerceAtLeast(8000)
            // One full rotation every ~6.5 s.
            val step = 0.15 / sampleRate
            var angle = 2.0 * Math.PI * phase
            var cos = Math.cos(angle)
            var sin = Math.sin(angle)

            while (inputBuffer.remaining() >= 4) {
                val left = inputBuffer.short
                val right = inputBuffer.short
                out.putShort((left * cos).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                out.putShort((right * sin).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                phase += step
                if (phase >= 1.0) phase -= 1.0
                angle = 2.0 * Math.PI * phase
                cos = Math.cos(angle)
                sin = Math.sin(angle)
            }
            // Passthrough any trailing odd bytes untouched.
            out.put(inputBuffer)
        }
        // The contract: write, then flip so the sink can read the output.
        out.flip()
    }
}
