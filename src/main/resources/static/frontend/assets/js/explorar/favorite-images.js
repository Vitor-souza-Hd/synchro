(() => {
    const images = '/frontend/assets/images/';
    const catalogRequests = new Map();
    const cacheAge = 24 * 60 * 60 * 1000;

    async function json(path, params = {}) {
        const url = new URL(path, location.origin);
        url.search = new URLSearchParams(params);
        try {
            const response = await fetch(url, {signal: AbortSignal.timeout(6000)});
            return response.ok ? await response.json() : null;
        } catch {
            return null;
        }
    }

    function key(value) {
        return value.trim().replace(/\s+/g, ' ').toLowerCase();
    }

    function catalogFor(artist) {
        const name = key(artist);
        if (!catalogRequests.has(name)) {
            catalogRequests.set(name, json('/catalog/artists/by-name', {name: artist}));
        }
        return catalogRequests.get(name);
    }

    function validImage(value) {
        try {
            const url = new URL(value);
            return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password &&
                !/placeholder|2a96cbd8b46e442fc41c2b86b821562f|placehold\.co/i.test(url.href);
        } catch {
            return false;
        }
    }

    function loadImage(url) {
        return new Promise(resolve => {
            const image = new Image();
            image.decoding = "async";
            const timer = setTimeout(() => finish(null), 5000);
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
        return (Array.isArray(item?.image) ? item.image : []).slice().reverse().map(image => image['#text']);
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

    async function* artistPhotos(artist, id) {
        const saved = (id ? await json("/catalog/artists/" + id) : await catalogFor(artist))?.artist;
        yield saved?.imgUrl;
        yield saved?.lastFm?.imageUrl;
        if (saved?.id) {
            const photo = await json('/catalog/artists/' + encodeURIComponent(saved.id) + '/photo');
            yield photo?.imgUrl;
        }
        for (const lang of ['pt', 'en']) {
            yield await wikiPhoto(artist, lang).catch(() => null);
        }
    }

    async function* covers(kind, title, artist, releaseGroupId, id) {
        let item;
        if (id) {
            item = kind === "track" ? await json("/catalog/songs/" + id) : (await json("/catalog/albums/" + id))?.album;
        }
        if (!item && artist) {
            const catalog = await catalogFor(artist);
            const items = kind === "track" ? catalog?.musicas : catalog?.albums;
            item = items?.find(entry => key(entry.titulo) === key(title));
        }
        yield item?.lastFm?.imageUrl;
        yield item?.album?.imgUrl;

        artist = item?.lastFm?.artistName || item?.artistas?.[0]?.nome || artist;
        title = item?.titulo || title;
        if (!artist) return;

        if (kind === 'track') {
            const result = await json('/musicbrainz/track/album', {artist, track: title});
            releaseGroupId = result?.album?.releaseGroupId;
        } else if (!releaseGroupId) {
            const result = await json("/musicbrainz/album/search", {album: title, artist});
            const groups = result?.["release-groups"] || [];
            releaseGroupId = groups.find(group => key(group.title || "") === key(title) &&
                group["artist-credit"]?.some(credit => key(credit.artist?.name || credit.name || "") === key(artist)))?.id;
        }
        if (releaseGroupId) {
            const result = await json('/cover-art/release-group/' + encodeURIComponent(releaseGroupId));
            yield result?.coverUrl;
        }

        if (kind === 'album') {
            const result = await json('/last-fm/discovery', {method: 'album.getInfo', artist, album: title});
            yield* lastFmImages(result?.album);
        } else {
            const result = await json('/last-fm/discovery', {method: 'track.getInfo', artist, track: title});
            const album = result?.track?.album;
            yield* lastFmImages(album);
            const albumTitle = album?.title || item?.album?.titulo;
            if (albumTitle) {
                const albumArtist = typeof album?.artist === 'string' ? album.artist : album?.artist?.name;
                const result = await json('/last-fm/discovery', {
                    method: 'album.getInfo', artist: albumArtist || artist, album: albumTitle
                });
                yield* lastFmImages(result?.album);
            }
        }
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

    function show(container, options) {
        const {kind, className, alt} = options;
        const fallback = document.createElement('img');
        fallback.className = className || '';
        fallback.alt = alt;
        fallback.src = images + (kind === 'artist' ? 'artist-placeholder.svg' : 'catalog-placeholder.svg');
        container.replaceChildren(fallback);

        const load = () => resolveImage(container, options);
        const scheduler = window.SynchroImageVisibility;
        if (scheduler) scheduler.whenNear(container, load);
        else load();
    }

    async function resolveImage(container, {kind, title, artist, releaseGroupId, id, className, alt, preferredImages = []}) {
        const cacheKey = 'synchro:favorite-image:' + JSON.stringify([kind, key(artist), key(title), releaseGroupId, artist ? null : id]);
        const seen = new Set();
        const catalogId = /^\d+$/.test(id || "") ? id : null;
        const sources = kind === "artist" ? artistPhotos(artist, catalogId) : covers(kind, title, artist, releaseGroupId, catalogId);
        for await (const url of candidates(cacheKey, withPreferredImages(preferredImages, sources))) {
            if (!container.isConnected) return;
            if (!validImage(url) || seen.has(url)) continue;
            seen.add(url);
            const image = await loadImage(url);
            if (!image) continue;
            if (!container.isConnected) return;
            image.className = className || '';
            image.alt = alt;
            container.replaceChildren(image);
            try {
                sessionStorage.setItem(cacheKey, JSON.stringify({url, savedAt: Date.now()}));
            } catch {
                // The image remains visible when storage is unavailable.
            }
            return;
        }
        try { sessionStorage.removeItem(cacheKey); } catch { /* Optional cache. */ }
    }

    async function* withPreferredImages(preferredImages, sources) {
        yield* preferredImages;
        yield* sources;
    }

    window.SynchroFavoriteImages = {show};
})();
