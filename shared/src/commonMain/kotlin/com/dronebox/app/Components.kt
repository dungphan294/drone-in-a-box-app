package com.dronebox.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A titled card. Every group of controls on both screens sits in one. */
@Composable
fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Dock.Surface,
        border = BorderStroke(1.dp, Dock.Line),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Dock.Dim)
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

enum class Tone { Neutral, Active, Stop, Primary }

/**
 * A big, square-ish control button with a drawn glyph.
 *
 * Sized for a gloved thumb on a phone held one-handed at the dock: 64 dp is
 * well past the 48 dp minimum, because a missed STOP costs more than space.
 */
@Composable
fun ControlButton(
    label: String,
    glyph: Glyph?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    enabled: Boolean = true,
    height: Dp = 68.dp,
) {
    val accent = when (tone) {
        Tone.Neutral -> Dock.Text
        Tone.Active -> Dock.Motion
        Tone.Stop -> Dock.Stop
        Tone.Primary -> Dock.Ok
    }
    val fill = when (tone) {
        Tone.Stop -> Dock.Stop.copy(alpha = 0.14f)
        Tone.Active -> Dock.Motion.copy(alpha = 0.12f)
        else -> Dock.Raised
    }
    val edge = if (tone == Tone.Neutral) Dock.Line else accent.copy(alpha = 0.7f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height).alpha(if (enabled) 1f else 0.4f),
        shape = RoundedCornerShape(12.dp),
        color = fill,
        border = BorderStroke(if (tone == Tone.Stop) 2.dp else 1.dp, edge),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            if (glyph != null) {
                GlyphIcon(glyph, accent, size = if (tone == Tone.Stop) 24.dp else 20.dp)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                label,
                color = accent,
                fontSize = if (tone == Tone.Stop) 15.sp else 13.sp,
                fontWeight = if (tone == Tone.Neutral) FontWeight.Medium else FontWeight.Bold,
                letterSpacing = if (tone == Tone.Stop) 1.5.sp else 0.sp,
                maxLines = 1,
            )
        }
    }
}

/** A wide action button, for Connect, Pair, Scan and Join. */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val accent = if (primary) Dock.Ok else Dock.Text
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).alpha(if (enabled) 1f else 0.45f),
        shape = RoundedCornerShape(12.dp),
        color = if (primary) Dock.Ok.copy(alpha = 0.14f) else Dock.Raised,
        border = BorderStroke(1.dp, if (primary) Dock.Ok.copy(alpha = 0.8f) else Dock.Line),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

/** A small rounded tag: link state, direction, auth mode. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, dot: Boolean = true) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Dock.Ok.copy(alpha = 0.14f) else Dock.Raised,
        border = BorderStroke(1.dp, if (selected) Dock.Ok.copy(alpha = 0.7f) else Dock.Line),
    ) {
        Text(
            text,
            color = if (selected) Dock.Ok else Dock.Dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun DockField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Dock.Faint) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Dock.Ok,
            unfocusedBorderColor = Dock.Line,
            focusedLabelColor = Dock.Ok,
            unfocusedLabelColor = Dock.Dim,
            cursorColor = Dock.Ok,
            focusedContainerColor = Dock.Raised,
            unfocusedContainerColor = Dock.Raised,
            disabledContainerColor = Dock.Raised,
        ),
    )
}

/** One label/value line of the telemetry table. */
@Composable
fun ReadoutRow(label: String, value: String, valueColor: Color = Dock.Text, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Dock.Dim, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 14.sp, fontFamily = Dock.Mono, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun SignalBars(bars: Int, color: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width(4.dp)
                    .height((4 + i * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i <= bars) color else Dock.Line),
            )
        }
    }
}

/** A 44 dp square button holding one glyph, for the top bars. */
@Composable
fun IconButton(glyph: Glyph, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp).semantics { contentDescription = label },
        shape = RoundedCornerShape(12.dp),
        color = Dock.Raised,
        border = BorderStroke(1.dp, Dock.Line),
    ) {
        Box(contentAlignment = Alignment.Center) { GlyphIcon(glyph, Dock.Text, size = 20.dp) }
    }
}

val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
