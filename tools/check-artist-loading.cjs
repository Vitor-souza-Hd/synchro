const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const base = process.env.SYNCHRO_TEST_URL || 'http://localhost:8080';
(async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage();
        await page.route(/^https:\/\//, route => route.abort());
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        const result = await (await page.request.get(base + '/search?q=Larping')).json();
        const artist = result.artists.find(item => item.nome === 'The Larping Tombstone');
        assert.ok(artist, 'The reported artist must exist in the fixture database');
        const details = await (await page.request.get(base + '/catalog/artists/' + artist.id)).json();
        await page.goto(base, { waitUntil: 'domcontentloaded' });
        await page.locator('input.search').fill('Larping');
        await page.locator(`.synchro-search-results a[href$="artista.html?id=${artist.id}"]`).click();
        await page.waitForFunction(name => document.querySelector('#artistName')?.textContent === name, artist.nome);
        assert.equal(await page.locator('#tracks a').count(), details.musicas.length);
        assert.equal(await page.locator('#albums a').count(), details.albums.length);
        assert.equal(await page.locator('#tracks a').first().evaluate(element => getComputedStyle(element).color), 'rgb(245, 243, 255)');
        await page.screenshot({ path: 'target/artist-larping.png' });
        for (const name of ['The Larping Tombstone', '  the larping tombstone  ']) {
            await page.goto(base + '/frontend/pages/artista.html?artist=' + encodeURIComponent(name), { waitUntil: 'domcontentloaded' });
            await page.waitForFunction(name => document.querySelector('#artistName')?.textContent === name, artist.nome);
            assert.equal(await page.locator('#tracks a').count(), details.musicas.length);
        }
        let release;
        await page.route('**/catalog/artists/' + artist.id, async route => {
            await new Promise(resolve => { release = resolve; });
            await route.fulfill({ json: details });
        });
        await page.goto(base + '/frontend/pages/artista.html?id=' + artist.id, { waitUntil: 'domcontentloaded' });
        await page.waitForFunction(() => document.querySelector('.artist-header').getAttribute('aria-busy') === 'true');
        assert.equal(await page.locator('#artistName').textContent(), 'Carregando...');
        assert.equal(await page.getByText('Artista não encontrado', { exact: true }).count(), 0);
        assert.equal(await page.locator('#favoriteBtn').isEnabled(), false);
        while (!release) await new Promise(resolve => setTimeout(resolve, 20));
        release();
        await page.waitForFunction(name => document.querySelector('#artistName')?.textContent === name, artist.nome);
        assert.equal(await page.locator('#favoriteBtn').isEnabled(), true);
        await page.goto(base + '/frontend/pages/artista.html?id=999999999', { waitUntil: 'domcontentloaded' });
        await page.getByText('Artista não encontrado', { exact: true }).waitFor();
        assert.equal(await page.locator('.artist-section:visible').count(), 0);
        assert.deepEqual(errors, []);
        console.log(`PASS: ${artist.nome}, ${details.musicas.length} songs, ${details.albums.length} albums; search click, normalized name, delayed response, and genuine missing record.`);
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
