package com.dronebox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Where a dock is usually found, offered as one-tap choices. */
private fun suggestions(): List<Pair<String, String>> = buildList {
    pageOrigin()?.let { add("This server" to it) }
    add("Dock Wi-Fi" to "192.168.4.1")
    add("Same network" to "dock.local")
}

/** A blank key is valid: it means "the open bench API", which is what the firmware runs today. */
internal fun keyProblem(key: String): String? {
    val k = key.trim()
    if (k.isEmpty()) return null
    if (k.length != 64 || !k.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
        return "A device key is 64 hex characters. Leave it blank while the dock's auth is off."
    }
    return null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    initialHost: String,
    initialKey: String,
    onConnect: (host: String, key: String) -> Unit,
) {
    var host by remember { mutableStateOf(initialHost) }
    var key by remember { mutableStateOf(initialKey) }
    var showKey by remember { mutableStateOf(initialKey.isNotBlank()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var pairing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun connect() {
        problem = when {
            host.isBlank() -> "Enter the dock's address."
            else -> keyProblem(key)
        }
        if (problem == null) onConnect(host.trim(), key.trim())
    }

    fun pair() {
        if (host.isBlank()) { problem = "Enter the dock's address first."; return }
        pairing = true
        problem = null
        scope.launch {
            val client = dockClient("", host.trim())
            try {
                key = client.pair()
                problem = "Paired. The key is held in memory for this session only."
            } catch (e: CancellationException) {
                throw e
            } catch (e: DockRefused) {
                problem = "Pairing refused: ${e.hint ?: e.body}"
            } catch (e: Throwable) {
                problem = "Could not reach the dock: ${e.describe()}"
            } finally {
                client.close()
                pairing = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Dock.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Dock.Ok.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.Lift, Dock.Ok, size = 30.dp) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("DRONE-IN-A-BOX", color = Dock.Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
                Text("Dock remote", color = Dock.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Join the dock's Wi-Fi, \"drone-in-a-box\", or a network the dock has joined. Then connect.",
                    color = Dock.Dim, fontSize = 15.sp, lineHeight = 21.sp,
                )
            }

            Panel("Address") {
                DockField(
                    value = host,
                    onValueChange = { host = it; problem = null },
                    label = "Dock address",
                    placeholder = "192.168.4.1 or dock.local",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { connect() }),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, value) in suggestions()) {
                        Chip("$label · ${value.substringAfter("://")}", selected = host.trim() == value) {
                            host = value
                            problem = null
                        }
                    }
                }
                if (isBrowser) {
                    Text(
                        "In a browser, requests go through this page's server, which forwards them to the address above. " +
                            "The dock itself sends no CORS headers, so the browser cannot call it directly.",
                        color = Dock.Faint, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
            }

            Panel(
                "Device key",
                trailing = {
                    Chip(if (showKey) "Hide" else "Optional", selected = showKey) { showKey = !showKey }
                },
            ) {
                Text(
                    "The dock's request signing is switched off on the current firmware, so no key is needed. " +
                        "Once it is back on, press BOOT on the dock and pair within 60 s.",
                    color = Dock.Dim, fontSize = 13.sp, lineHeight = 18.sp,
                )
                if (showKey) {
                    DockField(
                        value = key,
                        onValueChange = { key = it; problem = null },
                        label = "64-character hex key",
                        placeholder = "blank = unsigned",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    )
                    ActionButton(
                        if (pairing) "Pairing…" else "Pair with the dock",
                        onClick = ::pair,
                        primary = false,
                        enabled = !pairing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            problem?.let {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Dock.Raised).padding(12.dp),
                ) {
                    Text(it, color = if (it.startsWith("Paired")) Dock.Ok else Dock.Warn, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }

            ActionButton("Connect", onClick = ::connect, modifier = Modifier.fillMaxWidth().height(56.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Dock.Warn))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Bench use. While the API is open, anyone on the dock's network can drive the actuator.",
                    color = Dock.Faint, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
