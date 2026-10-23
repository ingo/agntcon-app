// Opens the built web app in a headless browser and checks that it starts: the loading message goes away
// (the app takes over the page) and the first screen draws real content rather than a blank canvas.
//
//   node scripts/web-smoke-test.mjs <url> [--js]
//   node scripts/web-smoke-test.mjs http://localhost:8080/app/
//
// Needs Playwright (npm i playwright && npx playwright install chromium). --js hides WebAssembly support so
// the JavaScript fallback is tested instead of the WebAssembly build.
import { chromium } from 'playwright';

const url = process.argv[2];
const fallback = process.argv.includes('--js');
if (!url) { console.error('usage: web-smoke-test.mjs <url> [--js]'); process.exit(2); }

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
const problems = [];
page.on('pageerror', (e) => problems.push('page error: ' + e.message));
page.on('requestfailed', (r) => problems.push(`request failed: ${r.url()} ${r.failure()?.errorText}`));
if (fallback) await page.addInitScript(() => { WebAssembly.validate = () => false; });

const colours = async (png) => {
  const viewer = await browser.newPage();
  const count = await viewer.evaluate(async (b64) => {
    const img = new Image(); img.src = 'data:image/png;base64,' + b64; await img.decode();
    const c = document.createElement('canvas'); c.width = 96; c.height = 96;
    const ctx = c.getContext('2d'); ctx.drawImage(img, 0, 0, 96, 96);
    const d = ctx.getImageData(0, 0, 96, 96).data; const seen = new Set();
    for (let i = 0; i < d.length; i += 4) seen.add((d[i] << 16) | (d[i + 1] << 8) | d[i + 2]);
    return seen.size;
  }, png.toString('base64'));
  await viewer.close();
  return count;
};

const started = Date.now();
const elapsed = () => ((Date.now() - started) / 1000).toFixed(1);
await page.goto(url, { waitUntil: 'domcontentloaded' });
const loadingShown = await page.locator('#loading').count();

let drawn = 0;
const deadline = started + 120_000;
while (Date.now() < deadline) {
  const loading = await page.locator('#loading').count();
  if (loading === 0) {
    // The app has taken over the page; the splash is a plain background, a drawn screen has text and colours
    drawn = await colours(await page.screenshot());
    if (drawn > 25) break;
  }
  await page.waitForTimeout(1000);
}
await page.screenshot({ path: 'web-smoke.png' });
await browser.close();

if (problems.length) console.warn(problems.join('\n'));
if (drawn <= 25) {
  console.error(`The app did not start within 2 minutes (${drawn} colours on screen). See web-smoke.png.`);
  process.exit(1);
}
if (!loadingShown) console.warn('Warning: the loading message was not shown while the app loaded');
console.log(`web app started in ${elapsed()}s (${fallback ? 'JavaScript' : 'WebAssembly'}), ${drawn} colours on screen`);
