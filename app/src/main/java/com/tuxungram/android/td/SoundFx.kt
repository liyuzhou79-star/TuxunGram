package com.tuxungram.android.td

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * In-app message sounds (v0.9.29, V1): the official client's
 * timing, our own audio. A short rising two-tone when a
 * message is sent, one soft low tone when a message lands in
 * the chat currently open, and a beep each way when voice
 * recording starts and stops. No audio files ship with the
 * app — every sound is synthesized into PCM at runtime and
 * played through an AudioTrack on the notification stream,
 * so the sounds follow the system notification volume and
 * stay silent under Do Not Disturb, like message sounds
 * should. (The media stream was the alternative; it keeps
 * playing at full volume under DND, which is wrong for
 * message sounds.)
 *
 * Every entry point checks the user's 音效 switch and, for
 * the send/arrival sounds, that the app is in the foreground
 * (maintained by MainActivity's onStart/onStop): a quick
 * reply sent from a notification must not beep from the
 * background, and background accounts have their own clients
 * that never reach the send hook in TdRepository.
 */
object SoundFx {

    @Volatile
    var appInForeground: Boolean = false

    @Volatile
    private var lastArrivalAt: Long = 0L

    private val player = Executors.newSingleThreadExecutor { r ->
        Thread(r, "soundfx").apply { isDaemon = true }
    }

    private fun on(): Boolean = UiSettings.soundFx.value

    /** A message left this device (any send path — see the
     *  hook in TdRepository.sendRaw). */
    fun send() {
        if (on() && appInForeground) play(SEND)
    }

    /** A message arrived in the chat the user is looking at.
     *  Throttled: a burst of arrivals is one ding, not five. */
    fun arrival() {
        if (!on() || !appInForeground) return
        val now = System.currentTimeMillis()
        if (now - lastArrivalAt < 1500) return
        lastArrivalAt = now
        play(ARRIVAL)
    }

    /** Voice recording started (mic is live). */
    fun recordStart() {
        if (on() && appInForeground) play(RECORD_START)
    }

    /** Voice recording stopped (sent or cancelled). */
    fun recordStop() {
        if (on() && appInForeground) play(RECORD_STOP)
    }

    // One sound = a list of (frequency Hz, duration ms, gain)
    // segments played back to back. Frequencies are plain
    // sine partials chosen to read as soft UI tones, not as
    // copies of anyone's recorded audio.
    private val SEND = listOf(
        Segment(660.0, 55, 0.32), Segment(880.0, 85, 0.32)
    )
    private val ARRIVAL = listOf(Segment(523.0, 110, 0.20))
    private val RECORD_START = listOf(Segment(740.0, 70, 0.28))
    private val RECORD_STOP = listOf(
        Segment(560.0, 45, 0.28), Segment(420.0, 65, 0.28)
    )

    private data class Segment(
        val freq: Double,
        val durationMs: Int,
        val gain: Double
    )

    private const val SAMPLE_RATE = 44100

    private fun play(segments: List<Segment>) {
        player.execute {
            try {
                val pcm = synthesize(segments)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(
                                AudioAttributes.USAGE_NOTIFICATION
                            )
                            .setContentType(
                                AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(
                                AudioFormat.CHANNEL_OUT_MONO
                            )
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.setPlaybackPositionUpdateListener(
                    object :
                        AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(
                            t: AudioTrack
                        ) {
                            try {
                                t.stop()
                            } catch (e: Exception) {
                                // Already stopped: fine.
                            }
                            t.release()
                        }

                        override fun onPeriodicNotification(
                            t: AudioTrack
                        ) = Unit
                    }
                )
                track.notificationMarkerPosition = pcm.size
                track.play()
                track.write(pcm, 0, pcm.size)
            } catch (t: Throwable) {
                // A sound must never take the app down with it.
            }
        }
    }

    /** Render the segments into 16-bit mono PCM: each segment
     *  is a sine with a 5 ms attack and an exponential decay,
     *  so the tones pluck softly instead of clicking. */
    private fun synthesize(segments: List<Segment>): ShortArray {
        val total = segments.sumOf {
            it.durationMs * SAMPLE_RATE / 1000
        }
        val out = ShortArray(total)
        var offset = 0
        for (seg in segments) {
            val frames = seg.durationMs * SAMPLE_RATE / 1000
            val attack = (0.005 * SAMPLE_RATE).toInt()
            for (i in 0 until frames) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = if (i < attack) {
                    i.toDouble() / attack
                } else {
                    exp(-3.0 * (i - attack) / frames)
                }
                val v = sin(2 * PI * seg.freq * t) * seg.gain * env
                out[offset + i] = (v * Short.MAX_VALUE).toInt()
                    .coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt()
                    ).toShort()
            }
            offset += frames
        }
        return out
    }
}
