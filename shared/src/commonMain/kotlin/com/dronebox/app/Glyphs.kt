package com.dronebox.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Icons drawn rather than typed. The browser build ships a minimal font, and
 * arrows typed as text come out as boxes there; drawn, they look the same on
 * every platform and need no icon library.
 */
enum class Glyph { Up, Down, ToTop, ToBottom, Plus, Minus, Stop, Wifi, Lift, Gear, Back }

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (glyph) {
            Glyph.Up -> arrow(color, stroke, up = true)
            Glyph.Down -> arrow(color, stroke, up = false)
            Glyph.ToTop -> {
                bar(color, stroke, y = w * 0.14f)
                chevron(color, stroke, up = true, cy = w * 0.58f)
            }
            Glyph.ToBottom -> {
                bar(color, stroke, y = w * 0.86f)
                chevron(color, stroke, up = false, cy = w * 0.42f)
            }
            Glyph.Plus -> {
                line(color, Offset(w * 0.2f, w / 2), Offset(w * 0.8f, w / 2), stroke)
                line(color, Offset(w / 2, w * 0.2f), Offset(w / 2, w * 0.8f), stroke)
            }
            Glyph.Minus -> line(color, Offset(w * 0.2f, w / 2), Offset(w * 0.8f, w / 2), stroke)
            Glyph.Stop -> drawRoundRect(
                color, topLeft = Offset(w * 0.22f, w * 0.22f), size = Size(w * 0.56f, w * 0.56f),
                cornerRadius = CornerRadius(w * 0.08f),
            )
            Glyph.Wifi -> {
                for (i in 0..2) {
                    val r = w * (0.18f + i * 0.17f)
                    drawArc(
                        color, startAngle = 225f, sweepAngle = 90f, useCenter = false,
                        topLeft = Offset(w / 2 - r, w * 0.78f - r), size = Size(r * 2, r * 2), style = stroke,
                    )
                }
                drawCircle(color, radius = w * 0.07f, center = Offset(w / 2, w * 0.8f))
            }
            Glyph.Gear -> {
                // Eight teeth around a ring, with a hub.
                val c = Offset(w / 2, w / 2)
                for (i in 0 until 8) {
                    val a = i * PI.toFloat() / 4
                    val dx = cos(a)
                    val dy = sin(a)
                    line(color, Offset(c.x + dx * w * 0.30f, c.y + dy * w * 0.30f), Offset(c.x + dx * w * 0.42f, c.y + dy * w * 0.42f), stroke)
                }
                drawCircle(color, radius = w * 0.27f, center = c, style = stroke)
                drawCircle(color, radius = w * 0.09f, center = c, style = stroke)
            }
            Glyph.Back -> {
                val path = Path().apply {
                    moveTo(w * 0.62f, w * 0.2f)
                    lineTo(w * 0.32f, w * 0.5f)
                    lineTo(w * 0.62f, w * 0.8f)
                }
                drawPath(path, color, style = stroke)
            }
            Glyph.Lift -> {
                // The mark: a platform on a mast, rising out of a box.
                drawRoundRect(
                    color, topLeft = Offset(w * 0.12f, w * 0.56f), size = Size(w * 0.76f, w * 0.32f),
                    cornerRadius = CornerRadius(w * 0.08f), style = stroke,
                )
                line(color, Offset(w / 2, w * 0.56f), Offset(w / 2, w * 0.28f), stroke)
                line(color, Offset(w * 0.26f, w * 0.2f), Offset(w * 0.74f, w * 0.2f), stroke)
            }
        }
    }
}

private fun DrawScope.line(color: Color, a: Offset, b: Offset, stroke: Stroke) =
    drawLine(color, a, b, strokeWidth = stroke.width, cap = StrokeCap.Round)

private fun DrawScope.bar(color: Color, stroke: Stroke, y: Float) =
    line(color, Offset(size.width * 0.2f, y), Offset(size.width * 0.8f, y), stroke)

private fun DrawScope.chevron(color: Color, stroke: Stroke, up: Boolean, cy: Float) {
    val w = size.width
    val d = if (up) -1 else 1
    val path = Path().apply {
        moveTo(w * 0.22f, cy - d * w * 0.14f)
        lineTo(w / 2, cy + d * w * 0.14f)
        lineTo(w * 0.78f, cy - d * w * 0.14f)
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.arrow(color: Color, stroke: Stroke, up: Boolean) {
    val w = size.width
    val tip = if (up) w * 0.16f else w * 0.84f
    val tail = if (up) w * 0.84f else w * 0.16f
    line(color, Offset(w / 2, tip), Offset(w / 2, tail), stroke)
    chevron(color, stroke, up, cy = if (up) w * 0.32f else w * 0.68f)
}
