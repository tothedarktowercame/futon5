#!/usr/bin/env python3
"""rocket.py -- the highlight-and-speak receiver (PATTERNS.md, interactive channel).

Joins two streams at rocket-time: the browser's current selection (POSTed by
rocket-client.js on every selection change) and the whisper transcript
(POSTed by enhanced-voice-typing.py --emit-url). When a transcript arrives
rocket-completed, the pair becomes a record:

    surface: whisper
    fragment: <highlighted region, with its data-sourcepos anchor>
    commentary: <rocket-completed transcript>

The record is appended to edit-notes.jsonl and belled to claude-5 via the
Agency. Claude patches the tex, extracts/extends the pattern in
writing-patterns.edn, sweeps for further matches, and POSTs annotations back
here; rocket-client.js polls them and paints typed track-changes.

Endpoints (all JSON, CORS-open to the 8129 page):
  POST /selection    {text, pos, id}        browser -> current selection
  POST /transcript   {text, rocket: bool}   voice client -> commentary parts
  POST /annotations  [{pat, label, hue?, sites:[{text}], note?}, ...]  claude
  GET  /annotations  the current list
  GET  /state        pill/debug: selection, pending commentary, last record

Run:  python3 rocket.py            (127.0.0.1:8130)
Test without voice:
  curl -s -X POST localhost:8130/transcript \
       -d '{"text":"too clunky, just say X. rocket","rocket":true}'
"""
import http.server, json, os, subprocess, sys, threading, time

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8130
HERE = os.path.dirname(os.path.abspath(__file__))
PAPER = os.path.abspath(os.path.join(HERE, "..", ".."))
NOTES = os.path.join(PAPER, "edit-notes.jsonl")
AGENCY_SEND = "/home/joe/code/futon3c/scripts/agency_send.py"
TO_AGENT = os.environ.get("ROCKET_TO", "claude-5")

state = {
    "selection": None,          # {text, pos, id, ts}
    "commentary": [],           # accumulating utterances until a rocket
    "annotations": [],          # what claude has applied, for the painter
    "log": [],                  # claude's per-record trace, shown in-page
    "in_flight": 0,             # records dispatched but not yet /done
    "voice_ts": 0,              # last voice-client heartbeat
    "records": 0,
    "last_record": None,
}
lock = threading.Lock()


def compose_and_dispatch():
    """Rocket landed: freeze the record, log it, bell claude."""
    with lock:
        sel = state["selection"]
        commentary = " ".join(t for t in state["commentary"] if t).strip()
        state["commentary"] = []
        if not commentary:
            return {"ok": False, "why": "empty commentary"}
        rec = {
            "surface": "whisper",
            "ts": time.strftime("%Y-%m-%dT%H:%M:%S"),
            "fragment": (sel or {}).get("text"),
            "sourcepos": (sel or {}).get("pos"),
            "anchor": (sel or {}).get("id"),
            "commentary": commentary,
        }
        state["records"] += 1
        state["in_flight"] += 1
        state["last_record"] = rec
        n = state["records"]
    with open(NOTES, "a") as f:
        f.write(json.dumps(rec) + "\n")
    prompt = (
        f"rocket record {n} (see PATTERNS.md interactive channel; "
        f"process: patch, extract pattern, count, propose/apply, then POST "
        f"annotations to http://127.0.0.1:{PORT}/annotations)\n\n"
        f"surface: whisper\n"
        f"fragment: {rec['fragment'] or '(no selection)'}\n"
        f"sourcepos: {rec['sourcepos'] or '?'}\n"
        f"commentary: {commentary}\n"
    )
    def bell():
        try:
            subprocess.run(
                [sys.executable, AGENCY_SEND, "--to", TO_AGENT,
                 "--from", "whisper", "--kind", "bell", "--surface", "whisper"],
                input=prompt, text=True, capture_output=True, timeout=30)
        except Exception as e:
            print(f"rocket: bell failed: {e}", file=sys.stderr)
    threading.Thread(target=bell, daemon=True).start()
    with lock:
        state["log"].append({"ts": time.strftime("%H:%M:%S"),
                             "msg": f"record {n} dispatched: "
                                    f"\u201c{commentary[:70]}\u201d"})
    print(f"rocket #{n}: fragment={str(rec['fragment'])[:60]!r} "
          f"commentary={commentary[:80]!r}", flush=True)
    return {"ok": True, "record": n}


class H(http.server.BaseHTTPRequestHandler):
    def _send(self, obj, code=200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        if self.path == "/feed":
            with lock:
                self._send({"annotations": state["annotations"],
                            "log": state["log"][-30:],
                            "records": state["records"],
                            "in_flight": state["in_flight"],
                            "voice": (time.time() - state["voice_ts"]) < 15})
        elif self.path == "/annotations":
            with lock:
                self._send(state["annotations"])
        elif self.path == "/state":
            with lock:
                self._send({k: state[k] for k in
                            ("selection", "commentary", "records",
                             "last_record")} | {"annotations":
                                                len(state["annotations"])})
        else:
            self._send({"ok": False}, 404)

    def do_POST(self):
        try:
            n = int(self.headers.get("Content-Length", 0))
            data = json.loads(self.rfile.read(n) or b"{}")
        except Exception as e:
            return self._send({"ok": False, "why": str(e)}, 400)
        if self.path == "/selection":
            with lock:
                # an empty selection clears nothing: keep the last non-empty
                # region so a click before speaking doesn't lose the fragment
                if (data.get("text") or "").strip():
                    data["ts"] = time.time()
                    state["selection"] = data
            self._send({"ok": True})
        elif self.path == "/transcript":
            with lock:
                state["commentary"].append(data.get("text", ""))
            if data.get("rocket"):
                self._send(compose_and_dispatch())
            else:
                self._send({"ok": True, "buffered": True})
        elif self.path == "/edit":
            # a wysiwyg-applied edit: finalized text supplied, so the bell
            # asks for generalize-and-sweep, not a patch
            rec = {"surface": "wysiwyg",
                   "ts": time.strftime("%Y-%m-%dT%H:%M:%S"),
                   "fragment": data.get("old"), "patch": data.get("new"),
                   "sourcepos": f"{data.get('file')}:{data.get('line')}",
                   "commentary": "(direct edit; finalized text supplied)"}
            with open(NOTES, "a") as f:
                f.write(json.dumps(rec) + "\n")
            with lock:
                state["records"] += 1
                state["in_flight"] += 1
                state["last_record"] = rec
                n = state["records"]
                state["log"].append(
                    {"ts": time.strftime("%H:%M:%S"),
                     "msg": f"edit captured (wysiwyg): "
                            f"\u201c{str(data.get('old'))[:40]}\u201d \u2192 "
                            f"\u201c{str(data.get('new'))[:40]}\u201d"})
            prompt = (f"rocket record {n} — WYSIWYG HAND EDIT, already applied "
                      f"by Emacs (see PATTERNS.md; do NOT re-edit the site; "
                      f"generalize into a pattern, sweep for further "
                      f"applications, annotate nested, POST log)\n\n"
                      f"surface: wysiwyg\nold: {data.get('old')}\n"
                      f"new: {data.get('new')}\n"
                      f"at: {rec['sourcepos']}\n")
            def bell():
                try:
                    subprocess.run(
                        [sys.executable, AGENCY_SEND, "--to", TO_AGENT,
                         "--from", "whisper", "--kind", "bell",
                         "--surface", "wysiwyg"],
                        input=prompt, text=True, capture_output=True,
                        timeout=30)
                except Exception as e:
                    print(f"rocket: edit bell failed: {e}", file=sys.stderr)
            threading.Thread(target=bell, daemon=True).start()
            self._send({"ok": True, "record": n})
        elif self.path == "/heartbeat":
            with lock:
                state["voice_ts"] = time.time()
            self._send({"ok": True})
        elif self.path == "/done":
            with lock:
                state["in_flight"] = max(0, state["in_flight"] - 1)
            self._send({"ok": True, "in_flight": state["in_flight"]})
        elif self.path == "/log":
            with lock:
                state["log"].append({"ts": time.strftime("%H:%M:%S"),
                                     "msg": str(data.get("msg", ""))})
            self._send({"ok": True})
        elif self.path == "/annotations":
            with lock:
                if isinstance(data, list):
                    state["annotations"] = data
                else:
                    state["annotations"].append(data)
                n = len(state["annotations"])
            self._send({"ok": True, "annotations": n})
        else:
            self._send({"ok": False}, 404)

    def log_message(self, *a):
        pass


print(f"rocket receiver on http://127.0.0.1:{PORT} -> bells {TO_AGENT}; "
      f"notes -> {NOTES}")
http.server.ThreadingHTTPServer(("127.0.0.1", PORT), H).serve_forever()
