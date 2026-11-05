package com.fakecall.app

import android.app.TimePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Green = Color(0xFF27AE60)
private val SkyBlue = Color(0xFF4AB8E8)
private val Blue = Color(0xFF1A6FD4)

private fun darkBg() = Brush.verticalGradient(listOf(Color(0xFF0F1B3D), Color(0xFF14307A)))

// ───────────────────────── مكونات صغيرة ─────────────────────────

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun Btn(text: String, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SmBtn(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Header(title: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 38.dp, bottom = 10.dp, start = 12.dp, end = 12.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text("✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(
            title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = SkyBlue,
    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
    cursorColor = Color.White,
    focusedLabelColor = Color.White.copy(alpha = 0.8f),
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
    focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f)
)

/** دايرة الصورة المصغّرة (أو أول حرف من الاسم). */
@Composable
private fun Thumb(s: AppState, p: Profile, dp: Int) {
    val ctx = LocalContext.current
    val bmp = remember(p.id, s.photoVersion) {
        Store.loadBitmap(Store.thumbFile(ctx, p.id)) ?: Store.loadBitmap(Store.photoFile(ctx, p.id))
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(dp.dp).clip(CircleShape)
        )
    } else {
        Box(
            Modifier.size(dp.dp).clip(CircleShape).background(Blue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                p.name.trim().take(1).ifEmpty { "?" },
                color = Color.White,
                fontSize = (dp / 2.4f).sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun PermWarning(missing: List<String>, onFix: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE67E22).copy(alpha = 0.25f))
            .padding(12.dp)
    ) {
        Text(
            "عشان المكالمة تفتح لوحدها وإنت في تطبيق تاني أو الموبايل مقفول، فعّل: " + missing.joinToString("، "),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        SmBtn("تفعيل", onFix)
    }
}

private fun fmtLeft(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%02d:%02d", m, s)
}

// ───────────────────────── الإعدادات ─────────────────────────

@Composable
fun SettingsScreen(s: AppState, pickRing: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var vibrate by remember { mutableStateOf(Store.vibrate(ctx)) }
    var redial by remember { mutableStateOf(Store.redial(ctx)) }
    val missing = remember(s.permVersion) { Perms.missing(ctx) }
    var delaySec by remember { mutableStateOf(Store.delay(ctx).toString()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(s.scheduledAt) {
        while (s.scheduledAt > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
        if (s.scheduledAt in 1..now) s.scheduledAt = 0L
    }

    val list = s.profiles.filter {
        q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) || it.number.contains(q.trim())
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(darkBg())
            .pointerInput(Unit) { detectTapGestures { } }
            .imePadding()
    ) {
        Header("الإعدادات") { s.closeSettings() }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                Text("البروفايلات", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("ابحث بالاسم أو الرقم") },
                    colors = fieldColors()
                )
                Spacer(Modifier.height(12.dp))
                if (list.isEmpty()) {
                    Text(
                        "مفيش نتائج",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
                    )
                }
                list.forEach { p ->
                    val isActive = p.id == s.activeId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isActive) Green.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.2f))
                            .border(1.dp, if (isActive) Green else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { s.select(p.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Thumb(s, p, 44)
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                            val sub = p.number + if (p.voiceName.isNotEmpty()) "   🔊 ${p.voiceName}" else ""
                            if (sub.isNotBlank()) {
                                Text(sub, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        if (isActive) Text("✓", color = Green, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable { s.openEditor(p.id) },
                            contentAlignment = Alignment.Center
                        ) { Text("✎", color = Color.White, fontSize = 15.sp) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Btn("＋ إضافة بروفايل", Color.White.copy(alpha = 0.15f)) { s.openEditor(null) }
            }

            Card {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("النغمة", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (s.ringName.isEmpty()) "الافتراضية (ماريمبا)" else s.ringName,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmBtn("اختيار") { pickRing() }
                        SmBtn("الافتراضية") { s.resetRing() }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("اهتزاز مع الرنة", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = vibrate,
                        onCheckedChange = { vibrate = it; s.setVibrate(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Green
                        )
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("رنين تاني بعد الرفض", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "لو رفضت المكالمة وهي بترن، ترن تاني بعد 30 ثانية",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = redial,
                        onCheckedChange = { redial = it; s.setRedial(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Green
                        )
                    )
                }
            }

            Card {
                Text("مكالمات بالموعد", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "مثلًا كل يوم الساعة 5 الصبح، أو يوم الثلاثاء الساعة 5",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                if (missing.isNotEmpty()) PermWarning(missing) { Perms.openFix(ctx) }
                s.timed.forEach { t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.2f))
                            .clickable { s.openTimedEditor(t) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                timeLabel(t.hour, t.minute),
                                color = Color.White.copy(alpha = if (t.enabled) 1f else 0.5f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                daysLabel(t.days) + " · " + (if (t.enabled) untilLabel(t) else "متوقف"),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = t.enabled,
                            onCheckedChange = { s.setTimedEnabled(t.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Green
                            )
                        )
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable { s.deleteTimed(t.id) },
                            contentAlignment = Alignment.Center
                        ) { Text("✕", color = Color.White, fontSize = 14.sp) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Btn("＋ إضافة موعد", Color.White.copy(alpha = 0.15f)) { s.openTimedEditor(null) }
            }

            Card {
                Text("جدولة مكالمة", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10 to "10 ث", 30 to "30 ث", 60 to "دقيقة", 300 to "5 دقايق", 900 to "15 دقيقة").forEach { (v, label) ->
                        val sel = delaySec.toIntOrNull() == v
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (sel) Blue else Color.Transparent)
                                .border(1.dp, if (sel) Blue else Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                                .clickable { delaySec = v.toString() }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أو مدة مخصصة:", color = Color.White, fontSize = 13.sp)
                    OutlinedTextField(
                        value = delaySec,
                        onValueChange = { v -> delaySec = v.filter { it.isDigit() }.take(5) },
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        colors = fieldColors()
                    )
                    Text("ثانية", color = Color.White, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                if (s.scheduledAt > now) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Green.copy(alpha = 0.22f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "المكالمة هتيجي بعد " + fmtLeft(((s.scheduledAt - now) / 1000).coerceAtLeast(0)),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        SmBtn("إلغاء") { s.cancelSchedule() }
                    }
                }
                if (missing.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE67E22).copy(alpha = 0.25f))
                            .padding(12.dp)
                    ) {
                        Text(
                            "عشان المكالمة المجدولة تفتح لوحدها وإنت في تطبيق تاني أو الموبايل مقفول، فعّل: " +
                                missing.joinToString("، "),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        SmBtn("تفعيل") { Perms.openFix(ctx) }
                    }
                }
                Btn("جدولة المكالمة", Green) {
                    s.schedule(delaySec.toIntOrNull() ?: 10)
                    delaySec = Store.delay(ctx).toString()
                }
            }
        }
    }
}

// ───────────────────────── محرر البروفايل ─────────────────────────

@Composable
fun EditorScreen(
    s: AppState,
    pickPhoto: (String) -> Unit,
    pickVoice: (String) -> Unit,
    pickBg: (String) -> Unit,
    pickProfileRing: (String) -> Unit
) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) {
        if (confirm) {
            delay(3000)
            confirm = false
        }
    }

    val photo = remember(s.edId, s.photoVersion) { Store.loadBitmap(Store.photoFile(ctx, s.edId)) }
    val hasBg = remember(s.edId, s.photoVersion) { Store.bgFile(ctx, s.edId).exists() }

    Column(
        Modifier
            .fillMaxSize()
            .background(darkBg())
            .pointerInput(Unit) { detectTapGestures { } }
            .imePadding()
    ) {
        Header(if (s.edIsNew) "بروفايل جديد" else "تعديل بروفايل") { s.cancelEditor() }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .clickable { pickPhoto(s.edId) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (photo != null) {
                            Image(
                                bitmap = photo.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("＋ صورة", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (photo != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "حذف الصورة",
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { s.clearPhoto() }.padding(6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = s.edName,
                    onValueChange = { s.edName = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("اسم المتصل") },
                    colors = fieldColors()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = s.edNumber,
                    onValueChange = { s.edNumber = it.take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("الرقم") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("صوت المتصل", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (s.edVoiceName.isEmpty()) "لا يوجد" else s.edVoiceName,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmBtn("اختيار") { pickVoice(s.edId) }
                        SmBtn("حذف") { s.clearVoice() }
                    }
                }
            }

            Card {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("نغمة الرنة", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (s.edRingName.isEmpty()) "الافتراضية" else s.edRingName,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmBtn("اختيار") { pickProfileRing(s.edId) }
                        if (s.edRingName.isNotEmpty()) SmBtn("حذف") { s.resetProfileRing() }
                    }
                }
            }

            Card {
                Text("الخلفية", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BG_PRESETS.forEachIndexed { i, colors ->
                        val sel = !hasBg && s.edBg == i
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(colors))
                                .border(
                                    if (sel) 3.dp else 1.dp,
                                    if (sel) Color.White else Color.White.copy(alpha = 0.3f),
                                    CircleShape
                                )
                                .clickable { s.selectBg(i) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmBtn("صورة من المعرض") { pickBg(s.edId) }
                    if (hasBg) SmBtn("حذف صورة الخلفية") { s.clearBgImage() }
                }
            }

            Btn("حفظ", Green) { s.saveEditor() }
            if (!s.edIsNew && s.profiles.size > 1) {
                Btn(if (confirm) "اضغط تاني للتأكيد" else "حذف البروفايل", Color(0xFFC0392B)) {
                    if (confirm) {
                        confirm = false
                        s.deleteEditor()
                    } else {
                        confirm = true
                    }
                }
            }
        }
    }
}


// ───────────────────────── محرر الموعد ─────────────────────────

@Composable
fun TimedEditorScreen(s: AppState) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(darkBg())
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Header(if (s.tId == 0) "موعد جديد" else "تعديل الموعد") { s.timedEditorOpen = false }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                Text("الوقت", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .clickable {
                            TimePickerDialog(
                                ctx,
                                { _, h, m ->
                                    s.tHour = h
                                    s.tMinute = m
                                },
                                s.tHour, s.tMinute, false
                            ).show()
                        }
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        timeLabel(s.tHour, s.tMinute),
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("اضغط على الوقت لتغييره", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }

            Card {
                Text("الأيام", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "من غير ما تختار أي يوم: ترن مرة واحدة في أقرب موعد",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DAY_ORDER.forEach { bit ->
                        val sel = (s.tDays and (1 shl bit)) != 0
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (sel) Blue else Color.Transparent)
                                .border(1.dp, if (sel) Blue else Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                                .clickable { s.tDays = s.tDays xor (1 shl bit) }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(DAY_NAMES[bit], color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmBtn("كل يوم") { s.tDays = 127 }
                    SmBtn("مسح الأيام") { s.tDays = 0 }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    daysLabel(s.tDays) + " · هترن " + untilLabel(Timed(0, s.tHour, s.tMinute, s.tDays, true)),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Btn("حفظ", Green) { s.saveTimed() }
        }
    }
}
