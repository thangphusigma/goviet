package vn.gviet.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.setPadding(0, dp(16), 0, dp(4))
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Gõ Việt"

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(12), dp(20), dp(24))

        val b1 = Button(this)
        b1.text = "1. Bật bàn phím Gõ Việt"
        b1.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        col.addView(b1)

        val b2 = Button(this)
        b2.text = "2. Chọn bàn phím Gõ Việt"
        b2.setOnClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        col.addView(b2)

        col.addView(label("Kiểu gõ"))
        val spMethod = Spinner(this)
        spMethod.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Method.values().map { it.label }
        )
        spMethod.setSelection(Prefs.method(this).ordinal)
        spMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                Prefs.setMethod(this@SettingsActivity, Method.values()[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        col.addView(spMethod)

        col.addView(label("Bảng mã"))
        val spCharset = Spinner(this)
        spCharset.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Charset.values().map { it.label }
        )
        spCharset.setSelection(Prefs.charset(this).ordinal)
        spCharset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                Prefs.setCharset(this@SettingsActivity, Charset.values()[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        col.addView(spCharset)

        val sw = Switch(this)
        sw.text = "Bỏ dấu kiểu mới (oà, uý)"
        sw.isChecked = Prefs.newStyle(this)
        sw.setPadding(0, dp(16), 0, dp(8))
        sw.setOnCheckedChangeListener { _, on -> Prefs.setNewStyle(this, on) }
        col.addView(sw)

        col.addView(label("Gõ thử (đổi cài đặt xong, chạm lại vào ô để áp dụng):"))
        val test = EditText(this)
        test.hint = "Việt Nam"
        test.minLines = 3
        col.addView(test)

        val scroll = ScrollView(this)
        scroll.addView(col)
        setContentView(scroll)
    }
}
