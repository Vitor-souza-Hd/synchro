const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const base = process.env.SYNCHRO_TEST_URL || 'http://localhost:8080';

(async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.route(/^https:\/\//, route => route.abort());
        const response = await page.request.get(base + '/search?q=radio');
        assert.equal(response.status(), 200);
        const data = await response.json();
        assert.ok(data.songs.length && data.albums.length, 'Need imported music and album fixtures');
        await page.goto(base, { waitUntil: 'domcontentloaded' });
        await page.locator('input.search').fill('radio');
        const songResult = page.locator(`.synchro-search-results a[href$="musica.html?id=${data.songs[0].id}"]`);
        await songResult.waitFor();
        await songResult.focus();
        await page.keyboard.press('Enter');
        await page.locator('#catalogContent:not([hidden])').waitFor();
        assert.equal(await page.locator('#catalogTitle').textContent(), data.songs[0].titulo);
        await page.screenshot({ path: 'target/catalog-song.png' });
        await page.locator('#favoriteBtn').click();
        assert.equal(await page.locator('#favoriteBtn').getAttribute('aria-pressed'), 'true');
        await page.reload({ waitUntil: 'domcontentloaded' });
        await page.locator('#catalogContent:not([hidden])').waitFor();
        assert.equal(await page.locator('#favoriteBtn').getAttribute('aria-pressed'), 'true');

        await page.goto(base, { waitUntil: 'domcontentloaded' });
        await page.locator('input.search').fill('radio');
        await page.locator(`.synchro-search-results a[href$="album.html?id=${data.albums[0].id}"]`).click();
        await page.locator('#catalogContent:not([hidden])').waitFor();
        assert.equal(await page.locator('#catalogTitle').textContent(), data.albums[0].titulo);
        await page.screenshot({ path: 'target/catalog-album.png' });
        const albumData = await (await page.request.get(base + '/catalog/albums/' + data.albums[0].id)).json();
        assert.equal(await page.locator('#catalogTracks a').count(), albumData.musicas.length);
        await page.locator('#catalogArtists a').first().click();
        const artist = data.albums[0].artistas[0];
        await page.waitForFunction(name => document.querySelector('#artistName')?.textContent === name, artist.nome);
        assert.equal(await page.locator('#artistName').textContent(), artist.nome);
        assert.ok(await page.locator('#tracks a').count() > 0);
        await page.locator('#tracks a').first().click();
        await page.locator('#catalogContent:not([hidden])').waitFor();
        await page.setViewportSize({ width: 390, height: 844 });
        await page.screenshot({ path: 'target/catalog-mobile.png' });
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
        await page.goto(base + '/frontend/pages/musica.html?id=999999999', { waitUntil: 'domcontentloaded' });
        await page.getByText('Registro não encontrado no catálogo.').waitFor();
        await page.goto(base + '/frontend/pages/album.html', { waitUntil: 'domcontentloaded' });
        await page.getByText('Selecione um resultado na pesquisa para abrir esta página.').waitFor();

        // Public-profile navigation is exercised with a fixture because the local catalog may have no accounts.
        await page.setViewportSize({ width: 1280, height: 900 });
        await page.route('**/search?**', route => route.fulfill({ json: {
            songs: [], artists: [], albums: [], users: [{ id: 123, username: 'public-profile-test' }]
        } }));
        await page.route('**/catalog/users/123', route => route.fulfill({ json: {
            username: 'public-profile-test', data: { fullName: 'Perfil público', scrobbles: 15, artistas: 3 }
        } }));
        await page.goto(base, { waitUntil: 'domcontentloaded' });
        await page.locator('input.search').fill('public');
        await page.locator('.synchro-search-results a[href$="user.html?user=123"]').click();
        await page.locator('#profilePage:not([hidden])').waitFor();
        assert.equal(await page.locator('#profileUsername').textContent(), '@public-profile-test');
        assert.equal(await page.locator('#profileEmail').isVisible(), false);
        assert.deepEqual(errors, []);
        console.log('PASS: clickable song/album/artist/user results, keyboard navigation, related records, favorites, mobile layout and missing records.');
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
