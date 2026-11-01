const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const { chromium } = require('playwright');

(async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        const root = path.resolve(__dirname, '../src/main/resources/static');
        let listStatus = 200;
        let playlists = [{ id: 7, titulo: 'Minhas favoritas' }, { id: 8, titulo: 'Playlist compartilhada', capa: '/frontend/assets/images/catalog-placeholder.svg', banner: '/frontend/assets/images/catalog-placeholder.svg' }];
        let saveStatus = 204;
        const saves = [];
        await page.route('**/*', async route => {
            const url = new URL(route.request().url());
            if (url.origin !== 'http://synchro.test') return route.abort();
            // Simulate the obsolete script kept under the old asset URL.
            if (url.pathname.endsWith('/song-playlists.js') && !url.searchParams.has('v')) {
                return route.fulfill({ contentType: 'text/javascript', body: 'throw new Error("Obsolete cached playlist script");' });
            }
            if (url.pathname === '/auth/cookie') return route.fulfill({ body: 'ok' });
            if (url.pathname === '/catalog/songs/42/refresh') return route.fulfill({ status: 204 });
            if (url.pathname === '/catalog/songs/42') return route.fulfill({ json: {
                id: 42, titulo: 'Música de teste', artistas: [], lastFm: {}, genero: '', duracao: 'PT3M'
            } });
            if (url.pathname === '/playlists/me') return route.fulfill({ status: listStatus, json: playlists });
            if (/^\/playlists\/\d+\/midias\/42$/.test(url.pathname)) {
                saves.push({ method: route.request().method(), path: url.pathname });
                await new Promise(resolve => setTimeout(resolve, 150));
                return route.fulfill(saveStatus === 204 ? { status: 204 } : {
                    status: saveStatus, json: { message: 'Não foi possível adicionar esta música.' }
                });
            }
            const file = path.resolve(root, '.' + url.pathname);
            if (!file.startsWith(root + path.sep)) return route.abort();
            try {
                const contentType = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' }[path.extname(file)];
                return route.fulfill({ body: await fs.readFile(file), contentType });
            } catch { return route.fulfill({ status: 404 }); }
        });
        await page.goto('http://synchro.test/frontend/pages/musica.html?id=42');
        const open = page.locator('#addToPlaylistBtn');
        const choices = page.locator('#songPlaylistChoices');
        const selectPlaylist = value => page.locator(`#songPlaylistCards input[value="${value}"]`).check();
        const submit = page.locator('#songPlaylistSubmit');
        const feedback = page.locator('#songPlaylistFeedback');
        const close = page.locator('#songPlaylistClose');
        await open.click();
        await selectPlaylist('8');
        await page.locator('.song-playlist-card').nth(1).locator('.song-playlist-banner').waitFor();
        assert.ok(await page.locator('.song-playlist-card').nth(1).locator('.song-playlist-cover').evaluate(image => image.complete && image.naturalWidth > 0));
        await submit.click();
        assert.equal(await submit.isDisabled(), true);
        await feedback.filter({ hasText: 'Música adicionada à playlist “Playlist compartilhada”!' }).waitFor();
        assert.deepEqual(saves, [{ method: 'POST', path: '/playlists/8/midias/42' }]);
        assert.equal(await submit.isDisabled(), true);
        await selectPlaylist('7');
        assert.equal(await submit.isEnabled(), true);
        await fs.mkdir('target', { recursive: true });
        await page.screenshot({ path: 'target/song-playlists-mobile.png' });
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
        await close.click();
        await page.setViewportSize({ width: 1280, height: 1000 });
        const home = await fs.readFile(path.join(root, 'index.html'), 'utf8');
        const sameMenu = await page.evaluate(html => {
            const homeDoc = new DOMParser().parseFromString(html, 'text/html');
            const labels = doc => [...doc.querySelectorAll('.sidebar .menu li')].map(item => item.textContent.trim());
            return JSON.stringify(labels(document)) === JSON.stringify(labels(homeDoc));
        }, home);
        assert.ok(sameMenu, 'Song sidebar must match home menu items');
        await page.locator('[data-create-playlist]').click();
        await page.locator('.playlist-dialog[open]').waitFor();
        await page.keyboard.press('Escape');
        assert.equal(await page.locator('[data-my-playlists]').getAttribute('href'), '/frontend/pages/playlists.html');
        await open.click();
        await selectPlaylist('8');
        await page.screenshot({ path: 'target/song-playlists-desktop.png' });
        await close.click();

        listStatus = 401;
        await open.click();
        await page.locator('#songPlaylistLogin:not([hidden])').waitFor();
        assert.equal(await choices.getAttribute('disabled'), '');
        await close.click();

        listStatus = 200;
        playlists = [];
        await open.click();
        await page.locator('#songPlaylistLibrary:not([hidden])').waitFor();
        assert.equal(await submit.isDisabled(), true);
        await close.click();

        listStatus = 500;
        await open.click();
        await page.locator('#songPlaylistRetry:not([hidden])').waitFor();
        listStatus = 200;
        playlists = [{ id: 7, titulo: '<b>Nome literal</b>' }];
        await page.locator('#songPlaylistRetry').click();
        await selectPlaylist('7');
        assert.equal(await page.locator('.song-playlist-card strong').last().textContent(), '<b>Nome literal</b>');
        saveStatus = 409;
        await submit.click();
        await feedback.filter({ hasText: 'Não foi possível adicionar esta música.' }).waitFor();
        assert.equal(await submit.isEnabled(), true);
        await page.keyboard.press('Escape');
        assert.equal(await page.locator('#songPlaylistDialog').isVisible(), false);
        assert.deepEqual(errors, []);
        console.log('PASS: seleção, adição, prevenção de envio duplicado, sessão expirada, lista vazia, falhas, nova tentativa e layout móvel.');
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
