package ir.parchin

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.animation.ValueAnimator
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.provider.Settings
import android.view.MotionEvent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var regular: Typeface
    private lateinit var bold: Typeface

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var btnEnable: Button
    private lateinit var badge: TextView
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private lateinit var etPrefix: EditText
    private lateinit var btnAdd: Button
    private lateinit var preview: View
    private lateinit var previewNumber: TextView

    private lateinit var background: CyberBackgroundView
    private lateinit var listArea: View
    private lateinit var pagination: View
    private lateinit var previous: Button
    private lateinit var next: Button
    private lateinit var pageLabel: TextView
    private var page = 0
    private var pageSize = 1
    private var resumed = false
    private var sounds: SoundPool? = null
    private var tapSound = 0
    private var addSound = 0

    private var silent = false
    private var settingsDialog: AlertDialog? = null
    private var swContacts: Switch? = null
    private var swNotify: Switch? = null

    // مقادیر مرحله‌ای پنجره تنظیمات؛ فقط با زدن «اعمال تغییرات» ذخیره می‌شوند
    private var stagedEnabled = false
    private var stagedContacts = false
    private var stagedLog = false
    private var stagedNotify = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        regular = resources.getFont(R.font.vazirmatn_regular)
        bold = resources.getFont(R.font.vazirmatn_bold)
        setContentView(R.layout.activity_main)

        background = findViewById(R.id.cyberBackground)
        listArea = findViewById(R.id.listArea)
        pagination = findViewById(R.id.pagination)
        previous = findViewById(R.id.btnPrevious)
        next = findViewById(R.id.btnNext)
        pageLabel = findViewById(R.id.pageLabel)
        sounds = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build().also {
            tapSound = it.load(this, R.raw.cyber_tap, 1)
            addSound = it.load(this, R.raw.cyber_add, 1)
        }
        listArea.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                val rowHeight = (56 * resources.displayMetrics.density + 0.5f).toInt()
                val newSize = ((bottom - top) / rowHeight).coerceAtLeast(1)
                if (newSize != pageSize) { pageSize = newSize; renderList() }
            }
        }
        previous.setOnClickListener { playSound(); if (page > 0) { page--; renderList() } }
        next.setOnClickListener { playSound(); page++; renderList() }
        animatePress(previous)
        animatePress(next)

        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        btnEnable = findViewById(R.id.btnEnable)
        badge = findViewById(R.id.badge)
        list = findViewById(R.id.prefixList)
        empty = findViewById(R.id.emptyText)
        etPrefix = findViewById(R.id.etPrefix)
        btnAdd = findViewById(R.id.btnAdd)
        preview = findViewById(R.id.preview)
        previewNumber = findViewById(R.id.previewNumber)

        findViewById<View>(R.id.btnSettings).apply {
            animatePress(this)
            setOnClickListener { playSound(); showSettings() }
        }
        animatePress(btnAdd)
        animatePress(btnEnable)

        etPrefix.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable) {
                val cleaned = Prefix.clean(s.toString())
                if (cleaned != s.toString()) {
                    s.replace(0, s.length, cleaned) // دوباره همین تابع صدا زده می‌شود
                    return
                }
                btnAdd.isEnabled = cleaned.length in Prefix.MIN..Prefix.MAX
                preview.visibility = if (cleaned.isEmpty()) View.GONE else View.VISIBLE
                previewNumber.text = "+98$cleaned"
            }
        })

        btnAdd.isEnabled = false
        btnAdd.setOnClickListener {
            val p = Prefix.clean(etPrefix.text.toString())
            if (p.length !in Prefix.MIN..Prefix.MAX) return@setOnClickListener
            if (!store.add(p)) {
                playSound()
                toast(R.string.dup)
            } else {
                playSound(add = true)
                page = (store.prefixes().size - 1) / pageSize
                renderList()
            }
            etPrefix.setText("")
        }

        btnEnable.setOnClickListener {
            playSound()
            val rm = getSystemService(RoleManager::class.java)
            if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                toast(R.string.role_unavailable)
            } else {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), RC_ROLE
                )
            }
        }

        renderList()
        fonts(findViewById(R.id.root))
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        background.setRunning(hasWindowFocus())
        refreshStatus()
    }

    override fun onPause() {
        resumed = false
        background.setRunning(false)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::background.isInitialized) background.setRunning(resumed && hasFocus)
    }

    override fun onDestroy() {
        settingsDialog?.dismiss()
        sounds?.release()
        sounds = null
        super.onDestroy()
    }

    private fun playSound(add: Boolean = false) {
        // Follow Android's touch-sound setting and silent/vibrate mode.
        if (Settings.System.getInt(contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 0) == 0) return
        val audio = getSystemService(AudioManager::class.java)
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        sounds?.play(if (add) addSound else tapSound, 0.35f, 0.35f, 1, 0, 1f)
    }

    private fun animatePress(view: View) {
        view.isSoundEffectsEnabled = false // use the short, system-setting-aware cyber sound instead
        view.setOnTouchListener { v, event ->
            if (ValueAnimator.areAnimatorsEnabled()) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(.96f).scaleY(.96f).setDuration(90).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
            }
            false // preserve normal clicks, ripple, accessibility and scrolling
        }
    }

    // ---------- تنظیمات (پنجره) ----------

    private fun showSettings() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null, false)
        val settings = view.findViewById<LinearLayout>(R.id.settingsList)

        stagedEnabled = store.enabled
        stagedContacts = store.skipContacts
        stagedLog = store.logCalls
        stagedNotify = store.notify

        addRow(settings, R.string.set_enabled, R.string.set_enabled_d, stagedEnabled) {
            stagedEnabled = it
        }

        swContacts = addRow(
            settings, R.string.set_contacts, R.string.set_contacts_d,
            stagedContacts && hasPermission(Manifest.permission.READ_CONTACTS)
        ) { on ->
            if (on && !hasPermission(Manifest.permission.READ_CONTACTS)) {
                setChecked(swContacts!!, false)
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), RC_CONTACTS)
            } else stagedContacts = on
        }

        addRow(settings, R.string.set_log, R.string.set_log_d, stagedLog) {
            stagedLog = it
        }

        swNotify = addRow(
            settings, R.string.set_notify, R.string.set_notify_d,
            stagedNotify && (Build.VERSION.SDK_INT < 33 ||
                hasPermission(Manifest.permission.POST_NOTIFICATIONS))
        ) { on ->
            if (on && Build.VERSION.SDK_INT >= 33 &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                setChecked(swNotify!!, false)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), RC_NOTIF)
            } else stagedNotify = on
        }

        fonts(view)

        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<Button>(R.id.btnCancel).apply {
            animatePress(this)
            setOnClickListener { playSound(); dialog.dismiss() }
        }
        view.findViewById<Button>(R.id.btnApply).apply {
            animatePress(this)
            setOnClickListener {
                playSound()
                store.enabled = stagedEnabled
                store.skipContacts = stagedContacts
                store.logCalls = stagedLog
                store.notify = stagedNotify
                refreshStatus()
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener {
            if (settingsDialog === dialog) {
                settingsDialog = null
                swContacts = null
                swNotify = null
            }
        }
        settingsDialog = dialog
        dialog.show()
    }

    private fun addRow(
        parent: LinearLayout, title: Int, desc: Int, checked: Boolean,
        onToggle: (Boolean) -> Unit
    ): Switch {
        val row = layoutInflater.inflate(R.layout.item_setting, parent, false)
        row.findViewById<TextView>(R.id.tvTitle).setText(title)
        row.findViewById<TextView>(R.id.tvDesc).setText(desc)
        val sw = row.findViewById<Switch>(R.id.sw)
        sw.trackDrawable = getDrawable(R.drawable.bg_switch_track)
        sw.thumbDrawable = getDrawable(R.drawable.bg_switch_thumb)
        sw.isSoundEffectsEnabled = false
        row.isSoundEffectsEnabled = false
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, v -> if (!silent) { playSound(); onToggle(v) } }
        row.setOnClickListener { sw.toggle() }
        parent.addView(row)
        return sw
    }

    private fun setChecked(sw: Switch, v: Boolean) {
        silent = true
        sw.isChecked = v
        silent = false
    }

    private fun hasPermission(p: String) =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        val granted = results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED
        when (code) {
            RC_CONTACTS -> if (granted) {
                stagedContacts = true
                swContacts?.let { setChecked(it, true) }
            } else toast(R.string.perm_contacts_denied)
            RC_NOTIF -> if (granted) {
                stagedNotify = true
                swNotify?.let { setChecked(it, true) }
            } else toast(R.string.perm_notif_denied)
        }
    }

    // ---------- وضعیت ----------

    private fun roleHeld(): Boolean {
        val rm = getSystemService(RoleManager::class.java)
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun updateLauncherIcon(active: Boolean) {
        val pm = packageManager
        val activeAlias = ComponentName(this, "${packageName}.MainActivityActive")
        val inactiveAlias = ComponentName(this, "${packageName}.MainActivityInactive")
        val desired = if (active) activeAlias else inactiveAlias
        val other = if (active) inactiveAlias else activeAlias

        // Enable the desired launcher entry first, then disable the old one so
        // the launcher never loses both entries at the same time.
        pm.setComponentEnabledSetting(
            desired,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            other,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun refreshStatus() {
        val held = roleHeld()
        updateLauncherIcon(held && store.enabled)
        val neon: Int
        val text: Int
        when {
            !held -> { neon = 0xFFB9E878.toInt(); text = R.string.status_warn }
            !store.enabled -> { neon = 0xFF91AAB0.toInt(); text = R.string.status_off }
            else -> { neon = 0xFFA7EB8C.toInt(); text = R.string.status_ok }
        }
        statusDot.background.mutate().setTint(neon)
        statusText.setTextColor(neon)
        statusText.setText(text)
        btnEnable.visibility = if (held) View.GONE else View.VISIBLE
    }

    // ---------- لیست ----------

    private fun renderList() {
        list.removeAllViews()
        val items = store.prefixes()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val pages = ((items.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        pagination.visibility = if (pages > 1) View.VISIBLE else View.INVISIBLE
        previous.isEnabled = page > 0
        next.isEnabled = page < pages - 1
        pageLabel.text = getString(R.string.page_fmt, (page + 1).toPersianDigits(), pages.toPersianDigits())
        for (p in items.drop(page * pageSize).take(pageSize)) {
            val v = layoutInflater.inflate(R.layout.item_prefix, list, false)
            val s = SpannableString("+98$p")
            s.setSpan(ForegroundColorSpan(Color.parseColor("#63E3D8")), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            v.findViewById<TextView>(R.id.tvNumber).text = s
            v.findViewById<View>(R.id.btnDelete).apply {
                contentDescription = getString(R.string.delete_number, "+98$p")
                animatePress(this)
                setOnClickListener {
                    playSound()
                    store.remove(p)
                    renderList()
                }
            }
            fonts(v)
            list.addView(v)
        }
        badge.text = getString(R.string.count_fmt, items.size.toPersianDigits())
    }

    private fun fonts(v: View) = Fonts.apply(v, regular, bold)

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private companion object {
        const val RC_ROLE = 1
        const val RC_CONTACTS = 2
        const val RC_NOTIF = 3
    }
}
