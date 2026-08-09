const { chromium } = require('@playwright/test');
const { execFileSync } = require('child_process');
const fs=require('fs'),crypto=require('crypto');
const TEX='/home/joe/code/futon5/holes/tech-notes/paper/draft8.tex';
const BK='/tmp/claude-1000/-home-joe-code/965b4071-c3d3-406b-922b-278a630e0eed/scratchpad/draft8.arm-backup.tex';
const em=f=>execFileSync('emacsclient',['--eval',f],{encoding:'utf8'}).trim();
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
(async()=>{
  fs.copyFileSync(TEX,BK); const before=sha(TEX);
  const b=await chromium.launch({channel:'chrome'}); const p=await b.newPage();
  await p.goto('http://127.0.0.1:8129/draft8.html');
  await p.waitForTimeout(2200);
  console.log('  pill (armed):', await p.evaluate(()=>document.getElementById('wysiwyg-pill').textContent));
  await p.evaluate(()=>window.__wysiwygEdit.setEditing(true));
  console.log('  pill (edit) :', await p.evaluate(()=>document.getElementById('wysiwyg-pill').textContent));
  const ok=await p.evaluate(()=>{
    const s=[...document.querySelectorAll('.wysiwyg-run')].find(x=>!x.closest('svg')
      && /^0:/.test((x.closest('p[data-sourcepos]')||{getAttribute:()=>''}).getAttribute('data-sourcepos'))
      && x.textContent.trim().length>120);
    if(!s) return null; s.focus(); s.dataset.original=s.textContent;
    s.textContent=s.textContent.replace(/\b([A-Za-z]{6,})\b/,'ARMCHECK'); s.blur(); return true;});
  await p.waitForTimeout(700);
  console.log('  emacs      :', em('(latex-wysiwyg-last-edit)').replace(/\s+/g,' ').slice(0,90));
  console.log('  in file    :', fs.readFileSync(TEX,'utf8').includes('ARMCHECK'));
  // now disarm and confirm the UI refuses up front
  em('(latex-wysiwyg-arm-edits t)'); await p.waitForTimeout(400);
  console.log('  pill (disarmed):', await p.evaluate(()=>document.getElementById('wysiwyg-pill').textContent));
  await b.close();
  fs.copyFileSync(BK,TEX);
  em(`(with-current-buffer (find-file-noselect "${TEX}") (revert-buffer t t t) t)`);
  console.log('  restored   :', sha(TEX)===before ? 'byte-identical' : 'MISMATCH');
  em('(latex-wysiwyg-arm-edits)');
})();
