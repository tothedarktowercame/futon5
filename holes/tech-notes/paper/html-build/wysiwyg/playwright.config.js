// Playwright config for M-latex-wysiwyg slice S1.
//
// The page is served over http rather than opened as file://, because a
// file:// origin is not reliably allowed to open a ws:// socket in Chromium.
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  timeout: 60000,
  expect: { timeout: 10000 },
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:8129',
    trace: 'off',
    headless: true,
    // Use the system Google Chrome rather than a downloaded browser build:
    // the ms-playwright cache here holds 1208/1217 and this Playwright wants
    // 1234, and there is no reason to pull 170 MB for a localhost test.
    channel: 'chrome',
  },
  webServer: {
    command: 'python3 -m http.server 8129 --bind 127.0.0.1 --directory page',
    url: 'http://127.0.0.1:8129/draft8.html',
    reuseExistingServer: true,
    timeout: 20000,
  },
});
