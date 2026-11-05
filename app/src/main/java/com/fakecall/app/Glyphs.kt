package com.fakecall.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** نفس أيقونات التصميم القديم (SVG بمقياس 24×24) مرسومة بـ Canvas من غير مكتبات أيقونات. */
enum class Ic { PHONE, DECLINE, REC, HOLD, BT, SPEAKER, MUTE, KEYPAD, MSG, VIDEO, PERSON }

private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

private val PHONE_PATH by lazy {
    parse("M6.6 10.8c1.4 2.8 3.8 5.1 6.6 6.6l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.58.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1C10.29 21 3 13.71 3 5c0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.36.03.76-.24 1.02L6.6 10.8z")
}
private val SPEAKER_PATH by lazy {
    parse("M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z")
}
private val MUTE_PATH by lazy {
    parse("M19 11h-1.7c0 .74-.16 1.43-.43 2.05l1.23 1.23c.56-.98.9-2.09.9-3.28zm-4.02.17c0-.06.02-.11.02-.17V5c0-1.66-1.34-3-3-3S9 3.34 9 5v.18l5.98 5.99zM4.27 3L3 4.27l6.01 6.01V11c0 1.66 1.33 3 2.99 3 .22 0 .44-.03.65-.08l1.66 1.66c-.71.33-1.5.52-2.31.52-2.76 0-5.3-2.1-5.3-5.1H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c.91-.13 1.77-.45 2.54-.9L19.73 21 21 19.73 4.27 3z")
}
private val MSG_PATH by lazy {
    parse("M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z")
}
private val VIDEO_PATH by lazy {
    parse("M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z")
}
private val PERSON_PATH by lazy {
    parse("M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v2.4h19.2v-2.4c0-3.2-6.4-4.8-9.6-4.8z")
}
private val BT_PATH by lazy {
    Path().apply {
        moveTo(6.5f, 6.5f)
        lineTo(17.5f, 17.5f)
        lineTo(12f, 23f)
        lineTo(12f, 1f)
        lineTo(17.5f, 6.5f)
        lineTo(6.5f, 17.5f)
    }
}

@Composable
fun Glyph(kind: Ic, color: Color, dp: Dp) {
    Canvas(Modifier.size(dp)) {
        val k = size.width / 24f
        scale(k, Offset.Zero) {
            when (kind) {
                Ic.PHONE -> drawPath(PHONE_PATH, color)
                Ic.DECLINE -> {
                    drawPath(PHONE_PATH, color)
                    drawLine(color, Offset(22f, 2f), Offset(2f, 22f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                }
                Ic.REC -> {
                    drawRoundRect(color, Offset(3f, 3f), Size(7f, 18f), CornerRadius(2f))
                    drawRoundRect(color, Offset(14f, 3f), Size(7f, 18f), CornerRadius(2f))
                }
                Ic.HOLD -> {
                    drawRoundRect(color, Offset(6f, 4f), Size(4f, 16f), CornerRadius(1.5f))
                    drawRoundRect(color, Offset(14f, 4f), Size(4f, 16f), CornerRadius(1.5f))
                }
                Ic.BT -> drawPath(
                    BT_PATH, color,
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                Ic.SPEAKER -> drawPath(SPEAKER_PATH, color)
                Ic.MUTE -> drawPath(MUTE_PATH, color)
                Ic.KEYPAD -> {
                    for (x in listOf(5f, 12f, 19f)) {
                        for (y in listOf(5f, 12f, 19f)) {
                            drawCircle(color, radius = 1.6f, center = Offset(x, y))
                        }
                    }
                }
                Ic.MSG -> drawPath(MSG_PATH, color)
                Ic.VIDEO -> drawPath(VIDEO_PATH, color)
                Ic.PERSON -> drawPath(PERSON_PATH, color)
            }
        }
    }
}
