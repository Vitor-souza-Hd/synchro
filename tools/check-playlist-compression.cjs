// Run with Playwright available in NODE_PATH. No server is required.
const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require('playwright');

(async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage();
        await page.goto('about:blank');
        await page.addScriptTag({ path: path.resolve(__dirname, '../src/main/resources/static/frontend/assets/js/playlists.js') });
        const result = await page.evaluate(async () => {
            const canvas = document.createElement('canvas');
            canvas.width = 2200;
            canvas.height = 1600;
            const context = canvas.getContext('2d');
            const pixels = context.createImageData(canvas.width, canvas.height);
            for (let i = 0; i < pixels.data.length; i += 4) {
                pixels.data[i] = Math.random() * 256;
                pixels.data[i + 1] = Math.random() * 256;
                pixels.data[i + 2] = Math.random() * 256;
                pixels.data[i + 3] = 255;
            }
            context.putImageData(pixels, 0, 0);
            const source = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'));
            let compressed = false;
            const data = await readPlaylistImage(source, () => compressed = true);
            const output = await (await fetch(data)).blob();
            const decoded = await createImageBitmap(output);
            const width = decoded.width, height = decoded.height;
            decoded.close();

            const smallCanvas = document.createElement('canvas');
            smallCanvas.width = smallCanvas.height = 16;
            const small = await new Promise(resolve => smallCanvas.toBlob(resolve, 'image/png'));
            let smallCompressed = false;
            const smallData = await readPlaylistImage(small, () => smallCompressed = true);
            const smallOutput = await (await fetch(smallData)).blob();
            const unchanged = (await small.arrayBuffer()).byteLength === (await smallOutput.arrayBuffer()).byteLength
                && smallData.startsWith('data:image/png;');
            let corruptRejected = false;
            try {
                await readPlaylistImage(new Blob([new Uint8Array(3 * 1024 * 1024)], { type: 'image/png' }));
            } catch { corruptRejected = true; }
            let formatRejected = false;
            try { await readPlaylistImage(new Blob(['svg'], { type: 'image/svg+xml' })); }
            catch { formatRejected = true; }
            return { before: source.size, after: output.size, compressed, width, height, smallCompressed,
                unchanged, corruptRejected, formatRejected, absent: await readPlaylistImage(null) };
        });
        assert.ok(result.before > 2 * 1024 * 1024);
        assert.ok(result.after <= 2 * 1024 * 1024);
        assert.equal(result.compressed, true);
        assert.equal(result.width / result.height, 2200 / 1600);
        assert.equal(result.smallCompressed, false);
        assert.equal(result.unchanged, true);
        assert.equal(result.corruptRejected, true);
        assert.equal(result.formatRejected, true);
        assert.equal(result.absent, null);
        console.log('OK: real browser compression, valid output under 2 MB, aspect ratio, small images, invalid files.', result);
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exit(1); });
