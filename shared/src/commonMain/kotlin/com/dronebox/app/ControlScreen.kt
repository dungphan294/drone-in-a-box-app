package com.dronebox.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/** Speed presets, as the dock's own page offers them. '0' means 100 %, not zero. */
private val SPEEDS = listOf(50 to '5', 70 to '7', 90 to '9', 100 to '0')

private enum class Page { Control, Settings }

@Composable
fun ControlScreen(
    session: DockSession,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onDisconnect: () -> Unit,
) {
    val state by session.state.collectAsState()
    var page by remember { mutableStateOf(Page.Control) }

    // Poll only while the app is visible. Going to the background stops the
    // poll, and with it the dead-man, so the dock stops a running motor on its
    // own -- the safe outcome when nobody is looking at the controls.
    //
    // This sits above both pages on purpose: opening Settings must not stop
    // the poll, or the dock would halt a move just because the operator
    // looked at the Wi-Fi form.
    LifecycleStartEffect(session) {
        session.start()
        onStopOrDispose { session.pause() }
    }

    // The system back gesture closes Settings rather than leaving the app.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = page == Page.Settings,
        onBackCompleted = { page = Page.Control },
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(Dock.Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth >= 860.dp
        when (page) {
            Page.Control -> ControlPage(state, session, wide, onSettings = { page = Page.Settings })
            Page.Settings -> SettingsScreen(
                state, session, themeMode, onThemeMode,
                onBack = { page = Page.Control }, onDisconnect = onDisconnect,
            )
        }
    }
}

@Composable
private fun ControlPage(state: DockState, session: DockSession, wide: Boolean, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 1180.dp).fillMaxWidth().padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TopBar(state, onSettings)
                LinkBanner(state)
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            HeroPanel(state)
                            ActivityPanel(state)
                        }
                        Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            DrivePanel(state, session)
                            PositionPanel(state, session)
                            SpeedPanel(state, session)
                        }
                    }
                } else {
                    HeroPanel(state)
                    PositionPanel(state, session)
                    SpeedPanel(state, session)
                    ActivityPanel(state)
                }
            }
        }
        // On a phone the drive controls never scroll away: STOP is always
        // one tap from wherever the operator has scrolled to.
        if (!wide) DriveBar(state, session)
    }
}

@Composable
private fun TopBar(state: DockState, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Dock.Ok.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.Lift, Dock.Ok, size = 24.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Dock", color = Dock.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                state.host.substringAfter("://"),
                color = Dock.Dim, fontSize = 12.sp, fontFamily = Dock.Mono,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        LinkPill(state.link)
        Spacer(Modifier.width(8.dp))
        IconButton(Glyph.Gear, "Settings", onSettings)
    }
}

@Composable
internal fun LinkPill(link: LinkState) = when (link) {
    LinkState.Connecting -> Pill("Connecting", Dock.Warn)
    LinkState.Live -> Pill("Live", Dock.Ok)
    LinkState.Lost -> Pill("No contact", Dock.Stop)
    LinkState.Paused -> Pill("Paused", Dock.Dim)
}

@Composable
private fun LinkBanner(state: DockState) {
    val (text, color) = when {
        state.link == LinkState.Lost ->
            "Lost contact. Commands may not arrive. The dock stops the actuator by itself 1.5 s after its last poll." to Dock.Stop
        state.link == LinkState.Connecting && state.status == null ->
            "Reaching ${state.host.substringAfter("://")}…" to Dock.Warn
        else -> return
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.10f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, color = color, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun HeroPanel(state: DockState) {
    val s = state.status
    Panel(
        "Lift",
        trailing = {
            if (s != null) {
                val (label, color) = directionLabel(s.dir)
                Pill(label, color)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiftGauge(s, Modifier.weight(1f))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LevelReadout(s)
                if (s != null) Pill("${s.speed}% speed", Dock.Text, dot = false)
            }
        }
    }
}

@Composable
private fun PositionPanel(state: DockState, session: DockSession) {
    // While a timed move runs the dock refuses a step, and a repeated Home or
    // Top would only restart the move -- so the buttons say so up front.
    val moving = state.status?.moving == true
    val enabled = !state.controlsFrozen && !moving
    Panel("Position") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton("Home", Glyph.ToBottom, { session.send('h') }, Modifier.weight(1f), enabled = enabled)
            ControlButton("Step −", Glyph.Minus, { session.send('-') }, Modifier.weight(1f), enabled = enabled)
            ControlButton("Step +", Glyph.Plus, { session.send('+') }, Modifier.weight(1f), enabled = enabled)
            ControlButton("Top", Glyph.ToTop, { session.send('t') }, Modifier.weight(1f), enabled = enabled)
        }
        Text(
            if (moving) "Moving to a level. Wait for it, or press STOP."
            else "Home and Top run into the end stop to find the level. Steps need a known level.",
            color = Dock.Faint, fontSize = 12.sp, lineHeight = 17.sp,
        )
    }
}

/** Retract / STOP / Extend. STOP is never disabled -- nothing on this screen may take it away. */
@Composable
private fun DriveButtons(state: DockState, session: DockSession, height: androidx.compose.ui.unit.Dp) {
    val dir = state.status?.dir
    val enabled = !state.controlsFrozen
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ControlButton(
            "Retract", Glyph.Down, { session.send('r') }, Modifier.weight(1f),
            tone = if (dir == "r") Tone.Active else Tone.Neutral, enabled = enabled, height = height,
        )
        ControlButton("STOP", Glyph.Stop, { session.send('s') }, Modifier.weight(1.3f), tone = Tone.Stop, height = height)
        ControlButton(
            "Extend", Glyph.Up, { session.send('e') }, Modifier.weight(1f),
            tone = if (dir == "e") Tone.Active else Tone.Neutral, enabled = enabled, height = height,
        )
    }
}

@Composable
private fun DrivePanel(state: DockState, session: DockSession) {
    Panel("Drive") {
        DriveButtons(state, session, 84.dp)
        Text(
            "Extend and Retract run until STOP, an end stop, or the dead-man. A manual move forgets the level.",
            color = Dock.Faint, fontSize = 12.sp, lineHeight = 17.sp,
        )
    }
}

@Composable
private fun DriveBar(state: DockState, session: DockSession) {
    Column(Modifier.fillMaxWidth().background(Dock.Surface)) {
        HorizontalDivider(color = Dock.Line)
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            DriveButtons(state, session, 72.dp)
        }
    }
}

@Composable
private fun SpeedPanel(state: DockState, session: DockSession) {
    val current = state.status?.speed
    // Timed moves run at 100 %; the dock refuses a change mid-move.
    val enabled = !state.controlsFrozen && state.status?.moving != true
    Panel("Speed", trailing = { if (current != null) Text("$current%", color = Dock.Dim, fontFamily = Dock.Mono, fontSize = 13.sp) }) {
        Row(
            Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)
                .clip(RoundedCornerShape(12.dp)).background(Dock.Raised).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for ((pct, cmd) in SPEEDS) {
                val selected = current == pct
                Surface(
                    onClick = { session.send(cmd) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = if (selected) Dock.Ok.copy(alpha = 0.16f) else Dock.Raised,
                    border = if (selected) BorderStroke(1.dp, Dock.Ok.copy(alpha = 0.7f)) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$pct%",
                            color = if (selected) Dock.Ok else Dock.Dim,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        Text(
            "Below 50% the actuator stalls, so the dock clamps there. Home, Top and steps always run at 100%.",
            color = Dock.Faint, fontSize = 12.sp, lineHeight = 17.sp,
        )
    }
}

@Composable
private fun ActivityPanel(state: DockState) {
    Panel("Activity") {
        if (state.log.isEmpty()) {
            Text("Nothing yet.", color = Dock.Faint, fontSize = 13.sp)
        }
        state.log.forEachIndexed { i, line ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.padding(top = 6.dp).size(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i == 0) Dock.Ok else Dock.Line),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    line,
                    color = if (i == 0) Dock.Text else Dock.Dim,
                    fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
        }
    }
}
