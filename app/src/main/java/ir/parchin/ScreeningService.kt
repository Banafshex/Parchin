package ir.parchin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService

/** سیستم اندروید این سرویس را برای هر تماس ورودی صدا می‌زند، حتی وقتی برنامه بسته است. */
class ScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        var response = CallResponse.Builder().build() // پیش‌فرض: اجازه‌ی تماس
        var blockedNumber: String? = null
        var notify = false

        try {
            val store = Store(this)
            val number = details.handle?.schemeSpecificPart
            if (details.callDirection == Call.Details.DIRECTION_INCOMING &&
                store.enabled && !number.isNullOrBlank()
            ) {
                val national = Prefix.nationalOf(number)
                val hit = national != null && store.prefixes().any { national.startsWith(it) }
                if (hit && !(store.skipContacts && isContact(number))) {
                    response = CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipCallLog(!store.logCalls)
                        .setSkipNotification(true)
                        .build()
                    blockedNumber = number
                    notify = store.notify
                }
            }
        } catch (e: Exception) {
            // در صورت هر خطا، تماس برقرار شود
        }

        respondToCall(details, response)
        if (notify && blockedNumber != null) showNotification(blockedNumber)
    }

    private fun isContact(number: String): Boolean {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { return it.count > 0 }
        return false
    }

    private fun showNotification(number: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val n = android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_fence)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(number)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), n)
    }

    private companion object {
        const val CHANNEL = "blocked"
    }
}
