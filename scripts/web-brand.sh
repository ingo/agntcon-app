#!/usr/bin/env bash
# Applies a conference's look to a built web app, without touching the KotlinConf web resources.
#
#   scripts/web-brand.sh <event-id> <dist-dir> <title> <description>
#   scripts/web-brand.sh agntcon-mcpcon-na-2026 app/webApp/build/dist/composeWebCompatibility/productionExecutable \
#       "AGNTCon + MCPCon North America 2026" "Schedule, notes and people for the conference"
#
# Copies app/webApp/branding/<event-id>/ (manifest, service worker, icons, splash screens) over <dist-dir>
# and points index.html at them: title, descriptions, icons, splash and colors.
set -euo pipefail

if [[ $# -ne 4 ]]; then
  sed -n '2,10p' "$0"
  exit 1
fi

EVENT=$1 DIST=$2 TITLE=$3 DESCRIPTION=$4
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BRAND="$ROOT/app/webApp/branding/$EVENT"

[[ -d "$BRAND" ]] || { echo "No branding for '$EVENT' in $BRAND" >&2; exit 1; }
[[ -f "$DIST/index.html" ]] || { echo "No built web app in $DIST" >&2; exit 1; }

cp -R "$BRAND"/. "$DIST"/

TITLE="$TITLE" DESCRIPTION="$DESCRIPTION" python3 - "$DIST/index.html" <<'PY'
import html, os, re, sys

path = sys.argv[1]
text = open(path, encoding="utf-8").read()
title = html.escape(os.environ["TITLE"], quote=True)
description = html.escape(os.environ["DESCRIPTION"], quote=True)

def sub(pattern, replacement, flags=0):
    global text
    text, n = re.subn(pattern, replacement, text, flags=flags)
    if n == 0:
        sys.exit(f"index.html no longer has a match for {pattern!r}; update scripts/web-brand.sh")

sub(r"<title>.*?</title>", f"<title>{title}</title>")
sub(r'(<meta property="og:title" content=)"[^"]*"', rf'\1"{title}"')
sub(r'(<meta property="og:site_name" content=)"[^"]*"', rf'\1"{title}"')
sub(r'(<meta name="twitter:title" content=)"[^"]*"', rf'\1"{title}"')
sub(r'(<meta name="description" content=)"[^"]*"', rf'\1"{description}"')
sub(r'(<meta property="og:description" content=)"[^"]*"', rf'\1"{description}"')
sub(r'(<meta name="twitter:description" content=)"[^"]*"', rf'\1"{description}"')
text = text.replace("images/KotlinConf-icon-360.png", "images/icon-360.png")
text = text.replace("images/KotlinConf-splash-screen-dark.svg", "images/splash-dark.svg")
text = text.replace("images/KotlinConf-splash-screen-light.svg", "images/splash-light.svg")
sub(r"background-color: #000;", "background-color: #1f213a;")
sub(
    r'(<link rel="manifest" href="manifest.json"/>)',
    r'\1\n        <link rel="apple-touch-icon" href="images/apple-touch-icon.png">'
    f'\n        <meta name="apple-mobile-web-app-capable" content="yes">'
    f'\n        <meta name="apple-mobile-web-app-title" content="AGNTCon">'
    f'\n        <meta name="theme-color" content="#1f213a">',
)

# Shown over the splash screen until the app has drawn its first frame. The app is several megabytes of
# WebAssembly, and a splash that never changes looks like a page that is stuck.
loading = """
        <div id="loading" role="status" aria-live="polite">
            <div class="track"><div class="bar"></div></div>
            <p id="loading-text">Loading the schedule&hellip;</p>
            <p class="note" id="loading-note">The first visit downloads a few megabytes. After that the app starts from your device and works offline.</p>
            <p id="loading-error" hidden><button type="button" onclick="location.reload()">Try again</button></p>
        </div>
        <style>
            #loading { position: fixed; left: 0; right: 0; bottom: 9%; padding: 0 28px; text-align: center;
                font: 15px/1.45 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #55565f; }
            #loading p { margin: 10px 0 0; }
            #loading .note { font-size: 13px; opacity: 0.8; }
            #loading .track { width: 160px; height: 4px; margin: 0 auto; border-radius: 2px; overflow: hidden; background: rgba(127,127,150,0.25); }
            #loading .bar { width: 40%; height: 100%; border-radius: 2px; background: #ea6289; animation: slide 1.2s ease-in-out infinite; }
            @keyframes slide { from { transform: translateX(-100%); } to { transform: translateX(260%); } }
            #loading button { font: inherit; padding: 8px 18px; border: 0; border-radius: 999px; color: #fff; background: #c2527a; }
            @media (prefers-color-scheme: dark) { #loading { color: #c9cbe3; } }
            @media (prefers-reduced-motion: reduce) { #loading .bar { animation: none; width: 100%; opacity: 0.5; } }
        </style>
"""
sub(r"</body>", loading + "    </body>")

# The app replaces everything in <body> when it starts, which also removes the loading message. This script
# lives in <head>, notices that, and tells the service worker what was loaded so the next visit starts from
# the cache (the worker takes control only after the first load).
# The app is two WebAssembly files of a few megabytes each. If the connection drops or changes while one is
# downloading (Wi-Fi to mobile, a VPN reconnecting), the browser aborts the download and the app stops with an
# error nobody sees. This downloads the files itself, retries, and says what is happening.
sub(r"</head>", """        <script>
            (() => {
                const nativeFetch = window.fetch.bind(window);
                const text = (id, value) => { const el = document.getElementById(id); if (el && !failed) el.textContent = value; };
                const megabytes = (bytes) => (bytes / 1048576).toFixed(1);
                let finished = 0;
                let failed = false;
                const fail = (message) => {
                    failed = true;
                    const set = (id, value) => { const el = document.getElementById(id); if (el) el.textContent = value; };
                    set("loading-text", message);
                    set("loading-note", "Check your connection, then try again.");
                    const retry = document.getElementById("loading-error");
                    if (retry) retry.hidden = false;
                };
                window.fetch = async (input, init) => {
                    const url = typeof input === "string" ? input : input.url;
                    if (!/\\.wasm(\\?|$)/.test(url)) return nativeFetch(input, init);
                    let lastError;
                    for (let attempt = 1; attempt <= 6; attempt++) {
                        try {
                            const response = await nativeFetch(input, init);
                            if (!response.ok) throw new Error("HTTP " + response.status);
                            const reader = response.body && response.body.getReader();
                            if (!reader) return response;
                            const chunks = [];
                            let size = 0;
                            for (;;) {
                                const { done, value } = await reader.read();
                                if (done) break;
                                chunks.push(value);
                                size += value.length;
                                text("loading-text", "Downloading the app\u2026 " + megabytes(finished + size) + " MB");
                            }
                            finished += size;
                            return new Response(new Blob(chunks), { status: 200, headers: { "Content-Type": "application/wasm" } });
                        } catch (error) {
                            lastError = error;
                            text("loading-text", "Connection interrupted, trying again\u2026");
                            await new Promise((resolve) => setTimeout(resolve, Math.min(1000 * attempt, 5000)));
                        }
                    }
                    fail("The app could not be downloaded.");
                    throw lastError;
                };
                // Anything else that stops the app before it has taken over the page
                const startupFailed = () => { if (document.getElementById("loading")) fail("The app did not start."); };
                addEventListener("unhandledrejection", startupFailed);
                addEventListener("error", startupFailed);
            })();
        </script>
    </head>""")
sub(r"</head>", """        <script>
            addEventListener("DOMContentLoaded", () => {
                new MutationObserver((_, observer) => {
                    if (document.getElementById("loading")) return;
                    observer.disconnect();
                    if (!("serviceWorker" in navigator)) return;
                    navigator.serviceWorker.ready.then((registration) => {
                        const urls = performance.getEntriesByType("resource").map((e) => e.name);
                        if (registration.active) registration.active.postMessage({ type: "precache", urls });
                    });
                }).observe(document.body, { childList: true });
            });
        </script>
    </head>""")
open(path, "w", encoding="utf-8").write(text)
PY

# The default KotlinConf images are not needed in a branded build
rm -f "$DIST"/images/KotlinConf-*
echo "Branded $DIST for $EVENT"
