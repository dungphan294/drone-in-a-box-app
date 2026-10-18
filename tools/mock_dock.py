#!/usr/bin/env python3
"""A stand-in for the dock's webserver, for running the app without the board.

Mirrors components/webserver/webserver.c in drone-in-a-box with the auth
check off (DOCK_REQUIRE_AUTH 0): the same endpoints, the same JSON, the same
refusals, and the same timing -- including the dead-man, which stops a running
actuator 1.5 s after the last /api/status poll.

    python3 tools/mock_dock.py                 # http://0.0.0.0:8000
    python3 tools/mock_dock.py --port 8001 --pairing

Reach it from:
    desktop / iOS simulator   localhost:8000
    Android emulator          10.0.2.2:8000
    browser                   DOCK_URL=http://localhost:8000 ./gradlew :webApp:wasmJsBrowserDevelopmentRun

Like the firmware it sends no CORS headers -- adding them here would hide the
problem the web build has to solve against the real dock.
"""

import argparse
import json
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

# From the firmware headers.
LEVELS = 5
TRAVEL_MS = 10_000
STEP_MS = TRAVEL_MS // LEVELS
HOME_MS = TRAVEL_MS * 13 // 10
SPEED_MIN_PCT = 50
STEP_SPEED_PCT = 100
DUTY_FULL_SCALE = 1 << 8
SESSION_TIMEOUT_MS = 1500
SCAN_MS = 2500

NETWORKS = [
    {"ssid": "Operator hotspot", "rssi": -48, "open": False, "auth": "WPA2"},
    {"ssid": "Hangar", "rssi": -63, "open": False, "auth": "WPA2/WPA3"},
    {"ssid": "Guest", "rssi": -74, "open": True, "auth": "OPEN"},
    {"ssid": "far-away-ap", "rssi": -86, "open": False, "auth": "WPA"},
]


def speed_to_duty(pct):
    pct = max(SPEED_MIN_PCT, min(100, pct))
    d = pct * (DUTY_FULL_SCALE - 1) // 100
    return DUTY_FULL_SCALE if d >= DUTY_FULL_SCALE - 1 else d


class Dock:
    def __init__(self, pairing):
        self.lock = threading.Lock()
        self.dir = "s"
        self.speed = 100
        self.slow_decay = True
        self.level = -1
        self.target = -1
        self.moving = False
        self.move_ends = 0.0
        self.event = "ready"
        self.last_poll = time.monotonic()
        self.key = secrets.token_bytes(32)
        self.pairing = pairing
        self.sta = None
        self.nonce = 0

    # -- motor / positioner ---------------------------------------------------
    def motor_set(self, d, speed):
        self.dir = d
        self.speed = max(SPEED_MIN_PCT, min(100, speed))

    def cancel(self):
        self.moving = False
        self.level = self.target = -1

    def start_move(self, d, ms, to):
        self.target = to
        self.moving = True
        self.move_ends = time.monotonic() + ms / 1000
        self.motor_set(d, STEP_SPEED_PCT)

    def describe_level(self):
        if self.moving:
            return "Level: moving..."
        if self.level < 0:
            return "Level: unknown (press h to home)"
        return f"Level: {self.level}/{LEVELS}"

    def describe_motor(self):
        a, b = self.duty()
        return f"Direction: {self.dir}, speed: {self.speed}%  [A={a} B={b}]"

    def duty(self):
        d = speed_to_duty(self.speed)
        return (d, 0) if self.dir == "e" else (0, d) if self.dir == "r" else (0, 0)

    def tick(self):
        """Advance timers: finish a timed move, enforce the dead-man."""
        now = time.monotonic()
        if self.moving and now >= self.move_ends:
            self.motor_set("s", self.speed)
            self.level = self.target
            self.moving = False
            self.event = self.describe_level()
        if self.dir != "s" and (now - self.last_poll) * 1000 > SESSION_TIMEOUT_MS:
            self.cancel()
            self.motor_set("s", self.speed)
            self.event = "web client stopped responding"
            print("STOP: web client stopped responding")

    # -- controller_handle() ---------------------------------------------------
    def handle(self, c):
        if c in "ers":
            self.cancel()
            self.motor_set(c, self.speed)
            reply = self.describe_motor()
        elif c.isdigit() and self.moving:
            reply = f"Move in progress at {STEP_SPEED_PCT}% -- set speed after it, or press s"
        elif c.isdigit():
            pct = 100 if c == "0" else int(c) * 10
            self.motor_set(self.dir, pct)
            reply = self.describe_motor()
            if pct < SPEED_MIN_PCT:
                reply = f"{pct}% below the {SPEED_MIN_PCT}% stall floor -- clamped. {reply}"
        elif c in "ht":
            to = 0 if c == "h" else LEVELS
            end = "retracted" if c == "h" else "extended"
            if self.moving and self.target == to:
                reply = f"Already homing to the {end} end"
            else:
                self.start_move("r" if c == "h" else "e", HOME_MS, to)
                reply = f"Homing to {end} end ({HOME_MS} ms)..."
        elif c in "+-":
            step = 1 if c == "+" else -1
            if self.moving:
                reply = "Move in progress -- wait for it, or press s to abort"
            elif self.level < 0:
                reply = "Level unknown -- press h or t first"
            elif not 0 <= self.level + step <= LEVELS:
                reply = f"Already at {'extended' if step > 0 else 'retracted'} end (level {self.level}/{LEVELS})"
            else:
                frm = self.level
                self.start_move("e" if step > 0 else "r", STEP_MS, frm + step)
                reply = f"Level {frm} -> {frm + step} ({STEP_MS} ms)..."
        elif c == "p":
            reply = self.describe_level()
        elif c == "d":
            self.slow_decay = not self.slow_decay
            reply = "Decay: " + ("slow (brake off-phase)" if self.slow_decay else "fast (coast off-phase)")
        else:
            return
        self.event = reply

    def status(self):
        a, b = self.duty()
        return {
            "dir": self.dir, "speed": self.speed, "dutyA": a, "dutyB": b,
            "level": self.level, "levels": LEVELS, "moving": self.moving,
            "slowDecay": self.slow_decay, "levelText": self.describe_level(), "event": self.event,
        }


def make_handler(dock):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt, *args):
            if "/api/status" not in self.path and "/api/net" not in self.path:
                print("HTTP: ", fmt % args)

        def reply(self, code, body):
            data = json.dumps(body).encode()
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            url = urlsplit(self.path)
            with dock.lock:
                dock.tick()
                if url.path == "/api/status":
                    dock.last_poll = time.monotonic()
                    return self.reply(200, dock.status())
                if url.path == "/api/net":
                    dock.last_poll = time.monotonic()
                    return self.reply(200, {
                        "staJoined": dock.sta is not None, "staIp": dock.sta or "",
                        "apSsid": "drone-in-a-box", "pairing": dock.pairing,
                    })
                if url.path == "/api/nonce":
                    return self.reply(200, {"nonce": dock.nonce})
                if url.path == "/api/pair":
                    if not dock.pairing:
                        return self.reply(403, {"error": "closed", "hint": "press the BOOT button on the dock"})
                    dock.pairing = False
                    return self.reply(200, {"key": dock.key.hex()})
                if url.path == "/api/scan":
                    if dock.dir != "s":
                        return self.reply(409, {
                            "error": "moving",
                            "hint": "stop the actuator before scanning: a scan takes longer than "
                                    "the dead-man timeout and would halt the motor",
                        })
            if url.path == "/api/scan":
                time.sleep(SCAN_MS / 1000)          # the radio is off channel
                return self.reply(200, {"networks": NETWORKS})
            if url.path == "/":
                return self.reply(200, {"mock": "drone-in-a-box dock", "api": "/api/status"})
            self.reply(404, {"error": "not found"})

        def do_POST(self):
            url = urlsplit(self.path)
            q = parse_qs(url.query, keep_blank_values=True)
            with dock.lock:
                dock.tick()
                if url.path == "/api/cmd":
                    dock.last_poll = time.monotonic()
                    c = q.get("c", [""])[0]
                    if not c:
                        return self.reply(400, {"error": "expected ?c=<command>"})
                    dock.handle(c[0])
                    print(f"CMD:   {c[0]!r} -> {dock.event}")
                    return self.reply(200, dock.status())
                if url.path == "/api/wifi":
                    ssid = q.get("ssid", [""])[0]
                    if not ssid:
                        return self.reply(400, {"error": "expected ssid"})
                    dock.sta = "172.20.10.4"
                    print(f'WiFi:  provisioned "{ssid}" -- joining')
                    return self.reply(200, {"ok": True})
            self.reply(404, {"error": "not found"})

    return Handler


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8000)
    ap.add_argument("--pairing", action="store_true", help="open the pairing window at start")
    args = ap.parse_args()

    dock = Dock(args.pairing)

    def watchdog():
        while True:
            time.sleep(0.1)
            with dock.lock:
                dock.tick()

    threading.Thread(target=watchdog, daemon=True).start()
    print(f"Mock dock on http://{args.host}:{args.port}  (open API, dead-man {SESSION_TIMEOUT_MS} ms)")
    ThreadingHTTPServer((args.host, args.port), make_handler(dock)).serve_forever()


if __name__ == "__main__":
    main()
