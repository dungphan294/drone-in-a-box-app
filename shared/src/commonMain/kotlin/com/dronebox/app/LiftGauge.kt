package com.dronebox.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/**
 * The lift as a stack of levels, 0 (retracted, "home") at the bottom.
 *
 * Green fill up to a known level; amber everywhere while the dock does not
 * know where it is. While the motor runs, a pulse travels the way the lift is
 * going -- so "moving" reads at a glance from across the bench, not from a
 * label.
 */
@Composable
fun LiftGauge(status: DockStatus?, modifier: Modifier = Modifier) {
    val levels = (status?.levels ?: 5).coerceIn(1, 20)
    val known = status?.levelKnown == true
    val level = status?.level ?: -1
    val dir = status?.dir ?: "s"
    val running = dir == "e" || dir == "r" || status?.moving == true

    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
    )
    // Which slot the pulse is on, counting in the direction of travel.
    val sweep = floor(phase * (levels + 1)).toInt().coerceIn(0, levels)
    val pulseAt = if (dir == "r") levels - sweep else sweep

    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (slot in levels downTo 0) {
            val target = when {
                running && slot == pulseAt -> Dock.Warn
                running -> Dock.Warn.copy(alpha = 0.14f)
                known && slot == level -> Dock.Ok
                known && slot < level -> Dock.Ok.copy(alpha = 0.38f)
                known -> Dock.Raised
                else -> Dock.Warn.copy(alpha = 0.08f)
            }
            val fill by animateColorAsState(target, tween(if (running) 120 else 260))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    slot.toString(),
                    color = if (known && slot == level && !running) Dock.Ok else Dock.Faint,
                    fontFamily = Dock.Mono,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(fill)
                        .then(
                            if (!known && !running) Modifier.border(1.dp, Dock.Warn.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            else Modifier
                        ),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 28.dp, top = 2.dp)) {
            Text("home", color = Dock.Faint, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
        }
    }
}

/** The headline number beside the gauge, with what it means underneath. */
@Composable
fun LevelReadout(status: DockStatus?, modifier: Modifier = Modifier) {
    val (big, bigColor, caption) = when {
        status == null -> Triple("–", Dock.Faint, "Waiting for the dock")
        status.moving -> Triple(if (status.levelKnown) "${status.level}" else "…", Dock.Warn, "Moving to a level")
        !status.levelKnown -> Triple("?", Dock.Warn, "Not homed. Press Home or Top so the dock knows where it is.")
        else -> Triple("${status.level}", Dock.Ok, "of ${status.levels} levels")
    }
    Column(modifier) {
        Text("LEVEL", color = Dock.Dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
        Text(big, color = bigColor, fontSize = 72.sp, fontWeight = FontWeight.Bold, fontFamily = Dock.Mono, lineHeight = 80.sp)
        Text(caption, color = Dock.Dim, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable
@ReadOnlyComposable
fun directionLabel(dir: String?): Pair<String, Color> = when (dir) {
    "e" -> "Extending" to Dock.Motion
    "r" -> "Retracting" to Dock.Motion
    "s", null -> "Stopped" to Dock.Dim
    "A", "B" -> "Raw hold $dir" to Dock.Warn
    else -> "dir $dir" to Dock.Dim
}
