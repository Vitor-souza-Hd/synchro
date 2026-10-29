// Run with Node.js and Playwright available in NODE_PATH; Synchro must be running.
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const baseUrl = process.env.SYNCHRO_TEST_URL || 'http://localhost:8080';

(async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage({ viewport: { width: 1280, height: 850 } });
        // External fonts/icons are not part of the search behavior being tested.
        await page.route(/^https:\/\//, route => route.abort());
        const api = await page.request.get(baseUrl + '/search?q=radio');
        assert.equal(api.status(), 200);
        const data = await api.json();
        assert.ok(data.songs.length > 0, 'Expected imported songs matching radio');
        await page.goto(baseUrl + '/', { waitUntil: 'domcontentloaded' });
        const input = page.locator('input.search');
        const panel = page.locator('.synchro-search-results');
        await input.fill('radio');
        await panel.getByRole('heading', { name: /^Músicas/ }).waitFor();
        assert.ok((await panel.textContent()).includes(data.songs[0].titulo));
        await page.screenshot({ path: 'target/search-desktop.png' });
        await input.press('Escape');
        assert.equal(await panel.isVisible(), false);
        await input.fill('r');
        await panel.getByText('Digite pelo menos 2 caracteres.').waitFor();
        await input.fill('zzzz-no-such-song-987654321');
        await input.press('Enter');
        await panel.getByText('Nenhum resultado encontrado.').waitFor();
        await input.fill('');
        assert.equal(await panel.isVisible(), false);

        let requests = 0;
        await page.route('**/search?**', async route => {
            requests++;
            const term = new URL(route.request().url()).searchParams.get('q');
            if (term === 'error') return route.fulfill({ status: 503, body: 'Unavailable' });
            if (term === 'slow') await new Promise(resolve => setTimeout(resolve, 700));
            await route.fulfill({ json: { users: [], songs: [], albums: [], artists: [
                { id: 1, nome: term === 'markup' ? '<img src=x onerror=alert(1)>' : term }
            ] } });
        });
        await input.fill('ra');
        await input.fill('rad');
        await input.fill('radio');
        await panel.getByText('radio', { exact: true }).waitFor();
        assert.equal(requests, 1, 'Typing should debounce requests');
        await input.fill('error');
        await input.press('Enter');
        await panel.getByText('Não foi possível pesquisar agora. Tente novamente.').waitFor();
        await input.fill('slow');
        await input.press('Enter');
        await panel.getByText('Pesquisando…').waitFor();
        await input.fill('new');
        await input.press('Enter');
        await panel.getByText('new', { exact: true }).waitFor();
        await page.waitForTimeout(900);
        assert.equal(await panel.getByText('slow', { exact: true }).count(), 0);
        await input.fill('markup');
        await input.press('Enter');
        await panel.getByText('<img src=x onerror=alert(1)>', { exact: true }).waitFor();
        assert.equal(await panel.locator('img').count(), 0, 'Results must be rendered as text');
        await page.setViewportSize({ width: 390, height: 844 });
        await input.fill('radio');
        await input.press('Enter');
        await panel.getByText('radio', { exact: true }).waitFor();
        const inputBounds = await input.boundingBox();
        assert.ok(inputBounds.width > 100, 'Mobile search field must remain usable');
        const bounds = await panel.boundingBox();
        assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= 390);
        await page.screenshot({ path: 'target/search-mobile.png' });
        await page.mouse.click(380, 820);
        assert.equal(await panel.isVisible(), false);
        await page.unroute('**/search?**');
        await page.setViewportSize({ width: 1280, height: 850 });

        for (const path of ['/frontend/pages/artista.html?artist=Radiohead', '/frontend/pages/user.html']) {
            // Supply a session fixture only for the profile page; search still uses the real API.
            if (path.includes('user.html')) await page.route('**/users/me', route => route.fulfill({
                json: { username: 'search-test', data: {} }
            }));
            await page.goto(baseUrl + path, { waitUntil: 'domcontentloaded' });
            await page.locator('input.search').fill('yu');
            await page.locator('.synchro-search-results').getByRole('heading', { name: /^Artistas/ }).waitFor();
        }
        console.log('PASS: live search on home/artist/profile, empty results, minimum length, Enter/Escape, debounce, errors, stale responses, text escaping and mobile layout.');
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
