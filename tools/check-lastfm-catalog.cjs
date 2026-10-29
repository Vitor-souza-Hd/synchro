const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const base = process.env.SYNCHRO_TEST_URL || 'http://localhost:8081';

(async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage({ viewport: { width: 1600, height: 950 }, ignoreHTTPSErrors: true });
        page.setDefaultTimeout(90000);
        const errors = [];
        page.on('pageerror', e => errors.push(e.message));
        let searches = 0;
        page.on('request', req => { if (req.url().includes('/search?q=')) searches++; });
        await page.goto(base, { waitUntil: 'domcontentloaded' });
        await page.locator('input.search').fill('Starboy');
        await page.getByText('Pressione Enter ou clique na lupa para pesquisar no Last.fm.').waitFor();
        assert.equal(searches, 0, 'Typing must not trigger API imports');
        await page.locator('input.search').press('Enter');
        const result = page.locator('.synchro-search-results a[href*="musica.html"]').filter({ hasText: 'Starboy' }).first();
        await result.waitFor();
        const href = await result.getAttribute('href');
        await result.click();
        await page.locator('#catalogContent:not([hidden])').waitFor();
        assert.equal(await page.locator('#catalogTitle').textContent(), 'Starboy');
        assert.equal(await page.locator('#songDuration').textContent(), '3:50');
        assert.ok(/\d/.test(await page.locator('#songPlaycount').textContent()));
        assert.ok((await page.locator('#lastFmLink').getAttribute('href')).startsWith('https://www.last.fm/'));
        assert.equal(await page.locator('#catalogArtists a').count(), 1);
        await page.locator('#catalogImage').evaluate(image => image.decode().catch(() => {}));
        await page.screenshot({ path: 'target/song-lastfm-desktop.png' });
        await page.locator('#favoriteBtn').click();
        assert.equal(await page.locator('#favoriteBtn').getAttribute('aria-pressed'), 'true');
        await page.locator('#catalogMeta a').click();
        await page.locator('#catalogContent:not([hidden])').waitFor();
        assert.ok(await page.locator('#catalogTracks a').count() > 0);
        await page.setViewportSize({ width: 390, height: 844 });
        await page.goto(base + href, { waitUntil: 'domcontentloaded' });
        await page.locator('#catalogContent:not([hidden])').waitFor();
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'No mobile overflow');
        await page.screenshot({ path: 'target/song-lastfm-mobile.png' });
        await page.locator('input.search').fill('Starboy');
        await page.getByRole('button', { name: 'Pesquisar no Last.fm', exact: true }).click();
        await page.locator('.synchro-search-results a').first().waitFor();
        await page.locator('input.search').press('Escape');
        assert.equal(await page.locator('.synchro-search-results').isVisible(), false);
        await page.route('**/catalog/songs/9999/refresh', route => route.fulfill({ status: 200 }));
        await page.route('**/catalog/songs/9999', route => route.fulfill({ json: {
            id: 9999, titulo: 'Sem metadados', descricao: '<script>window.injected=true</script><b>Texto seguro</b>',
            artistas: [], lastFm: { tags: ['<img src=x onerror=alert(1)>'], url: 'javascript:alert(1)' }
        } }));
        await page.goto(base + '/frontend/pages/musica.html?id=9999', { waitUntil: 'domcontentloaded' });
        await page.locator('#catalogContent:not([hidden])').waitFor();
        assert.equal(await page.locator('#songPlaycount').textContent(), 'Não informado');
        assert.equal(await page.locator('#lastFmLink').isVisible(), false);
        assert.equal(await page.locator('#catalogDescription').textContent(), 'Texto seguro');
        assert.equal(await page.locator('#catalogTags img').count(), 0);
        assert.equal(await page.evaluate(() => Boolean(window.injected)), false);
        await page.route('**/search?**', route => route.fulfill({ json: { songs: [], albums: [], artists: [], users: [], source: 'local', warning: 'Last.fm indisponível. Exibindo os dados já salvos no Synchro.' } }));
        await page.locator('input.search').fill('offline');
        await page.locator('input.search').press('Enter');
        await page.getByText('Last.fm indisponível. Exibindo os dados já salvos no Synchro.').waitFor();
        assert.deepEqual(errors, []);
        console.log('PASS: real Last.fm search, persisted details, submit-only search, related album, statistics, artwork, mobile, missing metadata and safe rendering.');
    } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
