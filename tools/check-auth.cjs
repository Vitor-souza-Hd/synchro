// Run with Node.js and Playwright available in NODE_PATH. No running server is needed.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

(async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage({ viewport: { width: 1280, height: 850 } });
        let loggedIn = false;
        let failLogout = false;
        let logoutCalls = 0;
        await page.route('**/*', async route => {
            const url = new URL(route.request().url());
            if (url.hostname !== 'synchro.test') return route.abort();
            if (url.pathname === '/auth/cookie') return route.fulfill({ body: loggedIn ? 'ok' : 'unauthenticated' });
            if (url.pathname === '/auth/logout') {
                assert.equal(route.request().method(), 'POST');
                logoutCalls++;
                await new Promise(resolve => setTimeout(resolve, 150));
                if (failLogout) return route.fulfill({ status: 503, body: '' });
                loggedIn = false;
                return route.fulfill({ status: 204, body: '' });
            }
            if (url.pathname === '/users/me') return route.fulfill({ json: { username: 'teste', data: {} } });
            // Only session and profile scripts are needed to verify these controls.
            if (url.pathname.endsWith('.js') && !/\/(auth|user)\.js$/.test(url.pathname)) {
                return route.fulfill({ contentType: 'application/javascript', body: '' });
            }
            const root = path.resolve(__dirname, '../src/main/resources/static');
            const file = path.resolve(root, '.' + url.pathname);
            if (!file.startsWith(root + path.sep) || !fs.existsSync(file)) {
                return route.fulfill({ status: 404, body: '' });
            }
            const contentType = { '.html': 'text/html', '.css': 'text/css', '.js': 'application/javascript', '.mp4': 'video/mp4' }[path.extname(file)] || 'application/octet-stream';
            return route.fulfill({ contentType, body: fs.readFileSync(file) });
        });

        const pages = ['/index.html', ...['artista', 'user', 'album', 'musica'].map(name => `/frontend/pages/${name}.html`)];
        for (const pathname of pages) {
            loggedIn = false;
            await page.goto('http://synchro.test' + pathname);
            assert.equal(await page.getByRole('button', { name: 'Sair', exact: true }).isVisible(), false);
            loggedIn = true;
            await page.reload();
            await page.getByRole('button', { name: 'Sair', exact: true }).waitFor();
        }

        await page.goto('http://synchro.test/index.html');
        const logout = page.getByRole('button', { name: 'Sair', exact: true });
        await logout.waitFor();
        assert.equal(await page.locator('#authActions').isVisible(), false);
        await page.screenshot({ path: 'target/logout-desktop.png' });
        await page.setViewportSize({ width: 390, height: 844 });
        await logout.scrollIntoViewIfNeeded();
        const bounds = await logout.boundingBox();
        assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= 390);
        await page.screenshot({ path: 'target/logout-mobile.png' });

        failLogout = true;
        await logout.click();
        await page.getByRole('button', { name: 'Saindo…' }).waitFor();
        assert.equal(await page.locator('.logout-button').isDisabled(), true);
        await page.getByText('Não foi possível sair. Tente novamente.').waitFor();
        assert.equal(await logout.isEnabled(), true);
        assert.equal(logoutCalls, 1);

        failLogout = false;
        await logout.click();
        await page.locator('#authActions').waitFor();
        assert.equal(await logout.isVisible(), false);
        assert.equal(logoutCalls, 2);
        await page.reload();
        await page.locator('#authActions').waitFor();
        assert.equal(await logout.isVisible(), false);
        console.log('PASS: authenticated-only logout across five pages, desktop/mobile layout, loading, failure/retry, redirect and anonymous state after reload.');
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
