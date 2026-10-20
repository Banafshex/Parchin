package ir.parchin

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** تبدیل ارقام فارسی و عربی به لاتین */
fun String.toLatinDigits(): String {
    val sb = StringBuilder(length)
    for (ch in this) {
        sb.append(
            when (ch) {
                in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                else -> ch
            }
        )
    }
    return sb.toString()
}

fun Int.toPersianDigits(): String =
    toString().map { '\u06F0' + (it - '0') }.joinToString("")

object Prefix {
    const val MIN = 3
    const val MAX = 6

    /**
     * پاک‌سازی ورودی کاربر برای فیلد بعد از +98:
     * فقط رقم، حذف صفرهای ابتدایی، حداکثر ۶ رقم.
     * اگر کاربر چیزی با +98 یا 0098 پیست کرد، آن بخش هم حذف می‌شود.
     */
    fun clean(raw: String): String {
        var s = raw.toLatinDigits().trim()
        if (s.startsWith("+98")) s = s.substring(3)
        else if (s.startsWith("0098")) s = s.substring(4)
        return s.filter { it in '0'..'9' }.trimStart('0').take(MAX)
    }

    /**
     * شماره‌ی تماس ورودی را به شکل ملی (بدون +98 و بدون صفر) درمی‌آورد.
     * برای شماره‌های غیرایرانی null برمی‌گرداند.
     */
    fun nationalOf(raw: String): String? {
        val s = raw.toLatinDigits().filter { it in '0'..'9' || it == '+' }
        return when {
            s.startsWith("+98") -> s.substring(3)
            s.startsWith("0098") -> s.substring(4)
            s.startsWith("+") || s.startsWith("00") -> null
            s.startsWith("98") && s.length == 12 -> s.substring(2)
            s.startsWith("0") -> s.substring(1)
            else -> s
        }
    }
}

object Fonts {
    fun apply(v: View, regular: Typeface, bold: Typeface) {
        if (v is TextView) {
            v.typeface = if (v.typeface?.isBold == true) bold else regular
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) apply(v.getChildAt(i), regular, bold)
        }
    }
}
