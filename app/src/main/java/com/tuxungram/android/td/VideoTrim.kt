package com.tuxungram.android.td

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Pre-send video trimming (v0.9.33, Z1): packet copy with
 * MediaExtractor + MediaMuxer — no re-encoding, so export
 * is fast and the picture is bit-identical.
 *
 * Containers (v0.9.57, B1): the whole ISO-BMFF family
 * rides the MPEG-4 muxer — that is MP4 proper plus MOV
 * and 3GP sources, whose H.264/HEVC + AAC tracks are
 * re-wrapped into an .mp4 without touching the samples
 * (a track the muxer refuses still fails honestly at
 * export). WebM rides the WebM muxer the same way, for
 * VP8/VP9 + Opus/Vorbis tracks only. Anything else
 * (AVI, FLV, or a codec no muxer here accepts) is refused
 * up front with a plain-language reason instead of a
 * broken file; re-encoding trims remain out of scope.
 *
 * Because nothing is re-encoded, the start point snaps to
 * the nearest keyframe at or before the picked point (the
 * error is about one GOP, typically a second or two); the
 * UI says so next to the sliders. Both tracks share one
 * time base — the video keyframe's own timestamp — so
 * audio and video stay in sync in the exported file.
 */
object VideoTrim {

    /** Which muxer family a source file can ride. */
    enum class Family { MP4, WEBM, OTHER }

    /** The only track types the platform WebM muxer
     *  accepts (v0.9.57, B1). */
    private val WEBM_MIMES = setOf(
        "video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9",
        "audio/opus", "audio/vorbis"
    )

    data class Info(
        val durationMs: Long,
        val family: Family,
        val rotation: Int,
        /** True when the file carries an audio track; the
         *  trim screen only offers its mute switch then
         *  (v0.9.34, A2). */
        val hasAudio: Boolean,
        /** Extension for the exported file ("mp4" / "webm"),
         *  following the muxer the source rides. */
        val outExtension: String
    )

    /** Container sniff: an ISO-BMFF file starts with an
     *  ftyp box (bytes 4..7 — MP4, MOV and 3GP all do);
     *  a WebM/Matroska file starts with the EBML header
     *  (1A 45 DF A3). The retriever supplies the duration
     *  and rotation. Null = unreadable file. */
    fun probe(path: String): Info? {
        val family = try {
            FileInputStream(File(path)).use { input ->
                val head = ByteArray(12)
                val read = input.read(head)
                when {
                    read == 12 &&
                        head[4] == 'f'.code.toByte() &&
                        head[5] == 't'.code.toByte() &&
                        head[6] == 'y'.code.toByte() &&
                        head[7] == 'p'.code.toByte() -> Family.MP4
                    read >= 4 &&
                        head[0] == 0x1A.toByte() &&
                        head[1] == 0x45.toByte() &&
                        head[2] == 0xDF.toByte() &&
                        head[3] == 0xA3.toByte() -> Family.WEBM
                    else -> Family.OTHER
                }
            }
        } catch (t: Throwable) {
            Family.OTHER
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return null
            val rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
            val hasAudio = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
            ) == "yes"
            Info(
                duration, family, rotation, hasAudio,
                outExtension = if (family == Family.WEBM) {
                    "webm"
                } else {
                    "mp4"
                }
            )
        } catch (t: Throwable) {
            null
        } finally {
            try {
                retriever.release()
            } catch (t: Throwable) {
                // Nothing useful to do about a failed release.
            }
        }
    }

    /** One preview frame, decoded small. The retriever is
     *  created per call — the trim screen calls this a
     *  handful of times for its film strip, never per frame
     *  of playback. */
    fun frameAt(path: String, timeMs: Long, maxWidth: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val frame = retriever.getFrameAtTime(
                timeMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null
            if (frame.width <= maxWidth) {
                frame
            } else {
                val scaled = Bitmap.createScaledBitmap(
                    frame,
                    maxWidth,
                    (frame.height * maxWidth) / frame.width,
                    true
                )
                if (scaled !== frame) frame.recycle()
                scaled
            }
        } catch (t: Throwable) {
            null
        } finally {
            try {
                retriever.release()
            } catch (t: Throwable) {
                // See probe().
            }
        }
    }

    /**
     * Copy [startMs, endMs) of [srcPath] into [dstPath].
     * Returns null on success or a plain-language failure.
     * Runs entirely on the caller's (background) thread.
     *
     * The copy is streaming (v0.9.34, A2 audit): one sample
     * at a time through a single buffer, so a huge source
     * file costs no more memory than a small one. The
     * buffer starts at the tracks' declared max input size
     * and doubles (to a 64 MB cap) if a sample turns out
     * bigger than declared — a too-small buffer makes
     * readSampleData throw BufferOverflowException WITHOUT
     * consuming the sample, so retrying with a bigger
     * buffer is safe.
     *
     * [muteAudio] drops the audio tracks instead of copying
     * them (the muxer simply never gets an audio track).
     */
    fun trim(
        srcPath: String,
        dstPath: String,
        startMs: Long,
        endMs: Long,
        muteAudio: Boolean = false
    ): String? {
        val info = probe(srcPath)
            ?: return "这个视频读不出来，没法剪辑。"
        if (info.family == Family.OTHER) {
            return "这个视频的格式暂时不能剪辑，可以发送原视频。"
        }
        if (endMs <= startMs || startMs < 0) {
            return "起止点不对，换个范围再试。"
        }
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(srcPath)
            val mux = MediaMuxer(
                dstPath,
                if (info.family == Family.WEBM) {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                } else {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                }
            )
            muxer = mux
            // The orientation hint is an MPEG-4 concept; the
            // WebM muxer has no use for it.
            if (info.rotation != 0 &&
                info.family == Family.MP4
            ) {
                mux.setOrientationHint(info.rotation)
            }
            // Map extractor tracks onto muxer tracks; only
            // video/audio tracks ride along (audio only when
            // not muted). A codec the muxer refuses fails
            // the whole export honestly. The WebM muxer
            // takes only VP8/VP9 video and Opus/Vorbis
            // audio, so those tracks are checked up front
            // (v0.9.57, B1) instead of dying inside addTrack
            // with a framework error.
            val trackMap = HashMap<Int, Int>()
            var maxInputSize = 1024 * 1024
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (!mime.startsWith("video/") &&
                    !mime.startsWith("audio/")
                ) {
                    continue
                }
                if (muteAudio && mime.startsWith("audio/")) {
                    continue
                }
                if (info.family == Family.WEBM &&
                    mime !in WEBM_MIMES
                ) {
                    return "这个 WebM 里的编码（$mime）没法直接" +
                        "剪辑，可以发送原视频。"
                }
                val muxIndex = try {
                    mux.addTrack(format)
                } catch (t: Throwable) {
                    return "这个视频的编码（$mime）没法直接剪辑，" +
                        "可以发送原视频。"
                }
                trackMap[i] = muxIndex
                extractor.selectTrack(i)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    maxInputSize = maxOf(
                        maxInputSize,
                        format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    )
                }
            }
            if (trackMap.isEmpty()) {
                return "这个文件里没有能剪的视频或音频轨道。"
            }
            // Start at the keyframe at or before the picked
            // start; its timestamp becomes the shared zero so
            // both tracks keep their relative timing.
            val startUs = startMs * 1000
            val endUs = endMs * 1000
            extractor.seekTo(
                startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC
            )
            var baseUs = -1L
            mux.start()
            var buffer = ByteBuffer.allocate(maxInputSize)
            val bufferInfo = MediaCodec.BufferInfo()
            // Tracks whose samples have all passed the picked
            // end; the copy stops once every kept track is
            // done instead of walking to the end of the file.
            val doneTracks = HashSet<Int>()
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val muxTrack = trackMap[track]
                val sampleUs = extractor.sampleTime
                if (muxTrack == null || sampleUs < 0) {
                    extractor.advance()
                    continue
                }
                if (sampleUs > endUs) {
                    doneTracks.add(track)
                    if (doneTracks.size >= trackMap.size) break
                    extractor.advance()
                    continue
                }
                if (baseUs < 0) baseUs = sampleUs
                var size = -1
                while (true) {
                    try {
                        size = extractor.readSampleData(buffer, 0)
                        break
                    } catch (e: java.nio.BufferOverflowException) {
                        // Sample bigger than the declared max
                        // input size: grow and retry the SAME
                        // sample (a failed read consumes
                        // nothing). Past the cap the export
                        // fails honestly via the outer catch.
                        val newCapacity = buffer.capacity() * 2
                        if (newCapacity > 64 * 1024 * 1024) throw e
                        buffer = ByteBuffer.allocate(newCapacity)
                    }
                }
                if (size < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = size
                bufferInfo.presentationTimeUs =
                    (sampleUs - baseUs).coerceAtLeast(0)
                bufferInfo.flags = if (
                    extractor.sampleFlags and
                    MediaExtractor.SAMPLE_FLAG_SYNC != 0
                ) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                mux.writeSampleData(muxTrack, buffer, bufferInfo)
                extractor.advance()
            }
            mux.stop()
            null
        } catch (t: Throwable) {
            "剪辑失败了（${t.javaClass.simpleName}），可以发送原视频。"
        } finally {
            try {
                muxer?.release()
            } catch (t: Throwable) {
                // Already reported through the return value.
            }
            try {
                extractor.release()
            } catch (t: Throwable) {
                // A throwing release must not replace the
                // result the caller is about to act on
                // (v0.9.34, A4).
            }
            // A failed export can leave a half-written file;
            // the caller checks the return value and deletes
            // the destination before falling back to the
            // original video.
        }
    }
}
