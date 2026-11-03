
(() => {
    "use strict";

    const PREFIX = "synchro:recent-cover:v1:";
    const DAY = 24 * 60 * 60 * 1000;
    const pending = new Map();

    function normalize(value) {
        return String(value || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .trim()
            .toLowerCase();
    }

    function validImage(value, type) {
        try {
            const url = new URL(value);
            if (url.protocol !== "https:") return false;

            const host = url.hostname;

            if (type === "Artista") {
                return host === "upload.wikimedia.org" ||
                    host === "commons.wikimedia.org";
            }

            return host === "coverartarchive.org" ||
                host === "archive.org" ||
                host.endsWith(".archive.org");

        } catch {
            return false;
        }
    }

    function cacheKey(item) {
        return PREFIX + encodeURIComponent(
            JSON.stringify([
                item.type,
                normalize(item.title),
                normalize(item.subtitle)
            ])
        );
    }

    function fromCache(item) {
        try {
            const raw = sessionStorage.getItem(
                cacheKey(item)
            );

            if (!raw) return null;

            const saved = JSON.parse(raw);

            if (
                Date.now() - saved.time > DAY ||
                !validImage(saved.url, item.type)
            ) {
                sessionStorage.removeItem(cacheKey(item));
                return null;
            }

            return saved.url;
        } catch {
            return null;
        }
    }

    function saveCache(item, url) {
        if (!validImage(url, item.type)) return;

        try {
            sessionStorage.setItem(
                cacheKey(item),
                JSON.stringify({
                    url,
                    time: Date.now()
                })
            );
        } catch {
            // O cache e opcional.
        }
    }

    async function getJson(url) {
        const response = await fetch(url, {
            headers: {
                Accept: "application/json"
            }
        });

        if (!response.ok) {
            throw new Error("HTTP " + response.status);
        }

        return response.json();
    }

    // =========================================
    // CAPAS DE ALBUNS E MUSICAS
    // =========================================


    async function findMusicCover(item) {

        let releaseGroupId = null;

        // Busca o identificador de um álbum pelo título
        // e artista, exigindo correspondência exata.
        async function findAlbumId(albumName, artistName) {
            const url = new URL(
                "/musicbrainz/album/search",
                location.origin
            );

            url.searchParams.set("album", albumName);
            url.searchParams.set("artist", artistName);

            const data = await getJson(url);

            const groups = Array.isArray(data["release-groups"])
                ? data["release-groups"]
                : [];

            const match = groups.find(group => {
                const credits = group["artist-credit"] || [];

                return group.id &&
                    normalize(group.title) ===
                    normalize(albumName) &&
                    group["primary-type"] === "Album" &&
                    credits.some(credit =>
                        normalize(
                            credit.artist?.name || credit.name
                        ) === normalize(artistName)
                    );
            });

            return match?.id || null;
        }

        if (item.type === "Música") {

            // 1. Reaproveitar a capa já identificada
            // na biblioteca de músicas favoritas.
            const favoriteKey =
                "synchro:track-cover:v1:" +
                encodeURIComponent(JSON.stringify([
                    item.subtitle.trim().toLowerCase(),
                    item.title.trim().toLowerCase()
                ]));

            try {
                const saved = JSON.parse(
                    sessionStorage.getItem(favoriteKey) || "null"
                );

                if (
                    saved &&
                    Date.now() - saved.savedAt <
                    24 * 60 * 60 * 1000 &&
                    validImage(saved.url, "Música")
                ) {
                    return saved.url;
                }
            } catch {
                // Continua a identificação normalmente.
            }

            // 2. Consultar o identificador habitual.
            try {
                const url = new URL(
                    "/musicbrainz/track/album",
                    location.origin
                );

                url.searchParams.set("artist", item.subtitle);
                url.searchParams.set("track", item.title);

                const data = await getJson(url);

                if (
                    data.status === "found" &&
                    data.album?.releaseGroupId
                ) {
                    releaseGroupId =
                        data.album.releaseGroupId;
                }

            } catch (error) {
                console.warn(
                    "[Synchro] Identificação da faixa:",
                    error
                );
            }

            // 3. Alternativa: Last.fm informa o álbum
            // ao qual a faixa está associada.
            if (!releaseGroupId) {

                try {
                    const url = new URL(
                        "https://ws.audioscrobbler.com/2.0/"
                    );

                    url.searchParams.set(
                        "method", "track.getinfo"
                    );
                    url.searchParams.set(
                        "track", item.title
                    );
                    url.searchParams.set(
                        "artist", item.subtitle
                    );
                    url.searchParams.set(
                        "api_key",
                        "7c62ff4c30df11f623f6ecd649c5d104"
                    );
                    url.searchParams.set("autocorrect", "1");
                    url.searchParams.set("format", "json");

                    const data = await getJson(url);
                    const album = data.track?.album;

                    if (album?.title) {
                        const albumArtist =
                            typeof album.artist === "string"
                                ? album.artist
                                : album.artist?.name ||
                                item.subtitle;

                        releaseGroupId = await findAlbumId(
                            album.title,
                            albumArtist
                        );
                    }

                } catch (error) {
                    console.warn(
                        "[Synchro] Last.fm não informou álbum:",
                        error
                    );
                }
            }

        } else if (item.type === "Álbum") {

            releaseGroupId = await findAlbumId(
                item.title,
                item.subtitle
            );
        }

        if (!releaseGroupId) return null;

        const coverData = await getJson(
            "/cover-art/release-group/" +
            encodeURIComponent(releaseGroupId)
        );

        return validImage(coverData.coverUrl, item.type)
            ? coverData.coverUrl
            : null;
    }

    // =========================================
    // FOTOGRAFIAS DOS ARTISTAS
    // =========================================


    async function findArtistPhoto(item) {

        const name = item.title;

        // ======================================
        // 1. PRIMEIRA TENTATIVA: WIKIPEDIA
        // ======================================

        for (const lang of ["pt", "en"]) {

            try {
                const url = new URL(
                    `https://${lang}.wikipedia.org/w/api.php`
                );

                url.searchParams.set("action", "query");
                url.searchParams.set("titles", name);
                url.searchParams.set("prop", "pageimages");
                url.searchParams.set("piprop", "thumbnail");
                url.searchParams.set("pithumbsize", "500");
                url.searchParams.set("redirects", "1");
                url.searchParams.set("format", "json");
                url.searchParams.set("formatversion", "2");
                url.searchParams.set("origin", "*");

                const data = await getJson(url);
                const page = data.query?.pages?.[0];

                if (!page || page.missing) continue;

                const requested = normalize(name);
                const actual = normalize(page.title);

                const redirected = (
                    data.query?.redirects || []
                ).some(entry =>
                    normalize(entry.from) === requested &&
                    normalize(entry.to) === actual
                );

                if (actual !== requested && !redirected) {
                    continue;
                }

                if (validImage(page.thumbnail?.source, "Artista")) {
                    return page.thumbnail.source;
                }

            } catch (error) {
                console.warn(
                    "[Synchro] Falha na Wikipedia:",
                    name,
                    error
                );
            }
        }

        // ======================================
        // 2. ALTERNATIVA: WIKIDATA
        // ======================================

        const musicalDescription =
            /singer|songwriter|rapper|musician|vocalist|band|musical group|record producer|cantor|cantora|rapper|banda|musico|compositor/i;

        for (const language of ["pt", "en"]) {

            try {
                const searchUrl = new URL(
                    "https://www.wikidata.org/w/api.php"
                );

                searchUrl.searchParams.set(
                    "action", "wbsearchentities"
                );
                searchUrl.searchParams.set("search", name);
                searchUrl.searchParams.set(
                    "language", language
                );
                searchUrl.searchParams.set(
                    "type", "item"
                );
                searchUrl.searchParams.set("limit", "10");
                searchUrl.searchParams.set("format", "json");
                searchUrl.searchParams.set("origin", "*");

                const searchData = await getJson(searchUrl);

                const candidates = (
                    searchData.search || []
                ).filter(candidate =>
                    normalize(candidate.label) ===
                    normalize(name) &&
                    musicalDescription.test(
                        candidate.description || ""
                    )
                );

                for (const candidate of candidates) {

                    const entityUrl = new URL(
                        "https://www.wikidata.org/w/api.php"
                    );

                    entityUrl.searchParams.set(
                        "action", "wbgetentities"
                    );
                    entityUrl.searchParams.set(
                        "ids", candidate.id
                    );
                    entityUrl.searchParams.set(
                        "props",
                        "labels|descriptions|claims"
                    );
                    entityUrl.searchParams.set(
                        "languages", "pt|en"
                    );
                    entityUrl.searchParams.set(
                        "format", "json"
                    );
                    entityUrl.searchParams.set(
                        "origin", "*"
                    );

                    const entityData = await getJson(entityUrl);
                    const entity =
                        entityData.entities?.[candidate.id];

                    if (!entity) continue;

                    const labels = [
                        entity.labels?.pt?.value,
                        entity.labels?.en?.value
                    ];

                    const descriptions = [
                        entity.descriptions?.pt?.value,
                        entity.descriptions?.en?.value
                    ].filter(Boolean).join(" ");

                    if (
                        !labels.some(label =>
                            normalize(label) === normalize(name)
                        ) ||
                        !musicalDescription.test(descriptions)
                    ) {
                        continue;
                    }

                    const claims = entity.claims?.P18 || [];

                    const photo = claims.find(claim =>
                        claim.rank !== "deprecated" &&
                        typeof claim.mainsnak
                            ?.datavalue?.value === "string"
                    );

                    const filename =
                        photo?.mainsnak?.datavalue?.value;

                    if (!filename) continue;

                    return (
                        "https://commons.wikimedia.org/wiki/" +
                        "Special:FilePath/" +
                        encodeURIComponent(filename) +
                        "?width=500"
                    );
                }

            } catch (error) {
                console.warn(
                    "[Synchro] Falha no Wikidata:",
                    name,
                    error
                );
            }
        }

        return null;
    }


    // =========================================
    // CARREGAMENTO E EXIBICAO
    // =========================================

    async function resolveCover(item) {

        const saved = fromCache(item);
        if (saved) return saved;

        const key = cacheKey(item);

        if (pending.has(key)) {
            return pending.get(key);
        }

        const operation = (
            item.type === "Artista"
                ? findArtistPhoto(item)
                : findMusicCover(item)
        ).catch(error => {
            console.warn(
                "[Synchro] Capa recente indisponível:",
                item.title,
                error
            );
            return null;
        }).finally(() => {
            pending.delete(key);
        });

        pending.set(key, operation);

        return operation;
    }

    async function load(item, container) {

        const imageUrl = await resolveCover(item);

        if (!imageUrl || !container.isConnected) {
            return;
        }

        const img = new Image();

        img.className = "recentes-image";
        img.alt = `Imagem de ${item.title}`;
        img.decoding = "async";

        img.onload = () => {
            if (!container.isConnected) return;

            saveCache(item, imageUrl);
            container.replaceChildren(img);
        };

        img.onerror = () => {
            try {
                sessionStorage.removeItem(
                    cacheKey(item)
                );
            } catch {}
        };

        img.src = imageUrl;
    }

    window.SynchroRecentCover = { load };
})();
