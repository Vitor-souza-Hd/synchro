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
    const context = {document, URL, URLSearchParams, console, CustomEvent,
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
    assert.equal(calls[0], '/artista');
    assert.equal(grid.children.length, 1, 'Local artists must appear before external rankings');
    assert.equal(grid.children[0].children[0].children[1].children[0].textContent, 'Saved artist');
    pending.resolve({ok: false}); await tick();
    assert.equal(grid.children.length, 1, 'Failed rankings must preserve saved artists');
    assert.equal(calls.filter(url => url === '/artista').length, 1, 'Reuse catalog response');
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
        if (url.startsWith('/last-fm/')) return ranking.promise;
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
    ranking.resolve(json({topartists: {artist: [{name: 'Missing photo'}]}})); await tick();
    assert.equal(grid.children[0].children[0].children[0].src, 'https://commons.wikimedia.org/missing.jpg');
    assert.equal(calls.filter(url => url.includes('/photo')).length, 2, 'Personalized re-render reuses found photos');
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
    await checkPopularArtistPhotos();
    await checkUnifiedArtist(false); await checkUnifiedArtist(true);
    console.log('PASS: albums, artists and Explore render saved data before external requests finish; refresh success/failure preserves content without duplicates.');
    console.log('PASS: popular artist photos load in the background, reuse saved images, recover broken URLs and keep a local placeholder when loading fails.');
    console.log('PASS: one artist page handles names and IDs, saved and popular lists, statistics, Last.fm links, photos and old URL compatibility.');
})().catch(error => { console.error(error); process.exitCode = 1; });
