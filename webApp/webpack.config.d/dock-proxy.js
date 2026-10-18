// Forward /api to the dock, so the browser build talks to it same-origin.
//
// The dock's webserver sends no CORS headers, so a page served from anywhere
// else cannot read its answers. Through this proxy the page and the API share
// an origin, exactly as they do for the page the dock serves itself.
//
// Which dock: the app names the address the operator typed in the
// X-Dock-Target header (DockClient.TARGET_HEADER). Without one -- the "This
// server" address -- requests go to DOCK_URL:
//
//   DOCK_URL=http://192.168.4.1 ./gradlew :webApp:wasmJsBrowserDevelopmentRun
//   DOCK_URL=http://localhost:8000 ...      # tools/mock_dock.py
//
// Only local-network targets are forwarded, so this cannot be used to reach
// arbitrary hosts through the dev server.
const LOCAL_HOST = [
    /^localhost$/i,
    /^127\./,
    /^10\./,
    /^192\.168\./,
    /^172\.(1[6-9]|2\d|3[01])\./,
    /\.local$/i,
    /^[a-z0-9-]+$/i,           // a bare name, like "dock"
];

function localTarget(value) {
    if (!value) return undefined;
    try {
        const url = new URL(value);
        if (url.protocol !== "http:") return undefined;
        if (!LOCAL_HOST.some((re) => re.test(url.hostname))) return undefined;
        return url.origin;
    } catch (e) {
        return undefined;
    }
}

// The dock only speaks IPv4. Left to itself Node also asks for an IPv6
// address, and for a .local name that query waits ~5 s for an answer that
// never comes -- longer than the app's timeout, so dock.local never worked.
const http = require("http");
const ipv4 = new http.Agent({ family: 4, keepAlive: true });

config.devServer = config.devServer || {};
config.devServer.proxy = [
    {
        context: ["/api"],
        target: process.env.DOCK_URL || "http://192.168.4.1",
        changeOrigin: true,
        agent: ipv4,
        router: (req) => localTarget(req.headers["x-dock-target"]),
        // The dock ignores unknown headers, but there is no reason to send it one.
        onProxyReq: (proxyReq) => proxyReq.removeHeader("x-dock-target"),
        // A dock that is off or out of range should fail fast, well inside the
        // app's own 2 s timeout, rather than hang the request.
        proxyTimeout: 2000,
        timeout: 2000,
    },
];
