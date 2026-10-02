// Renders icon.svg into launcher PNGs for every mipmap density.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const svg = fs.readFileSync(path.join(__dirname, 'icon.svg'), 'utf8');
const sizes = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
const res = path.join(__dirname, '..', 'app', 'res');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  for (const round of [false, true]) {
    for (const [d, s] of Object.entries(sizes)) {
      await page.setViewportSize({ width: s, height: s });
      const clip = round ? 'border-radius:50%;overflow:hidden;' : 'border-radius:22%;overflow:hidden;';
      await page.setContent(`<html><body style="margin:0;background:transparent"><div style="width:${s}px;height:${s}px;${clip}">${svg.replace('width="512" height="512"', `width="${s}" height="${s}"`)}</div></body></html>`);
      const dir = path.join(res, `mipmap-${d}`);
      fs.mkdirSync(dir, { recursive: true });
      await page.screenshot({ path: path.join(dir, round ? 'ic_launcher_round.png' : 'ic_launcher.png'), omitBackground: true });
    }
  }
  await page.setViewportSize({ width: 512, height: 512 });
  await page.setContent(`<html><body style="margin:0">${svg}</body></html>`);
  await page.screenshot({ path: path.join(__dirname, 'icon-512.png') });
  await browser.close();
})();
