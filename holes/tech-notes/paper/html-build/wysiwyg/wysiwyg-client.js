/* wysiwyg-client.js -- M-latex-wysiwyg slice S1, browser half.
 *
 * READ-ONLY. Click a block, Emacs puts point there. Move point in Emacs, the
 * block highlights here. Nothing in this file writes to the document or to
 * any source file; S1's whole purpose is to prove the navigation is accurate
 * before any edit path exists.
 *
 * Emacs runs the websocket server (latex-wysiwyg.el, port 7079) so that the
 * Agency socket on 7070 and its JVM are untouched.
 *
 * State is mirrored on window.__wysiwyg for the Playwright gate to read.
 */
(function () {
  "use strict";

  var PORT = 7079;
  var VERSION = "s4-4";   // sent to Emacs so we can tell which
                          // script a browser is actually running
  var state = {
    connected: false,
    lastSent: null,
    lastRecv: null,
    sentCount: 0,
    recvCount: 0,
    error: null
  };
  window.__wysiwyg = state;

  // ---------------------------------------------------------------- sourcepos
  function parsePos(el) {
    var raw = el.getAttribute("data-sourcepos");
    if (!raw) return null;
    var m = /^(\d+):(\d+):(\d+)-(\d+):(\d+):(\d+)$/.exec(raw);
    if (!m) return null;
    return {
      file: +m[1], line: +m[2], col: +m[3],
      endFile: +m[4], endLine: +m[5], endCol: +m[6]
    };
  }

  // The nearest ancestor that carries a position. Most body text lives under a
  // <p> whose anchor is a degenerate point (start only) -- that is expected;
  // Emacs derives the extent itself.
  function anchorFor(node) {
    var el = node instanceof Element ? node : node.parentElement;
    while (el && el !== document.body) {
      if (el.hasAttribute && el.hasAttribute("data-sourcepos")) return el;
      el = el.parentElement;
    }
    return null;
  }

  // ------------------------------------------------------------------- socket
  var ws = null, retry = null;

  // Every handler checks it is still the CURRENT socket before touching state.
  // Without that guard, restarting the Emacs server left overlapping sockets:
  // a stale socket's onclose fired after the live one had opened and flipped
  // `connected` back to false, so the pill read disconnected while clicks kept
  // working through the live socket. Reported 2026-08-08.
  function connect() {
    if (retry) { clearTimeout(retry); retry = null; }
    if (ws && (ws.readyState === 0 || ws.readyState === 1)) return;

    var sock;
    try {
      sock = new WebSocket("ws://localhost:" + PORT);
    } catch (e) {
      state.error = String(e);
      retry = setTimeout(connect, 1500);
      return;
    }
    ws = sock;

    sock.onopen = function () {
      if (ws !== sock) { try { sock.close(); } catch (e) {} return; }
      state.error = null;
      paintPill();
      // Identify ourselves: a stale cached script is otherwise invisible.
      try { sock.send(JSON.stringify({ type: "hello", version: VERSION,
                                       ua: navigator.userAgent })); } catch (e) {}
    };
    sock.onclose = function () {
      if (ws !== sock) return;          // a superseded socket: ignore
      paintPill();
      retry = setTimeout(connect, 1500);
    };
    sock.onerror = function () {
      if (ws !== sock) return;
      state.error = "socket error";
      paintPill();
    };
    sock.onmessage = function (ev) {
      if (ws !== sock) return;
      var msg;
      try { msg = JSON.parse(ev.data); } catch (e) { return; }
      state.recvCount++;
      state.lastRecv = msg;
      if (msg.type === "scope/point") highlightNearest(msg.file, msg.line);
      if (msg.type === "scope/ack")    { settleRun(true, null); settlePara(true, null); }
      if (msg.type === "scope/reject") { settleRun(false, msg.reason); settlePara(false, msg.reason); }
      if (msg.type === "state") { armed = (msg.armed === true); paintPill(); }
      if (msg.type === "build/done") {
        // Sources or figures changed and the page was re-snapped. Reload so
        // the text, the figures and the anchors all match the source again.
        toast("sources changed \u2014 reloading the re-snapped page");
        setTimeout(function () { location.reload(); }, 600);
      }
    };
  }

  function send(obj) {
    if (!ws || ws.readyState !== 1) return false;
    ws.send(JSON.stringify(obj));
    state.sentCount++;
    state.lastSent = obj;
    return true;
  }

  // -------------------------------------------------------------- highlight
  var lit = null;
  function light(el) {
    if (lit) lit.classList.remove("wysiwyg-active");
    lit = el;
    if (el) el.classList.add("wysiwyg-active");
  }

  // Emacs reports a point; find the anchored block that starts closest at or
  // before that line in the same file.
  //
  // Scroll discipline (2026-08-10): the point replay that arrives on
  // connect/reload must not move the page -- it yanked a reader working in
  // the (unmapped, F2) abstract to whatever block the nearest-before match
  // resolved to. Highlight always; scroll only for a genuine point CHANGE
  // once the page has been up a moment.
  var loadedAt = Date.now();
  var lastPointBlock = null;
  function highlightNearest(file, line) {
    var best = null, bestLine = -1;
    document.querySelectorAll("[data-sourcepos]").forEach(function (el) {
      var p = parsePos(el);
      if (!p || p.file !== file || p.line > line) return;
      if (p.line > bestLine) { bestLine = p.line; best = el; }
    });
    if (best) {
      light(best);
      if (Date.now() - loadedAt > 3000 && best !== lastPointBlock) {
        best.scrollIntoView({ block: "center", behavior: "smooth" });
      }
      lastPointBlock = best;
    }
  }


  // ------------------------------------------------------------------ edits
  // S2: the editable unit is a RUN -- a maximal stretch of text with no inline
  // markup. Measured on draft8: only 11% of paragraphs are wholly literal, but
  // runs >=40 chars cover 90% of the prose. A run is literal by construction,
  // so it appears verbatim in the source and Emacs can locate it exactly.
  var MIN_RUN = 40;
  var REASONS = {
    "edits-disarmed":  "Emacs has edits disarmed \u2014 M-x latex-wysiwyg-arm-edits",
    "quote-not-found": "couldn't find that text in the source \u2014 edit not applied",
    "quote-ambiguous": "that text occurs more than once \u2014 edit not applied",
    "unmapped-source": "this block has no source file (generated content)",
    "synthesised-source": "generated content (bibliography) \u2014 no source to edit",
    "map-stale":       "Emacs has a stale file map \u2014 M-x latex-wysiwyg-start",
    "page-stale":      "this page is older than the sources \u2014 rebuild it before editing",
    "empty-run":       "nothing to write",
    "not connected":   "no connection to Emacs",
    "atoms-changed":   "a formula or citation was altered \u2014 those must stay intact",
    "atoms-reordered": "formulae/citations were reordered \u2014 can't align that safely",
    "deletion-not-yet-supported":
      "deleting a formula isn't wired up yet \u2014 your text is saved in the journal",
    "literal-not-found": "couldn't line this paragraph up with the source",
    "decomposition-unsound": "couldn't prove the split was lossless \u2014 nothing written",
    "no-change":       "nothing changed"
  };

  var toastEl;
  function toast(text) {
    if (!toastEl) {
      toastEl = document.createElement("div");
      toastEl.id = "wysiwyg-toast";
      document.body.appendChild(toastEl);
    }
    toastEl.textContent = text;
    toastEl.style.opacity = "1";
    clearTimeout(toast._t);
    toast._t = setTimeout(function () { toastEl.style.opacity = "0"; }, 5000);
  }
  var editing = false;
  var armed = null;   // null = unknown, until Emacs tells us
  var pending = null;          // {span, old, para}

  function paraAnchorFor(el) {
    var p = el.closest("[data-sourcepos]");
    while (p && !/^\d+:\d+:\d+-/.test(p.getAttribute("data-sourcepos") || "")) {
      p = p.parentElement && p.parentElement.closest("[data-sourcepos]");
    }
    return p;
  }

  function wrapRuns() {
    document.querySelectorAll("p[data-sourcepos]").forEach(function (p) {
      if (p.closest("svg") || p.dataset.runsWrapped) return;
      p.dataset.runsWrapped = "1";
      var kids = [].slice.call(p.childNodes);
      kids.forEach(function (n) {
        if (n.nodeType !== 3) return;                 // text nodes only
        if (n.nodeValue.trim().length < MIN_RUN) return;
        var span = document.createElement("span");
        span.className = "wysiwyg-run";
        span.textContent = n.nodeValue;
        p.replaceChild(span, n);
      });
    });
  }

  function setEditing(on) {
    if (on && armed === false) { toast(REASONS["edits-disarmed"]); return; }
    editing = on;
    document.querySelectorAll("p[data-sourcepos]").forEach(function (p) {
      if (p.closest("svg")) return;
      if (on) {
        decorate(p);
        p._wysiwygOld = templateOf(p);
        p.setAttribute("contenteditable", "true");
        p.setAttribute("spellcheck", "false");
      } else {
        p.removeAttribute("contenteditable");
      }
    });
    document.body.classList.toggle("wysiwyg-editing", on);
    paintPill();
  }

  function settleRun(ok, reason) {
    if (!pending) return;
    var span = pending.span;
    if (ok) {
      span.classList.add("wysiwyg-ok");
      setTimeout(function () { span.classList.remove("wysiwyg-ok"); }, 900);
    } else {
      // Refused: put the original text back. The source is authoritative.
      span.textContent = pending.old;
      span.classList.add("wysiwyg-bad");
      span.title = "refused: " + (reason || "unknown");
      toast(REASONS[reason] || ("refused: " + (reason || "unknown")));
      setTimeout(function () { span.classList.remove("wysiwyg-bad"); }, 2000);
    }
    pending = null;
  }

  function commitRun(span) {
    var now = span.textContent;
    var was = span.dataset.original;
    if (was === undefined || now === was) return;
    var anchor = paraAnchorFor(span);
    var pos = anchor && parsePos(anchor);
    if (!pos) { span.textContent = was; return; }
    pending = { span: span, old: was, para: pos };
    var sent = send({ type: "scope/edit", file: pos.file, line: pos.line,
                      col: pos.col, old: was, new: now });
    if (!sent) settleRun(false, "not connected");
    else span.dataset.original = now;   // optimistic; reverted on reject
  }

  document.addEventListener("focusin", function (ev) {
    var s = ev.target.closest && ev.target.closest(".wysiwyg-run");
    if (s) s.dataset.original = s.textContent;
  });
  document.addEventListener("focusout", function (ev) {
    var s = ev.target.closest && ev.target.closest(".wysiwyg-run");
    if (s && editing) commitRun(s);
    var p = ev.target.closest && ev.target.closest("p[data-sourcepos]");
    if (p && editing && p.hasAttribute("contenteditable")) commitPara(p);
  });
  // Clicking a formula shows its TeX. Editing formulae is not wired yet:
  // atoms must survive an edit byte-identical, so the server refuses any
  // change to them rather than guessing.
  document.addEventListener("click", function (ev) {
    if (ev.target.closest && ev.target.closest("#wysiwyg-tex")) return;
    var a = ev.target.closest && ev.target.closest(".wysiwyg-atom");
    if (a && editing) {
      // let any pending literal edit commit on blur first, then open
      setTimeout(function () { openTex(a); }, 0);
    } else if (!a) { closePopup(); }
  });
  document.addEventListener("keydown", function (ev) {
    if (editing && !ev.target.closest("#wysiwyg-tex")) {
      if (ev.key === "Backspace" || ev.key === "Delete") {
        var a = atomAtCaret(ev.key === "Backspace" ? -1 : 1);
        if (a) { ev.preventDefault(); atomOp("scope/delete-atom", a); return; }
      }
      if (ev.key === "ArrowLeft" || ev.key === "ArrowRight") {
        var b = atomAtCaret(ev.key === "ArrowLeft" ? -1 : 1);
        if (b) { ev.preventDefault(); openTex(b); return; }
      }
    }
    // Shift+Enter splits the paragraph. In LaTeX a paragraph break IS a blank
    // line, so this needs no new message type: put "\n\n" into the literal at
    // the caret and commit normally. The server splices it verbatim, refill
    // then fills each side, and the watcher re-snaps the page into two
    // paragraphs.
    if (editing && ev.key === "Enter" && ev.shiftKey) {
      var para = ev.target.closest && ev.target.closest("p[data-sourcepos]");
      if (para && para.hasAttribute("contenteditable")) {
        ev.preventDefault();
        var sel = window.getSelection();
        if (sel && sel.rangeCount && sel.isCollapsed) {
          var r = sel.getRangeAt(0);
          if (r.startContainer.nodeType === 3) {
            r.startContainer.insertData(r.startOffset, "\n\n");
            commitPara(para);
            para.blur();
          } else {
            toast("put the cursor in the text where the split should go");
          }
        }
        return;
      }
    }
    var s = ev.target.closest && ev.target.closest(".wysiwyg-run");
    if (!s) return;
    if (ev.key === "Escape") { s.textContent = s.dataset.original; s.blur(); }
    if (ev.key === "Enter" && !ev.shiftKey) { ev.preventDefault(); s.blur(); }
  });

  window.__wysiwygEdit = { setEditing: setEditing, commitRun: commitRun,
                           isEditing: function () { return editing; } };


  // ------------------------------------------------- paragraph templates
  // A paragraph is  L1 A1 L2 A2 ...  : literal runs you may edit, and opaque
  // atoms you may not (math, citations, refs, macro output). Run-level editing
  // fragmented this into micro-regions -- a short word after a formula fell
  // below the length threshold and was silently uneditable. Now the whole
  // paragraph is editable and the atoms are inert, colour-coded islands.
  function isMath(el) {
    return el.tagName === "MATH" || el.querySelector && el.querySelector("math");
  }

  function decorate(p) {
    if (p.dataset.tpl) return;
    p.dataset.tpl = "1";
    [].slice.call(p.children).forEach(function (el) {
      if (el.classList.contains("wysiwyg-atom")) return;
      el.classList.add("wysiwyg-atom");
      el.classList.add(isMath(el) ? "wysiwyg-math" : "wysiwyg-opaque");
      el.setAttribute("contenteditable", "false");
      var tex = el.getAttribute("alttext") ||
                (el.querySelector("math") && el.querySelector("math").getAttribute("alttext"));
      if (tex) el.dataset.tex = tex;
      el.title = tex ? ("formula: " + tex + "  (click to view)")
                     : "not editable here - generated from the source";
    });
  }

  // Read the current template out of the DOM, in order.
  function templateOf(p) {
    var out = [];
    [].slice.call(p.childNodes).forEach(function (n) {
      if (n.nodeType === 3) {
        if (n.nodeValue.length) out.push({ k: "lit", t: n.nodeValue });
      } else if (n.nodeType === 1) {
        out.push({ k: "atom", t: n.textContent });
      }
    });
    // collapse adjacent literals so the server sees one run between atoms
    var merged = [];
    out.forEach(function (e) {
      var last = merged[merged.length - 1];
      if (e.k === "lit" && last && last.k === "lit") last.t += e.t;
      else merged.push({ k: e.k, t: e.t });
    });
    return merged;
  }

  var paraPending = null;

  function commitPara(p) {
    var now = templateOf(p);
    var was = p._wysiwygOld;
    if (!was) return;
    if (JSON.stringify(now) === JSON.stringify(was)) return;
    var pos = parsePos(p);
    if (!pos) return;
    paraPending = { p: p, old: was };
    var sent = send({ type: "scope/edit-para", file: pos.file, line: pos.line,
                      col: pos.col, old: was, new: now });
    if (!sent) settlePara(false, "not connected");
  }

  function settlePara(ok, reason) {
    if (!paraPending) return;
    var p = paraPending.p;
    if (ok) {
      p._wysiwygOld = templateOf(p);
      p.classList.add("wysiwyg-ok");
      setTimeout(function () { p.classList.remove("wysiwyg-ok"); }, 900);
    } else {
      // Put the source's version back; the file is authoritative.
      restoreTemplate(p, paraPending.old);
      p.classList.add("wysiwyg-bad");
      toast(REASONS[reason] || ("refused: " + (reason || "unknown")));
      setTimeout(function () { p.classList.remove("wysiwyg-bad"); }, 2000);
    }
    paraPending = null;
  }

  // Rebuild a paragraph's text nodes from a template, leaving atoms in place.
  function restoreTemplate(p, tpl) {
    var atoms = [].slice.call(p.children);
    var frag = document.createDocumentFragment(), ai = 0;
    tpl.forEach(function (e) {
      if (e.k === "lit") frag.appendChild(document.createTextNode(e.t));
      else if (atoms[ai]) frag.appendChild(atoms[ai++]);
    });
    p.textContent = "";
    p.appendChild(frag);
  }


  // ------------------------------------------------- formula interaction
  // Two gestures, per Joe 2026-08-08:
  //   Backspace/Delete AT a formula boundary  -> delete the whole formula
  //   click, or arrow INTO it                 -> open its LaTeX
  // The atom is contenteditable=false, so the browser would otherwise either
  // swallow it silently or refuse to move through it.

  function atomIndexOf(p, el) {
    return [].slice.call(p.querySelectorAll(".wysiwyg-atom")).indexOf(el);
  }

  function skipEmpty(node, dir) {
    while (node && node.nodeType === 3 && !node.nodeValue.length)
      node = dir < 0 ? node.previousSibling : node.nextSibling;
    return node;
  }

  // The atom immediately before (dir<0) or after (dir>0) a collapsed caret.
  function atomAtCaret(dir) {
    var sel = window.getSelection();
    if (!sel || !sel.rangeCount || !sel.isCollapsed) return null;
    var r = sel.getRangeAt(0), node = r.startContainer, off = r.startOffset, cand = null;
    if (node.nodeType === 3) {
      if (dir < 0 && off === 0) cand = skipEmpty(node.previousSibling, -1);
      else if (dir > 0 && off === node.nodeValue.length) cand = skipEmpty(node.nextSibling, 1);
    } else {
      var kids = node.childNodes;
      if (dir < 0 && off > 0) cand = skipEmpty(kids[off - 1], -1);
      else if (dir > 0 && off < kids.length) cand = skipEmpty(kids[off], 1);
    }
    return (cand && cand.nodeType === 1 && cand.classList.contains("wysiwyg-atom"))
      ? cand : null;
  }

  function atomOp(type, atomEl, tex) {
    var p = atomEl.closest("p[data-sourcepos]");
    var pos = parsePos(p);
    if (!pos || !p._wysiwygOld) return false;
    paraPending = { p: p, old: p._wysiwygOld };
    var msg = { type: type, file: pos.file, line: pos.line, col: pos.col,
                old: p._wysiwygOld, atom: atomIndexOf(p, atomEl) };
    if (tex !== undefined) msg.tex = tex;
    var sent = send(msg);
    if (!sent) settlePara(false, "not connected");
    else if (type === "scope/delete-atom") atomEl.remove();
    return sent;
  }

  // ----------------------------------------------------------- TeX popup
  var popup;
  function closePopup() { if (popup) { popup.remove(); popup = null; } }

  function openTex(atomEl) {
    closePopup();
    var tex = atomEl.dataset.tex || atomEl.textContent;
    popup = document.createElement("div");
    popup.id = "wysiwyg-tex";
    popup.innerHTML =
      '<label>LaTeX</label><textarea spellcheck="false"></textarea>' +
      '<div class="row"><button data-a="save">Save</button>' +
      '<button data-a="del">Delete</button>' +
      '<button data-a="cancel">Cancel</button></div>';
    document.body.appendChild(popup);
    var box = atomEl.getBoundingClientRect();
    popup.style.top = (window.scrollY + box.bottom + 6) + "px";
    popup.style.left = (window.scrollX + Math.max(8, box.left - 40)) + "px";
    var ta = popup.querySelector("textarea");
    ta.value = tex;
    ta.focus(); ta.select();

    popup.addEventListener("click", function (ev) {
      var a = ev.target.getAttribute && ev.target.getAttribute("data-a");
      if (!a) return;
      if (a === "save") atomOp("scope/edit-atom", atomEl, ta.value);
      if (a === "del") atomOp("scope/delete-atom", atomEl);
      closePopup();
    });
    ta.addEventListener("keydown", function (ev) {
      if (ev.key === "Escape") { ev.preventDefault(); closePopup(); }
      if (ev.key === "Enter" && (ev.ctrlKey || ev.metaKey)) {
        ev.preventDefault(); atomOp("scope/edit-atom", atomEl, ta.value); closePopup();
      }
    });
  }

  // ------------------------------------------------------------------- pill
  var pill;
  // Derive from the socket's live readyState rather than a cached flag, so the
  // indicator is self-correcting even if an event is missed or arrives late.
  function paintPill() {
    var live = !!ws && ws.readyState === 1;
    state.connected = live;
    if (!pill) return;
    pill.textContent = live
      ? ("emacs " + (editing ? "EDIT" : (armed === false ? "read\u00b7disarmed" : "read"))
         + " " + VERSION)
      : ("emacs closed " + (ws ? ws.readyState : "-"));
    pill.title = live
      ? "connected to latex-wysiwyg on :" + PORT
      : "no Emacs: M-x latex-wysiwyg-start";
    pill.style.opacity = live ? "1" : "0.45";
  }

  function build() {
    var style = document.createElement("style");
    style.textContent =
      ".wysiwyg-active{background:rgba(120,170,240,.16);" +
      "box-shadow:-3px 0 0 0 var(--link,#79b4f0);border-radius:2px;}" +
      "#wysiwyg-pill{position:fixed;right:.9rem;bottom:.9rem;z-index:999;" +
      "font:600 .72rem/1 system-ui,sans-serif;letter-spacing:.06em;" +
      "text-transform:uppercase;padding:.42rem .6rem;border-radius:999px;" +
      "background:var(--bg-sunk,#eee);color:var(--ink-muted,#555);" +
      "border:1px solid var(--rule,#ccc);cursor:default;user-select:none}" +
      "[data-sourcepos]{scroll-margin-top:4rem}" +
      ".wysiwyg-editing .wysiwyg-run{outline:1px dashed rgba(120,170,240,.45);" +
      "outline-offset:2px;border-radius:2px}" +
      ".wysiwyg-run:focus{outline:2px solid var(--link,#79b4f0);background:" +
      "rgba(120,170,240,.10)}" +
      ".wysiwyg-ok{background:rgba(110,190,120,.30)!important;transition:background .3s}" +
      ".wysiwyg-bad{background:rgba(224,108,124,.35)!important;transition:background .3s}" +
      ".wysiwyg-editing p[data-sourcepos]{outline:1px dashed rgba(120,170,240,.40);" +
      "outline-offset:3px;border-radius:3px}" +
      ".wysiwyg-editing p[data-sourcepos]:focus{outline:2px solid var(--link,#79b4f0)}" +
      ".wysiwyg-atom{border-radius:3px;padding:0 .1em}" +
      ".wysiwyg-editing .wysiwyg-math{background:rgba(224,178,60,.26);" +
      "box-shadow:inset 0 -2px 0 rgba(200,150,20,.75);cursor:help}" +
      ".wysiwyg-editing .wysiwyg-opaque{background:rgba(224,108,124,.22);" +
      "box-shadow:inset 0 -2px 0 rgba(190,70,90,.65);cursor:not-allowed}" +
      "#wysiwyg-pill{cursor:pointer}" +
      "#wysiwyg-tex{position:absolute;z-index:1000;width:26rem;max-width:90vw;" +
      "background:var(--bg-sunk,#f2f0ec);border:1px solid var(--rule,#ccc);" +
      "border-radius:6px;padding:.6rem;box-shadow:0 10px 30px rgb(0 0 0/.35);" +
      "font:400 .8rem/1.4 system-ui,sans-serif;color:var(--ink,#222)}" +
      "#wysiwyg-tex label{display:block;font-size:.68rem;letter-spacing:.09em;" +
      "text-transform:uppercase;color:var(--ink-muted,#666);margin-bottom:.3rem}" +
      "#wysiwyg-tex textarea{width:100%;min-height:4.5rem;font:400 .82rem/1.4 " +
      "ui-monospace,Menlo,monospace;background:var(--bg,#fff);color:var(--ink,#222);" +
      "border:1px solid var(--rule,#ccc);border-radius:4px;padding:.4rem;resize:vertical}" +
      "#wysiwyg-tex .row{display:flex;gap:.4rem;margin-top:.5rem}" +
      "#wysiwyg-tex button{font:600 .75rem system-ui,sans-serif;padding:.3rem .7rem;" +
      "border-radius:4px;border:1px solid var(--rule,#ccc);cursor:pointer;" +
      "background:var(--bg,#fff);color:var(--ink,#222)}" +
      "#wysiwyg-tex button[data-a=del]{color:#b3323f;border-color:#b3323f}" +
      "#wysiwyg-toast{position:fixed;right:.9rem;bottom:3.1rem;z-index:999;" +
      "max-width:26rem;opacity:0;transition:opacity .25s;pointer-events:none;" +
      "font:500 .8rem/1.35 system-ui,sans-serif;padding:.6rem .8rem;" +
      "border-radius:6px;background:#3a2226;color:#f4dada;" +
      "border:1px solid #7a4048;box-shadow:0 6px 20px rgb(0 0 0/.35)}";
    document.head.appendChild(style);

    pill = document.createElement("div");
    pill.id = "wysiwyg-pill";
    pill.addEventListener("click", function () { setEditing(!editing); });
    document.body.appendChild(pill);
    paintPill();

    document.addEventListener("click", function (ev) {
      // Never hijack a real link or an in-page anchor.
      if (ev.target.closest && ev.target.closest("a")) return;
      if (editing && ev.target.closest &&
          ev.target.closest(".wysiwyg-run")) return;   // editing, not navigating
      var el = anchorFor(ev.target);
      if (!el) return;
      var p = parsePos(el);
      if (!p) return;
      light(el);
      send({ type: "scope/point", file: p.file, line: p.line, col: p.col });
    }, true);
  }

  function boot() {
    build();
    connect();
    // Cheap self-heal: repaint from readyState, and reconnect if the socket
    // died without an onclose ever reaching us.
    setInterval(function () {
      paintPill();
      if (!ws || ws.readyState === 3) connect();
    }, 2000);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else { boot(); }
})();
