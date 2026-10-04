package vn.gviet.keyboard

import java.text.Normalizer

enum class Charset(val label: String) {
    UNICODE("Unicode"),
    UNICODE_COMP("Unicode tổ hợp"),
    CP1258("CP 1258"),
    VNI_WIN("VNI Windows"),
    VIQR("VIQR"),
    NCR_DEC("NCR Decimal"),
    NCR_HEX("NCR Hex"),
    UNICODE_C("Unicode C String")
}

/** Đổi chuỗi Unicode dựng sẵn (NFC) sang bảng mã đích. */
object Converter {

    fun convert(s: String, cs: Charset): String = when (cs) {
        Charset.UNICODE -> s
        Charset.UNICODE_COMP -> Normalizer.normalize(s, Normalizer.Form.NFD)
        Charset.CP1258 -> map(s, ::cp1258)
        Charset.VNI_WIN -> map(s, ::vni)
        Charset.VIQR -> map(s, ::viqr)
        Charset.NCR_DEC -> map(s) { if (it.code > 127) "&#${it.code};" else it.toString() }
        Charset.NCR_HEX -> map(s) { if (it.code > 127) "&#x%X;".format(it.code) else it.toString() }
        Charset.UNICODE_C -> map(s) { if (it.code > 127) "\\u%04X".format(it.code) else it.toString() }
    }

    private fun map(s: String, f: (Char) -> String): String {
        val sb = StringBuilder()
        for (c in s) sb.append(f(c))
        return sb.toString()
    }

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

    /** 0 sắc, 1 huyền, 2 hỏi, 3 ngã, 4 nặng, -1 không dấu. */
    private fun toneIdx(t: Char) = when (t) {
        '\u0301' -> 0
        '\u0300' -> 1
        '\u0309' -> 2
        '\u0303' -> 3
        '\u0323' -> 4
        else -> -1
    }

    // CP 1258: chữ cái gốc (â ê ô ă ơ ư đ dựng sẵn) + dấu thanh dạng tổ hợp.
    private fun cp1258(c: Char): String {
        if (c.code < 0xC0) return c.toString()
        val p = parts(c)
        val sb = StringBuilder()
        val base = if (p.mod != '\u0000')
            Normalizer.normalize("" + p.base + p.mod, Normalizer.Form.NFC) else p.base.toString()
        sb.append(base)
        if (p.tone != '\u0000') sb.append(p.tone)
        return sb.toString()
    }

    // VIQR: a( a^ o+ u+ dd + dấu ' ` ? ~ .
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

    // VNI Windows: chữ gốc + ký tự đánh dấu (ù ø û õ ï ...), ơ = ô, ư = ö, đ = ñ.
    private fun vni(c: Char): String {
        if (c == 'đ') return "ñ"
        if (c == 'Đ') return "Ñ"
        if (c.code < 0xC0) return c.toString()
        val p = parts(c)
        val up = p.base.isUpperCase()
        val b = p.base
        val bl = b.lowercaseChar()
        val ti = toneIdx(p.tone)
        fun mk(s: String) = if (up) s.uppercase() else s
        val out = StringBuilder()
        when {
            bl == 'i' && ti >= 0 -> out.append(mk("íìæóò"[ti].toString()))
            bl == 'y' && ti == 4 -> out.append(mk("î"))
            p.mod == '\u0302' -> {
                out.append(b).append(if (ti < 0) mk("â") else mk("áàåãä"[ti].toString()))
            }
            p.mod == '\u0306' -> {
                out.append(b).append(if (ti < 0) mk("ê") else mk("éèúüë"[ti].toString()))
            }
            p.mod == '\u031B' -> {
                out.append(mk(if (bl == 'o') "ô" else "ö"))
                if (ti >= 0) out.append(mk("ùøûõï"[ti].toString()))
            }
            else -> {
                out.append(b)
                if (ti >= 0) out.append(mk("ùøûõï"[ti].toString()))
            }
        }
        return out.toString()
    }
}
