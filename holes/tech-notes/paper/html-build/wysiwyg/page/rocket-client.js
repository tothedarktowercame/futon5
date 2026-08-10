/* rocket-client.js -- browser half of the highlight-and-speak loop.
 *
 * Tracks the current text selection (with its data-sourcepos anchor) and
 * keeps the rocket receiver (port 8130) informed, so that when a whisper
 * transcript lands rocket-completed the receiver can join the two streams
 * into a record. Also polls the receiver's annotations and paints them as
 * typed track-changes: each applied pattern gets a hue, each applied site a
 * <mark>, hover shows the pattern label.
 *
 * Read-only with respect to the document and its source: all edits flow
 * through claude patching the tex (the watcher then rebuilds this page).
 * State mirrored on window.__rocket for debugging / Playwright.
 */
(function () {
  "use strict";

  var BASE = "http://127.0.0.1:8130";
  var state = { lastSelection: null, sent: 0, annotations: [],
                painted: 0, error: null };
  window.__rocket = state;

  // ---------------------------------------------------------- selection
  function anchorFor(node) {
    var el = node instanceof Element ? node : node && node.parentElement;
    while (el && el !== document.body) {
      if (el.hasAttribute && el.hasAttribute("data-sourcepos")) return el;
      el = el.parentElement;
    }
    return null;
  }

  var debounce = null;
  document.addEventListener("selectionchange", function () {
    if (debounce) clearTimeout(debounce);
    debounce = setTimeout(function () {
      var sel = window.getSelection();
      var text = sel ? String(sel).trim() : "";
      if (!text) return;                    // keep last non-empty region
      var a = sel.anchorNode && anchorFor(sel.anchorNode);
      var payload = {
        text: text,
        pos: a ? a.getAttribute("data-sourcepos") : null,
        id: a ? (a.id || null) : null
      };
      state.lastSelection = payload;
      fetch(BASE + "/selection", {
        method: "POST", body: JSON.stringify(payload)
      }).then(function () { state.sent++; paintPill(); })
        .catch(function (e) { state.error = String(e); paintPill(); });
    }, 250);
  });

  // -------------------------------------------------------- annotations
  // Each annotation: {pat, label, hue?, sites:[{text}], note?}
  // Sites are verbatim strings as they appear in the page after the patch;
  // we walk text nodes and wrap first match of each site not yet painted.
  function hueFor(a, i) {
    if (typeof a.hue === "number") return a.hue;
    var h = 0, s = a.pat || String(i);
    for (var j = 0; j < s.length; j++) h = (h * 31 + s.charCodeAt(j)) % 360;
    return h;
  }

  function unpaint() {
    document.querySelectorAll("mark.rocket-mark").forEach(function (m) {
      var t = document.createTextNode(m.textContent);
      m.parentNode.replaceChild(t, m);
    });
    document.body.normalize();
    state.painted = 0;
  }

  function escapeRe(t) {
    return t.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }

  function paintSite(text, hue, label, pat) {
    // The page keeps the tex source's line breaks, so match any whitespace
    // run where the site string has a space.
    var re = new RegExp(text.trim().split(/\s+/).map(escapeRe)
                        .map(function (t) {
                          // tex dashes vs rendered dashes are equivalent
                          return t.replace(/-{1,3}|\u2014|\u2013/g,
                                           "[-\u2014\u2013]{1,3}");
                        })
                        .join("[\\s\u00a0]+"));
    var walker = document.createTreeWalker(document.body,
                                           NodeFilter.SHOW_TEXT, null);
    var n;
    while ((n = walker.nextNode())) {
      if (n.parentElement && n.parentElement.closest(
            "script, style, #rocket-pill, #rocket-log")) continue;
      var enclosing = n.parentElement &&
          n.parentElement.closest("mark.rocket-mark");
      if (enclosing && enclosing.getAttribute("data-pat") === pat) continue;
      var m = re.exec(n.nodeValue);
      if (!m) continue;
      var i = m.index;
      var r = document.createRange();
      r.setStart(n, i); r.setEnd(n, i + m[0].length);
      var m = document.createElement("mark");
      m.className = "rocket-mark";
      m.setAttribute("data-pat", pat || "");
      m.style.background = "hsla(" + hue + ",85%,82%," +
                           (enclosing ? "0.6" : "0.85") + ")";
      m.style.borderBottom = "2px solid hsl(" + hue + ",60%,45%)";
      if (enclosing) m.style.outline = "1px dotted hsl(" + hue + ",60%,40%)";
      m.title = enclosing ?
        label + "  [nested in: " + (enclosing.title || "mark") + "]" : label;
      try { r.surroundContents(m); state.painted++; } catch (e) {}
      return true;
    }
    return false;
  }

  function paint(anns) {
    unpaint();
    anns.forEach(function (a, i) {
      var hue = hueFor(a, i);
      (a.sites || []).forEach(function (s) {
        if (s && s.text) paintSite(s.text, hue,
                                   (a.pat || "pattern") +
                                   (a.note ? " -- " + a.note : ""), a.pat);
      });
    });
  }

  var lastAnnJson = "", seenLog = 0;
  function poll() {
    fetch(BASE + "/feed").then(function (r) { return r.json(); })
      .then(function (feed) {
        state.error = null;
        var anns = feed.annotations || [];
        var j = JSON.stringify(anns);
        if (j !== lastAnnJson || document.querySelectorAll(
              "mark.rocket-mark").length < state.painted) {
          lastAnnJson = j;
          state.annotations = anns;
          paint(anns);
        }
        state.log = feed.log || [];
        state.inFlight = feed.in_flight || 0;
        // toast every log line we have not shown yet (the visual trace of
        // the turn: dispatch, patch, count, paint)
        if (state.log.length > seenLog) {
          state.log.slice(seenLog).forEach(function (e) {
            toast(e.ts + "  " + e.msg);
          });
          seenLog = state.log.length;
          paintLogPanel();
        }
        paintPill();
      })
      .catch(function (e) { state.error = String(e); paintPill(); });
  }
  setInterval(poll, 2000);

  // ------------------------------------------------------ toast + log panel
  // HUD-style: toasts populate from the upper right, newest on top, at a
  // fixed width sized to sit over the Tufte margin column.
  var toastBox = null;
  function toast(msg) {
    if (!toastBox) {
      toastBox = document.createElement("div");
      toastBox.id = "rocket-toasts";
      toastBox.style.cssText =
        "position:fixed;top:1rem;right:1.5rem;z-index:9999;" +
        "width:min(22rem,24vw);display:flex;flex-direction:column;" +
        "gap:.5rem;pointer-events:none;";
      document.body.appendChild(toastBox);
    }
    var t = document.createElement("div");
    t.textContent = "🚀 " + msg;
    t.style.cssText =
      "font:13px/1.5 sans-serif;background:#333;color:#fff;" +
      "padding:6px 12px;border-radius:8px;opacity:0.95;" +
      "box-shadow:0 2px 8px rgba(0,0,0,.35);";
    toastBox.insertBefore(t, toastBox.firstChild);
    setTimeout(function () { t.remove(); }, 8000);
  }

  var panel = null, panelOpen = false;
  function paintLogPanel() {
    if (!panelOpen) return;
    if (!panel) {
      panel = document.createElement("div");
      panel.id = "rocket-log";
      panel.style.cssText =
        "position:fixed;bottom:3rem;right:.9rem;z-index:9998;width:26rem;" +
        "max-height:40vh;overflow-y:auto;font:12px/1.5 monospace;" +
        "background:#1b1b1b;color:#ddd;padding:.6rem .8rem;" +
        "border-radius:8px;box-shadow:0 2px 10px rgba(0,0,0,.4);";
      document.body.appendChild(panel);
    }
    panel.innerHTML = "";
    (state.log || []).slice().reverse().forEach(function (e) {
      var row = document.createElement("div");
      row.textContent = e.ts + "  " + e.msg;
      row.style.marginBottom = ".25rem";
      panel.appendChild(row);
    });
    panel.style.display = "block";
  }
  function toggleLogPanel() {
    panelOpen = !panelOpen;
    if (panel) panel.style.display = panelOpen ? "block" : "none";
    if (panelOpen) paintLogPanel();
  }

  // --------------------------------------------------------------- pill
  var pill = null;
  function paintPill() {
    if (!pill) {
      pill = document.createElement("div");
      pill.id = "rocket-pill";
      pill.style.cssText =
        "position:fixed;bottom:.9rem;right:9.5rem;z-index:9999;" +
        "font:12px/1.4 sans-serif;background:#222;color:#eee;" +
        "padding:4px 10px;border-radius:12px;opacity:0.85;cursor:pointer;";
      pill.addEventListener("click", toggleLogPanel);
      document.body.appendChild(pill);
    }
    // Sit a fixed 10px left of the wysiwyg pill, whatever size it currently
    // has (it changes width between Read/Edit states); measured every repaint.
    var wp = document.getElementById("wysiwyg-pill");
    if (wp) {
      var r = wp.getBoundingClientRect();
      if (r.width) pill.style.right = (window.innerWidth - r.left + 10) + "px";
    }
    var pats = {};
    state.annotations.forEach(function (a) {
      pats[a.pat || "?"] = (a.sites || []).length;
    });
    var names = Object.keys(pats);
    if (state.inFlight > 0) {
      pill.textContent = "🚀 rocket heard — processing\u2026";
      pill.style.background = "#7a3b10";
      pill.style.animation = "rocket-pulse 1s ease-in-out infinite";
      if (!document.getElementById("rocket-pulse-style")) {
        var st = document.createElement("style");
        st.id = "rocket-pulse-style";
        st.textContent = "@keyframes rocket-pulse{50%{opacity:0.45}}";
        document.head.appendChild(st);
      }
    } else {
      pill.style.background = "#222";
      pill.style.animation = "";
      pill.textContent = "🚀 " +
        (state.error ? "offline" :
         (names.length ? names.map(function (p) {
            return p + "×" + pats[p];
          }).join("  ") : "listening"));
    }
    pill.title = state.lastSelection ?
      ("fragment: " + state.lastSelection.text.slice(0, 120)) :
      "highlight a region, speak, say 'rocket'";
  }
  document.addEventListener("DOMContentLoaded", paintPill);
  paintPill();
})();
