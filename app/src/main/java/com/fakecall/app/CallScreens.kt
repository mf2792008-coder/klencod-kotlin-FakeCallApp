package com.fakecall.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.hypot
import kotlin.math.max
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Blue = Color(0xFF1A6FD4)
private val Green = Color(0xFF2E7D52)
private val Red = Color(0xFFB33B2E)

private fun fmt(sec: Int): String =
    String.format(java.util.Locale.US, "%02d:%02d", sec / 60, sec % 60)

/** ألوان الخلفية الجاهزة (أعلى، وسط، أسفل). رقم 0 هو الشكل الأصلي. */
val BG_PRESETS: List<List<Color>> = listOf(
    listOf(Color(0xFF6EE8C8), Color(0xFF4AB8E8), Color(0xFF2A6DE8)),
    listOf(Color(0xFFB57BEE), Color(0xFF7A5CE8), Color(0xFF3B2FA0)),
    listOf(Color(0xFFFFB36B), Color(0xFFFF6B6B), Color(0xFFB02A5B)),
    listOf(Color(0xFF3A3F47), Color(0xFF22262C), Color(0xFF0E1013)),
    listOf(Color(0xFF9BE15D), Color(0xFF3CB371), Color(0xFF0B6E4F)),
    listOf(Color(0xFFFF9ACB), Color(0xFFE0529C), Color(0xFF8B1E6B)),
    listOf(Color(0xFF4FC3F7), Color(0xFF1976D2), Color(0xFF0D2B6B))
)

/** خلفية الشاشة حسب البروفايل: صورة من المعرض، أو لون جاهز. */
@Composable
private fun BgLayer(s: AppState, p: Profile, active: Boolean) {
    val ctx = LocalContext.current
    val img = remember(p.id, s.photoVersion) { Store.loadBitmap(Store.bgFile(ctx, p.id)) }
    val preset = BG_PRESETS[p.bg.coerceIn(0, BG_PRESETS.lastIndex)]
    Box(Modifier.fillMaxSize()) {
        if (img != null) {
            Image(
                bitmap = img.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (active) 0.45f else 0.22f)))
        } else if (active && p.bg == 0) {
            Box(Modifier.fillMaxSize().background(Color(0xFF1A3A8A)))
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to preset[0], 0.28f to preset[1], 1f to preset[2])
                )
            )
            if (active) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
    }
}

// ───────────────────────── أجزاء مشتركة ─────────────────────────

@Composable
private fun NumTag(text: String = "1") {
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Blue)
            .padding(horizontal = 7.dp, vertical = 1.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** صورة المتصل الكبيرة. لو مفيش صورة مابتظهرش حاجة، فالشكل بيفضل زي القديم. */
@Composable
private fun BigAvatar(s: AppState, p: Profile, border: Color = Color.White.copy(alpha = 0.55f)) {
    val ctx = LocalContext.current
    val bmp = remember(p.id, s.photoVersion) { Store.loadBitmap(Store.photoFile(ctx, p.id)) }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(3.dp, border, CircleShape)
        )
        Spacer(Modifier.height(12.dp))
    }
}

/** اسم المتصل: ضغطة مطولة = الإعدادات، ضغطة عادية (وهو بيرن) = تبدأ الرنة. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallerName(s: AppState, name: String, size: TextUnit, color: Color) {
    val haptic = LocalHapticFeedback.current
    Text(
        text = name,
        color = color,
        fontSize = size,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (s.screen == Screen.Incoming) s.startRing() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    s.openSettings()
                }
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun CircleBtn(bg: Color, size: Int, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ───────────────────────── الاتصال الوارد ─────────────────────────

@Composable
fun IncomingScreen(s: AppState) {
    val p = s.active
    Box(Modifier.fillMaxSize()) {
        BgLayer(s, p, false)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(50.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumTag()
                Text("Incoming call", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(40.dp))
            BigAvatar(s, p)
            CallerName(s, p.name, 34.sp, Color.White)
            if (p.number.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Mobile ${p.number}",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 85.dp)
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            var anyPressed by remember { mutableStateOf(false) }
            CallButton(s, Ic.PHONE, Green, anyPressed, { anyPressed = it }) { s.accept() }
            // الرفض: يخرج من التطبيق مباشرة من غير شاشة "Call Ended"
            CallButton(s, Ic.DECLINE, Red, anyPressed, { anyPressed = it }) { s.closeApp() }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .width(110.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.45f))
            )
            Spacer(Modifier.height(7.dp))
            Text("Send message", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * زرار الرد/الرفض: الزرار نفسه ثابت ما بيتحركش أبدًا.
 * حواليه دايرة بيضا شفافة بتتموّج (تكبر وتصغر). أول ما تلمسه الدايرة بتثبت على أكبر حجم،
 * والزر المقابل الدايرة البيضا بتاعته بتروح صفر طول ما إنت ضاغط.
 * ولما تسحب دايرة سودا (شفافيتها أقل من البيضا) بتطلع فوق البيضا وتكبر مع السحب لحد ما تملى
 * مساحة البيضا. لو سيبت وهي مليانة المكالمة تفتح (أو تتقفل) من غير ما حاجة تغطي الشاشة.
 * لو سيبت قبل كده السواد يرجع والدايرة البيضا ترجع تتموّج.
 */
@Composable
private fun CallButton(
    s: AppState, ic: Ic, accent: Color,
    anyPressed: Boolean, setAnyPressed: (Boolean) -> Unit,
    onTrigger: () -> Unit
) {
    val trigger by rememberUpdatedState(onTrigger)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val limit = with(density) { 70.dp.toPx() }                    // مسافة السحب اللي بتملّي السودا
    val btnR = with(density) { 39.dp.toPx() }                     // نص قطر الزرار
    val whiteBase = with(density) { WHITE_RADIUS.toPx() }         // نص قطر الدايرة البيضا (قبل التكبير)
    val whiteMax = whiteBase * PULSE_PRESS                        // أقصى حجم للدايرة السودا عند السحب
    var pressed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var blackR by remember { mutableStateOf(0f) }                 // نص قطر الدايرة السودا بالبكسل
    val scale = remember { Animatable(PULSE_MIN) }

    // التموّج: يكبر ويصغر بشكل دائم. وقت اللمس يثبت على أكبر حجم.
    // لو الزر التاني اتضغط، الدايرة البيضا هنا تروح صفر.
    val otherPressed = anyPressed && !pressed
    LaunchedEffect(pressed, otherPressed) {
        if (pressed) {
            setAnyPressed(true)
            scale.animateTo(PULSE_PRESS, tween(120))
        } else if (otherPressed) {
            scale.animateTo(0f, tween(150))
        } else {
            while (true) {
                scale.animateTo(PULSE_MAX, tween(900, easing = FastOutSlowInEasing))
                scale.animateTo(PULSE_MIN, tween(900, easing = FastOutSlowInEasing))
            }
        }
    }

    Box(
        Modifier
            .size(120.dp)
            // الدايرة البيضا (شفافية 60%) وفوقها السودا، الاتنين ورا الزرار
            .drawBehind {
                if (scale.value > 0f) {
                    drawCircle(Color.White.copy(alpha = WHITE_ALPHA), radius = whiteBase * scale.value)
                }
                if (blackR > 0f) drawCircle(Color.Black.copy(alpha = BLACK_ALPHA), radius = blackR)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!done) {
                        pressed = true
                        var total = Offset.Zero
                        drag(down.id) { ch ->
                            total += ch.positionChange()
                            val p = (total.getDistance() / limit).coerceIn(0f, 1f)
                            blackR = if (p <= 0f) 0f else btnR + (whiteMax - btnR) * p
                            ch.consume()
                        }
                        if (total.getDistance() >= limit) {
                            // السودا مليانة مساحة البيضا: المكالمة تفتح (أو التطبيق يقفل)
                            done = true
                            trigger()
                        } else {
                            val from = blackR
                            pressed = false
                            setAnyPressed(false)   // الزر التاني يرجع يتموّج
                            scope.launch {
                                Animatable(from).animateTo(0f, tween(200)) { blackR = value }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // الزرار نفسه: ثابت، أخضر للرد وأحمر للرفض
        Box(
            Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            // الرد = سماعة واقفة، الرفض = سماعة نايمة
            Glyph(ic, Color.White, 38.dp)
        }
    }
}

/** نص قطر الدايرة البيضا قبل التكبير، وأقصى تكبير ليها، وكثافتها. */
private val WHITE_RADIUS = 39.dp   // = نص قطر الزرار، فالتموج واضح
private const val PULSE_MIN  = 1.20f   // الدايرة +20% من حجمها الأصلي
private const val PULSE_MAX  = 1.50f   // الدايرة +50% من حجمها الأصلي
private const val PULSE_PRESS = 3.0f   // عند الضغط تكبر 3x من حجمها الأصلي
private const val WHITE_ALPHA = 0.6f

/** كثافة الدايرة السودا. */
private const val BLACK_ALPHA = 0.4f

// ───────────────────────── المكالمة الجارية ─────────────────────────

@Composable
private fun PanelBtn(label: String, ic: Ic, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (on) Green else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Glyph(ic, if (on) Color.White else Color(0xFF111111), 28.dp)
        }
        Text(
            label,
            color = if (on) Green else Color(0xFF111111),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ActiveScreen(s: AppState) {
    val p = s.active
    val on = remember(s.callId) { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(s.callId) {
        while (true) {
            delay(1000)
            s.seconds++
        }
    }

    fun toggle(key: String, onMsg: String, offMsg: String, action: (Boolean) -> Boolean = { true }) {
        val next = !(on[key] ?: false)
        if (action(next)) {
            on[key] = next
            s.showToast(if (next) onMsg else offMsg)
        } else {
            on[key] = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        BgLayer(s, p, true)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(50.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                NumTag()
                Glyph(Ic.PHONE, Color.White, 15.dp)
                Text(fmt(s.seconds), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(26.dp))
            BigAvatar(s, p)
            CallerName(s, p.name, 34.sp, Color.White)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFFD2D6E4))
                .padding(horizontal = 20.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth()) {
                PanelBtn("Record", Ic.REC, on["rec"] == true, {
                    toggle("rec", "جاري التسجيل...", "تم إيقاف التسجيل")
                }, Modifier.weight(1f))
                PanelBtn("Hold", Ic.HOLD, on["hold"] == true, {
                    toggle("hold", "المكالمة قيد الانتظار", "تم استئناف المكالمة") { v -> s.audio.setHold(v); true }
                }, Modifier.weight(1f))
                PanelBtn("Bluetooth", Ic.BT, on["bt"] == true, {
                    val was = on["bt"] == true
                    toggle("bt", "البلوتوث مفعل", "تم إيقاف البلوتوث") { v -> s.audio.setBluetooth(v) }
                    if (!was && on["bt"] != true) s.showToast("مفيش جهاز بلوتوث متصل")
                }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth()) {
                PanelBtn("Speaker", Ic.SPEAKER, on["spk"] == true, {
                    toggle("spk", "مكبر الصوت مفعل", "تم إيقاف مكبر الصوت") { v -> s.audio.setSpeaker(v); true }
                }, Modifier.weight(1f))
                PanelBtn("Mute", Ic.MUTE, on["mute"] == true, {
                    toggle("mute", "تم كتم الصوت", "تم إلغاء الكتم") { v -> s.audio.setMuted(v); true }
                }, Modifier.weight(1f))
                PanelBtn("Keypad", Ic.KEYPAD, on["key"] == true, {
                    toggle("key", "لوحة المفاتيح مفعلة", "تم إخفاء لوحة المفاتيح")
                }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(26.dp))
            CircleBtn(Red, 68, { s.end() }) { Glyph(Ic.DECLINE, Color.White, 30.dp) }
        }
    }
}

// ───────────────────────── انتهاء المكالمة ─────────────────────────

@Composable
fun EndedScreen(s: AppState) {
    val p = s.active

    // شاشة "Call Ended" بتظهر ثانيتين وبعدين التطبيق يقفل ويتشال من الأخيرة
    LaunchedEffect(s.settingsOpen) {
        if (!s.settingsOpen) {
            delay(2000)
            s.closeApp()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(50.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumTag()
                Glyph(Ic.DECLINE, Red, 20.dp)
                Text(fmt(s.finalSeconds), color = Red, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Call Ended", color = Red, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(30.dp))
            BigAvatar(s, p, Color(0xFFE5E7EE))
            CallerName(s, p.name, 38.sp, Color.Black)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 200.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { s.openSettings() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Glyph(Ic.PERSON, Color(0xFF111111), 36.dp)
            Text("View Contact", color = Color(0xFF111111), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                CircleBtn(Color(0xFF25D366), 72, { s.restart() }) { Glyph(Ic.PHONE, Color.White, 34.dp) }
                Badge("1", Blue)
            }
            Box {
                CircleBtn(Color(0xFF25D366), 72, { s.restart() }) { Glyph(Ic.PHONE, Color.White, 34.dp) }
                Badge("2", Blue)
            }
            CircleBtn(Color(0xFF00BCD4), 72, {}) { Glyph(Ic.MSG, Color.White, 34.dp) }
            CircleBtn(Blue, 72, {}) { Glyph(Ic.VIDEO, Color.White, 34.dp) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Badge(text: String, bg: Color) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .size(20.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}