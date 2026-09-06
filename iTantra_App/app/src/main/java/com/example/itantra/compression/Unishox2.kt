package com.example.itantra.compression

import java.io.ByteArrayOutputStream

/**
 * Pure Kotlin implementation of the Unishox2 short-string compression and decompression
 * algorithm (Arundale Ramanathan, Siara Logics).
 *
 * Characteristics:
 * - Designed specifically for short strings (< 1KB) where LZ77 / DEFLATE / GZIP expand.
 * - Employs variable bit length Huffman-style horizontal & vertical encoding.
 * - Includes delta encoding for Unicode code points (providing high compression ratios
 *   for Indian languages: Hindi, Bengali, Tamil, Telugu, Kannada, Malayalam, Marathi,
 *   Gujarati, Odia).
 * - Zero external native dependencies; 100% JVM unit-test compatible.
 */
object Unishox2 {

    private const val UNISHOX_MAGIC_BITS = 0x80
    private const val UNISHOX_MAGIC_BIT_LEN = 1

    private const val USX_ALPHA = 0
    private const val USX_SYM = 1
    private const val USX_NUM = 2
    private const val USX_DICT = 3
    private const val USX_DELTA = 4

    private val USX_HCODES_DFLT = intArrayOf(0x00, 0x40, 0x80, 0xC0, 0xE0)
    private val USX_HCODE_LENS_DFLT = intArrayOf(2, 2, 2, 3, 3)

    private val USX_FREQ_SEQ_DFLT = arrayOf("\": \"", "\": ", "</", "=\"", "\":\"", "://")
    private val USX_TEMPLATES = arrayOf("tfff-of-tfTtf:rf:rf.fffZ", "tfff-of-tf", "(fff) fff-ffff", "tf:rf:rf", "")

    private val USX_SETS = arrayOf(
        "\u0000 etaoinsrlcdhupmbgwfyvkqjxz",
        "\"{}_<>:\n\u0000[]\\;'\t@*&?!^|\r~`\u0000\u0000\u0000",
        "\u0000,.01925-/34678() =+$%#\u0000\u0000\u0000\u0000\u0000"
    )

    private val USX_VCODES = intArrayOf(
        0x00, 0x40, 0x60, 0x80, 0x90, 0xA0, 0xB0,
        0xC0, 0xD0, 0xD8, 0xE0, 0xE4, 0xE8, 0xEC,
        0xEE, 0xF0, 0xF2, 0xF4, 0xF6, 0xF7, 0xF8,
        0xF9, 0xFA, 0xFB, 0xFC, 0xFD, 0xFE, 0xFF
    )

    private val USX_VCODE_LENS = intArrayOf(
        2, 3, 3, 4, 4, 4, 4,
        4, 5, 5, 6, 6, 6, 7,
        7, 7, 7, 7, 8, 8, 8,
        8, 8, 8, 8, 8, 8, 8
    )

    private val USX_FREQ_CODES = intArrayOf(
        (1 shl 5) + 25, (1 shl 5) + 26, (1 shl 5) + 27,
        (2 shl 5) + 23, (2 shl 5) + 24, (2 shl 5) + 25
    )

    private const val NICE_LEN = 5
    private const val RPT_CODE = (2 shl 5) + 26
    private const val TERM_CODE = (2 shl 5) + 27
    private const val LF_CODE = (1 shl 5) + 7
    private const val CRLF_CODE = (1 shl 5) + 8
    private const val CR_CODE = (1 shl 5) + 22
    private const val TAB_CODE = (1 shl 5) + 14
    private const val NUM_SPC_CODE = (2 shl 5) + 17

    private const val UNI_STATE_SPL_CODE = 0xF8
    private const val UNI_STATE_SPL_CODE_LEN = 5
    private const val UNI_STATE_SW_CODE = 0x80
    private const val UNI_STATE_SW_CODE_LEN = 2

    private const val SW_CODE = 0
    private const val SW_CODE_LEN = 2

    private const val TERM_BYTE_PRESET_1 = 0
    private const val TERM_BYTE_PRESET_1_LEN_LOWER = 6
    private const val TERM_BYTE_PRESET_1_LEN_UPPER = 4

    private const val USX_OFFSET_94 = 33

    private val USX_CODE_94 = IntArray(94)

    private val COUNT_BIT_LENS = intArrayOf(2, 4, 7, 11, 16)
    private val COUNT_ADDER = intArrayOf(4, 20, 148, 2196, 67732)
    private val COUNT_CODES = intArrayOf(0x01, 0x82, 0xC3, 0xE4, 0xF4)

    private val UNI_BIT_LEN = intArrayOf(6, 12, 14, 16, 21)
    private val UNI_ADDER = intArrayOf(0, 64, 4160, 20544, 86080)

    private val USX_MASK = intArrayOf(0x80, 0xC0, 0xE0, 0xF0, 0xF8, 0xFC, 0xFE, 0xFF)

    private const val SECTION_COUNT = 5
    private val USX_VSECTIONS = intArrayOf(0x7F, 0xBF, 0xDF, 0xEF, 0xFF)
    private val USX_VSECTION_POS = intArrayOf(0, 4, 8, 12, 20)
    private val USX_VSECTION_MASK = intArrayOf(0x7F, 0x3F, 0x1F, 0x0F, 0x0F)
    private val USX_VSECTION_SHIFT = intArrayOf(5, 4, 3, 1, 0)

    private val USX_VCODE_LOOKUP = intArrayOf(
        (1 shl 5) + 0, (1 shl 5) + 0, (2 shl 5) + 1, (2 shl 5) + 2,
        (3 shl 5) + 3, (3 shl 5) + 4, (3 shl 5) + 5, (3 shl 5) + 6,
        (3 shl 5) + 7, (3 shl 5) + 7, (4 shl 5) + 8, (4 shl 5) + 9,
        (5 shl 5) + 10, (5 shl 5) + 10, (5 shl 5) + 11, (5 shl 5) + 11,
        (5 shl 5) + 12, (5 shl 5) + 12, (6 shl 5) + 13, (6 shl 5) + 14,
        (6 shl 5) + 15, (6 shl 5) + 15, (6 shl 5) + 16, (6 shl 5) + 16,
        (6 shl 5) + 17, (6 shl 5) + 17, (7 shl 5) + 18, (7 shl 5) + 19,
        (7 shl 5) + 20, (7 shl 5) + 21, (7 shl 5) + 22, (7 shl 5) + 23,
        (7 shl 5) + 24, (7 shl 5) + 25, (7 shl 5) + 26, (7 shl 5) + 27
    )

    private val USX_SPL_CODE = intArrayOf(0, 0xE0, 0xC0, 0xF0)
    private val USX_SPL_CODE_LEN = intArrayOf(1, 4, 3, 4)

    private const val USX_NIB_NUM = 0
    private const val USX_NIB_HEX_LOWER = 1
    private const val USX_NIB_HEX_UPPER = 2
    private const val USX_NIB_NOT = 3

    init {
        for (i in 0 until 3) {
            for (j in 0 until 28) {
                val c = USX_SETS[i][j].code
                if (c > 32) {
                    USX_CODE_94[c - USX_OFFSET_94] = (i shl 5) + j
                    if (c in 97..122) { // 'a'..'z'
                        USX_CODE_94[c - USX_OFFSET_94 - (97 - 65)] = (i shl 5) + j
                    }
                }
            }
        }
    }

    private fun appendBits(out: ByteArray, olen: Int, olStart: Int, codeStart: Int, clenStart: Int): Int {
        var ol = olStart
        var code = codeStart and 0xFF
        var clen = clenStart
        while (clen > 0) {
            val curBit = ol % 8
            var blen = clen
            val aByte = (code and USX_MASK[blen - 1]) ushr curBit
            if (blen + curBit > 8) {
                blen = 8 - curBit
            }
            val oidx = ol ushr 3
            if (oidx < 0 || oidx >= olen) return -1
            if (curBit == 0) {
                out[oidx] = aByte.toByte()
            } else {
                out[oidx] = (out[oidx].toInt() or aByte).toByte()
            }
            code = (code shl blen) and 0xFF
            ol += blen
            clen -= blen
        }
        return ol
    }

    private fun appendSwitchCode(out: ByteArray, olen: Int, olStart: Int, state: Int): Int {
        var ol = olStart
        if (state == USX_DELTA) {
            ol = appendBits(out, olen, ol, UNI_STATE_SPL_CODE, UNI_STATE_SPL_CODE_LEN)
            if (ol < 0) return -1
            ol = appendBits(out, olen, ol, UNI_STATE_SW_CODE, UNI_STATE_SW_CODE_LEN)
            if (ol < 0) return -1
        } else {
            ol = appendBits(out, olen, ol, SW_CODE, SW_CODE_LEN)
            if (ol < 0) return -1
        }
        return ol
    }

    private fun appendCode(
        out: ByteArray,
        olen: Int,
        olStart: Int,
        code: Int,
        stateHolder: IntArray,
        usxHcodes: IntArray,
        usxHcodeLens: IntArray
    ): Int {
        var ol = olStart
        val hcode = code ushr 5
        val vcode = code and 0x1F
        if (usxHcodeLens[hcode] == 0 && hcode != USX_ALPHA) {
            return ol
        }
        when (hcode) {
            USX_ALPHA -> {
                if (stateHolder[0] != USX_ALPHA) {
                    ol = appendSwitchCode(out, olen, ol, stateHolder[0])
                    if (ol < 0) return -1
                    ol = appendBits(out, olen, ol, usxHcodes[USX_ALPHA], usxHcodeLens[USX_ALPHA])
                    if (ol < 0) return -1
                    stateHolder[0] = USX_ALPHA
                }
            }
            USX_SYM -> {
                ol = appendSwitchCode(out, olen, ol, stateHolder[0])
                if (ol < 0) return -1
                ol = appendBits(out, olen, ol, usxHcodes[USX_SYM], usxHcodeLens[USX_SYM])
                if (ol < 0) return -1
            }
            USX_NUM -> {
                if (stateHolder[0] != USX_NUM) {
                    ol = appendSwitchCode(out, olen, ol, stateHolder[0])
                    if (ol < 0) return -1
                    ol = appendBits(out, olen, ol, usxHcodes[USX_NUM], usxHcodeLens[USX_NUM])
                    if (ol < 0) return -1
                    val charInNum = USX_SETS[hcode][vcode]
                    if (charInNum in '0'..'9') {
                        stateHolder[0] = USX_NUM
                    }
                }
            }
        }
        ol = appendBits(out, olen, ol, USX_VCODES[vcode], USX_VCODE_LENS[vcode])
        return ol
    }

    private fun encodeCount(out: ByteArray, olen: Int, olStart: Int, count: Int): Int {
        var ol = olStart
        for (i in 0 until 5) {
            if (count < COUNT_ADDER[i]) {
                ol = appendBits(out, olen, ol, COUNT_CODES[i] and 0xF8, COUNT_CODES[i] and 0x07)
                if (ol < 0) return -1
                val count16 = (count - (if (i > 0) COUNT_ADDER[i - 1] else 0)) shl (16 - COUNT_BIT_LENS[i])
                if (COUNT_BIT_LENS[i] > 8) {
                    ol = appendBits(out, olen, ol, count16 ushr 8, 8)
                    if (ol < 0) return -1
                    ol = appendBits(out, olen, ol, count16 and 0xFF, COUNT_BIT_LENS[i] - 8)
                    if (ol < 0) return -1
                } else {
                    ol = appendBits(out, olen, ol, count16 ushr 8, COUNT_BIT_LENS[i])
                    if (ol < 0) return -1
                }
                return ol
            }
        }
        return ol
    }

    private fun encodeUnicode(out: ByteArray, olen: Int, olStart: Int, code: Int, prevCode: Int): Int {
        val codes = intArrayOf(0x01, 0x82, 0xC3, 0xE4, 0xF5, 0xFD)
        var till = 0
        var diff = code - prevCode
        if (diff < 0) diff = -diff
        var ol = olStart
        for (i in 0 until 5) {
            till += (1 shl UNI_BIT_LEN[i])
            if (diff < till) {
                ol = appendBits(out, olen, ol, codes[i] and 0xF8, codes[i] and 0x07)
                if (ol < 0) return -1
                ol = appendBits(out, olen, ol, if (prevCode > code) 0x80 else 0, 1)
                if (ol < 0) return -1
                var v = diff - UNI_ADDER[i]
                if (UNI_BIT_LEN[i] > 16) {
                    v = v shl (24 - UNI_BIT_LEN[i])
                    ol = appendBits(out, olen, ol, v ushr 16, 8)
                    if (ol < 0) return -1
                    ol = appendBits(out, olen, ol, (v ushr 8) and 0xFF, 8)
                    if (ol < 0) return -1
                    ol = appendBits(out, olen, ol, v and 0xFF, UNI_BIT_LEN[i] - 16)
                    if (ol < 0) return -1
                } else if (UNI_BIT_LEN[i] > 8) {
                    v = v shl (16 - UNI_BIT_LEN[i])
                    ol = appendBits(out, olen, ol, v ushr 8, 8)
                    if (ol < 0) return -1
                    ol = appendBits(out, olen, ol, v and 0xFF, UNI_BIT_LEN[i] - 8)
                    if (ol < 0) return -1
                } else {
                    v = v shl (8 - UNI_BIT_LEN[i])
                    ol = appendBits(out, olen, ol, v and 0xFF, UNI_BIT_LEN[i])
                    if (ol < 0) return -1
                }
                return ol
            }
        }
        return ol
    }

    private fun readUTF8(input: ByteArray, len: Int, l: Int, utf8len: IntArray): Int {
        val b0 = input[l].toInt() and 0xFF
        if (l < len - 1 && (b0 and 0xE0) == 0xC0 && (input[l + 1].toInt() and 0xC0) == 0x80) {
            utf8len[0] = 2
            var ret = (b0 and 0x1F) shl 6
            ret += (input[l + 1].toInt() and 0x3F)
            return if (ret < 0x80) 0 else ret
        }
        if (l < len - 2 && (b0 and 0xF0) == 0xE0 && (input[l + 1].toInt() and 0xC0) == 0x80
            && (input[l + 2].toInt() and 0xC0) == 0x80) {
            utf8len[0] = 3
            var ret = (b0 and 0x0F) shl 6
            ret += (input[l + 1].toInt() and 0x3F)
            ret = ret shl 6
            ret += (input[l + 2].toInt() and 0x3F)
            return if (ret < 0x0800) 0 else ret
        }
        if (l < len - 3 && (b0 and 0xF8) == 0xF0 && (input[l + 1].toInt() and 0xC0) == 0x80
            && (input[l + 2].toInt() and 0xC0) == 0x80 && (input[l + 3].toInt() and 0xC0) == 0x80) {
            utf8len[0] = 4
            var ret = (b0 and 0x07) shl 6
            ret += (input[l + 1].toInt() and 0x3F)
            ret = ret shl 6
            ret += (input[l + 2].toInt() and 0x3F)
            ret = ret shl 6
            ret += (input[l + 3].toInt() and 0x3F)
            return if (ret < 0x10000) 0 else ret
        }
        return 0
    }

    private fun matchOccurance(
        input: ByteArray,
        len: Int,
        lStart: Int,
        out: ByteArray,
        olen: Int,
        olHolder: IntArray,
        stateHolder: IntArray,
        usxHcodes: IntArray,
        usxHcodeLens: IntArray
    ): Int {
        var j: Int
        var k: Int
        var longestDist = 0
        var longestLen = 0
        var l = lStart
        j = l - NICE_LEN
        while (j >= 0) {
            k = l
            while (k < len && (j + k - l) < l) {
                if (input[k] != input[j + k - l]) break
                k++
            }
            while (k < len && ((input[k].toInt() and 0xFF) ushr 6) == 2) {
                k-- // skip partial UTF-8
            }
            if ((k - l) > (NICE_LEN - 1)) {
                val matchLen = k - l - NICE_LEN
                val matchDist = l - j - NICE_LEN + 1
                if (matchLen > longestLen) {
                    longestLen = matchLen
                    longestDist = matchDist
                }
            }
            j--
        }
        if (longestLen > 0) {
            var ol = appendSwitchCode(out, olen, olHolder[0], stateHolder[0])
            if (ol < 0) return -1
            ol = appendBits(out, olen, ol, usxHcodes[USX_DICT], usxHcodeLens[USX_DICT])
            if (ol < 0) return -1
            ol = encodeCount(out, olen, ol, longestLen)
            if (ol < 0) return -1
            ol = encodeCount(out, olen, ol, longestDist)
            if (ol < 0) return -1
            olHolder[0] = ol
            l += (longestLen + NICE_LEN)
            l--
            return l
        }
        return -l
    }

    private fun getBaseCode(ch: Int): Int {
        if (ch in 48..57) return (ch - 48) shl 4
        if (ch in 65..70) return (ch - 65 + 10) shl 4
        if (ch in 97..102) return (ch - 97 + 10) shl 4
        return 0
    }

    private fun getNibbleType(ch: Int): Int {
        if (ch in 48..57) return USX_NIB_NUM
        if (ch in 97..102) return USX_NIB_HEX_LOWER
        if (ch in 65..70) return USX_NIB_HEX_UPPER
        return USX_NIB_NOT
    }

    private fun appendNibbleEscape(
        out: ByteArray,
        olen: Int,
        olStart: Int,
        state: Int,
        usxHcodes: IntArray,
        usxHcodeLens: IntArray
    ): Int {
        var ol = appendSwitchCode(out, olen, olStart, state)
        if (ol < 0) return -1
        ol = appendBits(out, olen, ol, usxHcodes[USX_NUM], usxHcodeLens[USX_NUM])
        if (ol < 0) return -1
        ol = appendBits(out, olen, ol, 0, 2)
        return ol
    }

    private fun appendFinalBits(
        out: ByteArray,
        olen: Int,
        olStart: Int,
        state: Int,
        isAllUpper: Boolean,
        usxHcodes: IntArray,
        usxHcodeLens: IntArray
    ): Int {
        var ol = olStart
        if (usxHcodeLens[USX_ALPHA] > 0) {
            if (state != USX_NUM) {
                ol = appendSwitchCode(out, olen, ol, state)
                if (ol < 0) return -1
                ol = appendBits(out, olen, ol, usxHcodes[USX_NUM], usxHcodeLens[USX_NUM])
                if (ol < 0) return -1
            }
            ol = appendBits(out, olen, ol, USX_VCODES[TERM_CODE and 0x1F], USX_VCODE_LENS[TERM_CODE and 0x1F])
            if (ol < 0) return -1
        } else {
            ol = appendBits(out, olen, ol, TERM_BYTE_PRESET_1, if (isAllUpper) TERM_BYTE_PRESET_1_LEN_UPPER else TERM_BYTE_PRESET_1_LEN_LOWER)
            if (ol < 0) return -1
        }
        val fillByte = if (ol == 0 || (out[(ol - 1) ushr 3].toInt() and (0x80 ushr ((ol - 1) % 8))) == 0) 0 else 0xFF
        ol = appendBits(out, olen, ol, fillByte, (8 - (ol % 8)) and 7)
        return ol
    }

    /**
     * Compresses the input byte array using Unishox2 algorithm.
     * If compression fails or expands, returns the original bytes or compressed bytes.
     */
    fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val len = input.size
        val maxOut = (len * 2) + 64
        val out = ByteArray(maxOut)

        val stateHolder = intArrayOf(USX_ALPHA)
        var ol = 0
        var prevUni = 0
        var isAllUpper = false

        ol = appendBits(out, maxOut, ol, UNISHOX_MAGIC_BITS, UNISHOX_MAGIC_BIT_LEN)
        if (ol < 0) return input

        var l = 0
        val utf8len = IntArray(1)
        val olHolder = IntArray(1)

        while (l < len) {
            if (USX_HCODE_LENS_DFLT[USX_DICT] > 0 && l < (len - NICE_LEN + 1)) {
                olHolder[0] = ol
                val matchRes = matchOccurance(
                    input, len, l, out, maxOut, olHolder, stateHolder,
                    USX_HCODES_DFLT, USX_HCODE_LENS_DFLT
                )
                if (matchRes > 0) {
                    ol = olHolder[0]
                    l = matchRes + 1
                    continue
                } else if (matchRes < 0 && olHolder[0] < 0) {
                    return input
                }
            }

            var cIn = input[l].toInt() and 0xFF

            if (l > 0 && len > 4 && l < len - 4 && USX_HCODE_LENS_DFLT[USX_NUM] > 0 && cIn <= 126) {
                if (cIn == (input[l - 1].toInt() and 0xFF) &&
                    cIn == (input[l + 1].toInt() and 0xFF) &&
                    cIn == (input[l + 2].toInt() and 0xFF) &&
                    cIn == (input[l + 3].toInt() and 0xFF)
                ) {
                    var rptCount = l + 4
                    while (rptCount < len && (input[rptCount].toInt() and 0xFF) == cIn) {
                        rptCount++
                    }
                    rptCount -= l
                    ol = appendCode(out, maxOut, ol, RPT_CODE, stateHolder, USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                    if (ol < 0) return input
                    ol = encodeCount(out, maxOut, ol, rptCount - 4)
                    if (ol < 0) return input
                    l += rptCount
                    continue
                }
            }

            if (l <= (len - 36) && USX_HCODE_LENS_DFLT[USX_NUM] > 0) {
                if (input[l + 8].toInt() == 45 && input[l + 13].toInt() == 45 &&
                    input[l + 18].toInt() == 45 && input[l + 23].toInt() == 45
                ) {
                    var hexType = USX_NIB_NUM
                    var uidPos = l
                    while (uidPos < l + 36) {
                        val cUid = input[uidPos].toInt() and 0xFF
                        if (cUid == 45 && (uidPos == l + 8 || uidPos == l + 13 || uidPos == l + 18 || uidPos == l + 23)) {
                            uidPos++
                            continue
                        }
                        val nibType = getNibbleType(cUid)
                        if (nibType == USX_NIB_NOT) break
                        if (nibType != USX_NIB_NUM) {
                            if (hexType != USX_NIB_NUM && hexType != nibType) break
                            hexType = nibType
                        }
                        uidPos++
                    }
                    if (uidPos == l + 36) {
                        ol = appendNibbleEscape(out, maxOut, ol, stateHolder[0], USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                        if (ol < 0) return input
                        ol = appendBits(out, maxOut, ol, if (hexType == USX_NIB_HEX_LOWER) 0xC0 else 0xF0, if (hexType == USX_NIB_HEX_LOWER) 3 else 5)
                        if (ol < 0) return input
                        for (uPos in l until l + 36) {
                            val cUid = input[uPos].toInt() and 0xFF
                            if (cUid != 45) {
                                ol = appendBits(out, maxOut, ol, getBaseCode(cUid), 4)
                                if (ol < 0) return input
                            }
                        }
                        l += 36
                        continue
                    }
                }
            }

            if (l < (len - 5) && USX_HCODE_LENS_DFLT[USX_NUM] > 0) {
                var hexType = USX_NIB_NUM
                var hexLen = 0
                while (l + hexLen < len) {
                    val cUid = input[l + hexLen].toInt() and 0xFF
                    val nibType = getNibbleType(cUid)
                    if (nibType == USX_NIB_NOT) break
                    if (nibType != USX_NIB_NUM) {
                        if (hexType != USX_NIB_NUM && hexType != nibType) break
                        hexType = nibType
                    }
                    hexLen++
                }
                if (hexLen > 10 && hexType == USX_NIB_NUM) {
                    hexType = USX_NIB_HEX_LOWER
                }
                if ((hexType == USX_NIB_HEX_LOWER || hexType == USX_NIB_HEX_UPPER) && hexLen > 3) {
                    ol = appendNibbleEscape(out, maxOut, ol, stateHolder[0], USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                    if (ol < 0) return input
                    ol = appendBits(out, maxOut, ol, if (hexType == USX_NIB_HEX_LOWER) 0x80 else 0xE0, if (hexType == USX_NIB_HEX_LOWER) 2 else 4)
                    if (ol < 0) return input
                    ol = encodeCount(out, maxOut, ol, hexLen)
                    if (ol < 0) return input
                    for (hIdx in 0 until hexLen) {
                        ol = appendBits(out, maxOut, ol, getBaseCode(input[l + hIdx].toInt() and 0xFF), 4)
                        if (ol < 0) return input
                    }
                    l += hexLen
                    continue
                }
            }

            var freqMatched = false
            for (fIdx in 0 until 6) {
                val seqBytes = USX_FREQ_SEQ_DFLT[fIdx].toByteArray(Charsets.UTF_8)
                val seqLen = seqBytes.size
                if (l <= len - seqLen) {
                    var match = true
                    for (m in 0 until seqLen) {
                        if (input[l + m] != seqBytes[m]) {
                            match = false
                            break
                        }
                    }
                    if (match && USX_HCODE_LENS_DFLT[USX_FREQ_CODES[fIdx] ushr 5] > 0) {
                        ol = appendCode(out, maxOut, ol, USX_FREQ_CODES[fIdx], stateHolder, USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                        if (ol < 0) return input
                        l += seqLen
                        freqMatched = true
                        break
                    }
                }
            }
            if (freqMatched) continue

            cIn = input[l].toInt() and 0xFF

            var isUpper = false
            if (cIn in 65..90) {
                isUpper = true
            } else {
                if (isAllUpper) {
                    isAllUpper = false
                    ol = appendSwitchCode(out, maxOut, ol, stateHolder[0])
                    if (ol < 0) return input
                    ol = appendBits(out, maxOut, ol, USX_HCODES_DFLT[USX_ALPHA], USX_HCODE_LENS_DFLT[USX_ALPHA])
                    if (ol < 0) return input
                    stateHolder[0] = USX_ALPHA
                }
            }

            if (isUpper && !isAllUpper) {
                if (stateHolder[0] == USX_NUM) {
                    ol = appendSwitchCode(out, maxOut, ol, stateHolder[0])
                    if (ol < 0) return input
                    ol = appendBits(out, maxOut, ol, USX_HCODES_DFLT[USX_ALPHA], USX_HCODE_LENS_DFLT[USX_ALPHA])
                    if (ol < 0) return input
                    stateHolder[0] = USX_ALPHA
                }
                ol = appendSwitchCode(out, maxOut, ol, stateHolder[0])
                if (ol < 0) return input
                ol = appendBits(out, maxOut, ol, USX_HCODES_DFLT[USX_ALPHA], USX_HCODE_LENS_DFLT[USX_ALPHA])
                if (ol < 0) return input
                if (stateHolder[0] == USX_DELTA) {
                    stateHolder[0] = USX_ALPHA
                    ol = appendSwitchCode(out, maxOut, ol, stateHolder[0])
                    if (ol < 0) return input
                    ol = appendBits(out, maxOut, ol, USX_HCODES_DFLT[USX_ALPHA], USX_HCODE_LENS_DFLT[USX_ALPHA])
                    if (ol < 0) return input
                }
            }

            val cNext = if (l + 1 < len) input[l + 1].toInt() and 0xFF else 0

            if (cIn in 32..126) {
                if (isUpper && !isAllUpper) {
                    var allUpperCount = 0
                    for (ll in l + 4 downTo l) {
                        if (ll < len && input[ll].toInt() and 0xFF in 65..90) {
                            allUpperCount++
                        }
                    }
                    if (allUpperCount >= 5) {
                        ol = appendSwitchCode(out, maxOut, ol, stateHolder[0])
                        if (ol < 0) return input
                        ol = appendBits(out, maxOut, ol, USX_HCODES_DFLT[USX_ALPHA], USX_HCODE_LENS_DFLT[USX_ALPHA])
                        if (ol < 0) return input
                        stateHolder[0] = USX_ALPHA
                        isAllUpper = true
                    }
                }

                if (stateHolder[0] == USX_DELTA && (cIn == 32 || cIn == 46 || cIn == 44)) {
                    val chIdx = if (cIn == 32) 0 else if (cIn == 44) 2 else 3
                    ol = appendBits(out, maxOut, ol, UNI_STATE_SPL_CODE, UNI_STATE_SPL_CODE_LEN)
                    if (ol < 0) return input
                    ol = appendBits(out, maxOut, ol, USX_SPL_CODE[chIdx], USX_SPL_CODE_LEN[chIdx])
                    if (ol < 0) return input
                    l++
                    continue
                }

                var codeIn = cIn - 32
                if (isAllUpper && isUpper) {
                    codeIn += 32
                }
                if (codeIn == 0) {
                    if (stateHolder[0] == USX_NUM) {
                        ol = appendBits(out, maxOut, ol, USX_VCODES[NUM_SPC_CODE and 0x1F], USX_VCODE_LENS[NUM_SPC_CODE and 0x1F])
                    } else {
                        ol = appendBits(out, maxOut, ol, USX_VCODES[1], USX_VCODE_LENS[1])
                    }
                    if (ol < 0) return input
                } else {
                    codeIn--
                    ol = appendCode(out, maxOut, ol, USX_CODE_94[codeIn], stateHolder, USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                    if (ol < 0) return input
                }
            } else if (cIn == 13 && cNext == 10) {
                ol = appendCode(out, maxOut, ol, CRLF_CODE, stateHolder, USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                if (ol < 0) return input
                l++
            } else if (cIn == 10) {
                if (stateHolder[0] == USX_DELTA) {
                    ol = appendBits(out, maxOut, ol, UNI_STATE_SPL_CODE, UNI_STATE_SPL_CODE_LEN)
                    if (ol < 0) return input
                    ol = appendBits(out, maxOut, ol, 0xF0, 4)
                    if (ol < 0) return input
                } else {
                    ol = appendCode(out, maxOut, ol, LF_CODE, stateHolder, USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                    if (ol < 0) return input
                }
            } else if (cIn == 13) {
                ol = appendCode(out, maxOut, ol, CR_CODE, stateHolder, USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                if (ol < 0) return input
            } else if (cIn == 9) {
                ol = appendCode(out, maxOut, ol, TAB_CODE, stateHolder, USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                if (ol < 0) return input
            } else {
                val uni = readUTF8(input, len, l, utf8len)
                if (uni > 0) {
                    l += utf8len[0]
                    if (stateHolder[0] != USX_DELTA) {
                        val uni2 = if (l < len) readUTF8(input, len, l, utf8len) else 0
                        if (uni2 > 0) {
                            if (stateHolder[0] != USX_ALPHA) {
                                ol = appendSwitchCode(out, maxOut, ol, stateHolder[0])
                                if (ol < 0) return input
                                ol = appendBits(out, maxOut, ol, USX_HCODES_DFLT[USX_ALPHA], USX_HCODE_LENS_DFLT[USX_ALPHA])
                                if (ol < 0) return input
                            }
                            ol = appendSwitchCode(out, maxOut, ol, stateHolder[0])
                            if (ol < 0) return input
                            ol = appendBits(out, maxOut, ol, USX_HCODES_DFLT[USX_ALPHA], USX_HCODE_LENS_DFLT[USX_ALPHA])
                            if (ol < 0) return input
                            ol = appendBits(out, maxOut, ol, USX_VCODES[1], USX_VCODE_LENS[1])
                            if (ol < 0) return input
                            stateHolder[0] = USX_DELTA
                        } else {
                            ol = appendSwitchCode(out, maxOut, ol, stateHolder[0])
                            if (ol < 0) return input
                            ol = appendBits(out, maxOut, ol, USX_HCODES_DFLT[USX_DELTA], USX_HCODE_LENS_DFLT[USX_DELTA])
                            if (ol < 0) return input
                        }
                    }
                    ol = encodeUnicode(out, maxOut, ol, uni, prevUni)
                    if (ol < 0) return input
                    prevUni = uni
                    continue
                } else {
                    var binCount = 1
                    for (bi in l + 1 until len) {
                        if (readUTF8(input, len, bi, utf8len) > 0) break
                        binCount++
                    }
                    ol = appendNibbleEscape(out, maxOut, ol, stateHolder[0], USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                    if (ol < 0) return input
                    ol = appendBits(out, maxOut, ol, 0xF8, 5)
                    if (ol < 0) return input
                    ol = encodeCount(out, maxOut, ol, binCount)
                    if (ol < 0) return input
                    for (bIdx in 0 until binCount) {
                        ol = appendBits(out, maxOut, ol, input[l + bIdx].toInt() and 0xFF, 8)
                        if (ol < 0) return input
                    }
                    l += binCount
                    continue
                }
            }
            l++
        }

        val rst = (ol + 7) ushr 3
        appendFinalBits(out, rst, ol, stateHolder[0], isAllUpper, USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
        return out.copyOf(rst)
    }

    fun compress(text: String): ByteArray = compress(text.toByteArray(Charsets.UTF_8))

    // ========================================================================
    // DECOMPRESSOR
    // ========================================================================

    private fun readBit(input: ByteArray, bitNo: Int): Int {
        val byteIdx = bitNo ushr 3
        if (byteIdx >= input.size) return 0
        return (input[byteIdx].toInt() and 0xFF) and (0x80 ushr (bitNo and 0x07))
    }

    private fun read8bitCode(input: ByteArray, lenBytes: Int, bitNo: Int): Int {
        val bitPos = bitNo and 0x07
        var charPos = bitNo ushr 3
        if (charPos >= lenBytes) return 0xFF
        var code = ((input[charPos].toInt() and 0xFF) shl bitPos) and 0xFF
        charPos++
        if (charPos < lenBytes) {
            code = code or ((input[charPos].toInt() and 0xFF) ushr (8 - bitPos))
        } else {
            code = code or (0xFF ushr (8 - bitPos))
        }
        return code
    }

    private fun readVCodeIdx(input: ByteArray, lenBits: Int, bitNoHolder: IntArray): Int {
        var bitNo = bitNoHolder[0]
        if (bitNo < lenBits) {
            val code = read8bitCode(input, lenBits ushr 3, bitNo)
            var i = 0
            do {
                if (code <= USX_VSECTIONS[i]) {
                    val vcode = USX_VCODE_LOOKUP[USX_VSECTION_POS[i] + ((code and USX_VSECTION_MASK[i]) ushr USX_VSECTION_SHIFT[i])]
                    bitNo += (vcode ushr 5) + 1
                    bitNoHolder[0] = bitNo
                    if (bitNo > lenBits) return 99
                    return vcode and 0x1F
                }
            } while (++i < SECTION_COUNT)
        }
        return 99
    }

    private fun readHCodeIdx(input: ByteArray, lenBits: Int, bitNoHolder: IntArray, usxHcodes: IntArray, usxHcodeLens: IntArray): Int {
        if (usxHcodeLens[USX_ALPHA] == 0) return USX_ALPHA
        var bitNo = bitNoHolder[0]
        if (bitNo < lenBits) {
            val code = read8bitCode(input, lenBits ushr 3, bitNo)
            for (codePos in 0 until 5) {
                if (usxHcodeLens[codePos] > 0 && (code and USX_MASK[usxHcodeLens[codePos] - 1]) == usxHcodes[codePos]) {
                    bitNo += usxHcodeLens[codePos]
                    bitNoHolder[0] = bitNo
                    return codePos
                }
            }
        }
        return 99
    }

    private fun getStepCodeIdx(input: ByteArray, lenBits: Int, bitNoHolder: IntArray, limit: Int): Int {
        var bitNo = bitNoHolder[0]
        var idx = 0
        while (bitNo < lenBits && readBit(input, bitNo) > 0) {
            idx++
            bitNo++
            if (idx == limit) {
                bitNoHolder[0] = bitNo
                return idx
            }
        }
        if (bitNo >= lenBits) {
            bitNoHolder[0] = bitNo
            return 99
        }
        bitNo++
        bitNoHolder[0] = bitNo
        return idx
    }

    private fun getNumFromBits(input: ByteArray, lenBits: Int, bitNoHolder: IntArray, countParam: Int): Int {
        var bitNo = bitNoHolder[0]
        var ret = 0
        var count = countParam
        while (count-- > 0 && bitNo < lenBits) {
            ret = ret or (if (readBit(input, bitNo) > 0) 1 shl count else 0)
            bitNo++
        }
        bitNoHolder[0] = bitNo
        return if (count < 0) ret else -1
    }

    private fun readCount(input: ByteArray, bitNoHolder: IntArray, lenBits: Int): Int {
        val idx = getStepCodeIdx(input, lenBits, bitNoHolder, 4)
        if (idx == 99) return -1
        val bitNo = bitNoHolder[0]
        if (bitNo + COUNT_BIT_LENS[idx] - 1 >= lenBits) return -1
        val num = getNumFromBits(input, lenBits, bitNoHolder, COUNT_BIT_LENS[idx])
        return num + (if (idx > 0) COUNT_ADDER[idx - 1] else 0)
    }

    private fun readUnicode(input: ByteArray, bitNoHolder: IntArray, lenBits: Int): Int {
        val idx = getStepCodeIdx(input, lenBits, bitNoHolder, 5)
        if (idx == 99) return 0x7FFFFF00.toInt() + 99
        if (idx == 5) {
            val splIdx = getStepCodeIdx(input, lenBits, bitNoHolder, 4)
            return 0x7FFFFF00.toInt() + splIdx
        }
        if (idx >= 0) {
            var bitNo = bitNoHolder[0]
            val sign = if (bitNo < lenBits) readBit(input, bitNo) else 0
            bitNo++
            bitNoHolder[0] = bitNo
            if (bitNo + UNI_BIT_LEN[idx] - 1 >= lenBits) return 0x7FFFFF00.toInt() + 99
            var count = getNumFromBits(input, lenBits, bitNoHolder, UNI_BIT_LEN[idx])
            count += UNI_ADDER[idx]
            return if (sign > 0) -count else count
        }
        return 0
    }

    private fun writeUTF8(out: ByteArrayOutputStream, uni: Int) {
        if (uni < (1 shl 11)) {
            out.write(0xC0 or (uni ushr 6))
            out.write(0x80 or (uni and 0x3F))
        } else if (uni < (1 shl 16)) {
            out.write(0xE0 or (uni ushr 12))
            out.write(0x80 or ((uni ushr 6) and 0x3F))
            out.write(0x80 or (uni and 0x3F))
        } else {
            out.write(0xF0 or (uni ushr 18))
            out.write(0x80 or ((uni ushr 12) and 0x3F))
            out.write(0x80 or ((uni ushr 6) and 0x3F))
            out.write(0x80 or (uni and 0x3F))
        }
    }

    private fun decodeRepeat(
        input: ByteArray,
        lenBits: Int,
        out: ByteArrayOutputStream,
        bitNoHolder: IntArray
    ): Boolean {
        var dictLen = readCount(input, bitNoHolder, lenBits)
        dictLen += NICE_LEN
        if (dictLen < NICE_LEN) return false
        var dist = readCount(input, bitNoHolder, lenBits)
        dist += (NICE_LEN - 1)
        if (dist < NICE_LEN - 1) return false
        val outBytes = out.toByteArray()
        if (outBytes.size < dist) return false
        val startPos = outBytes.size - dist
        for (i in 0 until dictLen) {
            out.write(outBytes[startPos + (i % dist)].toInt() and 0xFF)
        }
        return true
    }

    private fun getHexChar(nibble: Int, hexType: Int): Char {
        return if (nibble in 0..9) {
            ('0'.code + nibble).toChar()
        } else if (hexType < USX_NIB_HEX_UPPER) {
            ('a'.code + nibble - 10).toChar()
        } else {
            ('A'.code + nibble - 10).toChar()
        }
    }

    /**
     * Decompresses the Unishox2 compressed byte array back to original UTF-8 bytes.
     */
    fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val lenBits = input.size shl 3
        val bitNoHolder = intArrayOf(UNISHOX_MAGIC_BIT_LEN)
        var dstate = USX_ALPHA
        var h = USX_ALPHA
        var isAllUpper = false
        var prevUni = 0
        val out = ByteArrayOutputStream()

        while (bitNoHolder[0] < lenBits) {
            val origBitNo = bitNoHolder[0]
            if (dstate == USX_DELTA || h == USX_DELTA) {
                if (dstate != USX_DELTA) h = dstate
                val delta = readUnicode(input, bitNoHolder, lenBits)
                if ((delta ushr 8) == 0x7FFFFF) {
                    val splCodeIdx = delta and 0xFF
                    if (splCodeIdx == 99) break
                    when (splCodeIdx) {
                        0 -> {
                            out.write(' '.code)
                            continue
                        }
                        1 -> {
                            h = readHCodeIdx(input, lenBits, bitNoHolder, USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                            if (h == 99) {
                                bitNoHolder[0] = lenBits
                                continue
                            }
                            if (h == USX_DELTA || h == USX_ALPHA) {
                                dstate = h
                                continue
                            }
                            if (h == USX_DICT) {
                                if (!decodeRepeat(input, lenBits, out, bitNoHolder)) return out.toByteArray()
                                h = dstate
                                continue
                            }
                        }
                        2 -> {
                            out.write(','.code)
                            continue
                        }
                        3 -> {
                            out.write('.'.code)
                            continue
                        }
                        4 -> {
                            out.write(10)
                            continue
                        }
                    }
                } else {
                    prevUni += delta
                    writeUTF8(out, prevUni)
                }
                if (dstate == USX_DELTA && h == USX_DELTA) continue
            } else {
                h = dstate
            }

            var c: Char = '\u0000'
            var isUpper = isAllUpper
            var v = readVCodeIdx(input, lenBits, bitNoHolder)
            if (v == 99 || h == 99) {
                bitNoHolder[0] = origBitNo
                break
            }

            if (v == 0 && h != USX_SYM) {
                if (bitNoHolder[0] >= lenBits) break
                if (h != USX_NUM || dstate != USX_DELTA) {
                    h = readHCodeIdx(input, lenBits, bitNoHolder, USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                    if (h == 99 || bitNoHolder[0] >= lenBits) {
                        bitNoHolder[0] = origBitNo
                        break
                    }
                }
                if (h == USX_ALPHA) {
                    if (dstate == USX_ALPHA) {
                        if (isAllUpper) {
                            isAllUpper = false
                            continue
                        }
                        v = readVCodeIdx(input, lenBits, bitNoHolder)
                        if (v == 99) {
                            bitNoHolder[0] = origBitNo
                            break
                        }
                        if (v == 0) {
                            h = readHCodeIdx(input, lenBits, bitNoHolder, USX_HCODES_DFLT, USX_HCODE_LENS_DFLT)
                            if (h == 99) {
                                bitNoHolder[0] = origBitNo
                                break
                            }
                            if (h == USX_ALPHA) {
                                isAllUpper = true
                                continue
                            }
                        }
                        isUpper = true
                    } else {
                        dstate = USX_ALPHA
                        continue
                    }
                } else if (h == USX_DICT) {
                    if (!decodeRepeat(input, lenBits, out, bitNoHolder)) break
                    continue
                } else if (h == USX_DELTA) {
                    continue
                } else {
                    if (h != USX_NUM || dstate != USX_DELTA) {
                        v = readVCodeIdx(input, lenBits, bitNoHolder)
                    }
                    if (v == 99) {
                        bitNoHolder[0] = origBitNo
                        break
                    }
                    if (h == USX_NUM && v == 0) {
                        val idx = getStepCodeIdx(input, lenBits, bitNoHolder, 5)
                        if (idx == 99) break
                        if (idx == 0) {
                            val tIdx = getStepCodeIdx(input, lenBits, bitNoHolder, 4)
                            if (tIdx >= 5) break
                            val rem = readCount(input, bitNoHolder, lenBits)
                            if (rem < 0 || tIdx >= USX_TEMPLATES.size) break
                            val template = USX_TEMPLATES[tIdx]
                            val tlen = template.length
                            if (rem > tlen) break
                            val countChars = tlen - rem
                            for (j in 0 until countChars) {
                                val cT = template[j]
                                if (cT == 'f' || cT == 'r' || cT == 't' || cT == 'o' || cT == 'F') {
                                    val nibLen = if (cT == 'f' || cT == 'F') 4 else if (cT == 'r') 3 else if (cT == 't') 2 else 1
                                    val rawChar = getNumFromBits(input, lenBits, bitNoHolder, nibLen)
                                    if (rawChar < 0) break
                                    out.write(getHexChar(rawChar, if (cT == 'f') USX_NIB_HEX_LOWER else USX_NIB_HEX_UPPER).code)
                                } else {
                                    out.write(cT.code)
                                }
                            }
                        } else if (idx == 5) {
                            val binCount = readCount(input, bitNoHolder, lenBits)
                            if (binCount <= 0) break
                            for (b in 0 until binCount) {
                                val rawByte = getNumFromBits(input, lenBits, bitNoHolder, 8)
                                if (rawByte < 0) break
                                out.write(rawByte)
                            }
                        } else {
                            val nibbleCount = if (idx == 2 || idx == 4) 32 else readCount(input, bitNoHolder, lenBits)
                            if (nibbleCount <= 0) break
                            for (nb in 0 until nibbleCount) {
                                val nibble = getNumFromBits(input, lenBits, bitNoHolder, 4)
                                if (nibble < 0) break
                                out.write(getHexChar(nibble, if (idx < 3) USX_NIB_HEX_LOWER else USX_NIB_HEX_UPPER).code)
                                val remaining = nibbleCount - nb
                                if ((idx == 2 || idx == 4) && (remaining == 25 || remaining == 21 || remaining == 17 || remaining == 13)) {
                                    out.write('-'.code)
                                }
                            }
                        }
                        if (dstate == USX_DELTA) h = USX_DELTA
                        continue
                    }
                }
            }

            if (isUpper && v == 1) {
                dstate = USX_DELTA
                h = USX_DELTA
                continue
            }

            if (h < 3 && v < 28) {
                c = USX_SETS[h][v]
            }

            if (c in 'a'..'z') {
                dstate = USX_ALPHA
                if (isUpper) {
                    c = (c.code - 32).toChar()
                }
            } else {
                if (c in '0'..'9') {
                    dstate = USX_NUM
                } else if (c == '\u0000') {
                    if (v == 8) {
                        out.write('\r'.code)
                        out.write('\n'.code)
                    } else if (h == USX_NUM && v == 26) {
                        val count = readCount(input, bitNoHolder, lenBits)
                        if (count < 0) break
                        val outBytes = out.toByteArray()
                        if (outBytes.isNotEmpty()) {
                            val rptC = outBytes.last()
                            for (r in 0 until count + 4) {
                                out.write(rptC.toInt() and 0xFF)
                            }
                        }
                    } else if (h == USX_SYM && v > 24) {
                        val fSeq = USX_FREQ_SEQ_DFLT[v - 25]
                        val fBytes = fSeq.toByteArray(Charsets.UTF_8)
                        out.write(fBytes)
                    } else if (h == USX_NUM && v in 23..25) {
                        val fSeq = USX_FREQ_SEQ_DFLT[v - 20]
                        val fBytes = fSeq.toByteArray(Charsets.UTF_8)
                        out.write(fBytes)
                    } else {
                        break // Terminator
                    }
                    if (dstate == USX_DELTA) h = USX_DELTA
                    continue
                }
            }

            if (dstate == USX_DELTA) h = USX_DELTA
            if (c != '\u0000') {
                out.write(c.code)
            }
        }

        return out.toByteArray()
    }

    fun decompressToString(input: ByteArray): String {
        val decompressed = decompress(input)
        return String(decompressed, Charsets.UTF_8)
    }
}
