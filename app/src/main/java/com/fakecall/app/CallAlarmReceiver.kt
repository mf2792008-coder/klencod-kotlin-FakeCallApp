package com.fakecall.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * بيستقبل المنبّه المجدول ويفتح شاشة المكالمة حتى لو التطبيق مقفول أو الشاشة مقفولة:
 * إشعار fullScreenIntent (الأساسي) + محاولة فتح الـ Activity مباشرة.
 */
class CallAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(c: Context, intent: Intent) {
        // المنبّه العادي بس هو اللي بيمسح حالة "مجدولة"؛ إعادة الرنين مالهاش علاقة بيها
        if (intent.action == ACTION) Store.setScheduledAt(c, 0L)
        if (intent.action == ACTION_TIMED) TimedCalls.onFired(c, intent.getIntExtra(TimedCalls.EXTRA_ID, -1))

        val list = Store.profiles(c)
        val name = (list.firstOrNull { it.id == Store.activeId(c) } ?: list.first()).name

        val launch = Intent(c, MainActivity::class.java)
            .putExtra(EXTRA_INCOMING, true)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        val pi = PendingIntent.getActivity(
            c, 8, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL, "Incoming call", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null) // الرنة بتتشغل من التطبيق نفسه
        ch.enableVibration(false)
        ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ch)

        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(name)
            .setContentText("Incoming call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        try {
            nm.notify(NOTIF_ID, n)
        } catch (_: Exception) {
        }

        // على أندرويد 10+ ممكن يتمنع من الخلفية، وساعتها الإشعار هو اللي بيفتح الشاشة
        try {
            c.startActivity(launch)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val ACTION = "com.fakecall.app.INCOMING_CALL"
        const val ACTION_REDIAL = "com.fakecall.app.REDIAL_CALL"
        const val ACTION_TIMED = "com.fakecall.app.TIMED_CALL"
        const val EXTRA_INCOMING = "incoming_call"
        const val CHANNEL = "fakecall_incoming"
        const val NOTIF_ID = 4242
    }
}

/** جدولة المكالمة بـ setAlarmClock: دقيقة بالثانية ومن غير إذن "المنبّهات الدقيقة". */
object Scheduler {

    private fun alarmPi(c: Context): PendingIntent {
        val i = Intent(c, CallAlarmReceiver::class.java).setAction(CallAlarmReceiver.ACTION)
        return PendingIntent.getBroadcast(
            c, 7, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(c: Context, seconds: Int): Boolean {
        return try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val at = System.currentTimeMillis() + seconds * 1000L
            val show = PendingIntent.getActivity(
                c, 9, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), alarmPi(c))
            Store.setScheduledAt(c, at)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun redialPi(c: Context): PendingIntent {
        val i = Intent(c, CallAlarmReceiver::class.java).setAction(CallAlarmReceiver.ACTION_REDIAL)
        return PendingIntent.getBroadcast(
            c, 11, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** رنين تاني بعد [seconds] ثانية (بعد رفض المكالمة). منبّه مستقل عن الجدولة العادية. */
    fun scheduleRedial(c: Context, seconds: Int): Boolean {
        return try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val at = System.currentTimeMillis() + seconds * 1000L
            val show = PendingIntent.getActivity(
                c, 12, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), redialPi(c))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun cancelRedial(c: Context) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(redialPi(c))
        } catch (_: Exception) {
        }
    }

    fun cancel(c: Context) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(alarmPi(c))
        } catch (_: Exception) {
        }
        Store.setScheduledAt(c, 0L)
    }
}
