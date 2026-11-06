const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../src/main/resources/static/frontend');
const oldPage = fs.readFileSync(path.join(root, 'pages/detalhes-artista.html'), 'utf8');
const redirect = oldPage.match(/<script>([\s\S]*?)<\/script>/)[1];
for (const search of ['?artist=AC%2FDC', '?id=366', '?artist=slowGM&id=366']) {
    let target;
    vm.runInNewContext(redirect, {location: {search, hash: '#albums', replace: value => { target = value; }}});
    assert.equal(target, 'artista.html' + search + '#albums');
}
const page = fs.readFileSync(path.join(root, 'pages/artista.html'), 'utf8');
for (const id of ['artistName','artistImage','artistLastFmLink','favoriteBtn','reviewModal','tracks','albums','artistTopTracks','artistTopAlbums']) {
    assert.equal((page.match(new RegExp('id="' + id + '"', 'g')) || []).length, 1, 'One control per function: ' + id);
}
assert.ok(!page.includes('/explorar/artista.js'), 'The unified page loads one artist controller');

const events = {}, store = new Map();
store.set('synchro:recentes:v1', JSON.stringify([{type:'Artista',title:'slowGM',subtitle:'Artista',
    url:'/frontend/pages/detalhes-artista.html?artist=slowGM',visitedAt:1}]));
function node() { return {children: [], append(...children) {this.children.push(...children);},
    appendChild(child) {this.append(child);}, replaceChildren() {this.children=[];}}; }
const nodes = new Map(['recentesGrid','recentesEmpty','recentesCount','limparRecentes'].map(id => [id,node()]));
const location = new URL('http://synchro.test/frontend/pages/recentes.html');
vm.runInNewContext(fs.readFileSync(path.join(root, 'assets/js/explorar/recentes.js'), 'utf8'), {
    location, URL, URLSearchParams, window: {}, console,
    localStorage: {getItem: key => store.get(key) ?? null, setItem: (key,value) => store.set(key,value)},
    document: {getElementById: id => nodes.get(id), createElement: node, addEventListener: (event,fn) => {events[event]=fn;}}
});
events.DOMContentLoaded();
assert.equal(nodes.get('recentesGrid').children[0].href, 'http://synchro.test/frontend/pages/artista.html?artist=slowGM',
    'Old recent-history entries link to the unified page');
location.pathname = '/frontend/pages/artista.html';
events['synchro:artist']({detail: {id: 366, name: 'slowGM'}});
const history = JSON.parse(store.get('synchro:recentes:v1'));
assert.equal(history.length, 1, 'Revisiting through an ID does not duplicate the old named history entry');
assert.equal(history[0].url, '/frontend/pages/artista.html?id=366');
console.log('PASS: legacy artist URLs preserve names, IDs and anchors; canonical page has one controller; saved history migrates without duplicates.');
