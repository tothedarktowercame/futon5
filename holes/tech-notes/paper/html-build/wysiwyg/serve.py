#!/usr/bin/env python3
"""Static server for the WYSIWYG page that forbids caching.

python3 -m http.server sends no Cache-Control, so a browser is free to reuse a
heuristically-cached copy of wysiwyg-client.js -- which is exactly how a fixed
client kept looking broken.
"""
import http.server, functools, sys

class NoCache(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store, must-revalidate")
        self.send_header("Pragma", "no-cache")
        super().end_headers()

port = int(sys.argv[1]) if len(sys.argv) > 1 else 8129
handler = functools.partial(NoCache, directory="page")
print(f"serving ./page on http://127.0.0.1:{port} (no-store)")
http.server.ThreadingHTTPServer(("127.0.0.1", port), handler).serve_forever()
