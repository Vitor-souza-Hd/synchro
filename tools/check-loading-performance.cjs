const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../src/main/resources/static/frontend/assets/js');
const tick = () => new Promise(resolve => setImmediate(resolve));
function deferred() { let resolve; const promise = new Promise(done => { resolve = done; }); return {promise, resolve}; }
class Element {
    constructor() {
        this.children = []; this.listeners = {}; this.attrs = {}; this.dataset = {}; this.style = {}; this.hidden = true;
        this.classList = {add() {}, toggle() {}, remove() {}, contains() { return false; }};
    }
    set innerHTML(value) { this.html = value; this.children = []; }
    get innerHTML() { return this.html || ''; }
    get childElementCount() { return this.children.length; }
    append(...nodes) { this.children.push(...nodes); }
    appendChild(node) { this.append(node); }
    replaceChildren(...nodes) { this.children = nodes; }
    before() {}
    setAttribute(key, value) { this.attrs[key] = value; }
    removeAttribute(key) { delete this.attrs[key]; }
    addEventListener(type, fn) { this.listeners[type] = fn; }
}
function environment(search) {
    const nodes = new Map(), selectors = new Map(), events = {};
    const node = id => { if (!nodes.has(id)) nodes.set(id, new Element()); return nodes.get(id); };
    const document = {readyState: 'loading', body: new Element(),
        getElementById: node, createElement: () => new Element(),
        querySelector: selector => selectors.get(selector) || null,
        querySelectorAll: selector => selectors.has(selector) ? [selectors.get(selector)] : [],
        addEventListener: (name, fn) => { events[name] = fn; },
        dispatchEvent: event => events[event.type]?.(event)};
    const context = {document, URL, URLSearchParams, console, CustomEvent, AbortSignal,
        window: {location: {search, origin: 'http://synchro.test'}, SynchroFeatures: {bindFavorite() {}, escapeHtml: value => value}},
        DOMParser: class { parseFromString(value) { return {body: {textContent: value}, querySelectorAll: () => []}; } }};
    return {node, selectors, events, context};
}
const json = data => ({ok: true, json: async () => data});

async function checkAlbum(success) {
    const env = environment('?id=1'), pending = deferred(), calls = [];
    const data = count => ({album: {id: 1, titulo: 'Saved album', artistas: [{id: 2, nome: 'Artist'}]},
        musicas: Array.from({length: count}, (_, i) => ({id: i + 1, titulo: 'Track ' + i}))});
    let current = data(1);
    env.context.fetch = async url => {
        calls.push(url); return url.endsWith('/refresh') ? pending.promise : json(current);
    };
    vm.runInNewContext(fs.readFileSync(path.join(root, 'media.js'), 'utf8'), env.context);
    await tick();
    assert.ok(!calls[0].endsWith('/refresh'), 'Album must load saved data first');
    assert.equal(env.node('catalogContent').hidden, false, 'Album is visible while refresh is pending');
    assert.equal(env.node('catalogTracks').children.length, 1);
    current = data(2); pending.resolve({ok: success}); await tick();
    assert.equal(env.node('catalogContent').hidden, false);
    assert.equal(env.node('catalogArtists').children.length, 1, 'Refreshing must not duplicate artists');
    assert.equal(env.node('catalogTracks').children.length, success ? 2 : 1);
}

async function checkArtist(success) {
    const env = environment('?id=2'), pending = deferred(), calls = [];
    const header = new Element(); env.selectors.set('.artist-header', header);
    env.selectors.set('.artist-header .feature-detail-link', new Element());
    env.selectors.set('.artist-section', new Element());
    const data = title => ({artist: {id: 2, nome: title}, musicas: [{id: 3, titulo: 'Track'}], albums: []});
    let current = data('Saved artist');
    env.context.fetch = async url => {
        calls.push(url); return url.endsWith('/refresh') ? pending.promise : json(current);
    };
    vm.runInNewContext(fs.readFileSync(path.join(root, 'artista.js'), 'utf8'), env.context);
    await env.events.DOMContentLoaded(); await tick();
    assert.equal(calls[0], '/catalog/artists/2');
    assert.equal(env.node('artistName').textContent, 'Saved artist');
    assert.equal(header.attrs['aria-busy'], 'false', 'Artist is usable while refresh is pending');
    assert.equal(env.node('favoriteBtn').disabled, false);
    assert.equal(env.node('artistLastFmLink').href, 'https://www.last.fm/music/Saved%20artist');
    assert.equal(env.node('artistLastFmLink').hidden, false);
    current = data('Updated artist'); pending.resolve({ok: success}); await tick();
    assert.equal(env.node('artistName').textContent, success ? 'Updated artist' : 'Saved artist');
    assert.equal(env.node('tracks').children.length, 1, 'Refreshing must not duplicate tracks');
}

async function checkHome() {
    const env = environment(''), pending = deferred(), grid = new Element(), calls = [];
    env.selectors.set('.artists', grid);
    env.context.fetch = async url => {
        calls.push(url);
        return url === '/artista' ? json([{nome: 'Saved artist'}]) : pending.promise;
    };
    vm.runInNewContext(fs.readFileSync(path.join(root, 'explorar/script2.js'), 'utf8'), env.context);
    env.events.DOMContentLoaded(); await tick();
    assert.ok(calls.includes('/artista'));
    assert.equal(grid.children.length, 1, 'Local artists must appear before external rankings');
    assert.equal(grid.children[0].children[0].children[1].children[0].textContent, 'Saved artist');
    pending.resolve({ok: false}); await tick();
    assert.equal(grid.children.length, 1, 'Failed rankings must preserve saved artists');
    assert.equal(calls.filter(url => url === '/artista').length, 1, 'Reuse catalog response');
}

async function checkDailyArtists() {
    const env = environment(''), chart = deferred(), catalog = deferred(), grid = new Element();
    env.selectors.set('.artists', grid);
    env.context.fetch = async url => {
        if (url === '/users/me/music-profile') return {ok: false, status: 401};
        if (url === '/artista') return catalog.promise;
        if (url === '/last-fm/discovery?method=chart.getTopArtists') return chart.promise;
        throw new Error('Unexpected request: ' + url);
    };
    vm.runInNewContext(fs.readFileSync(path.join(root, 'explorar/script2.js'), 'utf8'), env.context);
    env.events.DOMContentLoaded();
    chart.resolve(json({artists: {artist: [
        {name: 'First in chart', catalogId: 17, image: [{'#text': 'https://example.test/cover.jpg'}]},
        {name: 'Second in chart'}
    ]}}));
    await tick();
    catalog.resolve(json([{nome: 'Saved artist'}]));
    await tick();
    assert.deepEqual(grid.children.map(card => card.children[0].children[1].children[0].textContent),
        ['First in chart', 'Second in chart']);
    assert.equal(grid.children[0].children[0].href, '/frontend/pages/artista.html?id=17');
}
async function checkPopularArtistPhotos() {
    const env = environment(''), pending = deferred(), ranking = deferred(), grid = new Element(), calls = [];
    const artists = [{id: 1, nome: 'Missing photo'},
        {id: 2, nome: 'Saved photo', imgUrl: 'https://example.test/saved.jpg'},
        {id: 3, nome: 'Broken photo', imgUrl: 'https://example.test/broken.jpg'}];
    env.selectors.set('.artists', grid);
    env.context.fetch = async url => {
        calls.push(url);
        if (url === '/artista') return json(artists);
        if (url === '/catalog/artists/1/photo') return pending.promise;
        if (url === '/catalog/artists/3/photo?refresh=true') return json({status: 'found', imgUrl: 'https://commons.wikimedia.org/artist.jpg'});
        if (url === '/users/me/music-profile') return json({genres: ['Rock', 'Pop', 'Jazz', 'Soul', 'Blues']});
        if (url.startsWith('/last-fm/discovery?')) {
            const tag = new URL(url, 'http://synchro.test').searchParams.get('tag');
            if (tag === 'rock') return ranking.promise;
            if (tag === 'pop') return json({topartists: {artist: [
                {name: 'Pop artist', catalogId: 6}, {name: 'Shared Artist', catalogId: 7}
            ]}});
            if (tag === 'jazz') return json({topartists: {artist: [
                {name: 'Jazz artist', catalogId: 8}
            ]}});
            if (tag === 'soul' || tag === 'blues') return json({topartists: {artist: [
                {name: 'Soul and blues artist', catalogId: 10}
            ]}});
            if (tag === 'brazilian rock') return json({topartists: {artist: [
                {name: 'Brazilian artist', catalogId: 4}
            ]}});
            if (tag === 'brazilian') return json({topartists: {artist: [
                {name: 'Brazilian artist', catalogId: 4}, {name: 'Second Brazilian artist', catalogId: 5}
            ]}});
        }
        throw new Error('Unexpected photo request: ' + url);
    };
    vm.runInNewContext(fs.readFileSync(path.join(root, 'explorar/script2.js'), 'utf8'), env.context);
    env.events.DOMContentLoaded(); await tick();
    assert.equal(grid.children.length, 3, 'Cards must render before photo and ranking requests finish');
    const missing = grid.children[0].children[0].children[0];
    const saved = grid.children[1].children[0].children[0];
    const broken = grid.children[2].children[0].children[0];
    assert.equal(missing.src, '/frontend/assets/images/artist-placeholder.svg');
    assert.equal(saved.src, 'https://example.test/saved.jpg');
    assert.ok(!calls.some(url => url.includes('/artists/2/photo')), 'Working saved photos need no external lookup');
    broken.onerror(); await tick();
    assert.equal(broken.src, 'https://commons.wikimedia.org/artist.jpg');
    pending.resolve(json({status: 'found', imgUrl: 'https://commons.wikimedia.org/missing.jpg'})); await tick();
    assert.equal(missing.src, 'https://commons.wikimedia.org/missing.jpg');
    broken.onerror();
    assert.equal(broken.src, '/frontend/assets/images/artist-placeholder.svg');
    assert.equal(calls.filter(url => url.includes('/photo')).length, 2, 'Broken external images must not create retry loops');
    ranking.resolve(json({topartists: {artist: [
        {name: 'Missing photo', catalogId: 1}, {name: 'shared  artist', catalogId: 7},
        {name: 'Rock artist', catalogId: 9}
    ]}})); await tick();
    assert.deepEqual(grid.children.map(card => card.children[0].href), [
        '/frontend/pages/artista.html?id=4', '/frontend/pages/artista.html?id=5',
        '/frontend/pages/artista.html?id=7', '/frontend/pages/artista.html?id=10',
        '/frontend/pages/artista.html?id=1', '/frontend/pages/artista.html?id=6'
    ]);
    assert.equal(grid.children[0].children[0].children[1].children[1].textContent, 'Cena brasileira');
    assert.equal(grid.children[3].children[0].children[1].children[1].textContent, 'Soul');
}

async function checkRecentAlbumReleases() {
    const env = environment(''), albums = new Element(), calls = [], pending = deferred(), daily = deferred();
    env.selectors.set('.albums', albums);
    const now = new Date();
    const day = offset => new Date(now.getTime() + offset * 86400000).toISOString().slice(0, 10);
    const release = (name, releaseDate, genre, artistName = 'International artist') =>
        ({name, artistName, releaseDate, genres: [{name: genre}]});
    const international = release('New rock album', day(-3), 'Rock');
    const charts = {
        br: [release('Brazilian A', day(-30), 'Rock', 'Brazilian artist'),
            release('Brazilian B', day(-1), 'Rock', 'Brazilian artist'),
            release('Brazilian C', day(-5), 'Rock', 'Brazilian artist'), international],
        us: [release('Old album', day(-31), 'Rock'), release('Future album', day(1), 'Rock'),
            release('Undated album', day(-1).slice(0, 7), 'Rock'), international,
            release('Unrelated album', day(-1), 'Country'), release('No Last.fm listeners', day(-2), 'Rock'),
            release('Wrong artist', day(-2), 'Rock'), release('Another rock album', day(-1), 'Rock')]
    };
    env.context.fetch = async url => {
        calls.push(url);
        if (url === '/catalog/albums/popular') return daily.promise;
        if (url === '/users/me/music-profile') return json({genres: ['Rock', 'Pop', 'Jazz', 'Soul', 'Blues']});
        const target = new URL(url, 'http://synchro.test');
        if (target.pathname === '/catalog/albums/release-chart') {
            const market = target.searchParams.get('market');
            return market === 'br' ? pending.promise : json(charts[market]);
        }
        if (target.searchParams.get('method') === 'album.getInfo') {
            const name = target.searchParams.get('album');
            assert.equal(target.searchParams.get('preview'), 'true');
            return json({album: {name,
                artist: name === 'Wrong artist' ? 'Different artist' : target.searchParams.get('artist'),
                listeners: name === 'No Last.fm listeners' ? '0' : name === 'New rock album' ? '1000' : '10'}});
        }
        if (target.searchParams.get('method') === 'artist.getTopTags') {
            return json({toptags: {tag: target.searchParams.get('artist') === 'Brazilian artist' ? {name: 'Brasil'} : []}});
        }
        throw new Error('Unexpected request: ' + url);
    };
    vm.runInNewContext(fs.readFileSync(path.join(root, 'explorar/script2.js'), 'utf8'), env.context);
    env.events.DOMContentLoaded();
    assert.equal(albums.children.length, 6);
    const firstCard = albums.children[0].children[0];
    assert.equal(firstCard.children[1].children[1].textContent, 'Bad Bunny');
    const coverPath = firstCard.children[0].children[0].src;
    assert.ok(fs.existsSync(path.resolve(root, '../../..', '.' + coverPath)));
    assert.match(env.node('albumRecommendationStatus').textContent, /Seleção padrão/);
    await tick();
    assert.equal(albums.children[0].children[0], firstCard);
    pending.resolve(json(charts.br));
    await tick();
    daily.resolve(json([{name: 'Daily album', artist: 'Artist', releaseDate: day(-2)}]));
    await tick();
    assert.ok(!calls.some(url => url.startsWith('/last-fm/me/top/albums')));
    assert.equal(calls.filter(url => url.includes('method=artist.getTopTags') && url.includes('Brazilian+artist')).length, 1);
    assert.equal(calls.filter(url => url.includes('album=New+rock+album')).length, 1);
    assert.ok(!calls.some(url => /Old\+album|Future\+album|Undated\+album/.test(url)));
    const links = albums.children.map(card => new URL(card.children[0].href, 'http://synchro.test'));
    assert.ok(links.every(link => link.pathname === '/frontend/pages/album.html'));
    assert.deepEqual(links.map(link => link.searchParams.get('album')),
        ['New rock album', 'Brazilian A', 'Brazilian B', 'Brazilian C', 'Another rock album']);
    assert.match(env.node('albumRecommendationStatus').textContent, /Lançamentos dos últimos 30 dias/);
}

async function checkDefaultAlbumsWithoutConnection(signedIn) {
    const env = environment(''), albums = new Element(), calls = [], daily = deferred();
    env.selectors.set('.albums', albums);
    env.context.fetch = async url => {
        calls.push(url);
        if (url === '/catalog/albums/popular') return daily.promise;
        if (url === '/users/me/music-profile') return signedIn ? json({genres: []}) : {ok: false, status: 401};
        if (url === '/users/me') return json({lastFmConnected: false});
        throw new Error('Unexpected request: ' + url);
    };
    vm.runInNewContext(fs.readFileSync(path.join(root, 'explorar/script2.js'), 'utf8'), env.context);
    env.events.DOMContentLoaded();
    const initial = albums.children.slice();
    await tick();
    assert.equal(initial.length, 6);
    assert.deepEqual(albums.children, initial);
    assert.match(env.node('albumRecommendationStatus').textContent,
        signedIn ? /^Seleção padrão · Conecte o Last.fm/ : /^Seleção padrão · Entre na sua conta/);
    daily.resolve(json([{name: 'Daily album', artist: 'Artist', releaseDate: '2026-11-01'}]));
    await tick();
    assert.equal(albums.children.length, 1);
    assert.equal(albums.children[0].children[0].children[1].children[0].textContent, 'Daily album');
    assert.deepEqual(calls, signedIn ? ['/users/me/music-profile', '/catalog/albums/popular', '/users/me']
        : ['/users/me/music-profile', '/catalog/albums/popular']);
}

async function checkUnifiedArtist(byName) {
    const env = environment(byName ? '?artist=Test%20Artist' : '?id=42'), calls = [], registrations = [], visits = [];
    const refresh = deferred(), popularTracks = deferred(), popularAlbums = deferred();
    const header = new Element(); env.selectors.set('.artist-header', header);
    env.selectors.set('.artist-section', new Element());
    env.context.CustomEvent = class { constructor(type, {detail}) { this.type = type; this.detail = detail; } };
    env.context.document.dispatchEvent = event => visits.push(event);
    env.context.window.SynchroCatalog = {record: async item => { registrations.push(item); return {id: 42}; }};
    const data = {artist: {id: 42, nome: 'Test Artist', biografia: 'Saved biography', lastFm: {
        url: 'https://www.last.fm/music/Test+Artist', listeners: 7, playcount: 53, tags: ['rock']}},
        musicas: [{id: 1, titulo: 'Saved track'}], albums: [{id: 2, titulo: 'Saved album'}]};
    env.context.fetch = async input => {
        const url = new URL(input, 'http://synchro.test'); calls.push(url);
        if (url.pathname.endsWith('/refresh')) return refresh.promise;
        if (url.pathname.endsWith('/photo')) return json({status: 'found', imgUrl: 'https://lastfm.freetls.fastly.net/gallery.jpg'});
        if (url.searchParams.get('method') === 'artist.gettoptracks') return popularTracks.promise;
        if (url.searchParams.get('method') === 'artist.gettopalbums') return popularAlbums.promise;
        return json(data);
    };
    vm.runInNewContext(fs.readFileSync(path.join(root, 'artista.js'), 'utf8'), env.context);
    await env.events.DOMContentLoaded(); await tick();
    assert.equal(registrations.length, byName ? 1 : 0);
    assert.equal(env.node('artistName').textContent, 'Test Artist');
    assert.equal(header.attrs['aria-busy'], 'false', 'Catalog content must not wait for popular lists');
    assert.equal(env.node('artistLastFmLink').href, 'https://www.last.fm/music/Test+Artist');
    assert.equal(env.node('artistStatistics').textContent, '7 ouvintes · 53 reproduções');
    assert.equal(env.node('tracks').children[0].href, 'musica.html?id=1');
    assert.equal(env.node('albums').children[0].href, 'album.html?id=2');
    assert.equal(env.node('artistImage').src, 'https://lastfm.freetls.fastly.net/gallery.jpg');
    popularTracks.resolve(json({toptracks: {track: [{name: 'Popular track'}, {name: 'Popular track'}]}}));
    popularAlbums.resolve(json({topalbums: {album: {name: 'Popular album'}}}));
    await tick();
    assert.equal(env.node('artistTopTracks').children.length, 1, 'Popular list removes duplicates');
    assert.equal(env.node('artistTopAlbums').children.length, 1, 'Single-object API lists are supported');
    assert.equal(env.node('artistTopTracksSection').hidden, false);
    assert.equal(env.node('artistTopAlbumsSection').hidden, false);
    const track = new URL(env.node('artistTopTracks').children[0].href, 'http://synchro.test');
    assert.equal(track.searchParams.get('artist'), 'Test Artist');
    assert.equal(track.searchParams.get('track'), 'Popular track');
    refresh.resolve({ok: true}); await tick();
    assert.equal(visits.length, 1, 'Refreshing must not duplicate recent history');
    assert.equal(calls.filter(url => url.searchParams.get('method') === 'artist.gettoptracks').length, 1);
    assert.equal(env.node('tracks').children.length, 1);
    assert.equal(env.node('albums').children.length, 1);
    const oldHtml = fs.readFileSync(path.join(root, '../../pages/detalhes-artista.html'), 'utf8');
    assert.ok(oldHtml.includes("location.replace('artista.html' + location.search + location.hash)"));
    assert.ok(!oldHtml.includes('/explorar/artista.js'), 'Old page redirects instead of running a second controller');
}
(async () => {
    await checkAlbum(true); await checkAlbum(false);
    await checkArtist(true); await checkArtist(false);
    await checkHome();
    await checkDailyArtists();
    await checkPopularArtistPhotos();
    await checkRecentAlbumReleases();
    await checkDefaultAlbumsWithoutConnection(false);
    await checkDefaultAlbumsWithoutConnection(true);
    await checkUnifiedArtist(false); await checkUnifiedArtist(true);
    console.log('PASS: albums, artists and Explore render saved data before external requests finish; refresh success/failure preserves content without duplicates.');
    console.log('PASS: popular artist photos load in the background, reuse saved images, recover broken URLs and keep a local placeholder when loading fails.');
    console.log('PASS: one artist page handles names and IDs, saved and popular lists, statistics, Last.fm links, photos and old URL compatibility.');
})().catch(error => { console.error(error); process.exitCode = 1; });
