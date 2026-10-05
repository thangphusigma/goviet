package vn.gviet.keyboard

import java.text.Normalizer

enum class Charset(val label: String) {
    UNICODE("Unicode"),
    UNICODE_COMP("Unicode tổ hợp"),
    CP1258("CP 1258"),
    UTF8("UTF-8"),
    NCR_DEC("NCR Decimal"),
    NCR_HEX("NCR Hex"),
    UNICODE_C("Unicode C String"),
    VIQR("VIQR"),
    TCVN3("TCVN3 (ABC)"),
    VPS("VPS"),
    VISCII("VISCII"),
    BKHCM1("BK HCM 1"),
    BKHCM2("BK HCM 2"),
    VIETWARE_F("Vietware F"),
    VIETWARE_X("Vietware X"),
    VNI_WIN("VNI Windows")
}

/** Đổi chuỗi Unicode dựng sẵn (NFC) sang bảng mã đích. */
object Converter {

    fun convert(s: String, cs: Charset): String = when (cs) {
        Charset.UNICODE -> s
        Charset.UNICODE_COMP, Charset.CP1258 -> map(s, ::composite)
        Charset.UTF8 -> utf8Bytes(s)
        Charset.NCR_DEC -> map(s) { if (it.code > 127) "&#${it.code};" else it.toString() }
        Charset.NCR_HEX -> map(s) { if (it.code > 127) "&#x%X;".format(it.code) else it.toString() }
        Charset.UNICODE_C -> map(s) { if (it.code > 127) "\\u%04X".format(it.code) else it.toString() }
        Charset.VIQR -> map(s, ::viqr)
        Charset.TCVN3 -> legacy(s, LegacyTables.TCVN3, 1)
        Charset.VPS -> legacy(s, LegacyTables.VPS, 1)
        Charset.VISCII -> legacy(s, LegacyTables.VISCII, 1)
        Charset.BKHCM1 -> legacy(s, LegacyTables.BKHCM1, 1)
        Charset.BKHCM2 -> legacy(s, LegacyTables.BKHCM2, 2)
        Charset.VIETWARE_F -> legacy(s, LegacyTables.VIETWARE_F, 1)
        Charset.VIETWARE_X -> legacy(s, LegacyTables.VIETWARE_X, 2)
        Charset.VNI_WIN -> legacy(s, LegacyTables.VNI_WIN, 2)
    }

    private fun map(s: String, f: (Char) -> String): String {
        val sb = StringBuilder()
        for (c in s) sb.append(f(c))
        return sb.toString()
    }

    // ----------------------------------------------------- bảng mã cũ (tra bảng)

    private val index: HashMap<Char, Int> by lazy {
        val m = HashMap<Char, Int>()
        LegacyTables.VN.forEachIndexed { i, c -> m[c] = i }
        m
    }

    /** Ký tự ASCII (không phải chữ cái) mà bảng mã dùng làm chữ Việt, ví dụ BK HCM 1 dùng ^ ` { | } ~ . */
    private val usedCache = HashMap<String, Set<Char>>()

    private fun used(t: String): Set<Char> = usedCache.getOrPut(t) {
        t.filter { it.code in 0x21..0x7E && !it.isLetterOrDigit() }.toSet()
    }

    /**
     * Giống UniKey: ký tự không thuộc bảng mà trùng mã với một chữ Việt trong bảng mã đích
     * sẽ được thay bằng '?' để văn bản không bị đọc nhầm.
     */
    private fun legacy(s: String, t: String, w: Int): String {
        val sb = StringBuilder()
        val clash = used(t)
        for (c in s) {
            val i = index[c]
            if (i != null) {
                for (k in 0 until w) {
                    val ch = t[i * w + k]
                    if (ch != '\u0000') sb.append(ch)
                }
            } else if (c in clash) {
                sb.append('?')
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /** UTF-8 dạng byte: mỗi byte hiển thị như một ký tự Windows-1252 (giống UniKey "UTF-8"). */
    private fun utf8Bytes(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val v = b.toInt() and 0xFF
            sb.append(if (v in 0x80..0x9F) LegacyTables.CP1252_HIGH[v - 0x80] else v.toChar())
        }
        return sb.toString()
    }

    // ------------------------------------------------- Unicode tổ hợp / CP 1258

    private class Parts(val base: Char, val mod: Char, val tone: Char)

    private fun parts(c: Char): Parts {
        val d = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
        var mod = '\u0000'
        var tone = '\u0000'
        for (i in 1 until d.length) {
            when (d[i]) {
                '\u0302', '\u0306', '\u031B' -> mod = d[i]
                else -> tone = d[i]
            }
        }
        return Parts(d[0], mod, tone)
    }

    /** Giữ nguyên â ê ô ă ơ ư đ dựng sẵn, chỉ tách dấu thanh thành ký tự kết hợp (như UniKey). */
    private fun composite(c: Char): String {
        if (c.code < 0xC0) return c.toString()
        val p = parts(c)
        val base = if (p.mod != '\u0000')
            Normalizer.normalize("" + p.base + p.mod, Normalizer.Form.NFC) else p.base.toString()
        return if (p.tone != '\u0000') base + p.tone else base
    }

    // ------------------------------------------------------------------- VIQR

    private fun viqr(c: Char): String {
        if (c == 'đ') return "dd"
        if (c == 'Đ') return "DD"
        if (c.code < 0xC0) return c.toString()
        val p = parts(c)
        val sb = StringBuilder().append(p.base)
        when (p.mod) {
            '\u0302' -> sb.append('^')
            '\u0306' -> sb.append('(')
            '\u031B' -> sb.append('+')
        }
        when (p.tone) {
            '\u0301' -> sb.append('\'')
            '\u0300' -> sb.append('`')
            '\u0309' -> sb.append('?')
            '\u0303' -> sb.append('~')
            '\u0323' -> sb.append('.')
        }
        return sb.toString()
    }
}
