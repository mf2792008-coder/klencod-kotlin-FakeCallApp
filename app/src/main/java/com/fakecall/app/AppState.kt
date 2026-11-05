package com.fakecall.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen { Incoming, Active, Ended }

/** حالة التطبيق كلها. الواجهة بتقرأ منها، والأزرار بتنادي دوالها. */
class AppState(private val ctx: Context, val audio: CallAudio) {

    var screen by mutableStateOf(Screen.Incoming)
    var ringing by mutableStateOf(false)
    var seconds by mutableStateOf(0)
    var finalSeconds by mutableStateOf(0)
    var callId by mutableStateOf(0)

    var profiles by mutableStateOf(Store.profiles(ctx))
    var activeId by mutableStateOf(Store.activeId(ctx))
    var photoVersion by mutableStateOf(0)
    var permVersion by mutableStateOf(0)

    // مواعيد المكالمات
    var timed by mutableStateOf(Store.timed(ctx))
    var timedEditorOpen by mutableStateOf(false)
    var tId by mutableStateOf(0)
    var tHour by mutableStateOf(5)
    var tMinute by mutableStateOf(0)
    var tDays by mutableStateOf(0)

    /** بيتحط من MainActivity: بيقفل التطبيق وبيشيله من التطبيقات الأخيرة. */
    var closeApp: () -> Unit = {}

    var settingsOpen by mutableStateOf(false)
    var editorOpen by mutableStateOf(false)
    var ringName by mutableStateOf(Store.ringName(ctx))
    var scheduledAt by mutableStateOf(Store.scheduledAt(ctx))

    var toast by mutableStateOf<Pair<Int, String>?>(null)
    private var toastCounter = 0

    // محرر البروفايل
    var edId by mutableStateOf("")
    var edName by mutableStateOf("")
    var edNumber by mutableStateOf("")
    var edVoiceName by mutableStateOf("")
    var edIsNew by mutableStateOf(false)
    var edBg by mutableStateOf(0)
    var edRingName by mutableStateOf("")

    val active: Profile
        get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()

    fun showToast(msg: String) {
        toastCounter++
        toast = Pair(toastCounter, msg)
    }

    fun refresh() {
        profiles = Store.profiles(ctx)
        activeId = Store.activeId(ctx)
        ringName = Store.ringName(ctx)
        scheduledAt = Store.scheduledAt(ctx)
        timed = Store.timed(ctx)
        photoVersion++
    }

    // ───────── المكالمة ─────────

    fun startRing() {
        if (ringing) return
        ringing = true
        if (!audio.startRing(Store.vibrate(ctx), activeId)) showToast("تعذّر تشغيل النغمة")
    }

    fun stopRing() {
        ringing = false
        audio.stopRing()
    }

    /** بتتنادى لما المنبّه المجدول يفتح التطبيق (ringNow = true). */
    fun showIncoming(ringNow: Boolean) {
        if (screen == Screen.Active) audio.end()
        settingsOpen = false
        editorOpen = false
        timedEditorOpen = false
        refresh()
        seconds = 0
        screen = Screen.Incoming
        ringing = false
        if (ringNow) startRing()
    }

    fun accept() {
        ringing = false
        callId++
        seconds = 0
        Scheduler.cancelRedial(ctx) // اترد عليها، فمفيش رنين تاني
        audio.onAnswered(Store.voiceFile(ctx, activeId))
        screen = Screen.Active
    }

    fun end() {
        finalSeconds = seconds
        audio.end()
        screen = Screen.Ended
    }

    fun decline() {
        stopRing()
        finalSeconds = 0
        audio.end()
        // اترفضت وهي بترن: ترن تاني بعد 30 ثانية
        if (Store.redial(ctx)) Scheduler.scheduleRedial(ctx, 30)
        screen = Screen.Ended
    }

    fun restart() {
        seconds = 0
        screen = Screen.Incoming
        startRing()
    }

    // ───────── الإعدادات والبروفايلات ─────────

    fun openSettings() {
        if (settingsOpen) return
        audio.tick()
        // وقف النغمة والاهتزاز لما يدخل الإعدادات
        if (ringing) audio.stopRing()
        refresh()
        settingsOpen = true
    }

    fun closeSettings() {
        settingsOpen = false
        // لو المكالمة لسه بترن، أعد النغمة
        if (screen == Screen.Incoming && ringing) {
            audio.startRing(Store.vibrate(ctx), activeId)
        }
    }

    fun select(id: String) {
        Store.setActive(ctx, id)
        activeId = id
        settingsOpen = false
    }

    fun openEditor(id: String?) {
        val p = profiles.firstOrNull { it.id == id }
        edRingName = if (p != null) Store.profileRingName(ctx, p.id) else ""
        edIsNew = p == null
        edId = p?.id ?: Store.newId()
        edName = p?.name ?: ""
        edNumber = p?.number ?: ""
        edVoiceName = p?.voiceName ?: ""
        edBg = p?.bg ?: 0
        editorOpen = true
    }

    fun saveEditor() {
        if (edName.isBlank()) {
            showToast("اكتب اسم المتصل")
            return
        }
        val hasVoice = Store.voiceFile(ctx, edId).exists()
        val np = Profile(edId, edName.trim(), edNumber.trim(), if (hasVoice) edVoiceName else "", edBg)
        val list = if (edIsNew) profiles + np else profiles.map { if (it.id == edId) np else it }
        Store.saveProfiles(ctx, list)
        editorOpen = false
        refresh()
    }

    fun cancelEditor() {
        if (edIsNew) Store.deleteFiles(ctx, edId)
        editorOpen = false
    }

    fun deleteEditor() {
        if (profiles.size <= 1) return
        val list = profiles.filter { it.id != edId }
        Store.deleteFiles(ctx, edId)
        Store.saveProfiles(ctx, list)
        if (activeId == edId) Store.setActive(ctx, list.first().id)
        editorOpen = false
        refresh()
    }

    fun clearPhoto() {
        Store.photoFile(ctx, edId).delete()
        Store.thumbFile(ctx, edId).delete()
        photoVersion++
    }

    /** اختيار لون جاهز للخلفية (بيشيل صورة الخلفية لو كانت موجودة). */
    fun selectBg(i: Int) {
        edBg = i
        Store.bgFile(ctx, edId).delete()
        photoVersion++
    }

    fun clearBgImage() {
        Store.bgFile(ctx, edId).delete()
        photoVersion++
    }

    fun resetProfileRing() {
        Store.resetProfileRing(ctx, edId)
        edRingName = ""
    }

    fun clearVoice() {
        Store.voiceFile(ctx, edId).delete()
        edVoiceName = ""
    }

    // ───────── النغمة العامة والجدولة ─────────

    fun resetRing() {
        Store.resetRing(ctx)
        ringName = ""
    }

    fun setVibrate(v: Boolean) = Store.setVibrate(ctx, v)

    fun setRedial(v: Boolean) = Store.setRedial(ctx, v)

    fun schedule(seconds: Int) {
        val s = seconds.coerceAtLeast(3)
        Store.setDelay(ctx, s)
        if (Scheduler.schedule(ctx, s)) {
            scheduledAt = Store.scheduledAt(ctx)
            val missing = Perms.missing(ctx)
            if (missing.isEmpty()) {
                showToast("تمت الجدولة. تقدر تقفل التطبيق")
            } else {
                showToast("تمت الجدولة، بس لازم تفعّل: " + missing.first())
                Perms.openFix(ctx)
            }
        } else {
            showToast("تعذّرت الجدولة")
        }
    }

    // ───────── مواعيد المكالمات (ساعة + أيام) ─────────

    fun openTimedEditor(t: Timed?) {
        tId = t?.id ?: 0
        tHour = t?.hour ?: 5
        tMinute = t?.minute ?: 0
        tDays = t?.days ?: 0
        timedEditorOpen = true
    }

    fun saveTimed() {
        val list = Store.timed(ctx)
        val id = if (tId == 0) Store.nextTimedId(ctx) else tId
        val t = Timed(id, tHour, tMinute, tDays, true)
        val newList = if (tId == 0) list + t else list.map { if (it.id == id) t else it }
        Store.saveTimed(ctx, newList)
        TimedCalls.schedule(ctx, t)
        timed = newList
        timedEditorOpen = false
        val missing = Perms.missing(ctx)
        if (missing.isEmpty()) {
            showToast("تم الحفظ. هترن " + untilLabel(t))
        } else {
            showToast("تم الحفظ، بس لازم تفعّل: " + missing.first())
            Perms.openFix(ctx)
        }
    }

    fun setTimedEnabled(id: Int, on: Boolean) {
        val list = Store.timed(ctx).map { if (it.id == id) it.copy(enabled = on) else it }
        Store.saveTimed(ctx, list)
        list.firstOrNull { it.id == id }?.let { TimedCalls.schedule(ctx, it) }
        timed = list
    }

    fun deleteTimed(id: Int) {
        TimedCalls.cancel(ctx, id)
        val list = Store.timed(ctx).filter { it.id != id }
        Store.saveTimed(ctx, list)
        timed = list
    }

    fun cancelSchedule() {
        Scheduler.cancel(ctx)
        scheduledAt = 0L
    }

    /**
     * الشاشة اتقفلت (زرار القفل/الباور): لو المكالمة بترن بتتقفل (رفض)، ولو شغالة بتتنهي.
     * بنرجّع true علشان الـ Activity تصحّي الشاشة تاني، وشاشة "Call Ended" بتظهر ثانيتين
     * وبعدين التطبيق يقفل لوحده. لو الاتصال الوارد لسه صامت (ماحدش لمس) مابنعملش حاجة.
     */
    fun onScreenOff(): Boolean {
        return when {
            // في المكالمة: ينهيها ويظهر "Call Ended" ثانيتين
            screen == Screen.Active -> { end(); false }
            // قبل الرد أو غيره: مابنعملش حاجة، الشاشة تتطفي عادي
            else -> false
        }
    }

    /**
     * زرار خفض الصوت: بيرفض المكالمة وهي بترن، أو بينهي المكالمة الشغالة.
     * بيرجّع true لو استخدمناه (فالنظام مايغيّرش الصوت).
     */
    fun onVolumeDown(): Boolean {
        if (settingsOpen || editorOpen || timedEditorOpen) return false
        return when {
            screen == Screen.Incoming && ringing -> { decline(); true }
            screen == Screen.Active -> { end(); true }
            else -> false
        }
    }

    // ───────── زرار الرجوع ─────────

    fun onBack(finish: () -> Unit) {
        when {
            timedEditorOpen -> timedEditorOpen = false
            editorOpen -> cancelEditor()
            settingsOpen -> closeSettings()
            screen == Screen.Incoming -> decline()
            screen == Screen.Ended -> closeApp()
            else -> Unit // وأنت في مكالمة: الرجوع متعطّل، لازم تقفل بزرار الإنهاء
        }
    }
}
