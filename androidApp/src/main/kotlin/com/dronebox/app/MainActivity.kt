package com.dronebox.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import kotlinx.coroutines.*

/**
 * Diagnostic control screen for the dock.
 *
 * Plain views on purpose: this exists to prove the signed client reaches the
 * dock end to end, not to be the product UI. The key is typed in rather than
 * paired over BLE because DockBle has no platform implementation yet (T24);
 * get it from the dock's serial console with 'K'.
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var poller: Job? = null
    private var client: DockClient? = null

    private lateinit var hostField: EditText
    private lateinit var keyField: EditText
    private lateinit var levelView: TextView
    private lateinit var statusView: TextView
    private lateinit var eventView: TextView

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(buildUi())
    }

    /** Stop polling when the screen is not in front: see the warning in connect(). */
    override fun onStop() {
        super.onStop()
        poller?.cancel()
        poller = null
        eventView.text = "Polling stopped. The dock stops the actuator ~1.5 s " +
            "after the last poll, by design."
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun connect() {
        // Blank is a valid answer. The firmware's auth check is compiled out
        // for now (DOCK_REQUIRE_AUTH 0), so the dock ignores the signature
        // headers entirely and a key buys nothing -- refusing to connect
        // without one only kept the app out of a dock that answers anyone.
        // Typing a key still signs, so this keeps working when auth returns.
        val key = keyField.text.toString().trim()
        if (key.isNotEmpty() && (key.length != 64 || !key.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' })) {
            eventView.text = "A key must be 64 hex characters -- press 'K' on the dock's serial " +
                "console. Leave it blank to connect unsigned."
            return
        }
        client = DockClient(
            if (key.isEmpty()) null else key.hexToBytes(),
            hostField.text.toString().trim(),
        )
        eventView.text = if (key.isEmpty()) {
            "Connecting unsigned -- the dock's auth check is off."
        } else {
            "Connecting with a signed key."
        }

        poller?.cancel()
        poller = scope.launch {
            // This poll IS the dead-man switch. If it stops while the motor
            // runs, the dock stops the actuator itself after ~1.5 s.
            while (isActive) {
                try {
                    render(client!!.status())
                } catch (e: DockUnauthorized) {
                    eventView.text = if (keyField.text.toString().isBlank()) {
                        "Dock requires a signed request. Enter the key from 'K' and reconnect."
                    } else {
                        "Refused: ${e.message}. Wrong key, or the dock was erased."
                    }
                } catch (e: DockRefused) {
                    eventView.text = "Dock refused: ${e.body}"
                } catch (e: Exception) {
                    eventView.text = "No contact: ${e.message ?: e::class.simpleName}"
                    levelView.text = "—"
                }
                delay(400)
            }
        }
    }

    private fun send(command: Char) {
        val c = client ?: run { eventView.text = "Connect first."; return }
        scope.launch {
            try {
                render(c.send(command))
            } catch (e: DockUnauthorized) {
                eventView.text = "Refused: ${e.message}"
            } catch (e: Exception) {
                eventView.text = "Failed: ${e.message ?: e::class.simpleName}"
            }
        }
    }

    private fun render(s: DockStatus) {
        levelView.text = when {
            s.moving -> "· · ·"
            !s.levelKnown -> "not homed"
            else -> "${s.level} / ${s.levels}"
        }
        levelView.setTextColor(if (s.levelKnown && !s.moving) GREEN else AMBER)
        statusView.text = "dir ${s.dir}   speed ${s.speed}%   duty ${s.dutyA}/${s.dutyB}"
        eventView.text = s.event
    }

    // --- view construction ---------------------------------------------------

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(40, 60, 40, 40)
        }

        root.addView(label("Dock", 26f, Color.WHITE))
        root.addView(label("diagnostic build", 13f, GREY))

        hostField = field("192.168.4.1")
        keyField = field("device key — blank while the dock's auth is off").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(hostField)
        root.addView(keyField)
        root.addView(button("Connect") { connect() })

        levelView = label("—", 40f, AMBER).apply { gravity = Gravity.CENTER }
        root.addView(levelView)
        statusView = label("", 14f, GREY)
        root.addView(statusView)

        root.addView(label("POSITION", 11f, GREY))
        root.addView(row("Home ↓" to 'h', "− Step" to '-', "Step +" to '+', "Top ↑" to 't'))
        root.addView(label("MANUAL", 11f, GREY))
        root.addView(row("↓ Retract" to 'r', "STOP" to 's', "↑ Extend" to 'e'))
        root.addView(label("SPEED", 11f, GREY))
        root.addView(row("50%" to '5', "70%" to '7', "90%" to '9', "100%" to '0'))

        eventView = label(
            "Enter the dock address and press Connect. The key is only needed once " +
                "the dock's auth check is switched back on.", 13f, GREY,
        )
        root.addView(eventView)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#111111"))
            addView(root)
        }
    }

    private fun label(text: String, size: Float, colour: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(colour)
        setPadding(0, 16, 0, 8)
    }

    private fun field(hint: String) = EditText(this).apply {
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(GREY)
        setSingleLine()
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun row(vararg buttons: Pair<String, Char>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEach { (text, command) ->
            addView(button(text) { send(command) },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    private companion object {
        val GREEN = Color.parseColor("#4ade80")
        val AMBER = Color.parseColor("#fbbf24")
        val GREY = Color.parseColor("#888888")
    }
}

private fun String.hexToBytes() = ByteArray(length / 2) {
    substring(it * 2, it * 2 + 2).toInt(16).toByte()
}
