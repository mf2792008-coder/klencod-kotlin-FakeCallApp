package com.fakecall.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

/**
 * بيكتب المكالمة في سجل مكالمات الهاتف الحقيقي.
 * محتاج صلاحية WRITE_CALL_LOG (بيطلبها عند أول استخدام).
 * لو الصلاحية اترفضت بيتجاهل بهدوء.
 */
object CallLogWriter {

    fun write(
        ctx: Context,
        name: String,
        number: String,
        type: Int,          // CallLog.Calls.INCOMING_TYPE أو MISSED_TYPE
        durationSecs: Int
    ) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) return

        try {
            val cv = ContentValues().apply {
                put(CallLog.Calls.NUMBER,       number.ifBlank { "0000000000" })
                put(CallLog.Calls.CACHED_NAME,  name)
                put(CallLog.Calls.TYPE,         type)
                put(CallLog.Calls.DATE,         System.currentTimeMillis())
                put(CallLog.Calls.DURATION,     durationSecs)
                put(CallLog.Calls.NEW,          1)
                put(CallLog.Calls.CACHED_NUMBER_TYPE, 2) // Mobile
            }
            ctx.contentResolver.insert(CallLog.Calls.CONTENT_URI, cv)
        } catch (_: Exception) { }
    }

    /** مكالمة وردت وأُجيب عليها */
    fun writeAnswered(ctx: Context, name: String, number: String, durationSecs: Int) =
        write(ctx, name, number, CallLog.Calls.INCOMING_TYPE, durationSecs)

    /** مكالمة وردت ولم يُجب عليها (رفض أو مفوّتة) */
    fun writeMissed(ctx: Context, name: String, number: String) =
        write(ctx, name, number, CallLog.Calls.MISSED_TYPE, 0)
}
