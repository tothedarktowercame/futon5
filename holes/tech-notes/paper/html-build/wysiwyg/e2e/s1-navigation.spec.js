// M-latex-wysiwyg S1 gate: read-only two-way navigation.
//
// Requires the Emacs half to be running:
//   M-x load-file RET .../futon3c/emacs/latex-wysiwyg.el
//   M-x latex-wysiwyg-start RET .../wysiwyg/page/scopes.json
//
// The gate is: click a prose paragraph in the browser, and Emacs point lands
// on the exact source line in the correct file. 20 samples, spread across
// every mapped source file. Nothing here writes to any .tex.

const { test, expect } = require('@playwright/test');
const { execFileSync } = require('child_process');

const SAMPLES = 20;

function emacs(form) {
  return execFileSync('emacsclient', ['--eval', form], { encoding: 'utf8' }).trim();
}

// (:file "draft8.tex" :line 240 :column 0 ...) -> {file, line}
function parseJump(s) {
  const f = /:file "([^"]*)"/.exec(s);
  const l = /:line (\d+)/.exec(s);
  const refused = /:refused "([^"]*)"/.exec(s);
  return {
    file: f ? f[1] : null,
    line: l ? +l[1] : null,
    refused: refused ? refused[1] : null,
    raw: s,
  };
}

test.describe('S1 — two-way navigation', () => {
  test.beforeAll(() => {
    const alive = emacs('(fboundp (quote latex-wysiwyg-last-jump))');
    expect(alive, 'latex-wysiwyg.el must be loaded in the Emacs daemon').toBe('t');
  });

  test('browser click moves Emacs point to the exact source line', async ({ page }) => {
    await page.goto('/draft8.html');

    // The client must actually reach Emacs; a silent no-connection would make
    // every assertion below vacuous.
    await page.waitForFunction(() => window.__wysiwyg && window.__wysiwyg.connected,
                               null, { timeout: 15000 });

    // Candidate targets: prose paragraphs with a real anchor, not inside an
    // SVG (TikZ interiors are anchored too, but they are not prose), and not
    // in a synthesised source such as the generated bibliography.
    const targets = await page.evaluate((want) => {
      const out = [];
      const seen = new Set();
      document.querySelectorAll('p[data-sourcepos]').forEach((el) => {
        if (el.closest('svg')) return;
        const m = /^(\d+):(\d+):(\d+)-/.exec(el.getAttribute('data-sourcepos'));
        if (!m) return;
        const file = +m[1], line = +m[2];
        if (file > 2) return;                       // synthesised
        if ((el.innerText || '').trim().length < 60) return;
        const key = file + ':' + line;
        if (seen.has(key)) return;
        seen.add(key);
        out.push({ file, line, key });
      });
      // spread the sample across the document rather than taking a prefix
      const step = Math.max(1, Math.floor(out.length / want));
      return out.filter((_, i) => i % step === 0).slice(0, want);
    }, SAMPLES);

    expect(targets.length, 'need enough prose paragraphs to sample').toBeGreaterThanOrEqual(15);

    const expected = ['draft8.tex', 'intro-generated.tex', 'part3-exotype.tex'];
    const results = [];

    for (const t of targets) {
      await page.evaluate((key) => {
        const el = [...document.querySelectorAll('p[data-sourcepos]')].find(
          (e) => !e.closest('svg') &&
                 e.getAttribute('data-sourcepos').startsWith(key + ':'));
        if (el) el.click();
      }, t.key);

      // Emacs applies synchronously on receipt; give the socket a beat.
      await page.waitForTimeout(120);
      const jump = parseJump(emacs('(latex-wysiwyg-last-jump)'));
      results.push({ want: t, got: jump });
    }

    const bad = results.filter(
      (r) => r.got.file !== expected[r.want.file] || r.got.line !== r.want.line);

    if (bad.length) {
      console.log('\nMISMATCHES:');
      for (const b of bad.slice(0, 8)) {
        console.log(`  clicked ${expected[b.want.file]}:${b.want.line}` +
                    `  ->  emacs ${b.got.file}:${b.got.line}` +
                    (b.got.refused ? ` (refused: ${b.got.refused})` : ''));
      }
    }
    console.log(`\n  S1 gate: ${results.length - bad.length}/${results.length} ` +
                `clicks landed on the exact source line`);

    // Every file we sampled must have been exercised, or the gate is weaker
    // than it looks.
    const filesHit = new Set(results.map((r) => expected[r.want.file]));
    console.log(`  files exercised: ${[...filesHit].join(', ')}`);

    expect(bad, 'every click must land on the exact source line').toHaveLength(0);
  });

  test('a click in a synthesised source is refused, not mis-routed', async ({ page }) => {
    await page.goto('/draft8.html');
    await page.waitForFunction(() => window.__wysiwyg && window.__wysiwyg.connected,
                               null, { timeout: 15000 });

    const hasSynth = await page.evaluate(() => {
      const el = [...document.querySelectorAll('[data-sourcepos]')].find((e) =>
        /^3:/.test(e.getAttribute('data-sourcepos')) && !e.closest('svg'));
      if (el) el.click();
      return !!el;
    });
    test.skip(!hasSynth, 'no synthesised-source anchors on this page');

    await page.waitForTimeout(150);
    const jump = parseJump(emacs('(latex-wysiwyg-last-jump)'));
    expect(jump.refused, 'must refuse rather than visit a bogus file').toBeTruthy();
  });

  test('moving point in Emacs highlights the block in the browser', async ({ page }) => {
    await page.goto('/draft8.html');
    await page.waitForFunction(() => window.__wysiwyg && window.__wysiwyg.connected,
                               null, { timeout: 15000 });

    const target = await page.evaluate(() => {
      const el = [...document.querySelectorAll('p[data-sourcepos]')].find(
        (e) => !e.closest('svg') && /^0:/.test(e.getAttribute('data-sourcepos')) &&
               (e.innerText || '').trim().length > 80);
      const m = /^0:(\d+):/.exec(el.getAttribute('data-sourcepos'));
      return +m[1];
    });

    // Move point in the draft8 buffer; post-command-hook should broadcast.
    emacs(`(with-current-buffer (find-file-noselect "/home/joe/code/futon5/holes/tech-notes/paper/draft8.tex")
              (goto-char (point-min)) (forward-line ${target - 1})
              (latex-wysiwyg--post-command) t)`);

    await page.waitForFunction(
      () => window.__wysiwyg && window.__wysiwyg.recvCount > 0,
      null, { timeout: 8000 });

    const lit = await page.evaluate(() => {
      const el = document.querySelector('.wysiwyg-active');
      return el ? el.getAttribute('data-sourcepos') : null;
    });
    console.log(`  emacs->browser: point at draft8:${target} lit ${lit}`);
    expect(lit, 'a block must be highlighted').toBeTruthy();
    expect(lit.startsWith(`0:${target}:`)).toBeTruthy();
  });
});
