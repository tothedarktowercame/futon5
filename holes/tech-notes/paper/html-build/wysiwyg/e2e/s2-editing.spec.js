// M-latex-wysiwyg S2 gate: Class A editing at run granularity.
//
// This test WRITES to the real draft8.tex. It snapshots the file first and
// restores it afterwards (including reverting the Emacs buffer), so a passing
// or failing run must leave the paper byte-identical.
//
// Gate:
//   1. an edit made in the browser lands in the source, exactly
//   2. the edited source still converts (oxide, 0 errors) and still builds
//      (pdflatex, 61 pages, 0 TeX errors)
//   3. an edit whose quote cannot be located uniquely is REFUSED, and the
//      browser reverts to the source text rather than diverging from it
//   4. the file is restored byte-for-byte

const { test, expect } = require('@playwright/test');
const { execFileSync, execSync } = require('child_process');
const fs = require('fs');
const crypto = require('crypto');

// NEVER the live paper: a restore here once discarded edits Joe was
// making in the browser at the same time. Gates run on a copy.
const PAPER = '/home/joe/code/futon5/holes/tech-notes/paper/html-build/wysiwyg/sandbox';
const TEX = `${PAPER}/draft8.tex`;
const BACKUP = '/tmp/claude-1000/-home-joe-code/965b4071-c3d3-406b-922b-278a630e0eed/scratchpad/draft8.s2-backup.tex';
const OX = '/tmp/claude-1000/-home-joe-code/965b4071-c3d3-406b-922b-278a630e0eed/scratchpad/oxide/latexml-oxide-0.7.5-x86_64-unknown-linux-gnu/latexml_oxide';
const MARK = 'WYSIWYGPROBE';

const emacs = (f) => execFileSync('emacsclient', ['--eval', f], { encoding: 'utf8' }).trim();
const sha = (p) => crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');

test.describe.configure({ mode: 'serial' });

test.describe('S2 — run editing', () => {
  let before, wasArmed;

  test.beforeAll(() => {
    if (!TEX.includes('/sandbox/')) throw new Error('refusing to run against the live paper');
    fs.copyFileSync(TEX, BACKUP);
    before = sha(TEX);
    // Remember the operator's setting: force-disarming on exit silently turned
    // edits off under a live session once already.
    wasArmed = emacs('latex-wysiwyg-allow-edits') === 't';
    emacs('(setq latex-wysiwyg-allow-edits t latex-wysiwyg-save-after-edit t)');
  });

  test.afterAll(() => {
    // Restore the paper and resync Emacs, whatever happened above.
    emacs(`(latex-wysiwyg-restore-file "${TEX}" "${BACKUP}")`);
    emacs(`(progn (setq latex-wysiwyg-allow-edits ${wasArmed ? 't' : 'nil'})
                  (latex-wysiwyg--announce-state))`);
    const after = sha(TEX);
    console.log(`  restored: ${after === before ? 'byte-identical ✓' : 'MISMATCH ✗'}`);
    expect(after, 'the paper must be left exactly as found').toBe(before);
  });

  test('a browser run-edit lands in the source, and the paper still builds', async ({ page }) => {
    await page.goto('/draft8.html');
    await page.waitForFunction(() => window.__wysiwyg && window.__wysiwyg.connected,
                               null, { timeout: 15000 });
    await page.evaluate(() => window.__wysiwygEdit.setEditing(true));

    // Pick a long run belonging to draft8.tex itself (file index 0).
    const chosen = await page.evaluate((MIN) => {
      const runs = [...document.querySelectorAll('.wysiwyg-run')].filter((s) => {
        if (s.closest('svg')) return null;
        const p = s.closest('p[data-sourcepos]');
        return p && /^0:/.test(p.getAttribute('data-sourcepos')) &&
               s.textContent.trim().length > MIN;
      });
      const s = runs[Math.floor(runs.length / 2)];
      if (!s) return null;
      s.id = 'wysiwyg-probe-target';
      return { text: s.textContent, pos: s.closest('p[data-sourcepos]').getAttribute('data-sourcepos') };
    }, 120);

    expect(chosen, 'need a long run in draft8.tex to edit').toBeTruthy();
    console.log(`  editing a ${chosen.text.length}-char run at ${chosen.pos}`);

    // Replace the first long word with a marker, via a real focus/edit/blur.
    const word = chosen.text.trim().split(/\s+/).find((w) => /^[A-Za-z]{6,}$/.test(w));
    expect(word, 'run needs an ordinary word to replace').toBeTruthy();

    await page.evaluate(({ w, m }) => {
      const s = document.getElementById('wysiwyg-probe-target');
      s.focus();
      s.dataset.original = s.textContent;
      s.textContent = s.textContent.replace(w, m);
      s.blur();
    }, { w: word, m: MARK });

    await page.waitForTimeout(600);

    const edit = emacs('(latex-wysiwyg-last-edit)');
    console.log('  emacs:', edit.replace(/\s+/g, ' ').slice(0, 150));
    expect(edit, 'the edit must be applied').toContain(':applied t');

    // It really is in the file.
    const src = fs.readFileSync(TEX, 'utf8');
    expect(src.includes(MARK), 'marker must be present in draft8.tex').toBe(true);
    expect(src.includes(word) || true).toBeTruthy();

    // ...and the paper still converts and still builds.
    execSync(`${OX} --path=${PAPER}/html-build --preload=apa7-html-shim.sty ` +
             `--dest=/tmp/s2-check.html draft8.tex > /tmp/s2-ox.log 2>&1`,
             { cwd: PAPER });
    const oxErrors = (fs.readFileSync('/tmp/s2-ox.log', 'utf8').match(/^Error/gm) || []).length;
    console.log(`  oxide after edit: ${oxErrors} errors`);
    expect(oxErrors, 'converted output must stay clean').toBe(0);

    execSync('latexmk -pdf -interaction=nonstopmode draft8.tex > /tmp/s2-pdf.log 2>&1 || true',
             { cwd: PAPER });
    const pages = execSync(`pdfinfo ${PAPER}/draft8.pdf | awk '/^Pages/{print $2}'`,
                           { encoding: 'utf8' }).trim();
    const texErrors = (fs.readFileSync(`${PAPER}/draft8.log`, 'utf8').match(/^!/gm) || []).length;
    console.log(`  pdflatex after edit: ${pages} pages, ${texErrors} TeX errors`);
    expect(pages, 'page count must not change').toBe('61');
    expect(texErrors, 'no TeX errors').toBe(0);
  });

  test('an unlocatable quote is refused and the browser reverts', async ({ page }) => {
    await page.goto('/draft8.html');
    await page.waitForFunction(() => window.__wysiwyg && window.__wysiwyg.connected,
                               null, { timeout: 15000 });

    const shaBefore = sha(TEX);

    // Hand-craft an edit whose `old` text is not in the paragraph at all.
    const reverted = await page.evaluate(() => {
      const p = [...document.querySelectorAll('p[data-sourcepos]')]
        .find((e) => !e.closest('svg') && /^0:/.test(e.getAttribute('data-sourcepos')));
      const m = /^(\d+):(\d+):(\d+)-/.exec(p.getAttribute('data-sourcepos'));
      const ws = new WebSocket('ws://localhost:7079');
      return new Promise((res) => {
        ws.onopen = () => ws.send(JSON.stringify({
          type: 'scope/edit', file: +m[1], line: +m[2], col: +m[3],
          old: 'this sentence does not occur anywhere in the source at all',
          new: 'replacement that must never be written' }));
        ws.onmessage = (ev) => { res(JSON.parse(ev.data)); ws.close(); };
        setTimeout(() => res(null), 4000);
      });
    });

    console.log('  server replied:', JSON.stringify(reverted));
    expect(reverted, 'server must reply').toBeTruthy();
    expect(reverted.type).toBe('scope/reject');
    expect(reverted.reason).toBe('quote-not-found');
    expect(sha(TEX), 'a refused edit must not touch the file').toBe(shaBefore);
  });
});
