(() => {
    const images = '/frontend/assets/images/';
    const requests = new Map();
    const pausedUntil = new Map();
    const activeLoads = new WeakMap();
    const cacheAge = 24 * 60 * 60 * 1000;
    const unavailableAge = 15000;
    const lookupExpiry = {apple: 300000, cover: 120000, musicbrainz: 120000};
    const lookupTimeout = {apple: 13000, artistPhoto: 11000, cover: 22000, musicbrainz: 22000};

    async function readLookup(url, source, entry) {
        try {
            const response = await fetch(url, {signal: AbortSignal.timeout(lookupTimeout[source] || 6500)});
            if (response.ok) {
                const data = await response.json();
                const freshness = data?.partial ? unavailableAge : lookupExpiry[source] || 120000;
                entry.expires = Date.now() + freshness;
                return data;
            }
            const now = Date.now();
            const unavailable = response.status === 408 || response.status === 429 || response.status >= 500;
            entry.expires = now + (unavailable ? unavailableAge : 60000);
            const retryAfter = response.headers?.get('Retry-After');
            if (unavailable && (response.status === 429 || retryAfter)) {
                const wait = /^\d+$/.test(retryAfter || '') ? Number(retryAfter) * 1000 : Date.parse(retryAfter) - now;
                const until = now + (Number.isFinite(wait) ? Math.max(1000, wait) : unavailableAge);
                pausedUntil.set(source, Math.max(pausedUntil.get(source) || 0, until));
                entry.expires = Math.max(entry.expires, until);
            }
        } catch {
            entry.expires = Date.now() + unavailableAge;
        }
        return null;
    }

    function json(path, params = {}, source = 'catalog') {
        const url = new URL(path, location.origin);
        url.search = new URLSearchParams(params);
        const cached = requests.get(url.href);
        const now = Date.now();
        if (cached && now < cached.expires) return cached.promise;
        if (now < (pausedUntil.get(source) || 0)) return null;
        if (requests.size >= 160) {
            for (const [previousUrl, previous] of requests) {
                if (previous.expires === Infinity) continue;
                requests.delete(previousUrl);
                break;
            }
        }
        const entry = {expires: Infinity};
        entry.promise = readLookup(url, source, entry);
        requests.set(url.href, entry);
        return entry.promise;
    }

    function key(value) {
        return String(value ?? '').normalize('NFKC').trim().replace(/\s+/g, ' ').toLowerCase();
    }

    function asList(value) {
        return Array.isArray(value) ? value : [];
    }

    function artistName(value) {
        return typeof value === 'string' ? value : value?.name || '';
    }

    function hasArtistCredit(credit, artist) {
        const expected = key(artist);
        return Boolean(expected) && (key(credit) === expected ||
            key(credit).split(/\s+(?:&|and|x|feat\.?|ft\.?|with)\s+|\s*,\s*/).includes(expected));
    }

    function matchesTrack(track, title, artist) {
        return Boolean(title) && key(track?.name) === key(title) && hasArtistCredit(artistName(track?.artist), artist);
    }

    function confirmedTrackRelease(title, artist) {
        if (key(title) !== 'instável' || !hasArtistCredit(artist, 'kouth')) return null;
        // Symphonic Distribution official art track: youtube.com/watch?v=z6yygkR-GpI
        return {title: 'DIÁRIO DE UMA GAROTA SUBVERSIVA (DELUXE)', artist: 'kouth',
            cover: 'https://i.ytimg.com/vi/z6yygkR-GpI/sddefault.jpg'};
    }

    function imageUrl(value) {
        try {
            if (typeof value !== 'string' || !value.trim()) return null;
            const path = value.trim().replace(/^\.\.\/assets\//, images).replace(/^frontend\/assets\//, images);
            const url = new URL(path, location.origin);
            if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password ||
                    /placeholder|2a96cbd8b46e442fc41c2b86b821562f|placehold\.co/i.test(url.href)) return null;
            if (!/^https?:\/\//i.test(value.trim()) &&
                    (url.origin !== location.origin || !url.pathname.startsWith(images))) return null;
            url.hash = '';
            return url.href;
        } catch {
            return null;
        }
    }

    function loadImage(url) {
        return new Promise(resolve => {
            const image = new Image();
            image.decoding = "async";
            const timer = setTimeout(() => {
                finish(null);
                image.removeAttribute('src');
            }, 5000);
            function finish(result) {
                clearTimeout(timer);
                image.onload = image.onerror = null;
                resolve(result);
            }
            image.onload = () => finish(image.naturalWidth > 1 && image.naturalHeight > 1 ? image : null);
            image.onerror = () => finish(null);
            image.src = url;
        });
    }

    function lastFmImages(item) {
        return asList(item?.image).slice().reverse().map(image => image?.['#text']);
    }

    function safePhoto(url) {
        try {
            const parsed = new URL(url);
            return parsed.protocol === "https:" &&
                (parsed.hostname === "upload.wikimedia.org" ||
                    parsed.hostname.endsWith(".wikimedia.org"));
        } catch {
            return false;
        }
    }

    function normalize(value) {
        return String(value || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .toLowerCase()
            .replace(/_/g, " ")
            .replace(/\s+/g, " ")
            .trim();
    }

    async function wikiPhoto(name, lang) {
        const url = new URL(`https://${lang}.wikipedia.org/w/api.php`);
        url.searchParams.set("action", "query");
        url.searchParams.set("titles", name);
        url.searchParams.set("prop", "pageimages");
        url.searchParams.set("piprop", "thumbnail");
        url.searchParams.set("pithumbsize", "500");
        url.searchParams.set("format", "json");
        url.searchParams.set("formatversion", "2");
        url.searchParams.set("redirects", "1");
        url.searchParams.set("origin", "*");

        const response = await fetch(url, {signal: AbortSignal.timeout(6000)});

        if (!response.ok) return null;

        const data = await response.json();
        const page = data.query?.pages?.[0];

        if (!page || page.missing) return null;

        const validNames = new Set([normalize(name)]);

        for (const redirect of (data.query?.redirects || [])) {
            if (normalize(redirect.from) === normalize(name)) {
                validNames.add(normalize(redirect.to));
            }
        }

        if (!validNames.has(normalize(page.title))) return null;

        return safePhoto(page.thumbnail?.source)
            ? page.thumbnail.source
            : null;
    }

    async function* artistPhotos(artist, id, request, active) {
        const path = id ? '/catalog/artists/' + encodeURIComponent(id) + '/photo' : '/catalog/artists/by-name/photo';
        const params = id ? {} : {name: artist};
        const photo = await request(path, params, 'artistPhoto');
        yield photo?.imgUrl;
        if (photo?.imgUrl && active()) {
            const replacement = await request(path, {...params, refresh: 'true'}, 'artistPhoto');
            yield replacement?.imgUrl;
        }
        for (const lang of ['pt', 'en']) {
            if (!active()) return;
            yield await wikiPhoto(artist, lang).catch(() => null);
        }
    }

    function catalogArtists(item) {
        return [item?.lastFm?.artistName, artistName(item?.artist),
            ...asList(item?.artistas).map(artist => artist.nome)].filter(Boolean);
    }

    function matchesItem(item, title, artist) {
        return item && (!title || key(item.titulo || item.name) === key(title)) &&
            (!artist || catalogArtists(item).some(name => key(name) === key(artist)));
    }

    async function savedAlbum(title, artist, id, knownAlbum, request) {
        if (knownAlbum) return matchesItem(knownAlbum, title, artist) ? knownAlbum : null;
        if (id) {
            const saved = (await request('/catalog/albums/' + id))?.album;
            if (matchesItem(saved, title, artist)) return saved;
        }
        if (!artist || !title) return null;
        const catalog = await request('/catalog/artists/by-name', {name: artist});
        return asList(catalog?.albums).find(item => matchesItem(item, title, artist));
    }
    function releaseGroup(value) {
        return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
            ? value.toLowerCase() : null;
    }

    function archiveUrls(result) {
        return [...new Set([...asList(result?.coverUrls), result?.coverUrl].map(imageUrl).filter(Boolean))];
    }

    async function* archiveCovers(id, request) {
        const path = '/cover-art/release-group/' + id;
        const primary = archiveUrls(await request(path, {}, 'cover'));
        yield* primary;
        // The normal lookup checks editions when the group has no cover. Only ask for
        // additional editions when its selected URLs were unusable in this browser.
        if (primary.length) {
            yield* archiveUrls(await request(path, {alternatives: 'true'}, 'cover'));
        }
    }

    async function* lastFmAlbum(title, artist, request, track) {
        const result = await request('/last-fm/discovery', {
            method: 'album.getInfo', artist, album: title, preview: 'true'
        }, 'lastfm');
        const album = result?.album;
        const tracks = album?.tracks?.track;
        if (key(album?.name || album?.title) === key(title) && hasArtistCredit(artistName(album?.artist), artist) &&
                (!track || (Array.isArray(tracks) ? tracks : [tracks]).some(item => matchesTrack(item, track.title, track.artist)))) {
            yield* lastFmImages(album);
        }
    }

    async function* chartCovers(title, artist, request) {
        for (const market of ['br', 'us']) {
            const matches = await request('/catalog/albums/release-chart', {market}, 'apple');
            for (const entry of asList(matches)) {
                if (key(entry.name) === key(title) && key(entry.artistName) === key(artist)) yield entry.artworkUrl100;
            }
        }
    }

    function matchesReleaseGroup(group, title, artist) {
        if (key(group.title) !== key(title)) return false;
        const credits = asList(group['artist-credit']);
        const creditedName = credits.map(credit => (credit.name || credit.artist?.name || '') +
            (credit.joinphrase || '')).join('');
        return key(creditedName) === key(artist) ||
            credits.some(credit => [credit.name, credit.artist?.name].some(name => key(name) === key(artist)));
    }

    async function* albumCovers(title, artist, knownGroup, request) {
        const checked = new Set();
        if (knownGroup) {
            checked.add(knownGroup);
            yield* archiveCovers(knownGroup, request);
        }
        yield* lastFmAlbum(title, artist, request);
        yield* chartCovers(title, artist, request);

        const result = await request('/musicbrainz/album/search', {album: title, artist}, 'musicbrainz');
        for (const group of asList(result?.['release-groups'])) {
            const id = releaseGroup(group?.id);
            if (!id || checked.has(id) || !matchesReleaseGroup(group, title, artist)) continue;
            checked.add(id);
            yield* archiveCovers(id, request);
        }
    }

    async function* trackCovers(title, artist, albumHint, request) {
        if (!title || !artist) return;
        const confirmed = confirmedTrackRelease(title, artist);
        if (confirmed) {
            yield confirmed.cover;
            yield* lastFmAlbum(confirmed.title, confirmed.artist, request, {title, artist});
            return;
        }

        if (albumHint?.title && hasArtistCredit(albumHint.artist, artist)) {
            yield* lastFmAlbum(albumHint.title, albumHint.artist, request, {title, artist});
        }

        // This route identifies a release from a recording with matching title and artist credits.
        const result = await request('/musicbrainz/track/album', {artist, track: title}, 'musicbrainz');
        const group = releaseGroup(result?.album?.releaseGroupId);
        if (result?.status === 'found' && group) yield* archiveCovers(group, request);

        const info = await request('/last-fm/discovery', {
            method: 'track.getInfo', artist, track: title, preview: 'true'
        }, 'lastfm');
        if (matchesTrack(info?.track, title, artist)) {
            const album = info.track.album;
            const credit = artistName(album?.artist);
            if (album?.title && hasArtistCredit(credit, artist)) {
                yield* lastFmImages(album);
                yield* lastFmAlbum(album.title, credit, request);
            }
        }

        // A single is valid only when its track list also confirms the requested recording.
        yield* lastFmAlbum(title, artist, request, {title, artist});
    }

    async function* covers(title, artist, releaseGroupId, id, knownAlbum, request) {
        const item = await savedAlbum(title, artist, id, knownAlbum, request);
        yield item?.lastFm?.imageUrl;
        yield* asList(item?.images);

        artist = artist || catalogArtists(item)[0];
        title = title || item?.titulo || item?.name;
        if (artist && title) yield* albumCovers(title, artist, releaseGroup(releaseGroupId), request);
    }

    function readCache(cacheKey) {
        try {
            const entry = JSON.parse(sessionStorage.getItem(cacheKey));
            return entry && Date.now() - entry.savedAt < cacheAge ? entry.url : null;
        } catch {
            return null;
        }
    }

    async function* candidates(cacheKey, sources) {
        yield readCache(cacheKey);
        yield* sources;
    }

    function styleImage(image, {className, alt, imageId}) {
        image.className = className || '';
        image.alt = alt || '';
        if (imageId) image.id = imageId;
    }

    function show(container, options) {
        const loadId = {};
        activeLoads.set(container, loadId);
        const fallback = document.createElement('img');
        styleImage(fallback, options);
        fallback.src = images + (options.kind === 'artist' ? 'artist-placeholder.svg' : 'catalog-placeholder.svg');
        container.replaceChildren(fallback);

        const active = () => container.isConnected && activeLoads.get(container) === loadId;
        const load = () => resolveImage(container, options, active);
        const scheduler = window.SynchroImageVisibility;
        if (scheduler) scheduler.whenNear(container, load);
        else load();
    }

    function photoKey(url) {
        const clean = imageUrl(url);
        if (!clean) return null;
        const {hostname} = new URL(clean);
        if (hostname === 'lastfm.freetls.fastly.net') return clean.replace(/(\/i\/u\/)[^/]+\//, '$1');
        if (hostname === 'coverartarchive.org' || hostname.endsWith('.archive.org')) {
            return clean.replace(/-\d{2,4}(\.jpg)(?=$|\?)/i, '$1');
        }
        return clean;
    }

    async function findImage(options, active, {cacheKey, excluded = [], currentUrl = () => null} = {}) {
        if (!active()) return null;
        const {kind, title, artist, releaseGroupId, id, knownAlbum, albumHint, preferredImages} = options;
        const seen = new Set(excluded.map(photoKey));
        const catalogId = /^\d+$/.test(id || '') ? id : null;
        const request = (...args) => active() ? json(...args) : null;
        const sources = kind === 'artist' ? artistPhotos(artist, catalogId, request, active)
            : kind === 'track' ? trackCovers(title, artist, albumHint, request)
            : covers(title, artist, releaseGroupId, catalogId, knownAlbum, request);
        const confirmed = kind === 'track' && confirmedTrackRelease(title, artist);
        const preferred = withPreferredImages(confirmed ? [] : preferredImages, sources);
        for await (const candidate of (cacheKey ? candidates(cacheKey, preferred) : preferred)) {
            if (!active()) return null;
            const url = imageUrl(candidate);
            const identity = cacheKey ? url : photoKey(url);
            if (!identity || seen.has(identity) || (!cacheKey && identity === photoKey(currentUrl()))) continue;
            seen.add(identity);
            const image = await loadImage(url);
            if (!active()) return null;
            if (image && (cacheKey || identity !== photoKey(currentUrl()))) return image;
        }
        return null;
    }

    async function resolveImage(container, options, active) {
        const {kind, title, artist, releaseGroupId, id} = options;
        const cacheKey = (kind === 'track' ? 'synchro:track-cover:v3:' : 'synchro:favorite-image:v2:') + JSON.stringify([kind, key(artist), key(title), releaseGroupId, artist ? null : id]);
        const image = await findImage(options, active, {cacheKey});
        if (!active()) return;
        if (image) {
            styleImage(image, options);
            container.replaceChildren(image);
            try {
                sessionStorage.setItem(cacheKey, JSON.stringify({url: image.src, savedAt: Date.now()}));
            } catch {
                // The image remains visible when storage is unavailable.
            }
        } else {
            try { sessionStorage.removeItem(cacheKey); } catch { /* Optional cache. */ }
        }
    }

    async function* withPreferredImages(preferredImages, sources) {
        yield* asList(preferredImages);
        yield* sources;
    }

    window.SynchroFavoriteImages = {
        show,
        matchesTrack,
        confirmedTrackRelease,
        findAlternative: (options, active, excluded, currentUrl) => findImage(options, active, {excluded, currentUrl}),
        cancel: container => activeLoads.delete(container)
    };
})();
