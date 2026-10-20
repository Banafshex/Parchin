package ir.parchin

import android.content.Context

class Store(context: Context) {
    private val p = context.applicationContext
        .getSharedPreferences("parchin", Context.MODE_PRIVATE)

    // پیش‌شماره‌هایی که در نصب اولیه برنامه از قبل مسدود هستند.
    // +98 در رابط کاربری جداگانه اعمال می‌شود، بنابراین اینجا فقط ادامه شماره‌ها ذخیره می‌شوند.
    private val defaultPrefixes = listOf(
        "942",
        "941",
        "219101",
        "219130"
    )

    init {
        // فقط در اولین اجرا مقداردهی می‌شود و تنظیمات/لیست کاربر در اجراهای بعدی دست‌نخورده می‌ماند.
        if (!p.contains("prefixes")) {
            p.edit()
                .putString("prefixes", defaultPrefixes.joinToString(","))
                .apply()
        }
    }

    var enabled: Boolean
        get() = p.getBoolean("enabled", true)
        set(v) = p.edit().putBoolean("enabled", v).apply()

    /** مخاطبین ذخیره‌شده مسدود نشوند */
    var skipContacts: Boolean
        get() = p.getBoolean("skip_contacts", false)
        set(v) = p.edit().putBoolean("skip_contacts", v).apply()

    /** ثبت تماس مسدودشده در تاریخچه‌ی تماس‌ها */
    var logCalls: Boolean
        get() = p.getBoolean("log_calls", true)
        set(v) = p.edit().putBoolean("log_calls", v).apply()

    /** اعلان برای تماس مسدودشده */
    var notify: Boolean
        get() = p.getBoolean("notify", false)
        set(v) = p.edit().putBoolean("notify", v).apply()

    fun prefixes(): List<String> =
        (p.getString("prefixes", "") ?: "")
            .split(",")
            .filter { it.isNotEmpty() }

    /** false یعنی از قبل وجود داشته */
    fun add(prefix: String): Boolean {
        val list = prefixes()
        if (prefix in list) return false
        save(list + prefix)
        return true
    }

    fun remove(prefix: String) = save(prefixes() - prefix)

    private fun save(list: List<String>) =
        p.edit().putString("prefixes", list.joinToString(",")).apply()
}
