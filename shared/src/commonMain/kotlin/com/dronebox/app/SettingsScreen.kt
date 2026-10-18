package com.dronebox.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Everything that is not driving the lift: where the dock is, its Wi-Fi, and
 * the motor's finer settings and readings.
 *
 * The poll keeps running while this page is open -- it is owned by
 * ControlScreen, above both pages -- and STOP stays on screen, because a move
 * started on the control page is still running here.
 */
@Composable
fun SettingsScreen(
    state: DockState,
    session: DockSession,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(Glyph.Back, "Back", onBack)
                    Spacer(Modifier.width(12.dp))
                    Text("Settings", color = Dock.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    LinkPill(state.link)
                }
                ConnectionPanel(state, onDisconnect)
                WifiPanel(state, session)
                MotorPanel(state, session)
                AppearancePanel(themeMode, onThemeMode)
            }
        }
        StopBar(session)
    }
}

@Composable
private fun ConnectionPanel(state: DockState, onDisconnect: () -> Unit) {
    val net = state.net
    Panel("Connection") {
        Column {
            ReadoutRow("Address", state.host.substringAfter("://"))
            ReadoutRow(
                "Dock network",
                when {
                    net == null -> "–"
                    net.staJoined -> "${net.staIp} · dock.local"
                    else -> "AP only · ${net.apSsid}"
                },
            )
            ReadoutRow(
                "Requests",
                if (state.signed) "signed" else "unsigned (open API)",
                if (state.signed) Dock.Ok else Dock.Warn,
            )
        }
        ActionButton("Disconnect", onClick = onDisconnect, primary = false, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WifiPanel(state: DockState, session: DockSession) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val moving = state.status?.let { it.dir != "s" } == true

    Panel("Dock Wi-Fi", trailing = { GlyphIcon(Glyph.Wifi, Dock.Dim, size = 18.dp) }) {
        Text(
            "Join the dock to your phone's hotspot so this phone keeps its mobile data. " +
                "The dock's own access point stays up either way.",
            color = Dock.Dim, fontSize = 13.sp, lineHeight = 18.sp,
        )
        ActionButton(
            when {
                state.scanning -> "Scanning…"
                state.networks == null -> "Scan for networks"
                else -> "Scan again"
            },
            onClick = session::scan,
            primary = false,
            enabled = !state.scanning,
            modifier = Modifier.fillMaxWidth(),
        )
        if (moving) {
            Text("The dock will refuse to scan while the actuator runs.", color = Dock.Warn, fontSize = 12.sp)
        }

        state.networks?.takeIf { it.isNotEmpty() }?.let { list ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Dock.Raised)) {
                list.forEachIndexed { i, ap ->
                    if (i > 0) HorizontalDivider(color = Dock.Line)
                    val picked = ssid == ap.ssid
                    // Picking only fills the name in; the field stays the one
                    // thing that gets submitted, as on the dock's own page.
                    Surface(
                        onClick = { ssid = ap.ssid; password = "" },
                        color = if (picked) Dock.Ok.copy(alpha = 0.08f) else Dock.Raised,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SignalBars(ap.bars, if (picked) Dock.Ok else Dock.Text)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                ap.ssid.ifEmpty { "(hidden)" },
                                color = if (picked) Dock.Ok else Dock.Text, fontSize = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            Text(
                                (if (ap.open) "open" else ap.auth) + " · ${ap.rssi} dBm",
                                color = Dock.Faint, fontSize = 12.sp, fontFamily = Dock.Mono,
                            )
                        }
                    }
                }
            }
        }

        DockField(
            ssid, { ssid = it }, "Network name",
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        )
        DockField(
            password, { password = it }, "Password",
            placeholder = "blank if open",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        )
        ActionButton(
            if (state.joining) "Saving…" else "Join network",
            onClick = { session.join(ssid, password); password = "" },
            enabled = !state.joining && ssid.isNotBlank() && !state.scanning,
            modifier = Modifier.fillMaxWidth(),
        )
        // The answer to a join lands in the session log; show it here, since
        // the Activity panel is on the other page.
        wifiNotice(state.message)?.let { (text, good) ->
            Text(text, color = if (good) Dock.Ok else Dock.Warn, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun MotorPanel(state: DockState, session: DockSession) {
    val s = state.status
    Panel("Motor") {
        Column {
            val (dirLabel, dirColor) = directionLabel(s?.dir)
            ReadoutRow("Direction", if (s == null) "–" else dirLabel, if (s == null) Dock.Faint else dirColor)
            ReadoutRow("Speed", s?.let { "${it.speed}%" } ?: "–")
            ReadoutRow("Duty A / B", s?.let { "${it.dutyA} / ${it.dutyB}" } ?: "–")
            ReadoutRow("Decay", s?.let { if (it.slowDecay) "slow" else "fast" } ?: "–") {
                Chip("Toggle", selected = false) { session.send('d') }
            }
        }
        Text(
            "Slow decay brakes in the PWM off-phase; fast decay lets the motor coast.",
            color = Dock.Faint, fontSize = 12.sp, lineHeight = 17.sp,
        )
    }
}

@Composable
private fun AppearancePanel(mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    Panel("Appearance") {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Dock.Raised).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (option in ThemeMode.entries) {
                val selected = option == mode
                Surface(
                    onClick = { onMode(option) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = if (selected) Dock.Ok.copy(alpha = 0.16f) else Dock.Raised,
                    border = if (selected) BorderStroke(1.dp, Dock.Ok.copy(alpha = 0.7f)) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            option.label,
                            color = if (selected) Dock.Ok else Dock.Dim,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        Text(
            "System follows this device's light or dark setting. Light reads better in direct sun.",
            color = Dock.Faint, fontSize = 12.sp, lineHeight = 17.sp,
        )
    }
}

@Composable
private fun StopBar(session: DockSession) {
    Column(Modifier.fillMaxWidth().background(Dock.Surface)) {
        HorizontalDivider(color = Dock.Line)
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            ControlButton(
                "STOP", Glyph.Stop, { session.send('s') },
                Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                tone = Tone.Stop, height = 64.dp,
            )
        }
    }
}

/** The Wi-Fi panel's own messages, picked out of the session log: text, and whether it is good news. */
private fun wifiNotice(message: String): Pair<String, Boolean>? = when {
    message.startsWith("Joining") -> message to true
    message.endsWith("found.") -> message to true
    message.startsWith("Scanning") -> message to true
    message.startsWith("Scan ") || message.startsWith("Lost contact during the scan") ||
        message.startsWith("No networks found") || message.startsWith("Could not save") ||
        message == "Enter a network name." -> message to false
    else -> null
}
