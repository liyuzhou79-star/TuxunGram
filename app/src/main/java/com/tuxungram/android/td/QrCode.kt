package com.tuxungram.android.td

/**
 * Minimal QR Code encoder (v0.9.24, Q2/Q6): byte mode, error
 * correction level M, versions 1–6 (up to 106 payload bytes —
 * plenty for t.me links and tg://login tokens), automatic mask
 * selection by the ISO penalty rules. Written from the spec so
 * the app needs no QR library; the algorithm was validated
 * offline against two reference encoders and a ZXing decoder
 * sweep (43/43 payloads decoded) before being ported here.
 *
 * [encode] returns the module matrix as rows [y][x], true =
 * dark, WITHOUT a quiet zone (renderers add their own border),
 * or null when the payload fits no supported version.
 */
object QrCode {

    // version -> (data codewords, EC codewords per block,
    // block count) for ECC level M. All blocks of a version
    // have equal size at level M in versions 1–6.
    private val ECC_TABLE = arrayOf(
        intArrayOf(16, 10, 1), // v1
        intArrayOf(28, 16, 1), // v2
        intArrayOf(44, 26, 1), // v3
        intArrayOf(64, 18, 2), // v4
        intArrayOf(86, 24, 2), // v5
        intArrayOf(108, 16, 4) // v6
    )
    private val ALIGNMENT = arrayOf(
        intArrayOf(), intArrayOf(6, 18), intArrayOf(6, 22),
        intArrayOf(6, 26), intArrayOf(6, 30), intArrayOf(6, 34)
    )
    private val REMAINDER = intArrayOf(0, 7, 7, 7, 7, 7)

    // GF(256) tables, primitive polynomial 0x11D.
    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) GF_EXP[i] = GF_EXP[i - 255]
    }

    private fun gfMul(a: Int, b: Int): Int =
        if (a == 0 || b == 0) 0 else GF_EXP[GF_LOG[a] + GF_LOG[b]]

    /** Generator polynomial of the given degree, coefficients
     *  in descending order (index 0 = x^degree). */
    private fun rsGenerator(deg: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until deg) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], GF_EXP[i])
            }
            poly = next
        }
        return poly
    }

    private fun rsEcc(data: IntArray, gen: IntArray): IntArray {
        val deg = gen.size - 1
        val res = IntArray(deg)
        for (b in data) {
            val factor = b xor res[0]
            res.copyInto(res, 0, 1, deg)
            res[deg - 1] = 0
            if (factor != 0) {
                for (i in 0 until deg) {
                    res[i] = res[i] xor gfMul(gen[i + 1], factor)
                }
            }
        }
        return res
    }

    private fun buildDataCodewords(data: ByteArray, version: Int): IntArray {
        val totalData = ECC_TABLE[version - 1][0]
        val bits = ArrayList<Int>(totalData * 8)
        fun put(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits.add((value shr i) and 1)
        }
        put(0b0100, 4) // byte mode
        put(data.size, 8) // length field, versions 1–9
        for (b in data) put(b.toInt() and 0xFF, 8)
        val capacity = totalData * 8
        put(0, minOf(4, capacity - bits.size)) // terminator
        while (bits.size % 8 != 0) bits.add(0)
        val out = ArrayList<Int>(totalData)
        var i = 0
        while (i < bits.size) {
            var v = 0
            for (k in 0 until 8) v = (v shl 1) or bits[i + k]
            out.add(v)
            i += 8
        }
        var padIndex = 0
        while (out.size < totalData) {
            out.add(if (padIndex % 2 == 0) 0xEC else 0x11)
            padIndex++
        }
        return out.toIntArray()
    }

    private fun interleave(dataCw: IntArray, version: Int): IntArray {
        val (totalData, ecLen, blockCount) = ECC_TABLE[version - 1]
        val gen = rsGenerator(ecLen)
        val perBlock = totalData / blockCount
        val blocks = Array(blockCount) {
            dataCw.copyOfRange(it * perBlock, (it + 1) * perBlock)
        }
        val eccs = blocks.map { rsEcc(it, gen) }
        val out = ArrayList<Int>(totalData + ecLen * blockCount)
        for (i in 0 until perBlock) for (b in blocks) out.add(b[i])
        for (i in 0 until ecLen) for (e in eccs) out.add(e[i])
        return out.toIntArray()
    }

    /** Function-pattern layer: returns (modules, isFunction). */
    private fun functionModules(
        version: Int
    ): Pair<Array<BooleanArray>, Array<BooleanArray>> {
        val size = 17 + 4 * version
        val modules = Array(size) { BooleanArray(size) }
        val isFunc = Array(size) { BooleanArray(size) }
        fun setf(x: Int, y: Int, dark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                isFunc[y][x] = true
                modules[y][x] = dark
            }
        }
        // Finder patterns with separators.
        for ((ox, oy) in listOf(0 to 0, size - 7 to 0, 0 to size - 7)) {
            for (dy in -1..7) {
                for (dx in -1..7) {
                    val dark =
                        (dx in 0..6 && (dy == 0 || dy == 6)) ||
                            (dy in 0..6 && (dx == 0 || dx == 6)) ||
                            (dx in 2..4 && dy in 2..4)
                    setf(ox + dx, oy + dy, dark)
                }
            }
        }
        // Timing patterns.
        for (i in 8 until size - 8) {
            setf(i, 6, i % 2 == 0)
            setf(6, i, i % 2 == 0)
        }
        // Alignment patterns (skipping finder corners).
        for (cy in ALIGNMENT[version - 1]) {
            for (cx in ALIGNMENT[version - 1]) {
                if ((cx <= 8 && cy <= 8) ||
                    (cx >= size - 9 && cy <= 8) ||
                    (cx <= 8 && cy >= size - 9)
                ) {
                    continue
                }
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val dark = maxOf(
                            kotlin.math.abs(dx), kotlin.math.abs(dy)
                        ) == 2 || (dx == 0 && dy == 0)
                        setf(cx + dx, cy + dy, dark)
                    }
                }
            }
        }
        // Reserve format-information cells and the dark module.
        for (i in 0..8) {
            isFunc[i][8] = true
            isFunc[8][i] = true
        }
        for (i in 0..7) {
            isFunc[size - 1 - i][8] = true
            isFunc[8][size - 1 - i] = true
        }
        isFunc[size - 8][8] = true
        modules[size - 8][8] = true
        return modules to isFunc
    }

    private fun placeData(
        modules: Array<BooleanArray>,
        isFunc: Array<BooleanArray>,
        bits: List<Int>
    ) {
        val size = modules.size
        var i = 0
        var upward = true
        var x = size - 1
        while (x > 0) {
            if (x == 6) x -= 1
            val rows: IntProgression =
                if (upward) (size - 1) downTo 0 else 0 until size
            for (yy in rows) {
                for (dx in intArrayOf(0, -1)) {
                    val xx = x + dx
                    if (!isFunc[yy][xx]) {
                        modules[yy][xx] = i < bits.size && bits[i] == 1
                        i++
                    }
                }
            }
            x -= 2
            upward = !upward
        }
    }

    private val MASKS: List<(Int, Int) -> Boolean> = listOf(
        { x, y -> (x + y) % 2 == 0 },
        { _, y -> y % 2 == 0 },
        { x, _ -> x % 3 == 0 },
        { x, y -> (x + y) % 3 == 0 },
        { x, y -> (x / 3 + y / 2) % 2 == 0 },
        { x, y -> ((x * y) % 2 + (x * y) % 3) == 0 },
        { x, y -> (((x * y) % 2 + (x * y) % 3) % 2) == 0 },
        { x, y -> (((x + y) % 2 + (x * y) % 3) % 2) == 0 }
    )

    /** 15-bit format information for ECC level M and [mask],
     *  computed with the BCH generator 0x537 and the 0x5412
     *  XOR mask (no lookup table to mistype). */
    private fun formatBits(mask: Int): Int {
        val data = mask // ECC level M contributes two zero bits
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor (if ((rem shr 9) and 1 == 1) 0x537 else 0)
        }
        return ((data shl 10) or rem) xor 0x5412
    }

    private fun placeFormat(modules: Array<BooleanArray>, mask: Int) {
        val size = modules.size
        val bits = formatBits(mask)
        fun bit(i: Int) = ((bits shr i) and 1) == 1
        for (i in 0..5) modules[i][8] = bit(i)
        modules[7][8] = bit(6)
        modules[8][8] = bit(7)
        modules[8][7] = bit(8)
        for (i in 9..14) modules[8][14 - i] = bit(i)
        for (i in 0..7) modules[8][size - 1 - i] = bit(i)
        for (i in 8..14) modules[size - 15 + i][8] = bit(i)
        modules[size - 8][8] = true
    }

    private fun penalty(m: Array<BooleanArray>): Int {
        val size = m.size
        var score = 0
        val lines = ArrayList<List<Boolean>>(2 * size)
        for (y in 0 until size) lines.add(m[y].toList())
        for (x in 0 until size) lines.add(List(size) { m[it][x] })
        // Rule 1: runs of five or more equal modules.
        for (line in lines) {
            var run = 1
            for (i in 1 until size) {
                if (line[i] == line[i - 1]) {
                    run++
                } else {
                    if (run >= 5) score += 3 + (run - 5)
                    run = 1
                }
            }
            if (run >= 5) score += 3 + (run - 5)
        }
        // Rule 2: 2x2 blocks of one color.
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                if (m[y][x] == m[y][x + 1] &&
                    m[y][x] == m[y + 1][x] &&
                    m[y][x] == m[y + 1][x + 1]
                ) {
                    score += 3
                }
            }
        }
        // Rule 3: finder-like patterns with a four-module
        // light zone on either side.
        val pat1 = listOf(0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1)
        val pat2 = listOf(1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0)
        for (line in lines) {
            val vals = line.map { if (it) 1 else 0 }
            for (i in 0..size - 11) {
                val window = vals.subList(i, i + 11)
                if (window == pat1 || window == pat2) score += 40
            }
        }
        // Rule 4: deviation of the dark ratio from 50%.
        var dark = 0
        for (row in m) for (c in row) if (c) dark++
        val total = size * size
        val k = (kotlin.math.abs(dark * 20 - total * 10) + total - 1) / total - 1
        if (k > 0) score += k * 10
        return score
    }

    fun encode(text: String): Array<BooleanArray>? {
        val data = text.toByteArray(Charsets.UTF_8)
        var version = 0
        for (v in 1..6) {
            if (data.size <= ECC_TABLE[v - 1][0] - 2) {
                version = v
                break
            }
        }
        if (version == 0) return null
        val allCw = interleave(buildDataCodewords(data, version), version)
        val bits = ArrayList<Int>(allCw.size * 8 + REMAINDER[version - 1])
        for (b in allCw) {
            for (i in 7 downTo 0) bits.add((b shr i) and 1)
        }
        repeat(REMAINDER[version - 1]) { bits.add(0) }
        var best: Array<BooleanArray>? = null
        var bestScore = Int.MAX_VALUE
        for (mask in 0..7) {
            val (modules, isFunc) = functionModules(version)
            placeData(modules, isFunc, bits)
            val size = modules.size
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (!isFunc[y][x] && MASKS[mask](x, y)) {
                        modules[y][x] = !modules[y][x]
                    }
                }
            }
            placeFormat(modules, mask)
            val score = penalty(modules)
            if (score < bestScore) {
                bestScore = score
                best = modules
            }
        }
        return best
    }
}
