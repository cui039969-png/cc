// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.audio

import android.media.*
import java.io.File
import java.nio.ByteOrder

/** Generate one second of a tone directly from PCM; never opens a microphone. */
internal object SyntheticAac {
    fun write(file: File) {
        val rate = 44100
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxing = false
        try {
            codec.configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 64000)
            }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            var frame = 0; var ended = false; var track = -1
            val info = MediaCodec.BufferInfo()
            val deadline = android.os.SystemClock.elapsedRealtime() + 10000
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                if (!ended) {
                    val input = codec.dequeueInputBuffer(10000)
                    if (input >= 0) {
                        val buffer = requireNotNull(codec.getInputBuffer(input)).apply { clear(); order(ByteOrder.nativeOrder()) }
                        val count = minOf(buffer.remaining() / 2, rate - frame)
                        repeat(count) { offset -> buffer.putShort((kotlin.math.sin(2 * Math.PI * 440 * (frame + offset) / rate) * 2000).toInt().toShort()) }
                        val timestamp = frame * 1_000_000L / rate
                        frame += count; ended = frame == rate
                        codec.queueInputBuffer(input, 0, count * 2, timestamp, if (ended) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    }
                }
                when (val output = codec.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start(); muxing = true }
                    else -> if (output >= 0) {
                        val buffer = requireNotNull(codec.getOutputBuffer(output))
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(muxing); buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        val done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(output, false)
                        if (done) return
                    }
                }
            }
            error("Synthetic AAC encoding timed out")
        } finally {
            runCatching { codec.stop() }; codec.release()
            if (muxing) muxer.stop()
            muxer.release()
        }
    }
}
