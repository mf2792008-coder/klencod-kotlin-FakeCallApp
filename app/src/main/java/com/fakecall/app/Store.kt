package com.fakecall.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** متصل واحد: اسم + رقم + (صورة وصوت كملفات باسم الـ id). */
data class Profile(
    val id: String,
    val name: String,
    val number: String,
    val voiceName: String,
    val bg: Int = 0
)

/** التخزين الدائم: SharedPreferences للبيانات، ومجلد التطبيق للصور والأصوات. */
object Store {
    const val DEF_NAME = "اخوويا الكبيير ❤️❤️"
    const val DEF_NUMBER = "01155763302"

    private fun prefs(c: Context) = c.getSharedPreferences("fakecall", Context.MODE_PRIVATE)

    // ───────── البروفايلات ─────────

    fun profiles(c: Context): List<Profile> {
        val raw = prefs(c).getString("profiles", null)
        if (raw != null) {
            try {
                val a = JSONArray(raw)
                val out = (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    Profile(
                        o.getString("id"),
                        o.optString("name"),
                        o.optString("number"),
                        o.optString("voiceName"),
                        o.optInt("bg", 0)
                    )
                }
                if (out.isNotEmpty()) return out
            } catch (_: Exception) {
            }
        }
        val def = listOf(Profile("default", DEF_NAME, DEF_NUMBER, ""))
        saveProfiles(c, def)
        prefs(c).edit().putString("active", "default").apply()
        return def
    }

    fun saveProfiles(c: Context, list: List<Profile>) {
        val a = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("number", p.number)
            o.put("voiceName", p.voiceName)
            o.put("bg", p.bg)
            a.put(o)
        }
        prefs(c).edit().putString("profiles", a.toString()).apply()
    }

    fun activeId(c: Context): String {
        val list = profiles(c)
        val id = prefs(c).getString("active", "") ?: ""
        return if (list.any { it.id == id }) id else list.first().id
    }

    fun setActive(c: Context, id: String) {
        prefs(c).edit().putString("active", id).apply()
    }

    fun newId(): String = java.lang.Long.toString(System.currentTimeMillis(), 36)

    // ───────── الملفات ─────────

    fun photoFile(c: Context, id: String) = File(c.filesDir, "p_${id}_photo.jpg")
    fun thumbFile(c: Context, id: String) = File(c.filesDir, "p_${id}_thumb.jpg")
    fun voiceFile(c: Context, id: String) = File(c.filesDir, "p_${id}_voice")
    fun bgFile(c: Context, id: String) = File(c.filesDir, "p_${id}_bg.jpg")
    fun ringFile(c: Context) = File(c.filesDir, "ring_custom")       // النغمة العامة (fallback)
    fun profileRingFile(c: Context, id: String) = File(c.filesDir, "p_${id}_ring") // نغمة البروفايل

    fun deleteFiles(c: Context, id: String) {
        photoFile(c, id).delete()
        thumbFile(c, id).delete()
        voiceFile(c, id).delete()
        bgFile(c, id).delete()
        profileRingFile(c, id).delete()
    }

    fun loadBitmap(f: File): Bitmap? =
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null

    // ───────── الإعدادات العامة ─────────

    fun vibrate(c: Context) = prefs(c).getBoolean("vibrate", true)
    fun setVibrate(c: Context, v: Boolean) = prefs(c).edit().putBoolean("vibrate", v).apply()

    /** إعادة الرنين بعد 30 ثانية لو المكالمة اترفضت وهي بترن. */
    fun redial(c: Context) = prefs(c).getBoolean("redial", true)
    fun setRedial(c: Context, v: Boolean) = prefs(c).edit().putBoolean("redial", v).apply()

    fun delay(c: Context) = prefs(c).getInt("delay", 10)
    fun setDelay(c: Context, v: Int) = prefs(c).edit().putInt("delay", v).apply()

    // ───────── النغمة العامة (fallback) ─────────
    fun ringName(c: Context): String =
        if (ringFile(c).exists()) (prefs(c).getString("ring_name", "ملف مخصص") ?: "ملف مخصص") else ""

    fun setRingName(c: Context, n: String) = prefs(c).edit().putString("ring_name", n).apply()

    fun resetRing(c: Context) {
        ringFile(c).delete()
        prefs(c).edit().remove("ring_name").apply()
    }

    // ───────── نغمة البروفايل ─────────
    fun profileRingName(c: Context, id: String): String =
        if (profileRingFile(c, id).exists())
            (prefs(c).getString("ring_name_$id", "ملف مخصص") ?: "ملف مخصص")
        else ""

    fun setProfileRingName(c: Context, id: String, n: String) =
        prefs(c).edit().putString("ring_name_$id", n).apply()

    fun resetProfileRing(c: Context, id: String) {
        profileRingFile(c, id).delete()
        prefs(c).edit().remove("ring_name_$id").apply()
    }

    /**
     * النغمة الفعلية اللي المفروض تشتغل لبروفايل معيّن:
     * نغمة البروفايل لو موجودة، وإلا النغمة العامة المخصصة، وإلا الافتراضية.
     */
    fun effectiveRingFile(c: Context, id: String): File? {
        val pf = profileRingFile(c, id)
        if (pf.exists()) return pf
        val gf = ringFile(c)
        if (gf.exists()) return gf
        return null
    }

    fun scheduledAt(c: Context): Long {
        val at = prefs(c).getLong("scheduled_at", 0L)
        return if (at > System.currentTimeMillis()) at else 0L
    }

    fun setScheduledAt(c: Context, at: Long) = prefs(c).edit().putLong("scheduled_at", at).apply()

    // ───────── مواعيد المكالمات ─────────

    fun timed(c: Context): List<Timed> {
        val raw = prefs(c).getString("timed", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Timed(o.getInt("id"), o.getInt("hour"), o.getInt("minute"), o.getInt("days"), o.optBoolean("enabled", true))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTimed(c: Context, list: List<Timed>) {
        val a = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("hour", t.hour)
            o.put("minute", t.minute)
            o.put("days", t.days)
            o.put("enabled", t.enabled)
            a.put(o)
        }
        prefs(c).edit().putString("timed", a.toString()).apply()
    }

    fun nextTimedId(c: Context): Int {
        val n = prefs(c).getInt("timed_next_id", 1)
        prefs(c).edit().putInt("timed_next_id", n + 1).apply()
        return n
    }

    // ───────── نسخ الملفات والصور ─────────

    fun copyUri(c: Context, uri: Uri, dest: File) {
        val input = c.contentResolver.openInputStream(uri) ?: throw IllegalStateException("no stream")
        input.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
    }

    fun displayName(c: Context, uri: Uri): String {
        try {
            c.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) {
                        val n = cur.getString(i)
                        if (!n.isNullOrEmpty()) return n
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "ملف مخصص"
    }

    /** بنفك الصورة بحد أقصى [maxPx] ونصلّح الدوران. */
    private fun decodeUpright(c: Context, uri: Uri, maxPx: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        val max = maxOf(bounds.outWidth, bounds.outHeight)
        while (max / sample > maxPx) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("decode failed")

        try {
            c.contentResolver.openInputStream(uri)?.use { s ->
                val ex = ExifInterface(s)
                val deg = when (ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (deg != 0f) {
                    val m = Matrix()
                    m.postRotate(deg)
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                }
            }
        } catch (_: Exception) {
        }
        return bmp
    }

    /** صورة المتصل: أقصى 800px + نسخة مصغّرة للقايمة. */
    fun savePhoto(c: Context, uri: Uri, id: String) {
        val bmp = decodeUpright(c, uri, 800)
        photoFile(c, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }

        val k = 160f / maxOf(bmp.width, bmp.height)
        val thumb = if (k < 1f) {
            Bitmap.createScaledBitmap(bmp, Math.round(bmp.width * k), Math.round(bmp.height * k), true)
        } else bmp
        thumbFile(c, id).outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }

    /** خلفية البروفايل: أقصى 1280px. */
    fun saveBackground(c: Context, uri: Uri, id: String) {
        val bmp = decodeUpright(c, uri, 1280)
        bgFile(c, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }
}
