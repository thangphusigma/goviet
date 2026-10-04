package vn.gviet.keyboard

import java.text.Normalizer

enum class Method(val label: String) {
    TELEX("Telex"),
    SIMPLE_TELEX("Simple Telex"),
    VNI("VNI"),
    VIQR("VIQR")
}

/**
 * Bộ gõ tiếng Việt cho MỘT từ đang soạn. Kết quả luôn là Unicode dựng sẵn (NFC);
 * việc đổi sang bảng mã khác do [Converter] đảm nhiệm.
 *
 * feed(c) = true  -> ký tự đã được nhận vào từ (gọi word() để lấy chữ hiển thị)
 * feed(c) = false -> ký tự không thuộc từ: hãy chốt từ rồi gõ ký tự đó ra bình thường
 */
class VietEngine {
    var method = Method.TELEX
    var newStyle = true // true: oà, uý — false: òa, úy

    // mod: 0 không, 1 mũ (â ê ô), 2 trăng (ă), 3 móc (ơ ư), 4 gạch (đ)
    private class VC(var ch: Char, var upper: Boolean, var mod: Int = 0, var fromW: Boolean = false)

    private val cs = ArrayList<VC>()
    private var tone = 0 // 0 không, 1 sắc, 2 huyền, 3 hỏi, 4 ngã, 5 nặng
    private val locked = HashSet<Char>() // phím đã bị "hoàn tác" trong từ này -> gõ thẳng
    private var escape = false

    fun hasWord() = cs.isNotEmpty()

    fun reset() {
        cs.clear(); tone = 0; locked.clear(); escape = false
    }

    fun backspace() {
        if (escape) { escape = false; return }
        if (cs.isEmpty()) return
        val tp = if (tone > 0) tonePos(cluster()) else -1
        val last = cs.size - 1
        if (last == tp) tone = 0
        cs.removeAt(last)
        if (cluster().isEmpty()) tone = 0
        if (cs.isEmpty()) locked.clear()
    }

    fun feed(k: Char): Boolean {
        if (escape) { escape = false; return false }
        return when (method) {
            Method.TELEX, Method.SIMPLE_TELEX -> telex(k)
            Method.VNI -> vni(k)
            Method.VIQR -> viqr(k)
        }
    }

    fun word(): String {
        val tp = if (tone > 0) tonePos(cluster()) else -1
        val sb = StringBuilder()
        for (i in cs.indices) sb.append(render(cs[i], if (i == tp) tone else 0))
        return sb.toString()
    }

    // ---------------------------------------------------------------- hiển thị

    private fun render(v: VC, t: Int): String {
        if (v.mod == 4) return (if (v.upper) 'Đ' else 'đ').toString()
        val b = if (v.upper) v.ch.uppercaseChar() else v.ch
        if (v.mod == 0 && t == 0) return b.toString()
        val s = StringBuilder().append(b)
        when (v.mod) {
            1 -> s.append('\u0302')
            2 -> s.append('\u0306')
            3 -> s.append('\u031B')
        }
        when (t) {
            1 -> s.append('\u0301')
            2 -> s.append('\u0300')
            3 -> s.append('\u0309')
            4 -> s.append('\u0303')
            5 -> s.append('\u0323')
        }
        return Normalizer.normalize(s, Normalizer.Form.NFC)
    }

    // ------------------------------------------------------- cấu trúc âm tiết

    private fun isVowelCh(c: Char) = c in "aeiouy"

    /** Chỉ số các nguyên âm của cụm nguyên âm đầu tiên (bỏ u trong "qu", i trong "gi" + nguyên âm). */
    private fun cluster(): List<Int> {
        val r = ArrayList<Int>()
        for (i in cs.indices) {
            val c = cs[i].ch
            var v = isVowelCh(c) && cs[i].mod != 4
            if (v && i > 0 && c == 'u' && cs[i - 1].ch == 'q') v = false
            if (v && i > 0 && c == 'i' && cs[i - 1].ch == 'g' && i + 1 < cs.size && isVowelCh(cs[i + 1].ch)) v = false
            if (v) r.add(i) else if (r.isNotEmpty()) break
        }
        return r
    }

    /** Vị trí đặt dấu thanh. */
    private fun tonePos(cl: List<Int>): Int {
        if (cl.isEmpty()) return -1
        if (cl.size == 1) return cl[0]
        val modded = cl.filter { cs[it].mod in 1..3 }
        if (modded.isNotEmpty()) return modded.last()
        val hasFinal = cl.last() + 1 < cs.size
        if (cl.size == 2) {
            val s = "" + cs[cl[0]].ch + cs[cl[1]].ch
            if (s == "oa" || s == "oe" || s == "uy") return if (hasFinal || newStyle) cl[1] else cl[0]
            return cl[0]
        }
        return cl[1]
    }

    // ------------------------------------------------------------- tiện ích

    private fun add(k: Char) {
        cs.add(VC(k.lowercaseChar(), k.isUpperCase()))
    }

    private fun undo(k: Char) {
        locked.add(k.lowercaseChar())
        add(k)
    }

    private fun setTone(t: Int, k: Char): Boolean {
        if (cluster().isEmpty()) return false
        if (tone == t) { tone = 0; undo(k) } else tone = t
        return true
    }

    /** Gắn dấu mũ/trăng/móc lên nguyên âm thích hợp. uo = true: "uo" -> "ươ". */
    private fun mark(m: Int, elig: String, k: Char, uo: Boolean): Boolean {
        val cl = cluster()
        if (cl.isEmpty()) return false
        if (uo) {
            for (j in 0 until cl.size - 1) {
                val u = cs[cl[j]]
                val o = cs[cl[j + 1]]
                if (u.ch == 'u' && o.ch == 'o' && cl[j + 1] == cl[j] + 1) {
                    if (u.mod == 3 && o.mod == 3) { u.mod = 0; o.mod = 0; undo(k) }
                    else { u.mod = 3; o.mod = 3 }
                    return true
                }
            }
        }
        for (j in cl.indices.reversed()) {
            val v = cs[cl[j]]
            if (v.ch in elig) {
                if (v.mod == m) { v.mod = 0; undo(k) } else v.mod = m
                return true
            }
        }
        return false
    }

    /** "dd" -> đ (Telex, VIQR). */
    private fun ddAdjacent(k: Char): Boolean {
        val last = cs.lastOrNull() ?: return false
        if (last.ch != 'd') return false
        if (last.mod == 0) { last.mod = 4; return true }
        if (last.mod == 4) { last.mod = 0; undo(k); return true }
        return false
    }

    /** "9" -> đ (VNI). */
    private fun dStroke(k: Char): Boolean {
        val i = cs.indexOfFirst { it.ch == 'd' && it.mod == 0 }
        if (i >= 0) { cs[i].mod = 4; return true }
        val j = cs.indexOfFirst { it.ch == 'd' && it.mod == 4 }
        if (j >= 0) { cs[j].mod = 0; undo(k); return true }
        return false
    }

    // ----------------------------------------------------------------- Telex

    private fun telex(k: Char): Boolean {
        val isAscii = k in 'a'..'z' || k in 'A'..'Z'
        if (!isAscii && k != '[' && k != ']') return false
        val l = k.lowercaseChar()
        if (l in locked) { add(k); return true }
        when (l) {
            's' -> if (setTone(1, k)) return true
            'f' -> if (setTone(2, k)) return true
            'r' -> if (setTone(3, k)) return true
            'x' -> if (setTone(4, k)) return true
            'j' -> if (setTone(5, k)) return true
            'z' -> if (tone != 0) { tone = 0; return true }
            'a', 'e', 'o' -> {
                val last = cs.lastOrNull()
                if (last != null && last.ch == l) {
                    if (last.mod == 0) { last.mod = 1; return true }
                    if (last.mod == 1) { last.mod = 0; undo(k); return true }
                }
            }
            'd' -> if (ddAdjacent(k)) return true
            'w' -> return telexW(k)
            '[' -> if (method == Method.TELEX) { cs.add(VC('o', false, 3)); return true }
            ']' -> if (method == Method.TELEX) { cs.add(VC('u', false, 3)); return true }
        }
        if (!isAscii) return false
        add(k)
        return true
    }

    private fun telexW(k: Char): Boolean {
        val lastVc = cs.lastOrNull()
        if (lastVc != null && lastVc.fromW) { // "ww" -> w
            cs.removeAt(cs.size - 1); undo(k); return true
        }
        val cl = cluster()
        if (cl.isNotEmpty()) {
            if (mark(3, "", k, true)) return true // uo -> ươ
            for (j in cl.indices.reversed()) {
                val v = cs[cl[j]]
                val m = when (v.ch) { 'a' -> 2; 'o', 'u' -> 3; else -> 0 }
                if (m != 0) {
                    if (v.mod == m) { v.mod = 0; undo(k) } else v.mod = m
                    return true
                }
            }
        } else if (method == Method.TELEX) { // w đứng một mình -> ư
            cs.add(VC('u', k.isUpperCase(), 3, true))
            return true
        }
        add(k)
        return true
    }

    // ------------------------------------------------------------------- VNI

    private fun vni(k: Char): Boolean {
        when (k) {
            in '1'..'5' -> return setTone(k - '0', k)
            '0' -> { if (tone != 0) { tone = 0; return true }; return false }
            '6' -> return mark(1, "aeo", k, false)
            '7' -> return mark(3, "ou", k, true)
            '8' -> return mark(2, "a", k, false)
            '9' -> return dStroke(k)
        }
        if (!(k in 'a'..'z' || k in 'A'..'Z')) return false
        add(k)
        return true
    }

    // ------------------------------------------------------------------ VIQR

    private fun viqr(k: Char): Boolean {
        when (k) {
            '\\' -> { if (cs.isNotEmpty()) { escape = true; return true }; return false }
            '\'' -> return setTone(1, k)
            '`' -> return setTone(2, k)
            '?' -> return setTone(3, k)
            '~' -> return setTone(4, k)
            '.' -> return setTone(5, k)
            '^' -> return mark(1, "aeo", k, false)
            '(' -> return mark(2, "a", k, false)
            '+' -> return mark(3, "ou", k, true)
        }
        if (!(k in 'a'..'z' || k in 'A'..'Z')) return false
        if ((k == 'd' || k == 'D') && ddAdjacent(k)) return true
        add(k)
        return true
    }
}
