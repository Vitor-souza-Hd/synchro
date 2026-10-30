// Run with Node.js and Playwright in NODE_PATH; uses a simulated Last.fm connection.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

(async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage({ viewport: { width: 1280, height: 850 } });
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        let connected = false;
        let failConnection = false;
        let failSync = false;
        let authCalls = 0;
        let syncCalls = 0;
        let scrobbles = 3210;
        const profile = () => ({ username: 'alice', email: 'alice@example.com', birthDay: '2000-01-01',
            lastFmUsername: connected ? 'lastfm-listener' : 'name-only', lastFmConnected: connected,
            data: { fullName: 'Alice Souza', gender: 'female', scrobbles: connected ? scrobbles : 0,
                artistas: connected ? 87 : 0, lastFmRealName: 'Alice no Last.fm', lastFmCountry: 'Brazil',
                lastFmTracks: 240, lastFmAlbums: 33, lastFmUpdatedAt: '2026-10-29T20:30:00Z',
                lastFmImageUrl: 'https://lastfm.freetls.fastly.net/avatar.jpg' } });
        await page.route('**/*', async route => {
            const url = new URL(route.request().url());
            if (url.hostname === 'www.last.fm') return route.fulfill({ contentType: 'text/html', body: '<h1>Autorizar Synchro</h1>' });
            if (url.hostname !== 'synchro.test') return route.abort();
            if (url.pathname === '/auth/cookie') return route.fulfill({ body: 'ok' });
            if (url.pathname === '/users/me' || url.pathname === '/catalog/users/2') return route.fulfill({ json: profile() });
            if (url.pathname === '/last-fm/auth/login') {
                authCalls++;
                await new Promise(resolve => setTimeout(resolve, 150));
                return failConnection ? route.fulfill({ status: 503, json: { error: 'Não foi possível conectar. Tente novamente.' } })
                    : route.fulfill({ json: { authUrl: 'https://www.last.fm/api/auth/?api_key=test&cb=http%3A%2F%2Fsynchro.test%2Flast-fm%2Fauth%2Fcallback' } });
            }
            if (url.pathname === '/last-fm/sync') {
                assert.equal(route.request().method(), 'POST');
                syncCalls++;
                await new Promise(resolve => setTimeout(resolve, 150));
                if (failSync) return route.fulfill({ status: 502, json: { error: 'Não foi possível atualizar. Seus dados salvos continuam disponíveis.' } });
                scrobbles = 4000;
                return route.fulfill({ json: profile() });
            }
            if (url.pathname.endsWith('.js') && !/\/(auth|user)\.js$/.test(url.pathname)) {
                return route.fulfill({ contentType: 'application/javascript', body: '' });
            }
            const root = path.resolve(__dirname, '../src/main/resources/static');
            const file = path.resolve(root, '.' + url.pathname);
            if (!file.startsWith(root + path.sep) || !fs.existsSync(file)) return route.fulfill({ status: 404, body: '' });
            const contentType = { '.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript', '.jpg': 'image/jpeg' }[path.extname(file)] || 'application/octet-stream';
            return route.fulfill({ contentType, body: fs.readFileSync(file) });
        });
        const url = 'http://synchro.test/frontend/pages/user.html';
        await page.goto(url);
        const connect = page.getByRole('button', { name: 'Conectar ao lastFM', exact: true });
        const sync = page.getByRole('button', { name: 'Atualizar dados do Last.fm', exact: true });
        await connect.waitFor();
        assert.equal(await sync.isVisible(), false, 'A manually entered username must not count as authorized');
        await page.screenshot({ path: 'target/lastfm-disconnected-desktop.png' });
        await page.setViewportSize({ width: 390, height: 844 });
        await connect.scrollIntoViewIfNeeded();
        const bounds = await connect.boundingBox();
        assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= 390);
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
        await page.screenshot({ path: 'target/lastfm-disconnected-mobile.png', fullPage: true });
        await page.setViewportSize({ width: 320, height: 740 });
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
        await page.setViewportSize({ width: 390, height: 844 });

        failConnection = true;
        await connect.click();
        await page.getByRole('button', { name: 'Conectando…' }).waitFor();
        assert.equal(await page.locator('#connectLastFm').isDisabled(), true);
        await page.getByText('Não foi possível conectar. Tente novamente.').waitFor();
        assert.equal(await connect.isEnabled(), true);
        failConnection = false;
        await connect.click();
        await page.waitForURL('https://www.last.fm/**');
        assert.equal(authCalls, 2);
        await page.goBack();
        await connect.waitFor();
        assert.equal(await connect.isEnabled(), true, 'Back from Last.fm must allow another connection attempt');

        connected = true;
        await page.goto(url + '?lastfm=connected');
        await sync.waitFor();
        await page.getByText('Last.fm conectado! Seus dados já estão no perfil.').waitFor();
        assert.equal(new URL(page.url()).search, '');
        assert.equal(await connect.isVisible(), false);
        assert.equal(await page.locator('#profileScrobbles').textContent(), '3.210');
        assert.equal(await page.locator('#profileArtists').textContent(), '87');
        assert.equal(await page.locator('#profileTracks').textContent(), '240');
        assert.equal(await page.locator('#profileAlbums').textContent(), '33');
        assert.equal(await page.locator('#lastFmProfileLink').getAttribute('href'), 'https://www.last.fm/user/lastfm-listener');
        await page.waitForFunction(() => document.getElementById('profileImage').src.endsWith('userphoto.jpg'));
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
        await page.screenshot({ path: 'target/lastfm-connected-mobile.png', fullPage: true });
        await page.setViewportSize({ width: 1280, height: 850 });
        await page.screenshot({ path: 'target/lastfm-connected-desktop.png' });

        failSync = true;
        await sync.click();
        await page.getByRole('button', { name: 'Atualizando…' }).waitFor();
        assert.equal(await page.locator('#syncLastFm').isDisabled(), true);
        await page.getByText('Não foi possível atualizar. Seus dados salvos continuam disponíveis.').waitFor();
        assert.equal(await page.locator('#profileScrobbles').textContent(), '3.210');
        assert.equal(await sync.isEnabled(), true);
        failSync = false;
        await sync.click();
        await page.getByText('Dados do Last.fm atualizados!').waitFor();
        assert.equal(await page.locator('#profileScrobbles').textContent(), '4.000');
        assert.equal(syncCalls, 2);
        await page.reload();
        await sync.waitFor();
        assert.equal(await page.locator('#profileScrobbles').textContent(), '4.000');

        await page.goto(url + '?user=2');
        await page.locator('#profilePage').waitFor();
        assert.equal(await connect.isVisible(), false);
        assert.equal(await sync.isVisible(), false);
        assert.equal(await page.locator('#profileEmail').isVisible(), false);
        assert.equal(await page.locator('#lastFmDetails').isVisible(), true);
        connected = false;
        await page.goto(url + '?lastfm=invalid_state');
        await connect.waitFor();
        assert.match(await page.locator('#lastFmStatus').textContent(), /expirou/);
        for (const [result, message] of [
            ['token_expired', 'A autorização do Last.fm expirou ou já foi utilizada.'],
            ['not_authorized', 'O acesso ainda não foi autorizado no Last.fm.'],
            ['api_configuration', 'O Last.fm recusou as credenciais do Synchro.'],
            ['unavailable', 'O Last.fm está temporariamente indisponível.']
        ]) {
            await page.goto(url + '?lastfm=' + result);
            await connect.waitFor();
            assert.ok((await page.locator('#lastFmStatus').textContent()).startsWith(message));
            assert.equal(await connect.isEnabled(), true);
            assert.equal(new URL(page.url()).search, '');
        }
        assert.deepEqual(errors, []);
        console.log('PASS: connect, redirect, callback, imported data, persisted display, loading/retry, public profile, photo fallback and mobile/desktop layout.');
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
