const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../src/main/resources/static/frontend/assets/js');
const features = fs.readFileSync(path.join(root, 'features.js'), 'utf8');
const bootstrap = fs.readFileSync(path.join(root, 'song-details.js'), 'utf8');
const tick = () => new Promise(resolve => setImmediate(resolve));
function setup(page) {
    const events = new Map(), calls = [], nodes = new Map();
    function element() { return {dataset: {}, append() {}, prepend() {}, setAttribute() {}, addEventListener() {}}; }
    const document = {body: element(), createElement: element, querySelector: () => null, querySelectorAll: () => [],
        getElementById: id => { if (!nodes.has(id)) nodes.set(id, element()); return nodes.get(id); },
        addEventListener: (type, fn) => { if (!events.has(type)) events.set(type, []); events.get(type).push(fn); },
        dispatchEvent: event => { for (const fn of events.get(event.type) || []) fn(event); }};
    const location = new URL('http://synchro.test/frontend/pages/' + page);
    let fail = false;
    const fetch = async (url, options = {}) => {
        calls.push({url, options});
        if (url === '/catalog/interactions') return {ok: !fail, json: async () => ({type: JSON.parse(options.body).type, id: 42})};
        if (url === '/catalog/songs/42') return {ok: true, json: async () => ({id: 42, titulo: 'Track'})};
        throw new Error('Unexpected request: ' + url);
    };
    const context = vm.createContext({document, location, window: {location}, URL, URLSearchParams, fetch,
        console: {warn() {}}, CustomEvent: class {constructor(type, options) {this.type = type; this.detail = options.detail;}}});
    vm.runInContext(features, context);
    return {context, events, calls, fail: value => { fail = value; }};
}
(async () => {
    for (const [page, type] of [
        ['detalhes-musica.html?track=Track&artist=Artist&album=Album', 'track'],
        ['detalhes-artista.html?artist=Artist', 'artist'],
        ['album-explorar.html?album=Album&artist=Artist', 'album']
    ]) {
        const env = setup(page);
        let delivered;
        if (type === 'track') {
            vm.runInContext(bootstrap, env.context);
            env.context.document.addEventListener('synchro:song', event => { delivered = event.detail.song; });
        }
        await Promise.all(env.events.get('DOMContentLoaded').map(fn => fn())); await tick();
        const posts = env.calls.filter(call => call.url === '/catalog/interactions');
        assert.equal(posts.length, 1, 'Page and song bootstrap share one registration');
        assert.equal(JSON.parse(posts[0].options.body).type, type);
        assert.equal(posts[0].options.keepalive, true, 'Registration survives navigation');
        if (type === 'track') assert.equal(delivered.id, 42, 'Reviews and playlists receive the saved media ID');
    }
    const env = setup('explorar.html');
    const anchor = {href: 'http://synchro.test/frontend/pages/detalhes-artista.html?artist=Clicked',
        closest: () => null};
    const event = {target: {closest: () => anchor}};
    env.events.get('click')[0](event); env.events.get('click')[0](event); await tick();
    assert.equal(env.calls.length, 1, 'Repeated clicks reuse the same catalog request');
    assert.equal(JSON.parse(env.calls[0].options.body).name, 'Clicked');
    const item = {type: 'album', name: 'Retry album', artist: 'Artist'};
    env.fail(true);
    await assert.rejects(env.context.window.SynchroCatalog.record(item));
    env.fail(false);
    assert.equal((await env.context.window.SynchroCatalog.record(item)).id, 42, 'Failed writes can be retried');
    const existing = setup('detalhes-musica.html?id=42');
    await Promise.all(existing.events.get('DOMContentLoaded').map(fn => fn())); await tick();
    assert.equal(existing.calls.length, 0, 'An existing catalog ID does not need another insert');
    console.log('PASS: opened songs/albums/artists and media clicks register in catalog; requests are shared, survive navigation and can be retried; songs expose their saved ID.');
})().catch(error => { console.error(error); process.exitCode = 1; });
