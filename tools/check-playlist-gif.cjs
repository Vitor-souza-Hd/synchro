// Run with Playwright and Sharp available in NODE_PATH. No server is required.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const { chromium } = require('playwright');

(async () => {
    const width = 400, height = 400, frames = 16;
    const raw = Buffer.alloc(width * height * frames * 3);
    let seed = 123456;
    for (let i = 0; i < raw.length; i += 3) {
        seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5;
        const color = seed & 255;
        raw[i] = (color >> 5) * 36;
        raw[i + 1] = ((color >> 2) & 7) * 36;
        raw[i + 2] = (color & 3) * 85;
    }
    const delay = Array.from({ length: frames }, (_, i) => i % 2 ? 110 : 70);
    const input = await sharp(raw, { raw: { width, height: height * frames, channels: 3, pageHeight: height } })
        .gif({ colours: 256, dither: 0, effort: 1, loop: 4, delay }).toBuffer();
    assert.ok(input.length > 2 * 1024 * 1024, `Fixture must exceed limit: ${input.length}`);
    const before = await sharp(input, { animated: true }).metadata();
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage();
        const root = path.resolve(__dirname, '../src/main/resources/static');
        await page.route('**/*', async route => {
            const url = new URL(route.request().url());
            if (url.hostname !== 'synchro.test') return route.abort();
            if (url.pathname === '/') return route.fulfill({ contentType: 'text/html', body: '<!doctype html><title>GIF test</title>' });
            if (url.pathname === '/fixture.gif') return route.fulfill({ contentType: 'image/gif', body: input });
            const file = path.resolve(root, '.' + url.pathname);
            if (!file.startsWith(root + path.sep) || !fs.existsSync(file)) return route.fulfill({ status: 404 });
            return route.fulfill({ contentType: 'application/javascript', body: fs.readFileSync(file) });
        });
        await page.goto('http://synchro.test/');
        await page.addScriptTag({ url: '/frontend/assets/js/playlists.js' });
        const data = await page.evaluate(async () => {
            const blob = await (await fetch('/fixture.gif')).blob();
            return readPlaylistImage(new File([blob], 'animated.gif', { type: 'image/gif' }));
        });
        assert.ok(data.startsWith('data:image/gif;base64,'));
        const output = Buffer.from(data.split(',')[1], 'base64');
        const after = await sharp(output, { animated: true }).metadata();
        assert.ok(output.length <= 2 * 1024 * 1024);
        assert.equal(after.pages, before.pages);
        assert.equal(after.width, before.width);
        assert.equal(after.pageHeight, before.pageHeight);
        assert.deepEqual(after.delay, before.delay);
        assert.equal(after.loop, before.loop);
        // Decode every output frame, rather than checking the GIF header alone.
        const decoded = await sharp(output, { animated: true }).raw().toBuffer({ resolveWithObject: true });
        assert.equal(decoded.info.height, height * frames);
        console.log('OK: animated GIF compressed; dimensions, all frames, delays and loops preserved.',
            { before: input.length, after: output.length, frames: after.pages, loop: after.loop });
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
