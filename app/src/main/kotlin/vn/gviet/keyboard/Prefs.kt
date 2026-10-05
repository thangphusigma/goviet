package vn.gviet.keyboard

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences("gviet", Context.MODE_PRIVATE)

    fun method(c: Context): Method =
        Method.values().firstOrNull { it.name == sp(c).getString("method2", null) } ?: Method.TELEX

    fun charset(c: Context): Charset =
        Charset.values().firstOrNull { it.name == sp(c).getString("charset2", null) } ?: Charset.UNICODE

    fun newStyle(c: Context): Boolean = sp(c).getBoolean("newStyle", true)
    fun viet(c: Context): Boolean = sp(c).getBoolean("viet", true)

    fun setMethod(c: Context, m: Method) = sp(c).edit().putString("method2", m.name).apply()
    fun setCharset(c: Context, cs: Charset) = sp(c).edit().putString("charset2", cs.name).apply()
    fun setNewStyle(c: Context, v: Boolean) = sp(c).edit().putBoolean("newStyle", v).apply()
    fun setViet(c: Context, v: Boolean) = sp(c).edit().putBoolean("viet", v).apply()
}
