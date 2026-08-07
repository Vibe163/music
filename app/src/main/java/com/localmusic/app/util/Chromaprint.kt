package com.localmusic.app.util

import java.util.Arrays
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Faithful Kotlin port of the AcoustID chromaprint default fingerprint algorithm
 * (Test1 config, frame grid = k * hop on mono int16 @ 11025 Hz).
 *
 * Verified on desktop against fpcalc 1.6.1: identical audio content (volume change,
 * stereo→mono, resample 44100→11025) yields avg hamming distance 0.0; different
 * content yields ~15–17/32 bits. Source: acoustid/chromaprint (MIT).
 */
object Chromaprint {

    const val SAMPLE_RATE = 11025
    const val FRAME_SIZE = 4096
    private const val FRAME_HOP = FRAME_SIZE / 3 // 1365
    private const val MIN_FREQ = 28
    private const val MAX_FREQ = 3520
    private const val NUM_BANDS = 12

    // 匹配参数：滑动对齐 ±24 帧（±3s），交叠区平均汉明距离阈值
    const val MAX_ALIGN_OFFSET = 24
    private const val MIN_OVERLAP_FRAMES = 18
    private const val MAX_AVG_HD = 8.0 // 32 位子指纹平均不一致位数（同内容≈0-3，不同内容≈15+）

    private val FILTER_COEFFS = doubleArrayOf(0.25, 0.75, 1.0, 0.75, 0.25)

    private val F_T = intArrayOf(0, 1, 1, 3, 2, 5, 1, 3, 3, 2, 3, 2, 3, 5, 5, 2)
    private val F_Y = intArrayOf(0, 0, 4, 0, 8, 6, 9, 4, 9, 1, 3, 8, 4, 4, 9, 1)
    private val F_H = intArrayOf(3, 4, 4, 4, 2, 2, 2, 2, 2, 3, 6, 1, 4, 2, 2, 8)
    private val F_W = intArrayOf(15, 14, 11, 14, 4, 15, 16, 10, 16, 6, 2, 10, 14, 15, 3, 4)
    private val Q_T0 = doubleArrayOf(2.10543, -0.345922, -0.392132, -0.192851, -0.0771619, -0.710437, -0.353724, -0.128418, -0.139052, -0.133562, -0.0267, -0.0972417, -0.141434, -0.64035, -0.322792, -0.0741375)
    private val Q_T1 = doubleArrayOf(2.45354, 0.0463746, 0.0291077, 0.00583535, -0.00991999, -0.518954, -0.0189719, -0.0285697, -0.0228468, 0.00669205, 0.00804829, 0.0152227, 0.00374515, -0.466999, -0.254258, -0.00590933)
    private val Q_T2 = doubleArrayOf(2.69414, 0.446251, 0.443391, 0.204053, 0.0575406, -0.330402, 0.289768, 0.0591791, 0.0879723, 0.155012, 0.0459773, 0.129003, 0.149935, -0.285493, -0.174278, 0.0600357)
    private val GRAY_CODE = byteArrayOf(0, 1, 3, 2)

    /** mono int16 @ 11025 Hz → 指纹（每个 int 为 32 位子指纹，按产生顺序排列）。 */
    fun computeFingerprint(pcm: ShortArray): IntArray {
        val window = DoubleArray(FRAME_SIZE)
        for (i in 0 until FRAME_SIZE) {
            window[i] = (1.0 / 32768.0) * (0.54 - 0.46 * cos(2.0 * PI * i / (FRAME_SIZE - 1)))
        }
        val fft = Fft(FRAME_SIZE)
        val frameIn = DoubleArray(FRAME_SIZE)
        val framePower = DoubleArray(FRAME_SIZE / 2 + 1)

        val note = IntArray(FRAME_SIZE)
        val minIndex = max(1, round(FRAME_SIZE * MIN_FREQ / SAMPLE_RATE.toDouble()).toInt())
        val maxIndex = min(FRAME_SIZE / 2, round(FRAME_SIZE * MAX_FREQ / SAMPLE_RATE.toDouble()).toInt())
        for (i in minIndex until maxIndex) {
            val freq = i * SAMPLE_RATE.toDouble() / FRAME_SIZE
            val octave = ln(freq / (440.0 / 16.0)) / ln(2.0)
            note[i] = (NUM_BANDS * (octave - floor(octave))).toInt()
        }

        val image = RollingIntegralImage(256)
        val ring = Array(5) { DoubleArray(NUM_BANDS) }
        var chromaCount = 0
        val fp = ArrayList<Int>()

        val n = pcm.size
        var start = 0
        while (start + FRAME_SIZE <= n) {
            for (i in 0 until FRAME_SIZE) frameIn[i] = pcm[start + i].toDouble() * window[i]
            fft.forwardPower(frameIn, framePower)

            val feats = ring[chromaCount % 5]
            Arrays.fill(feats, 0.0)
            for (i in minIndex until maxIndex) feats[note[i]] += framePower[i]
            chromaCount++

            if (chromaCount >= 5) {
                val f = DoubleArray(NUM_BANDS)
                val base = chromaCount - 5
                for (j in 0 until 5) {
                    val c = ring[(base + j) % 5]
                    val co = FILTER_COEFFS[j]
                    for (b in 0 until NUM_BANDS) f[b] += c[b] * co
                }
                var norm = 0.0
                for (b in 0 until NUM_BANDS) norm += f[b] * f[b]
                norm = sqrt(norm)
                if (norm < 0.01) {
                    Arrays.fill(f, 0.0)
                } else {
                    for (b in 0 until NUM_BANDS) f[b] /= norm
                }
                image.addRow(f)
                if (image.numRows >= 16) {
                    fp.add(subfingerprint(image, image.numRows - 16))
                }
            }
            start += FRAME_HOP
        }
        return fp.toIntArray()
    }

    /**
     * 两个指纹是否视为同一音频：
     *  在 ±MAX_ALIGN_OFFSET 帧内滑动对齐，取交叠 ≥ MIN_OVERLAP_FRAMES 的窗口，
     *  平均汉明距离（每个 32 bit 子指纹）≤ MAX_AVG_HD 即判重复。
     */
    fun isSimilar(a: IntArray, b: IntArray): Boolean {
        val shorter = if (a.size <= b.size) a else b
        val longer = if (a.size <= b.size) b else a
        for (off in -MAX_ALIGN_OFFSET..MAX_ALIGN_OFFSET) {
            val lo = max(0, off)
            val hi = min(shorter.size, longer.size + off)
            val n = hi - lo
            if (n < MIN_OVERLAP_FRAMES) continue
            var bits = 0
            for (i in lo until hi) {
                bits += Integer.bitCount(shorter[i] xor longer[i - off])
                if (bits > n * MAX_AVG_HD) break // 预算已超，无法达标
            }
            if (bits <= n * MAX_AVG_HD) return true
        }
        return false
    }

    /** 子指纹序列 → BLOB（4 字节小端）。 */
    fun encode(fp: IntArray): ByteArray {
        val out = ByteArray(fp.size * 4)
        var p = 0
        for (x in fp) {
            out[p] = (x and 0xFF).toByte()
            out[p + 1] = ((x ushr 8) and 0xFF).toByte()
            out[p + 2] = ((x ushr 16) and 0xFF).toByte()
            out[p + 3] = ((x ushr 24) and 0xFF).toByte()
            p += 4
        }
        return out
    }

    /** BLOB → 子指纹序列。 */
    fun decode(bytes: ByteArray): IntArray {
        val n = bytes.size / 4
        val out = IntArray(n)
        var p = 0
        for (i in 0 until n) {
            out[i] = (bytes[p].toInt() and 0xFF) or
                ((bytes[p + 1].toInt() and 0xFF) shl 8) or
                ((bytes[p + 2].toInt() and 0xFF) shl 16) or
                ((bytes[p + 3].toInt() and 0xFF) shl 24)
            p += 4
        }
        return out
    }

    private fun subfingerprint(img: RollingIntegralImage, offset: Int): Int {
        var bits = 0
        for (i in 0 until 16) {
            val v = applyFilter(F_T[i], F_Y[i], F_H[i], F_W[i], img, offset)
            val q = if (v < Q_T1[i]) (if (v < Q_T0[i]) 0 else 1) else (if (v < Q_T2[i]) 2 else 3)
            bits = (bits shl 2) or GRAY_CODE[q].toInt()
        }
        return bits
    }

    private fun applyFilter(type: Int, y: Int, h: Int, w: Int, img: RollingIntegralImage, x: Int): Double {
        return when (type) {
            0 -> subLog(area(img, x, y, x + w, y + h), 0.0)
            1 -> {
                val h2 = h / 2
                subLog(area(img, x, y + h2, x + w, y + h), area(img, x, y, x + w, y + h2))
            }
            2 -> {
                val w2 = w / 2
                subLog(area(img, x + w2, y, x + w, y + h), area(img, x, y, x + w2, y + h))
            }
            3 -> {
                val w2 = w / 2
                val h2 = h / 2
                subLog(
                    area(img, x, y + h2, x + w2, y + h) + area(img, x + w2, y, x + w, y + h2),
                    area(img, x, y, x + w2, y + h2) + area(img, x + w2, y + h2, x + w, y + h)
                )
            }
            4 -> {
                val h3 = h / 3
                subLog(
                    area(img, x, y + h3, x + w, y + 2 * h3),
                    area(img, x, y, x + w, y + h3) + area(img, x, y + 2 * h3, x + w, y + h)
                )
            }
            5 -> {
                val w3 = w / 3
                subLog(
                    area(img, x + w3, y, x + 2 * w3, y + h),
                    area(img, x, y, x + w3, y + h) + area(img, x + 2 * w3, y, x + w, y + h)
                )
            }
            else -> 0.0
        }
    }

    private fun subLog(a: Double, b: Double) = ln((1.0 + a) / (1.0 + b))

    private fun area(img: RollingIntegralImage, r1: Int, c1: Int, r2: Int, c2: Int) = img.area(r1, c1, r2, c2)

    /** 位反转蝶形 FFT（radix-2），输出实部 bin 的功率谱 power[0..n/2]。 */
    private class Fft(private val n: Int) {
        private val re = DoubleArray(n)
        private val im = DoubleArray(n)
        private val rev = IntArray(n)

        init {
            val bits = Integer.numberOfTrailingZeros(n)
            for (i in 0 until n) {
                var r = 0
                var x = i
                repeat(bits) { r = (r shl 1) or (x and 1); x = x ushr 1 }
                rev[i] = r
            }
        }

        fun forwardPower(input: DoubleArray, power: DoubleArray) {
            for (i in 0 until n) { re[i] = input[rev[i]]; im[i] = 0.0 }
            var len = 2
            while (len <= n) {
                val angle = -2.0 * PI / len
                val wr = cos(angle); val wi = sin(angle)
                var i = 0
                while (i < n) {
                    var cr = 1.0; var ci = 0.0
                    val m = len shr 1
                    var j = 0
                    while (j < m) {
                        val a = i + j; val b2 = i + j + m
                        val br = re[b2]; val bi = im[b2]
                        val tr = cr * br - ci * bi
                        val ti = cr * bi + ci * br
                        re[b2] = re[a] - tr
                        im[b2] = im[a] - ti
                        re[a] += tr
                        im[a] += ti
                        val nwr = cr * wr - ci * wi
                        ci = cr * wi + ci * wr
                        cr = nwr
                        j++
                    }
                    i += len
                }
                len = len shl 1
            }
            for (i in 0..n / 2) power[i] = re[i] * re[i] + im[i] * im[i]
        }
    }

    /** 累计积分图像（等价于 chromaprint 的 RollingIntegralImage）。 */
    private class RollingIntegralImage(@Suppress("unused") maxRows: Int) {
        private val rows = ArrayList<DoubleArray>()
        private var numCols = 0
        var numRows = 0
            private set

        fun addRow(row: DoubleArray) {
            if (numCols == 0) numCols = row.size
            val cur = DoubleArray(numCols)
            var acc = 0.0
            for (c in 0 until numCols) { acc += row[c]; cur[c] = acc }
            if (numRows > 0) {
                val prev = rows[numRows - 1]
                for (c in 0 until numCols) cur[c] += prev[c]
            }
            rows.add(cur)
            numRows++
        }

        fun area(r1: Int, c1: Int, r2: Int, c2: Int): Double {
            if (r1 == r2 || c1 == c2) return 0.0
            val row2 = rows[r2 - 1]
            if (r1 == 0) return if (c1 == 0) row2[c2 - 1] else row2[c2 - 1] - row2[c1 - 1]
            val row1 = rows[r1 - 1]
            if (c1 == 0) return row2[c2 - 1] - row1[c2 - 1]
            return row2[c2 - 1] - row1[c2 - 1] - row2[c1 - 1] + row1[c1 - 1]
        }
    }
}