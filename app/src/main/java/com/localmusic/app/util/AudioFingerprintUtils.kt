package com.localmusic.app.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

/**
 * 音频指纹工具：把任意本地音频（MediaExtractor + MediaCodec 硬解码）
 * 规整为单声道 11025 Hz int16 PCM，再交给 [Chromaprint] 计算指纹。
 * 同时提供文件 lastModified / size 查询，供指纹缓存 freshness 判断。
 */
object AudioFingerprintUtils {

    /** 文件修改时间；提供方不可用时返回 0。 */
    fun lastModified(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).lastModified() } ?: 0L
        }
        if (uri.scheme == "content") {
            // 文档 URI 才有 COLUMN_LAST_MODIFIED；不支持该列的提供方返回 0（视为无法判断）
            return runCatching {
                val col = DocumentsContract.Document.COLUMN_LAST_MODIFIED
                context.contentResolver.query(uri, arrayOf(col), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
                } ?: 0L
            }.getOrDefault(0L)
        }
        return 0L
    }

    /** 文件大小（字节）；提供方不可用时返回 0。 */
    fun fileSize(context: Context, uri: Uri): Long = queryLong(context, uri, OpenableColumns.SIZE)

    /** 解码 + 计算指纹；文件无法解码时返回 null（调用方按无指纹处理）。 */
    fun computeFingerprint(context: Context, uri: Uri): IntArray? {
        val pcm = decodePcm(context, uri) ?: return null
        return Chromaprint.computeFingerprint(pcm)
    }

    /** MediaExtractor + MediaCodec 解码为单声道 11025Hz int16 PCM。 */
    fun decodePcm(context: Context, uri: Uri): ShortArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrack = i
                    break
                }
            }
            if (audioTrack < 0) return null
            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            var channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                1
            }
            if (sampleRate <= 0) sampleRate = 44100
            if (channelCount <= 0) channelCount = 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val acc = ShortAccumulator()
            val info = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            var guard = 0

            while (!outputEos && guard++ < 10_000_000) {
                if (!inputEos) {
                    val size = extractor.sampleSize
                    if (size <= 0) {
                        val idx = codec.dequeueInputBuffer(50_000)
                        if (idx >= 0) {
                            codec.getInputBuffer(idx)!!.clear()
                            codec.queueInputBuffer(
                                idx, 0, 0, extractor.sampleTime,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEos = true
                        }
                    } else {
                        val idx = codec.dequeueInputBuffer(50_000)
                        if (idx >= 0) {
                            val b = codec.getInputBuffer(idx)!!
                            b.clear()
                            val got = extractor.readSampleData(b, 0)
                            if (got > 0) {
                                codec.queueInputBuffer(idx, 0, got, extractor.sampleTime, 0)
                                extractor.advance()
                            } else {
                                // 无法读出的样本直接跳过，防止死循环
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 50_000)
                when {
                    outIdx >= 0 -> {
                        if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            val out = codec.getOutputBuffer(outIdx)!!
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val frames = info.size / 2 / channelCount
                            if (frames > 0) {
                                if (channelCount == 1) {
                                    acc.addAll(out, frames)
                                } else {
                                    for (f in 0 until frames) {
                                        var sum = 0L
                                        repeat(channelCount) { sum += out.getShort().toLong() }
                                        acc.add((sum / channelCount).toShort())
                                    }
                                }
                            }
                        }
                        val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outIdx, false)
                        if (eos) outputEos = true
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (channelCount <= 0) channelCount = 1
                    }
                    else -> {
                        if (inputEos) Thread.sleep(2)
                    }
                }
            }

            val mono = acc.toArray()
            return if (sampleRate == Chromaprint.SAMPLE_RATE) mono
            else resample(mono, sampleRate, Chromaprint.SAMPLE_RATE)
        } catch (t: Throwable) {
            return null
        } finally {
            codec?.stop()
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** 线性插值重采样（与桌面验证所用的实现一致）。 */
    fun resample(source: ShortArray, inRate: Int, outRate: Int): ShortArray {
        if (inRate == outRate || source.isEmpty()) return source
        val outLen = source.size.toLong() * outRate / inRate
        if (outLen == 0L) return ShortArray(0)
        val out = ShortArray(outLen.toInt())
        val ratio = inRate.toDouble() / outRate.toDouble()
        for (o in out.indices) {
            val pos = o * ratio
            val i = pos.toInt()
            val frac = pos - i
            val s0 = source[i].toInt()
            val s1 = if (i + 1 < source.size) source[i + 1].toInt() else s0
            out[o] = (s0 + ((s1 - s0) * frac).toInt()).toShort()
        }
        return out
    }

    private fun queryLong(context: Context, uri: Uri, column: String): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    /** 免装箱增长型 int16 缓冲区。 */
    private class ShortAccumulator {
        private val blocks = ArrayList<ShortArray>()
        private var cur = ShortArray(BLOCK_SIZE)
        private var curLen = 0
        private var total = 0L

        fun add(v: Short) {
            if (curLen == cur.size) {
                blocks.add(cur)
                cur = ShortArray(BLOCK_SIZE)
                curLen = 0
            }
            cur[curLen++] = v
            total++
        }

        /** 追加缓冲区中的前 [count] 个 int16（单声道直通）。 */
        fun addAll(buf: java.nio.ByteBuffer, count: Int) {
            var remaining = count
            var need: Int
            while (remaining > 0) {
                if (curLen == cur.size) {
                    blocks.add(cur)
                    cur = ShortArray(BLOCK_SIZE)
                    curLen = 0
                }
                need = minOf(remaining, cur.size - curLen)
                var i = 0
                while (i < need) {
                    cur[curLen + i] = buf.short
                    i++
                }
                curLen += need
                remaining -= need
                total += need
            }
        }

        fun toArray(): ShortArray {
            val out = ShortArray(total.toInt())
            var p = 0
            for (b in blocks) {
                System.arraycopy(b, 0, out, p, b.size)
                p += b.size
            }
            if (curLen > 0) System.arraycopy(cur, 0, out, p, curLen)
            return out
        }

        private companion object {
            const val BLOCK_SIZE = 4096
        }
    }
}