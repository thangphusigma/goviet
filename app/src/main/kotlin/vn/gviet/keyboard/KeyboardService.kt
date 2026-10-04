package vn.gviet.keyboard

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView

class KeyboardService : InputMethodService() {

    private val engine = VietEngine()
    private var charset = Charset.UNICODE
    private var viet = true
    private var forceEnglish = false // ô mật khẩu, email, URL, số...
    private var shift = 0 // 0 tắt, 1 một lần, 2 caps lock
    private var lastShiftTap = 0L
    private var symbols = false

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var keyArea: LinearLayout
    private lateinit var tvViet: TextView
    private lateinit var tvMethod: TextView
    private lateinit var tvCharset: TextView
    private var shiftKey: TextView? = null
    private val letterKeys = ArrayList<Pair<TextView, Char>>()

    private val cBg = Color.parseColor("#1B1E23")
    private val cKey = Color.parseColor("#3B4048")
    private val cSpecial = Color.parseColor("#2A2E34")
    private val cAccent = Color.parseColor("#3D7EFF")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------ vòng đời

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(cBg)
        root.setPadding(dp(3), dp(3), dp(3), dp(6))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        tvViet = chip(1f) { toggleViet() }
        tvMethod = chip(2.2f) { cycleMethod() }
        tvCharset = chip(2.8f) { cycleCharset() }
        val gear = chip(1f) {
            val i = Intent(this, SettingsActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }
        gear.text = "⚙"
        bar.addView(tvViet); bar.addView(tvMethod); bar.addView(tvCharset); bar.addView(gear)
        root.addView(bar)

        keyArea = LinearLayout(this)
        keyArea.orientation = LinearLayout.VERTICAL
        root.addView(keyArea)

        loadPrefs()
        buildKeys()
        updateChips()
        return root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        engine.reset()
        loadPrefs()
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val plainText = cls == InputType.TYPE_CLASS_TEXT
        forceEnglish = !plainText || variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI
        )
        val wantSymbols = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
        if (wantSymbols != symbols) { symbols = wantSymbols; if (::keyArea.isInitialized) buildKeys() }
        shift = 0
        updateAutoCap()
        updateChips()
    }

    override fun onFinishInput() {
        currentInputConnection?.finishComposingText()
        engine.reset()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Người dùng chạm sang chỗ khác khi đang soạn từ -> bỏ từ hiện tại
        if (engine.hasWord() && candidatesStart == -1 && candidatesEnd == -1) engine.reset()
    }

    private fun loadPrefs() {
        engine.method = Prefs.method(this)
        engine.newStyle = Prefs.newStyle(this)
        charset = Prefs.charset(this)
        viet = Prefs.viet(this)
    }

    // ------------------------------------------------------------ thanh chế độ

    private fun chip(weight: Float, onTap: () -> Unit): TextView {
        val t = TextView(this)
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.WHITE)
        t.textSize = 13f
        t.maxLines = 1
        t.background = round(cSpecial)
        val lp = LinearLayout.LayoutParams(0, dp(34), weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        t.layoutParams = lp
        touch(t, false) { onTap() }
        return t
    }

    private fun updateChips() {
        if (!::tvViet.isInitialized) return
        val on = viet && !forceEnglish
        tvViet.text = if (on) "V" else "E"
        tvViet.background = round(if (on) cAccent else cSpecial)
        tvMethod.text = engine.method.label
        tvCharset.text = charset.label
    }

    private fun toggleViet() {
        finishWord()
        viet = !viet
        Prefs.setViet(this, viet)
        updateChips()
    }

    private fun cycleMethod() {
        finishWord()
        val all = Method.values()
        engine.method = all[(engine.method.ordinal + 1) % all.size]
        Prefs.setMethod(this, engine.method)
        updateChips()
    }

    private fun cycleCharset() {
        finishWord()
        val all = Charset.values()
        charset = all[(charset.ordinal + 1) % all.size]
        Prefs.setCharset(this, charset)
        updateChips()
    }

    // ------------------------------------------------------------ dựng phím

    private fun buildKeys() {
        keyArea.removeAllViews()
        letterKeys.clear()
        shiftKey = null

        val r1 = if (symbols) "@#\$%&*-+()" else "qwertyuiop"
        val r2 = if (symbols) "!\":;/?'`~" else "asdfghjkl"
        val r3 = if (symbols) "^_=[]\\|" else "zxcvbnm"
        val letters = !symbols

        keyArea.addView(charRow("1234567890", false, 0f))
        keyArea.addView(charRow(r1, letters, 0f))
        keyArea.addView(charRow(r2, letters, 0.5f))

        val row3 = newRow()
        if (letters) {
            val sk = key("⇧", 1.5f, cSpecial) { onShift() }
            shiftKey = sk
            row3.addView(sk)
        } else {
            row3.addView(spacer(1.5f))
        }
        for (c in r3) row3.addView(charKey(c, letters))
        row3.addView(key("⌫", 1.5f, cSpecial, repeat = true) { doBackspace() })
        keyArea.addView(row3)

        val row4 = newRow()
        row4.addView(key(if (symbols) "ABC" else "?123", 1.5f, cSpecial) {
            finishWord(); symbols = !symbols; buildKeys()
        })
        row4.addView(charKey(',', false))
        row4.addView(key("␣", 5f, cKey) { typeChar(' ') })
        row4.addView(charKey('.', false))
        row4.addView(key("⏎", 1.5f, cAccent) { doEnter() })
        keyArea.addView(row4)
        refreshCase()
    }

    private fun newRow(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return r
    }

    private fun spacer(w: Float): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(0, dp(46), w)
        return v
    }

    private fun charRow(chars: String, letters: Boolean, pad: Float): LinearLayout {
        val r = newRow()
        if (pad > 0f) r.addView(spacer(pad))
        for (c in chars) r.addView(charKey(c, letters))
        if (pad > 0f) r.addView(spacer(pad))
        return r
    }

    private fun charKey(c: Char, letter: Boolean): TextView {
        val t = key(c.toString(), 1f, cKey) {
            val ch = if (letter && shift != 0) c.uppercaseChar() else c
            typeChar(ch)
            if (letter && shift == 1) { shift = 0; refreshCase() }
        }
        if (letter) letterKeys.add(Pair(t, c))
        return t
    }

    private fun key(label: String, weight: Float, bg: Int, repeat: Boolean = false, onDown: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.WHITE)
        t.textSize = 18f
        t.background = round(bg)
        val lp = LinearLayout.LayoutParams(0, dp(46), weight)
        lp.setMargins(dp(2), dp(3), dp(2), dp(3))
        t.layoutParams = lp
        touch(t, repeat, onDown)
        return t
    }

    private fun round(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(6).toFloat()
        return d
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun touch(v: View, repeat: Boolean, action: () -> Unit) {
        val rep = object : Runnable {
            override fun run() { action(); handler.postDelayed(this, 45) }
        }
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.alpha = 0.6f
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    action()
                    if (repeat) handler.postDelayed(rep, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1f
                    handler.removeCallbacks(rep)
                }
            }
            true
        }
    }

    // ------------------------------------------------------------ Shift

    private fun onShift() {
        val now = System.currentTimeMillis()
        shift = when {
            shift == 2 -> 0
            now - lastShiftTap < 400 -> 2
            shift == 1 -> 0
            else -> 1
        }
        lastShiftTap = now
        refreshCase()
    }

    private fun refreshCase() {
        for ((tv, c) in letterKeys) tv.text = (if (shift != 0) c.uppercaseChar() else c).toString()
        shiftKey?.apply {
            text = if (shift == 2) "⇪" else "⇧"
            background = round(if (shift != 0) cAccent else cSpecial)
        }
    }

    private fun updateAutoCap() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        if (shift == 2 || symbols) return
        val newShift = if (ic.getCursorCapsMode(ei.inputType) != 0) 1 else 0
        if (newShift != shift) { shift = newShift; refreshCase() }
    }

    // ------------------------------------------------------------ nhập liệu

    private fun typeChar(ch: Char) {
        val ic = currentInputConnection ?: return
        if (viet && !forceEnglish) {
            if (engine.feed(ch)) { showComposing(ic); return }
            finishWord(ic)
        }
        ic.commitText(ch.toString(), 1)
        if (!ch.isLetterOrDigit()) updateAutoCap()
    }

    private fun showComposing(ic: InputConnection) {
        ic.setComposingText(Converter.convert(engine.word(), charset), 1)
    }

    private fun finishWord(ic: InputConnection? = currentInputConnection) {
        if (engine.hasWord()) ic?.finishComposingText()
        engine.reset()
    }

    private fun doBackspace() {
        val ic = currentInputConnection ?: return
        if (engine.hasWord()) {
            engine.backspace()
            if (engine.hasWord()) showComposing(ic) else { ic.commitText("", 1); engine.reset() }
            return
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateAutoCap()
    }

    private fun doEnter() {
        val ic = currentInputConnection ?: return
        finishWord(ic)
        val ei = currentInputEditorInfo
        val action = if (ei != null) ei.imeOptions and EditorInfo.IME_MASK_ACTION else EditorInfo.IME_ACTION_NONE
        val noEnterAction = ei != null && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        updateAutoCap()
    }
}
