const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname,
    '../src/main/resources/static/frontend/assets/js/explorar/musica.js'), 'utf8');

async function scenario({catalog = false, cover = false, brokenCover = false, albumOnly = false, failures = false}) {
    const calls = [], images = [], nodes = new Map();
    let listener;
    function element() {
        return {dataset: {}, hidden: true, textContent: '', append() {}, replaceChildren() {},
            removeAttribute() {}, set src(url) {
                this.url = url; images.push(url);
                queueMicrotask(() => (url.includes('archive.org') && brokenCover
                    ? this.onerror : this.onload)?.());
            }};
    }
    const document = {
        addEventListener: (_, fn) => { listener = fn; }, querySelector: () => null,
        getElementById: id => { if (!nodes.has(id)) nodes.set(id, element()); return nodes.get(id); },
        createElement: element
    };
    const fetch = async input => {
        const url = new URL(input, 'http://synchro.test'); calls.push(url);
        let data = {};
        if (url.pathname.includes('/refresh')) return {ok: false};
        if (url.pathname.startsWith('/musicbrainz/')) {
            if (failures) throw new Error('offline');
            data = cover ? {status: 'found', album: {title: 'Album', releaseGroupId: 'group'}} : {};
        }
        if (url.pathname.startsWith('/cover-art/')) data = {status: 'found', coverUrl: 'https://archive.org/cover.jpg'};
        if (url.searchParams.get('method') === 'track.getInfo') data = {track: {
            name: 'Track', album: {title: 'Album', artist: 'Artist', image: albumOnly ? [] : [{'#text': 'https://lastfm.freetls.fastly.net/track.jpg'}]}
        }};
        if (url.searchParams.get('method') === 'album.getInfo') data = {album: {
            image: [{'#text': 'https://lastfm.freetls.fastly.net/album.jpg'}]
        }};
        return {ok: true, json: async () => data};
    };
    vm.runInNewContext(source, {document, fetch, URL, console: {warn() {}, info() {}, error() {}},
        window: {location: {origin: 'http://synchro.test'}}});
    listener({detail: {params: new URLSearchParams({track: 'Track', artist: 'Artist'}),
        song: catalog ? {id: 1, titulo: 'Track', album: {id: 2, titulo: 'Album'}} : null}});
    for (let i = 0; i < 30; i++) await new Promise(resolve => setImmediate(resolve));
    assert.equal(nodes.get('musicImage').hidden, false);
    return {calls, images, url: nodes.get('musicImage').url};
}
(async () => {
    assert.match((await scenario({cover: true})).url, /archive.org/);
    assert.match((await scenario({cover: true, brokenCover: true})).url, /track.jpg/);
    assert.match((await scenario({catalog: true})).url, /track.jpg/);
    assert.match((await scenario({albumOnly: true})).url, /album.jpg/);
    const failed = await scenario({failures: true});
    assert.match(failed.url, /track.jpg/);
    assert.equal(failed.calls.filter(url => url.searchParams.get('method') === 'track.getInfo').length, 1);
    console.log('PASS: MusicBrainz/Cover Art, Last.fm track/album, broken images, missing catalog cover and source failures.');
})().catch(error => { console.error(error); process.exitCode = 1; });
