package com.fakecall.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar
import java.util.Locale

/**
 * موعد مكالمة: ساعة + دقيقة + أيام الأسبوع.
 * days = قناع بتّات: البت رقم (Calendar.DAY_OF_WEEK - 1)، يعني الأحد = بت 0 والسبت = بت 6.
 * days == 0 معناها مرة واحدة في أقرب موعد.
 */
data class Timed(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Int,
    val enabled: Boolean
)

val DAY_NAMES = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

/** ترتيب عرض الأيام (الأسبوع يبدأ بالسبت). القيم هي أرقام البتّات. */
val DAY_ORDER = listOf(6, 0, 1, 2, 3, 4, 5)

fun timeLabel(h: Int, m: Int): String {
    val h12 = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.US, "%d:%02d %s", h12, m, if (h < 12) "ص" else "م")
}

fun daysLabel(days: Int): String = when (days) {
    0 -> "مرة واحدة"
    127 -> "كل يوم"
    else -> DAY_ORDER.filter { days and (1 shl it) != 0 }.joinToString("، ") { DAY_NAMES[it] }
}

fun untilLabel(t: Timed): String {
    val mins = ((TimedCalls.nextTime(t) - System.currentTimeMillis()) / 60000L).coerceAtLeast(0L)
    val d = mins / 1440
    val h = (mins % 1440) / 60
    val m = mins % 60
    val parts = ArrayList<String>()
    if (d > 0) parts.add("$d يوم")
    if (h > 0) parts.add("$h ساعة")
    if (m > 0 || parts.isEmpty()) parts.add("$m دقيقة")
    return "بعد " + parts.joinToString(" و")
}

object TimedCalls {
    const val EXTRA_ID = "timed_id"

    private fun pi(c: Context, id: Int): PendingIntent {
        val i = Intent(c, CallAlarmReceiver::class.java)
            .setAction(CallAlarmReceiver.ACTION_TIMED)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            c, 100 + id, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** أقرب وقت بعد [from] يناسب الساعة والأيام المختارة. */
    fun nextTime(t: Timed, from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = from
        cal.set(Calendar.HOUR_OF_DAY, t.hour)
        cal.set(Calendar.MINUTE, t.minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        for (i in 0..7) {
            val dayOk = t.days == 0 || (t.days and (1 shl (cal.get(Calendar.DAY_OF_WEEK) - 1))) != 0
            if (cal.timeInMillis > from && dayOk) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return from + 24L * 3600_000L
    }

    fun schedule(c: Context, t: Timed): Boolean {
        if (!t.enabled) {
            cancel(c, t.id)
            return true
        }
        return try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val show = PendingIntent.getActivity(
                c, 13, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(nextTime(t), show), pi(c, t.id))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun cancel(c: Context, id: Int) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pi(c, id))
        } catch (_: Exception) {
        }
    }

    fun rescheduleAll(c: Context) {
        for (t in Store.timed(c)) schedule(c, t)
    }

    /** بتتنادى لما منبّه موعد يرن: المتكرر بيتجدول للمرة الجاية، والمرة الواحدة بيتوقف. */
    fun onFired(c: Context, id: Int) {
        val list = Store.timed(c)
        val t = list.firstOrNull { it.id == id } ?: return
        if (t.days == 0) {
            Store.saveTimed(c, list.map { if (it.id == id) it.copy(enabled = false) else it })
        } else {
            schedule(c, t)
        }
    }
}

/** بعد إعادة تشغيل الموبايل (أو تحديث التطبيق) المنبّهات بتتمسح، فبنرجّع المواعيد. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        TimedCalls.rescheduleAll(c)
    }
}
