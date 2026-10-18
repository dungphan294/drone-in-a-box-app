import SwiftUI
import DockKit

/// Diagnostic control screen for the dock.
///
/// The key field may be left blank: the firmware's auth check is compiled out
/// at the moment (DOCK_REQUIRE_AUTH 0), so the dock ignores the signature
/// headers and signing buys nothing. Fill it in and requests are signed again,
/// which is what the dock will want once that flag goes back to 1.
///
/// The key is typed in rather than paired over BLE: DockBle has no iOS
/// implementation yet (task T24). Get it from the dock's serial console with
/// the 'K' command.
struct ContentView: View {
    @State private var host = "192.168.4.1"
    @State private var key = ""
    @State private var client: DockClient?
    @State private var status: DockStatus?
    @State private var message = "Enter the dock address and press Connect. "
        + "The key is only needed once the dock's auth check is switched back on."
    @State private var poller: Task<Void, Never>?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Dock").font(.largeTitle.bold())
                Text("signed control · diagnostic build")
                    .font(.caption).foregroundStyle(.secondary)

                TextField("192.168.4.1 or dock.local", text: $host)
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)

                SecureField("device key — blank while the dock's auth is off", text: $key)
                    .textFieldStyle(.roundedBorder)

                Button("Connect") { connect() }
                    .buttonStyle(.borderedProminent)

                Text(levelText)
                    .font(.system(size: 44, weight: .semibold, design: .rounded))
                    .foregroundStyle(levelColour)
                    .frame(maxWidth: .infinity, alignment: .center)

                if let s = status {
                    Text("dir \(s.dir)   speed \(s.speed)%   duty \(s.dutyA)/\(s.dutyB)")
                        .font(.caption.monospaced()).foregroundStyle(.secondary)
                }

                section("POSITION", [("Home ↓", "h"), ("− Step", "-"), ("Step +", "+"), ("Top ↑", "t")])
                section("MANUAL", [("↓ Retract", "r"), ("STOP", "s"), ("↑ Extend", "e")])
                section("SPEED", [("50%", "5"), ("70%", "7"), ("90%", "9"), ("100%", "0")])

                Text(message).font(.footnote).foregroundStyle(.secondary)
            }
            .padding()
        }
        .onDisappear { stopPolling() }
    }

    private var levelText: String {
        guard let s = status else { return "—" }
        if s.moving { return "· · ·" }
        return s.levelKnown ? "\(s.level) / \(s.levels)" : "not homed"
    }

    private var levelColour: Color {
        guard let s = status, s.levelKnown, !s.moving else { return .orange }
        return .green
    }

    private func section(_ title: String, _ buttons: [(String, String)]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption2.bold()).foregroundStyle(.secondary)
            HStack {
                ForEach(buttons, id: \.0) { label, command in
                    Button(label) { send(Character(command)) }
                        .buttonStyle(.bordered)
                        .frame(maxWidth: .infinity)
                }
            }
        }
    }

    private func connect() {
        do {
            let c = try DockClient.companion.fromHexKey(hexKey: key, host: host)
            client = c
            startPolling(c)
            message = key.trimmingCharacters(in: .whitespaces).isEmpty
                ? "Connecting unsigned — the dock's auth check is off."
                : "Connecting with a signed key."
        } catch {
            message = "\(error.localizedDescription)"
        }
    }

    /// The poll IS the dead-man switch: if it stops while the motor runs, the
    /// dock stops the actuator itself after about 1.5 s.
    private func startPolling(_ c: DockClient) {
        stopPolling()
        poller = Task {
            while !Task.isCancelled {
                do {
                    status = try await c.status()
                    message = status?.event ?? ""
                } catch {
                    message = "No contact: \(error.localizedDescription)"
                }
                try? await Task.sleep(for: .milliseconds(400))
            }
        }
    }

    private func stopPolling() {
        poller?.cancel()
        poller = nil
    }

    private func send(_ command: Character) {
        guard let c = client else { message = "Connect first."; return }
        Task {
            do {
                status = try await c.send(command: command.utf16.first.map { unichar($0) } ?? 0)
                message = status?.event ?? ""
            } catch {
                message = "Failed: \(error.localizedDescription)"
            }
        }
    }
}
