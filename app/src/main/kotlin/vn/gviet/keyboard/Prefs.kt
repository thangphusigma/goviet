package vn.gviet.keyboard

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences("gviet", Context.MODE_PRIVATE)

    fun method(c: Context): Method =
        Method.values().getOrElse(sp(c).getInt("method", 0)) { Method.TELEX }

    fun charset(c: Context): Charset =
        Charset.values().getOrElse(sp(c).getInt("charset", 0)) { Charset.UNICODE }

    fun newStyle(c: Context): Boolean = sp(c).getBoolean("newStyle", true)
    fun viet(c: Context): Boolean = sp(c).getBoolean("viet", true)

    fun setMethod(c: Context, m: Method) = sp(c).edit().putInt("method", m.ordinal).apply()
    fun setCharset(c: Context, cs: Charset) = sp(c).edit().putInt("charset", cs.ordinal).apply()
    fun setNewStyle(c: Context, v: Boolean) = sp(c).edit().putBoolean("newStyle", v).apply()
    fun setViet(c: Context, v: Boolean) = sp(c).edit().putBoolean("viet", v).apply()
}
