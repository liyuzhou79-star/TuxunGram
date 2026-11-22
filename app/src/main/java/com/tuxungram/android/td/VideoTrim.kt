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
 * is fast and the picture is bit-identical. MP4 family
 * only: the muxer writes an MPEG-4 container, and other
 * containers are refused up front with a plain-language
 * reason instead of a broken file.
 *
 * Because nothing is re-encoded, the start point snaps to
 * the nearest keyframe at or before the picked point (the
 * error is about one GOP, typically a second or two); the
 * UI says so next to the sliders. Both tracks share one
 * time base — the video keyframe's own timestamp — so
 * audio and video stay in sync in the exported file.
 */
object VideoTrim {

    data class Info(
        val durationMs: Long,
        val isMp4: Boolean,
        val rotation: Int
    )

    /** Container sniff: an MP4-family file starts with an
     *  ftyp box (bytes 4..7). The retriever supplies the
     *  duration and rotation. Null = unreadable file. */
    fun probe(path: String): Info? {
        val isMp4 = try {
            FileInputStream(File(path)).use { input ->
                val head = ByteArray(12)
                val read = input.read(head)
                read == 12 &&
                    head[4] == 'f'.code.toByte() &&
                    head[5] == 't'.code.toByte() &&
                    head[6] == 'y'.code.toByte() &&
                    head[7] == 'p'.code.toByte()
            }
        } catch (t: Throwable) {
            false
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
            Info(duration, isMp4, rotation)
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
     */
    fun trim(
        srcPath: String,
        dstPath: String,
        startMs: Long,
        endMs: Long
    ): String? {
        val info = probe(srcPath)
            ?: return "这个视频读不出来，没法剪辑。"
        if (!info.isMp4) {
            return "只有 MP4 格式的视频能剪辑，这个文件不是 MP4。"
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
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            muxer = mux
            if (info.rotation != 0) {
                mux.setOrientationHint(info.rotation)
            }
            // Map extractor tracks onto muxer tracks; only
            // video/audio tracks ride along. A codec the
            // muxer refuses fails the whole export honestly.
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
            val buffer = ByteBuffer.allocate(maxInputSize)
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
                val size = extractor.readSampleData(buffer, 0)
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
            extractor.release()
            // A failed export can leave a half-written file;
            // the caller checks the return value and deletes
            // the destination before falling back to the
            // original video.
        }
    }
}
