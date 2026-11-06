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
            if (url.pathname.endsWith('.js') && !/\/(auth|user|playlists)\.js$/.test(url.pathname)) {
                return route.fulfill({ contentType: 'application/javascript', body: '' });
            }
            const root = path.resolve(__dirname, '../src/main/resources/static');
            const file = path.resolve(root, '.' + url.pathname);
            if (!file.startsWith(root + path.sep) || !fs.existsSync(file)) return route.fulfill({ status: 404, body: '' });
            const contentType = { '.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript', '.jpg': 'image/jpeg' }[path.extname(file)] || 'application/octet-stream';
            return route.fulfill({ contentType, body: fs.readFileSync(file) });
        });
        const url = 'http://synchro.test/frontend/pages/perfil.html';
        async function openSettings() {
            await page.locator('#profilePage').waitFor();
            const settings = page.locator('#profileSettings');
            if (await settings.getAttribute('open') === null) await settings.locator('summary').click();
        }
        async function checkBannerHeight() {
            const header = await page.locator('.profile-header').boundingBox();
            const background = await page.locator('.banner-background').boundingBox();
            const content = await page.locator('#profilePage > main').evaluate(main => {
                const rect = main.getBoundingClientRect();
                const style = getComputedStyle(main);
                const left = parseFloat(style.paddingLeft), right = parseFloat(style.paddingRight);
                return {x: rect.x + left, width: rect.width - left - right};
            });
            assert.ok(Math.abs(header.x - content.x) < 1 && Math.abs(header.width - content.width) < 1,
                'Profile banner must follow the shared site content width and margins');
            assert.ok(header.height >= 375, 'Banner must retain the requested height at every screen width');
            assert.ok(background.y <= header.y && background.y + background.height >= header.y + header.height,
                'Background image must cover the entire banner height');
        }
        async function waitForMenuLayout() {
            await page.waitForFunction(() => {
                const root = document.getElementById('profilePage');
                const main = root.querySelector('main').getBoundingClientRect();
                const sidebar = document.getElementById('profileSidebar').getBoundingClientRect();
                const collapsed = root.classList.contains('sidebar-collapsed');
                const gap = parseFloat(getComputedStyle(root).columnGap);
                const expectedLeft = root.getBoundingClientRect().x + (collapsed ? 0 : sidebar.width + gap);
                const sidebarIsAligned = collapsed || Math.abs(sidebar.x - root.getBoundingClientRect().x) < 1;
                return sidebarIsAligned && Math.abs(main.x - expectedLeft) < 1;
            });
        }
        await page.goto(url);
        const connect = page.getByRole('button', { name: 'Conectar ao lastFM', exact: true });
        const sync = page.getByRole('button', { name: 'Atualizar dados do Last.fm', exact: true });
        await page.locator('#profilePage').waitFor();
        assert.equal(await page.locator('#profileSettings').getAttribute('open'), null, 'Profile settings start closed');
        assert.equal(await connect.isVisible(), false, 'Connection controls stay inside closed settings');
        const desktopToggle = page.locator('.menu-toggle');
        assert.equal(await desktopToggle.getAttribute('aria-expanded'), 'true', 'Desktop menu starts expanded');
        const expandedWidth = (await page.locator('#profilePage > main').boundingBox()).width;
        await desktopToggle.click();
        await waitForMenuLayout();
        assert.equal(await page.locator('#profileSidebar').evaluate(sidebar => sidebar.inert), true);
        assert.ok((await page.locator('#profilePage > main').boundingBox()).width > expandedWidth, 'Retracting the menu expands the content');
        await page.screenshot({path: 'target/profile-sidebar-collapsed.png'});
        await page.reload();
        await page.locator('#profilePage').waitFor();
        assert.equal(await desktopToggle.getAttribute('aria-expanded'), 'false', 'Desktop menu preference survives reload');
        await desktopToggle.click();
        await waitForMenuLayout();
        const playlistMenu = page.getByRole('navigation', {name: 'Playlists', exact: true});
        assert.equal(await playlistMenu.getByRole('link', {name: 'Suas playlists'}).getAttribute('href'), '/frontend/pages/playlists.html');
        await playlistMenu.getByRole('button', {name: 'Criar uma nova'}).click();
        const playlistDialog = page.getByRole('dialog', {name: 'Criar playlist', exact: true});
        await playlistDialog.waitFor();
        await page.keyboard.press('Escape');
        await playlistDialog.waitFor({state: 'hidden'});
        assert.equal(await desktopToggle.getAttribute('aria-expanded'), 'true', 'Dismissing the playlist dialog does not collapse the menu');
        assert.ok((await page.locator('.profile-header').boundingBox()).height <= 400, 'Desktop banner fits the profile header');
        await checkBannerHeight();
        for (const width of [320, 390, 768, 769, 1280, 1348, 1600, 2560]) {
            await page.setViewportSize({width, height: 850});
            if (width > 768) await waitForMenuLayout();
            await checkBannerHeight();
            assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Site layout fits the screen');
            if (width === 390) await page.screenshot({path: 'target/profile-banner-responsive-mobile.png'});
            if (width === 1348) {
                await page.screenshot({path: 'target/profile-site-layout.png'});
            }
        }
        await page.setViewportSize({width: 1280, height: 850});
        await page.screenshot({ path: 'target/profile-banner-compact.png' });
        await openSettings();
        await connect.waitFor();
        assert.equal(await sync.isVisible(), false, 'A manually entered username must not count as authorized');
        await page.screenshot({ path: 'target/lastfm-disconnected-desktop.png' });
        await page.setViewportSize({ width: 390, height: 844 });
        await checkBannerHeight();
        const menuToggle = page.getByRole('button', {name: 'Abrir menu', exact: true});
        await menuToggle.click();
        assert.equal(await page.locator('.menu-toggle').getAttribute('aria-expanded'), 'true');
        assert.equal(await page.locator('#profileSidebar').evaluate(sidebar => sidebar.inert), false);
        await page.keyboard.press('Escape');
        assert.equal(await page.locator('#profileSidebar').evaluate(sidebar => sidebar.inert), true);
        await menuToggle.click();
        await page.getByRole('button', {name: 'Fechar menu lateral', exact: true}).click();
        assert.equal(await page.locator('.menu-toggle').getAttribute('aria-expanded'), 'false');
        await menuToggle.click();
        await page.screenshot({path: 'target/profile-sidebar-mobile-open.png'});
        await playlistMenu.getByRole('button', {name: 'Criar uma nova'}).click();
        await playlistDialog.waitFor();
        assert.equal(await page.locator('.menu-toggle').getAttribute('aria-expanded'), 'false', 'Opening the playlist form closes the mobile drawer');
        await playlistDialog.getByRole('button', {name: 'Cancelar', exact: true}).click();
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
        await openSettings();
        await connect.waitFor();
        assert.equal(await connect.isEnabled(), true, 'Back from Last.fm must allow another connection attempt');

        connected = true;
        await page.goto(url + '?lastfm=connected');
        await openSettings();
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
        await page.locator('#profileSettings > summary').click();
        assert.equal(await sync.isVisible(), false, 'Settings can be hidden again');
        await page.screenshot({ path: 'target/profile-banner-compact-connected.png' });
        await page.evaluate(() => document.documentElement.classList.add('synchro-light'));
        await page.screenshot({ path: 'target/profile-banner-compact-light.png' });
        await page.evaluate(() => document.documentElement.classList.remove('synchro-light'));
        await openSettings();

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
        await openSettings();
        await sync.waitFor();
        assert.equal(await page.locator('#profileScrobbles').textContent(), '4.000');

        await page.goto(url + '?user=2');
        await page.locator('#profilePage').waitFor();
        assert.equal(await connect.isVisible(), false);
        assert.equal(await sync.isVisible(), false);
        assert.equal(await page.locator('#profileEmail').isVisible(), false);
        assert.equal(await page.locator('#lastFmDetails').isVisible(), true);
        assert.equal(await page.locator('#profileSettings').isVisible(), false, 'Public profiles hide owner settings');
        connected = false;
        await page.goto(url + '?lastfm=invalid_state');
        await openSettings();
        await connect.waitFor();
        assert.match(await page.locator('#lastFmStatus').textContent(), /expirou/);
        for (const [result, message] of [
            ['token_expired', 'A autorização do Last.fm expirou ou já foi utilizada.'],
            ['not_authorized', 'O acesso ainda não foi autorizado no Last.fm.'],
            ['api_configuration', 'O Last.fm recusou as credenciais do Synchro.'],
            ['unavailable', 'O Last.fm está temporariamente indisponível.']
        ]) {
            await page.goto(url + '?lastfm=' + result);
            await openSettings();
            await connect.waitFor();
            assert.ok((await page.locator('#lastFmStatus').textContent()).startsWith(message));
            assert.equal(await connect.isEnabled(), true);
            assert.equal(new URL(page.url()).search, '');
        }
        assert.deepEqual(errors, []);
        console.log('PASS: profile data, search layout, desktop menu collapse and saved preference, mobile drawer, playlist dialog, Last.fm connection/sync, public profile and photo fallback.');
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
