const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const { chromium } = require('playwright');

(async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
        const root = path.resolve(__dirname, '../src/main/resources/static');
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        let listStatus = 200;
        let playlists = [{
            id: 7, titulo: 'Para ouvir de novo', descricao: 'Músicas que acompanham o dia.', colaborador: true,
            capa: '/frontend/assets/images/catalog-placeholder.svg',
            banner: '/frontend/assets/images/catalog-placeholder.svg',
            criador: { id: 1, username: 'ana' }, usuarios: [{ id: 1, username: 'ana' }, { id: 2, username: 'bia' }],
            musicas: [{ id: 42, titulo: 'Frescor de Outono', duracao: 'PT3M20S' },
                { id: 42, titulo: 'Frescor de Outono', duracao: 'PT3M20S' },
                { id: 43, titulo: 'Caminhos', duracao: null }]
        }, { id: 8, titulo: 'Playlist compartilhada', musicas: [], criador: { id: 2, username: 'bia' }, usuarios: [] }];
        await page.route('**/*', async route => {
            const url = new URL(route.request().url());
            if (url.origin !== 'http://synchro.test') return route.abort();
            if (url.pathname === '/auth/cookie') return route.fulfill({ body: 'ok' });
            if (url.pathname === '/playlists/me') return route.fulfill({ status: listStatus, json: playlists });
            if (url.pathname === '/playlists/update-playlist/7') {
                const request = route.request().postDataJSON();
                playlists[0] = { ...playlists[0], ...request, capa: playlists[0].capa, banner: playlists[0].banner };
                return route.fulfill({ json: playlists[0] });
            }
            const file = path.resolve(root, '.' + url.pathname);
            if (!file.startsWith(root + path.sep)) return route.abort();
            try {
                const contentType = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' }[path.extname(file)];
                return route.fulfill({ body: await fs.readFile(file), contentType });
            } catch { return route.fulfill({ status: 404 }); }
        });
        const base = 'http://synchro.test/frontend/pages/playlists.html';
        await page.goto(base);
        await page.locator('.playlist-library-card').first().waitFor();
        assert.equal(await page.locator('.playlist-library-card').count(), 2);
        await fs.mkdir('target', { recursive: true });
        await page.screenshot({ path: 'target/playlist-library-desktop.png' });
        await page.locator('.playlist-library-card').first().click();
        await page.locator('#playlistDetail:not([hidden])').waitFor();
        assert.equal(await page.locator('#detailPlaylistTitle').textContent(), 'Para ouvir de novo');
        assert.equal(await page.locator('#playlistEdit').isVisible(), true);
        assert.equal(await page.locator('#playlistParticipation').textContent(), 'Você participa desta playlist e pode editá-la.');
        assert.equal(await page.locator('.playlist-track').count(), 3);
        assert.equal(await page.locator('#playlistCollaborators a').count(), 2);
        assert.equal(await page.locator('.playlist-track').first().getAttribute('href'), 'detalhes-musica.html?id=42');
        assert.equal(await page.locator('.playlist-track').last().locator('span').last().textContent(), '—');
        assert.ok(await page.locator('#detailPlaylistBanner').isVisible());
        assert.ok(await page.locator('#detailPlaylistCover').evaluate(image => image.complete && image.naturalWidth > 0));
        await page.screenshot({ path: 'target/playlist-detail-desktop.png' });
        await page.locator('#playlistEdit').click();
        await page.locator('.playlist-form input[name="titulo"]').fill('Novo título');
        await page.locator('.playlist-form [type="submit"]').click();
        await page.locator('#playlistDetail #detailPlaylistTitle').filter({ hasText: 'Novo título' }).waitFor();
        await page.locator('.playlist-close').click();
        await page.setViewportSize({ width: 390, height: 844 });
        await page.screenshot({ path: 'target/playlist-detail-mobile.png', fullPage: true });
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
        await page.locator('.playlist-back').click();
        await page.locator('.playlist-library-card').last().click();
        await page.locator('#playlistTracksEmpty:not([hidden])').waitFor();
        assert.equal(await page.locator('#playlistEdit').isVisible(), false);
        assert.equal(await page.locator('#playlistParticipation').textContent(), 'Você não é colaborador desta playlist.');
        await page.locator('#playlistEdit').evaluate(button => button.click());
        assert.equal(await page.locator('.playlist-dialog[open]').count(), 0);
        playlists[1].colaborador = true;
        await page.reload();
        await page.locator('#playlistEdit:not([hidden])').waitFor();
        assert.equal(await page.locator('#playlistParticipation').textContent(), 'Você participa desta playlist e pode editá-la.');
        assert.equal(await page.locator('#detailPlaylistBanner').isVisible(), false);
        assert.ok(await page.locator('#detailPlaylistCover').evaluate(image => image.complete && image.naturalWidth > 0));
        await page.goto(base + '?id=999');
        await page.getByText('Playlist não encontrada ou você não participa dela.').waitFor();
        await page.goto(base + '?id=abc');
        await page.getByText('Playlist não encontrada. Abra uma playlist pela sua biblioteca.').waitFor();
        listStatus = 401;
        await page.goto(base);
        await page.locator('#playlistPageLogin:not([hidden])').waitFor();
        listStatus = 500;
        await page.goto(base);
        await page.locator('#playlistPageRetry:not([hidden])').waitFor();
        listStatus = 200;
        playlists = [];
        await page.locator('#playlistPageRetry').click();
        await page.getByText('Você ainda não participa de nenhuma playlist. Use “Criar playlist” para começar.').waitFor();
        assert.deepEqual(errors, []);
        console.log('PASS: biblioteca, navegação, detalhes, músicas repetidas, imagens, participantes, edição, celular, sessão, erros e lista vazia.');
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
