package ir.parchin

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
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

    private lateinit var statusCard: View
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
    private lateinit var settings: LinearLayout
    private lateinit var swContacts: Switch
    private lateinit var swNotify: Switch

    private var silent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        regular = resources.getFont(R.font.vazirmatn_regular)
        bold = resources.getFont(R.font.vazirmatn_bold)
        setContentView(R.layout.activity_main)

        statusCard = findViewById(R.id.statusCard)
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
        settings = findViewById(R.id.settingsList)

        buildSettings()

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
            if (!store.add(p)) toast(R.string.dup) else renderList()
            etPrefix.setText("")
        }

        btnEnable.setOnClickListener {
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
        refreshStatus()
    }

    // ---------- تنظیمات ----------

    private fun buildSettings() {
        addRow(R.string.set_enabled, R.string.set_enabled_d, store.enabled) {
            store.enabled = it
            refreshStatus()
        }

        swContacts = addRow(
            R.string.set_contacts, R.string.set_contacts_d,
            store.skipContacts && hasPermission(Manifest.permission.READ_CONTACTS)
        ) { on ->
            if (on && !hasPermission(Manifest.permission.READ_CONTACTS)) {
                setChecked(swContacts, false)
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), RC_CONTACTS)
            } else store.skipContacts = on
        }

        addRow(R.string.set_log, R.string.set_log_d, store.logCalls) { store.logCalls = it }

        swNotify = addRow(R.string.set_notify, R.string.set_notify_d, store.notify) { on ->
            if (on && Build.VERSION.SDK_INT >= 33 &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                setChecked(swNotify, false)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), RC_NOTIF)
            } else store.notify = on
        }
    }

    private fun addRow(title: Int, desc: Int, checked: Boolean, onToggle: (Boolean) -> Unit): Switch {
        val row = layoutInflater.inflate(R.layout.item_setting, settings, false)
        row.findViewById<TextView>(R.id.tvTitle).setText(title)
        row.findViewById<TextView>(R.id.tvDesc).setText(desc)
        val sw = row.findViewById<Switch>(R.id.sw)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, v -> if (!silent) onToggle(v) }
        row.setOnClickListener { sw.toggle() }
        settings.addView(row)
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
                store.skipContacts = true
                setChecked(swContacts, true)
            } else toast(R.string.perm_contacts_denied)
            RC_NOTIF -> if (granted) {
                store.notify = true
                setChecked(swNotify, true)
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
        val bg: Int
        val fg: Int
        val dot: Int
        val text: Int
        when {
            !held -> { bg = 0xFFFFF3E0.toInt(); fg = 0xFFE65100.toInt(); dot = 0xFFFF9800.toInt(); text = R.string.status_warn }
            !store.enabled -> { bg = 0xFFECEFF1.toInt(); fg = 0xFF546E7A.toInt(); dot = 0xFF90A4AE.toInt(); text = R.string.status_off }
            else -> { bg = 0xFFE8F5E9.toInt(); fg = 0xFF2E7D32.toInt(); dot = 0xFF4CAF50.toInt(); text = R.string.status_ok }
        }
        statusCard.background.mutate().setTint(bg)
        statusDot.background.mutate().setTint(dot)
        statusText.setTextColor(fg)
        statusText.setText(text)
        btnEnable.visibility = if (held) View.GONE else View.VISIBLE
    }

    // ---------- لیست ----------

    private fun renderList() {
        list.removeAllViews()
        val items = store.prefixes()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        for (p in items) {
            val v = layoutInflater.inflate(R.layout.item_prefix, list, false)
            val s = SpannableString("+98$p")
            s.setSpan(ForegroundColorSpan(Color.parseColor("#1565C0")), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            v.findViewById<TextView>(R.id.tvNumber).text = s
            v.findViewById<View>(R.id.btnDelete).setOnClickListener {
                store.remove(p)
                renderList()
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
