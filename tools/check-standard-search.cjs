// Deterministic search behavior and page wiring checks; no running server required.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../src/main/resources/static');
const script = fs.readFileSync(path.join(root, 'frontend/assets/js/search.js'), 'utf8');
const files = [path.join(root, 'index.html'), ...fs.readdirSync(path.join(root, 'frontend/pages'))
    .filter(name => name.endsWith('.html')).map(name => path.join(root, 'frontend/pages', name))];
let pages = 0;
for (const file of files) {
    const html = fs.readFileSync(file, 'utf8');
    if (!/class="search"/.test(html)) continue;
    assert.equal((html.match(/<script[^>]+src="[^"]*\/search\.js(?:\?[^"\s]*)?"/g) || []).length, 1, file);
    assert.ok(html.includes('search.js?v=20261105-single-search-1'), 'Fresh search asset: ' + file);
    assert.ok(html.includes('assets/css/search.css'), file);
    assert.ok(!html.includes('explorar/busca.js'), file);
    pages++;
}
assert.ok(!fs.readFileSync(path.join(root, 'frontend/assets/js/explorar/script2.js'), 'utf8')
    .includes('searchInput.addEventListener'), 'No competing legacy search listeners');

class Element {
    constructor(tag) { this.tag = tag; this.children = []; this.listeners = {}; this.attrs = {}; this.style = {}; this.dataset = {}; }
    setAttribute(key, value) { this.attrs[key] = value; }
    removeAttribute(key) { delete this.attrs[key]; }
    append(...nodes) { this.children.push(...nodes); }
    replaceChildren(...nodes) { this.children = nodes; }
    addEventListener(type, fn) { this.listeners[type] = fn; }
    getBoundingClientRect() { return {width: 400, left: 20, bottom: 60}; }
    closest() { return box; }
    querySelector() { return icon; }
    querySelectorAll() { return []; }
    contains(el) { return el === this || this.children.some(child => child.contains?.(el)); }
    replaceWith(element) { box.children = box.children.map(child => child === this ? element : child); }
}
const input = new Element('input'), box = new Element('div'), icon = new Element('i');
const body = new Element('body'); box.append(icon, input); body.append(box);
let calls = [];
let respond = () => fixture;
const fixture = {songs: [{id: 1, titulo: 'Song', artistas: [{nome: 'Artist'}]}],
    artists: [{id: 2, nome: 'Artist'}], albums: [{id: 3, titulo: 'Album'}], users: [{id: 4, username: 'User'}]};
const context = vm.createContext({document: {readyState: 'complete', body,
    querySelectorAll: () => [input], createElement: tag => new Element(tag), addEventListener() {}},
    window: {innerWidth: 1280, innerHeight: 850, addEventListener() {}}, AbortController, setTimeout, clearTimeout,
    fetch: async url => { calls.push(url); return {ok: true, json: async () => respond(url)}; }});
vm.runInContext(script, context);
vm.runInContext(script, context);
context.window.SynchroSearch.initialize();
assert.equal(body.children.filter(node => node.className === 'synchro-search-results').length, 1,
    'Repeated initialization must create only one results panel');
assert.equal(box.children.filter(node => node.className === 'synchro-search-submit').length, 1,
    'Repeated initialization must create only one submit button');
vm.runInContext(fs.readFileSync(path.join(root, 'frontend/assets/js/explorar/busca.js'), 'utf8'), context);
assert.equal(body.children.filter(node => node.className === 'synchro-search-results').length, 1,
    'Legacy entry point must reuse the shared search');
const tick = () => new Promise(resolve => setImmediate(resolve));
const flatten = node => [node, ...node.children.flatMap(flatten)];
(async () => {
    const panel = body.children[1], button = box.children[0];
    input.value = 'radio'; input.listeners.input();
    await new Promise(resolve => setTimeout(resolve, 450));
    assert.deepEqual(calls, [], 'Typing must not submit a search');
    let prevented = false;
    input.listeners.keydown({key: 'Enter', preventDefault() { prevented = true; }});
    await tick(); assert.ok(prevented);
    assert.deepEqual(calls, ['/search?q=radio']);
    assert.deepEqual(flatten(panel).filter(node => node.tag === 'h3').map(node => node.textContent),
        ['Músicas (1)', 'Artistas (1)', 'Álbuns (1)', 'Usuários (1)']);
    assert.deepEqual(flatten(panel).filter(node => node.tag === 'a').map(node => node.href), [
        '/frontend/pages/detalhes-musica.html?id=1', '/frontend/pages/artista.html?id=2',
        '/frontend/pages/album.html?id=3', '/frontend/pages/perfil.html?user=4']);
    input.listeners.keydown({key: 'Escape'}); assert.equal(panel.hidden, true);
    input.value = 'artist'; button.listeners.click(); await tick();
    assert.equal(calls.at(-1), '/search?q=artist');
    input.value = 'r'; button.listeners.click(); await tick(); assert.equal(calls.length, 2);
    input.value = ''; input.listeners.input(); assert.equal(panel.hidden, true);
    let polls = 0;
    respond = () => ++polls === 1 ? {source: 'local', refreshing: true, users: fixture.users} : fixture;
    input.value = 'pending'; button.listeners.click(); await tick();
    assert.equal(flatten(panel).filter(node => node.tag === 'a').length, 1, 'Saved users are usable before Last.fm responds');
    assert.ok(flatten(panel).some(node => node.textContent?.includes('buscando mais')));
    await new Promise(resolve => setTimeout(resolve, 1100));
    assert.equal(polls, 2, 'Pending query is checked again without another user submission');
    assert.equal(flatten(panel).filter(node => node.tag === 'a').length, 4);
    respond = () => ({source: 'local', refreshing: true});
    input.value = 'cancelled'; button.listeners.click(); await tick();
    input.listeners.keydown({key: 'Escape'});
    const count = calls.length;
    await new Promise(resolve => setTimeout(resolve, 1100));
    assert.equal(calls.length, count, 'Closing search cancels pending polling');
    assert.equal(panel.hidden, true);
    console.log(`PASS: ${pages} pages use shared search; Enter, magnifier, no automatic requests, four categories, catalog links, Escape and minimum length.`);
    console.log('PASS: local results appear during pending provider queries; polling completes and is cancelled by Escape.');
})().catch(error => { console.error(error); process.exitCode = 1; });
