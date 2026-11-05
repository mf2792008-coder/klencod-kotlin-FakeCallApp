package com.fakecall.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * أذونات الجدولة. من غيرها أندرويد بيمنع التطبيق يفتح شاشة المكالمة من الخلفية،
 * وبيظهر إشعار صغير بس بدل ما الشاشة تفتح لوحدها.
 */
object Perms {

    fun overlayOk(c: Context) = Settings.canDrawOverlays(c)

    fun notificationsOk(c: Context) =
        Build.VERSION.SDK_INT < 33 ||
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun fullScreenOk(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return try {
            (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()
        } catch (_: Exception) {
            true
        }
    }

    /** أسماء الأذونات الناقصة بالترتيب اللي بنفتحها بيه. */
    fun missing(c: Context): List<String> {
        val out = ArrayList<String>()
        if (!overlayOk(c)) out.add("الظهور فوق التطبيقات")
        if (!notificationsOk(c)) out.add("الإشعارات")
        if (!fullScreenOk(c)) out.add("الإشعارات بملء الشاشة")
        return out
    }

    /** بيفتح صفحة الإعدادات الخاصة بأول إذن ناقص. */
    fun openFix(c: Context): Boolean {
        val pkg = c.packageName
        val i: Intent? = when {
            !overlayOk(c) ->
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg"))
            !notificationsOk(c) ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            !fullScreenOk(c) && Build.VERSION.SDK_INT >= 34 ->
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg"))
            else -> null
        }
        if (i == null) return false
        return try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
