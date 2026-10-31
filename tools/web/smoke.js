// Smoke-tests the browser build in headless Chrome: serves shared/build/dist/web, clicks start,
// walks a "mouse foot" around, steps through every animation, and saves a screenshot of each.
// Fails (exit 1) on any page error, console error, or scene error shown in the panel.
// Usage: PW=<path to playwright> node tools/web/smoke.js <dist dir> <output dir> [seconds per scene]
const fs = require('fs');
const path = require('path');
const http = require('http');
const { chromium } = require(process.env.PW || 'playwright');

const dist = path.resolve(process.argv[2]);
const out = path.resolve(process.argv[3]);
const perScene = Number(process.argv[4] || 1.6);
fs.mkdirSync(out, { recursive: true });
// Like a typical static host (itch.io's CDN, S3, GitHub Pages): only common extensions get a
// specific type; anything else is a generic download, which browsers refuse to run as a module.
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json' };

const server = http.createServer((req, res) => {
  const file = path.join(dist, decodeURIComponent(req.url.split('?')[0]).replace(/\/$/, '/index.html'));
  if (!file.startsWith(dist) || !fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});

(async () => {
  await new Promise(r => server.listen(0, r));
  const url = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--autoplay-policy=no-user-gesture-required'] });
  const page = await browser.newPage({ viewport: { width: 960, height: 540 } });
  const problems = [];
  page.on('pageerror', e => problems.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') problems.push('console: ' + m.text()); });
  await page.goto(url);
  await page.waitForSelector('#start .go');
  await page.screenshot({ path: path.join(out, '00-start.png') });
  await page.click('#start');
  await page.waitForTimeout(800);

  // Open the panel once to read the scene list and check it renders.
  await page.keyboard.press('Space');
  await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(out, '01-panel.png') });
  const count = await page.$$eval('#panel button.tab', b => b.length);
  await page.keyboard.press('Space');

  // Total animations = sum over categories (tabs are rebuilt on click, so re-query each time).
  let total = 0;
  await page.keyboard.press('Space');
  for (let i = 0; i < count; i++) {
    await page.locator('#panel button.tab').nth(i).click();
    total += await page.$$eval('#panel button.scene', b => b.length);
  }
  // Back to the very first animation, then close the panel.
  await page.locator('#panel button.tab').nth(0).click();
  await page.locator('#panel button.scene').nth(0).click();
  await page.keyboard.press('Space');
  await page.waitForTimeout(300);

  const results = [];
  for (let s = 0; s < total; s++) {
    // A foot walking a loop through the middle of the floor.
    const steps = Math.round(perScene * 20);
    for (let k = 0; k < steps; k++) {
      const a = k / steps * Math.PI * 2;
      await page.mouse.move(480 + Math.cos(a) * 200, 270 + Math.sin(a * 2) * 110);
      await page.waitForTimeout(50);
    }
    await page.keyboard.press('Space');
    await page.waitForTimeout(150);
    const info = await page.evaluate(() => ({
      title: document.querySelector('#panel button.scene.active')?.textContent?.replace('✓ ', '') || '?',
      status: document.querySelector('#panel .status')?.textContent || '',
    }));
    await page.keyboard.press('Space');
    await page.waitForTimeout(250);
    const name = String(s + 2).padStart(2, '0') + '-' + info.title.toLowerCase().replace(/[^a-z0-9]+/g, '_') + '.png';
    await page.screenshot({ path: path.join(out, name) });
    results.push(`${info.title}: ${info.status}`);
    if (info.status.includes('⚠')) problems.push(`${info.title}: ${info.status}`);
    await page.keyboard.press('ArrowRight');
  }
  // Keystone: K enters edit mode, drag the top-right corner inward, K again saves it.
  await page.keyboard.press('k');
  await page.waitForTimeout(200);
  await page.mouse.move(958, 2);
  await page.mouse.down();
  await page.mouse.move(860, 60, { steps: 8 });
  await page.mouse.up();
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(out, '99-keystone_edit.png') });
  await page.keyboard.press('k');
  const saved = await page.evaluate(() => localStorage.getItem('phantomfloor.keystone'));
  if (!saved || saved.startsWith('-1.0,-1.0,1.0,-1.0,1.0,1.0')) problems.push('keystone drag was not saved: ' + saved);
  results.push('keystone saved: ' + saved);
  const audio = await page.evaluate(() => typeof AudioContext !== 'undefined');
  results.push('Web Audio available: ' + audio);
  console.log(results.join('\n'));
  await browser.close();
  server.close();
  if (problems.length) { console.error('PROBLEMS:\n' + [...new Set(problems)].join('\n')); process.exit(1); }
  console.log(`OK: ${total} animations rendered without errors`);
})().catch(e => { console.error(e); server.close(); process.exit(1); });
