package com.fakecall.app

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var audio: CallAudio
    private lateinit var app: AppState

    /** البروفايل اللي بنختار له صورة/صوت (بيتحفظ لحد ما الاختيار يرجع). */
    private var pickTarget = ""

    /** آخر ضغطة خفض صوت استخدمناها (علشان نبلع الرفع بتاعها كمان). */
    private var volumeUsed = false

    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val id = pickTarget
                Thread {
                    try {
                        Store.savePhoto(this, uri, id)
                        runOnUiThread { app.photoVersion++ }
                    } catch (_: Exception) {
                        runOnUiThread { app.showToast("تعذّر قراءة الصورة") }
                    }
                }.start()
            }
        }

    private val bgPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val id = pickTarget
                Thread {
                    try {
                        Store.saveBackground(this, uri, id)
                        runOnUiThread { app.photoVersion++ }
                    } catch (_: Exception) {
                        runOnUiThread { app.showToast("تعذّر قراءة الصورة") }
                    }
                }.start()
            }
        }

    private val voicePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val id = pickTarget
                Thread {
                    try {
                        Store.copyUri(this, uri, Store.voiceFile(this, id))
                        val name = Store.displayName(this, uri)
                        runOnUiThread {
                            app.edVoiceName = name
                            app.showToast("تم اختيار الصوت")
                        }
                    } catch (_: Exception) {
                        runOnUiThread { app.showToast("تعذّر قراءة الملف") }
                    }
                }.start()
            }
        }

    private val ringPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                Thread {
                    try {
                        Store.copyUri(this, uri, Store.ringFile(this))
                        val name = Store.displayName(this, uri)
                        Store.setRingName(this, name)
                        runOnUiThread {
                            app.ringName = name
                            app.showToast("تم تغيير النغمة")
                        }
                    } catch (_: Exception) {
                        runOnUiThread { app.showToast("تعذّر قراءة الملف") }
                    }
                }.start()
            }
        }

    private val profileRingPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                Thread {
                    try {
                        val id = pickTarget
                        Store.copyUri(this, uri, Store.profileRingFile(this, id))
                        val name = Store.displayName(this, uri)
                        Store.setProfileRingName(this, id, name)
                        runOnUiThread {
                            app.edRingName = name
                            app.showToast("تم تغيير نغمة البروفايل")
                        }
                    } catch (_: Exception) {
                        runOnUiThread { app.showToast("تعذّر قراءة الملف") }
                    }
                }.start()
            }
        }

    /** قفل الشاشة بزرار الباور = قفل المكالمة. */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (::app.isInitialized) app.onScreenOff()
        }
    }

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ظهور فوق شاشة القفل + إبقاء الشاشة شغالة + ملء الشاشة
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideBars()

        audio = CallAudio(applicationContext)
        app = AppState(applicationContext, audio)
        app.closeApp = { finishAndRemoveTask() } // يقفل التطبيق ويشيله من الأخيرة
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                BackHandler { app.onBack { finishAndRemoveTask() } }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FakeCallApp(
                        s = app,
                        pickPhoto = { id -> pickTarget = id; photoPicker.launch("image/*") },
                        pickVoice = { id -> pickTarget = id; voicePicker.launch("audio/*") },
                        pickBg = { id -> pickTarget = id; bgPicker.launch("image/*") },
                        pickRing = { ringPicker.launch("audio/*") },
                        pickProfileRing = { id -> pickTarget = id; profileRingPicker.launch("audio/*") }
                    )
                }
            }
        }

        askPermissions()
        handleIntent(intent)
    }

    /** محاولة إبقاء الشاشة شغالة بعد ضغط الباور (النظام بيطفيها، فبنصحّيها تاني). مش مضمونة على كل الأجهزة. */
    @Suppress("DEPRECATION")
    private fun keepScreenOn() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "FakeCall:keepOn"
            ).acquire(3000L)
        } catch (_: Exception) {
        }
    }

    /** زرار خفض الصوت = رفض/إنهاء المكالمة (مضمون، لأن زرار الباور مابيوصلش للتطبيقات). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && ::app.isInitialized) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                volumeUsed = app.onVolumeDown()
            }
            if (volumeUsed) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onResume() {
        super.onResume()
        // بنحدّث حالة الأذونات بعد الرجوع من صفحة الإعدادات
        if (::app.isInitialized) app.permVersion++
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
        }
        audio.end()
        super.onDestroy()
    }

    private fun hideBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun askPermissions() {
        val need = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (need.isNotEmpty()) permissions.launch(need.toTypedArray())
    }

    /** لو اتفتحنا من المنبّه المجدول: المكالمة ترن على طول. وإلا: الاتصال الوارد صامت لحد أول لمسة. */
    private fun handleIntent(i: Intent?) {
        if (i == null || !i.getBooleanExtra(CallAlarmReceiver.EXTRA_INCOMING, false)) return
        i.removeExtra(CallAlarmReceiver.EXTRA_INCOMING)

        if (Build.VERSION.SDK_INT >= 27) {
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(CallAlarmReceiver.NOTIF_ID)
        } catch (_: Exception) {
        }
        Store.setScheduledAt(this, 0L)
        app.showIncoming(true)
    }
}

@Composable
private fun FakeCallApp(
    s: AppState,
    pickPhoto: (String) -> Unit,
    pickVoice: (String) -> Unit,
    pickBg: (String) -> Unit,
    pickRing: () -> Unit,
    pickProfileRing: (String) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // أول لمسة في أي مكان على الاتصال الوارد = تبدأ الرنة
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (s.screen == Screen.Incoming && !s.ringing && !s.settingsOpen && !s.editorOpen) {
                        s.startRing()
                    }
                })
            }
    ) {
        when (s.screen) {
            Screen.Incoming -> IncomingScreen(s)
            Screen.Active -> ActiveScreen(s)
            Screen.Ended -> EndedScreen(s)
        }


        if (s.settingsOpen) SettingsScreen(s, pickRing)
        if (s.editorOpen) EditorScreen(s, pickPhoto, pickVoice, pickBg, pickProfileRing)
        if (s.timedEditorOpen) TimedEditorScreen(s)

        s.toast?.let { (id, msg) ->
            LaunchedEffect(id) {
                delay(2500)
                if (s.toast?.first == id) s.toast = null
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}