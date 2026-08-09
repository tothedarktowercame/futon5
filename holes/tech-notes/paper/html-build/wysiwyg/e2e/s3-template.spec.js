// M-latex-wysiwyg S3 gate: paragraph-template editing.
//
// The case that motivated it (Joe, 2026-08-08): in
//   "...flipping the bits that are mapped under <math>sigma</math> names;"
// the word "names" is a sub-threshold run after a formula, and run-level
// editing left it silently uneditable. Paragraph templates must make it
// editable while leaving the formula byte-identical.
//
// Restores draft8.tex afterwards.

const { test, expect } = require('@playwright/test');
const { execFileSync } = require('child_process');
const fs = require('fs');
const crypto = require('crypto');

// NEVER the live paper: a restore here once discarded edits Joe was
// making in the browser at the same time. Gates run on a copy.
const PAPER = '/home/joe/code/futon5/holes/tech-notes/paper/html-build/wysiwyg/sandbox';
const TEX = `${PAPER}/draft8.tex`;
const BACKUP = '/tmp/claude-1000/-home-joe-code/965b4071-c3d3-406b-922b-278a630e0eed/scratchpad/draft8.s3-backup.tex';
const emacs = (f) => execFileSync('emacsclient', ['--eval', f], { encoding: 'utf8' }).trim();
const sha = (p) => crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');

test.describe.configure({ mode: 'serial' });

test.describe('S3 — paragraph templates', () => {
  let before, wasArmed;

  test.beforeAll(() => {
    if (!TEX.includes('/sandbox/')) throw new Error('refusing to run against the live paper');
    fs.copyFileSync(TEX, BACKUP);
    before = sha(TEX);
    wasArmed = emacs('latex-wysiwyg-allow-edits') === 't';
    emacs('(setq latex-wysiwyg-allow-edits t latex-wysiwyg-save-after-edit t)');
  });

  test.afterAll(() => {
    emacs(`(latex-wysiwyg-restore-file "${TEX}" "${BACKUP}")`);
    emacs(`(progn (setq latex-wysiwyg-allow-edits ${wasArmed ? 't' : 'nil'})
                  (latex-wysiwyg--announce-state))`);
    console.log(`  restored: ${sha(TEX) === before ? 'byte-identical' : 'MISMATCH'}`);
    expect(sha(TEX)).toBe(before);
  });

  test('a short literal after a formula is editable, and the formula survives', async ({ page }) => {
    await page.goto('/draft8.html');
    await page.waitForFunction(() => window.__wysiwyg && window.__wysiwyg.connected,
                               null, { timeout: 15000 });
    await page.evaluate(() => window.__wysiwygEdit.setEditing(true));

    // A draft8 paragraph with at least one atom AND a short trailing literal.
    const info = await page.evaluate(() => {
      const ps = [...document.querySelectorAll('p[data-sourcepos]')].filter(
        (p) => !p.closest('svg') && /^0:/.test(p.getAttribute('data-sourcepos')));
      for (const p of ps) {
        const kids = [...p.childNodes];
        const atoms = kids.filter((n) => n.nodeType === 1).length;
        const shortLit = kids.find(
          (n) => n.nodeType === 3 && n.nodeValue.trim().length > 2
                                  && n.nodeValue.trim().length < 30);
        if (atoms >= 1 && shortLit) {
          p.id = 'wysiwyg-s3-target';
          return { pos: p.getAttribute('data-sourcepos'), atoms,
                   shortLit: shortLit.nodeValue,
                   mathCount: p.querySelectorAll('math').length };
        }
      }
      return null;
    });

    expect(info, 'need a paragraph with an atom and a short literal').toBeTruthy();
    console.log(`  paragraph ${info.pos}: ${info.atoms} atoms (${info.mathCount} math), ` +
                `short literal ${JSON.stringify(info.shortLit.trim())}`);

    // Capture the formula source before the edit, to prove it survives.
    const texBefore = await page.evaluate(() => {
      const m = document.getElementById('wysiwyg-s3-target').querySelector('math');
      return m ? m.getAttribute('alttext') : null;
    });

    // Edit ONLY the short literal -- the case that used to be impossible.
    await page.evaluate(() => {
      const p = document.getElementById('wysiwyg-s3-target');
      p.focus();
      for (const n of [...p.childNodes]) {
        if (n.nodeType === 3 && n.nodeValue.trim().length > 2 &&
            n.nodeValue.trim().length < 30) {
          n.nodeValue = n.nodeValue.replace(/(\w+)/, 'S3MARK');
          break;
        }
      }
      p.blur();
    });
    await page.waitForTimeout(700);

    const res = emacs('(latex-wysiwyg-last-edit)');
    console.log('  emacs:', res.replace(/\s+/g, ' ').slice(0, 130));
    expect(res, 'the paragraph edit must apply').toContain(':applied t');

    const src = fs.readFileSync(TEX, 'utf8');
    expect(src.includes('S3MARK'), 'marker must reach the source').toBe(true);

    // The formula's TeX must still be present, untouched, in the source.
    if (texBefore) {
      const core = texBefore.replace(/[\s{}]/g, '').slice(0, 12);
      const flat = src.replace(/[\s{}]/g, '');
      expect(flat.includes(core), `formula ${texBefore} must survive verbatim`).toBe(true);
    }
  });

  test('altering a formula is refused, not guessed at', async ({ page }) => {
    await page.goto('/draft8.html');
    await page.waitForFunction(() => window.__wysiwyg && window.__wysiwyg.connected,
                               null, { timeout: 15000 });
    await page.evaluate(() => window.__wysiwygEdit.setEditing(true));
    const shaBefore = sha(TEX);

    const reply = await page.evaluate(() => {
      const p = [...document.querySelectorAll('p[data-sourcepos]')].find(
        (e) => !e.closest('svg') && /^0:/.test(e.getAttribute('data-sourcepos')) &&
               e.querySelector('math'));
      const m = /^(\d+):(\d+):(\d+)-/.exec(p.getAttribute('data-sourcepos'));
      const tpl = [];
      [...p.childNodes].forEach((n) => {
        if (n.nodeType === 3) tpl.push({ k: 'lit', t: n.nodeValue });
        else if (n.nodeType === 1) tpl.push({ k: 'atom', t: n.textContent });
      });
      // drop an atom: the server must refuse rather than reflow the source
      const broken = tpl.filter((e) => e.k === 'lit');
      const ws = new WebSocket('ws://localhost:7079');
      return new Promise((res) => {
        ws.onopen = () => ws.send(JSON.stringify({
          type: 'scope/edit-para', file: +m[1], line: +m[2], col: +m[3],
          old: tpl, new: broken }));
        ws.onmessage = (ev) => { res(JSON.parse(ev.data)); ws.close(); };
        setTimeout(() => res(null), 4000);
      });
    });

    console.log('  server replied:', JSON.stringify(reply));
    expect(reply.type).toBe('scope/reject');
    expect(reply.reason).toBe('atoms-changed');
    expect(sha(TEX), 'a refused edit must not touch the file').toBe(shaBefore);
  });
});
