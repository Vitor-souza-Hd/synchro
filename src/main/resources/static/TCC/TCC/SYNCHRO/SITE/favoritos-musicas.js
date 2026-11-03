
document.addEventListener("DOMContentLoaded", () => {

    const grid = document.getElementById("favoriteTracksGrid");
    const feedback = document.getElementById("favoriteTracksFeedback");
    const counter = document.getElementById("favoriteTracksCount");

    if (!grid || !feedback || !counter) {
        console.warn("Elementos da biblioteca não encontrados.");
        return;
    }

    const coverCache = new Map();

    // =========================================
    // CACHE DAS CAPAS DE MUSICAS
    // =========================================

    const CACHE_PREFIX = "synchro:track-cover:v1:";
    const CACHE_DURATION = 24 * 60 * 60 * 1000;

    function coverKey(track, artist) {
        return CACHE_PREFIX +
            encodeURIComponent(
                JSON.stringify([
                    artist.trim().toLowerCase(),
                    track.trim().toLowerCase()
                ])
            );
    }

    function readSavedCover(track, artist) {

        const key = coverKey(track, artist);

        try {
            const saved = sessionStorage.getItem(key);

            if (!saved) return null;

            const data = JSON.parse(saved);

            if (
                !data.savedAt ||
                Date.now() - data.savedAt > CACHE_DURATION ||
                !validCoverUrl(data.url)
            ) {
                sessionStorage.removeItem(key);
                return null;
            }

            return data.url;

        } catch {
            return null;
        }
    }

    function saveCover(track, artist, url) {

        if (!validCoverUrl(url)) return;

        try {
            sessionStorage.setItem(
                coverKey(track, artist),
                JSON.stringify({
                    url,
                    savedAt: Date.now()
                })
            );
        } catch {
            // Falha de armazenamento nao impede o site.
        }
    }

    function removeSavedCover(track, artist) {

        try {
            sessionStorage.removeItem(
                coverKey(track, artist)
            );
        } catch {
            // Cache opcional.
        }
    }


    function showMessage(text) {
        feedback.textContent = text;
        feedback.hidden = false;
    }

    function validCoverUrl(value) {
        try {
            const url = new URL(value);

            return url.protocol === "https:" &&
                (
                    url.hostname === "coverartarchive.org" ||
                    url.hostname === "archive.org" ||
                    url.hostname.endsWith(".archive.org")
                );
        } catch {
            return false;
        }
    }

    // =========================================
    // BUSCAR CAPA DA MÚSICA
    // =========================================


    async function findCover(track, artist) {

        // Primeiro tenta a capa salva na sessao.
        const savedCover = readSavedCover(track, artist);

        if (savedCover) {
            console.log(
                "[Synchro] Capa recuperada do cache:",
                track
            );

            return savedCover;
        }

        const key =
            `${artist.toLowerCase()}::${track.toLowerCase()}`;

        // Evita requisicoes duplicadas
        // durante o carregamento da pagina.
        if (coverCache.has(key)) {
            return coverCache.get(key);
        }


        const operation = (async () => {

            const albumUrl = new URL(
                "/musicbrainz/track/album",
                window.location.origin
            );

            albumUrl.searchParams.set("artist", artist);
            albumUrl.searchParams.set("track", track);

            const albumResponse = await fetch(albumUrl);

            if (!albumResponse.ok) {
                throw new Error(
                    "MusicBrainz HTTP " + albumResponse.status
                );
            }

            const albumData = await albumResponse.json();

            console.log(
                "[Synchro] Álbum identificado:",
                track,
                albumData
            );

            const id = albumData.album?.releaseGroupId;

            if (albumData.status !== "found" || !id) {
                return null;
            }

            const coverResponse = await fetch(
                "/cover-art/release-group/" +
                encodeURIComponent(id)
            );

            if (!coverResponse.ok) {
                throw new Error(
                    "Cover Art HTTP " + coverResponse.status
                );
            }

            const coverData = await coverResponse.json();

            console.log(
                "[Synchro] Resultado da capa:",
                track,
                coverData
            );

            if (validCoverUrl(coverData.coverUrl)) {
                return coverData.coverUrl;
            }

            console.warn(
                "[Synchro] URL de capa ausente ou inválida:",
                coverData
            );

            return null;

        })().catch(error => {
            console.warn(
                "[Synchro] Erro ao localizar capa:",
                track,
                error
            );

            return null;
        });

        coverCache.set(key, operation);
        return operation;
    }

    // =========================================
    // INSERIR A CAPA NO CARTÃO
    // =========================================


    async function loadCover(cover, track, artist) {

        const imageUrl = await findCover(track, artist);

        if (!imageUrl || !cover.isConnected) {
            console.info(
                "[Synchro] Mantendo ícone musical:",
                track
            );
            return;
        }

        // Usamos Image() sem lazy loading,
        // pois a imagem ainda nao esta no DOM.
        const img = new Image();

        img.className = "favorite-track-image";
        img.alt = `Capa de ${track}`;
        img.decoding = "async";

        img.style.width = "100%";
        img.style.height = "100%";
        img.style.objectFit = "cover";
        img.style.display = "block";


        img.onload = () => {

            if (!cover.isConnected) return;

            // Salva apenas depois de carregar a imagem.
            saveCover(track, artist, imageUrl);

            cover.replaceChildren(img);

            console.log(
                "[Synchro] Capa carregada:",
                track,
                img.naturalWidth,
                img.naturalHeight
            );
        };



        img.onerror = () => {

            // Remove o endereco salvo se a imagem falhar.
            removeSavedCover(track, artist);

            console.warn(
                "[Synchro] Falha ao carregar capa:",
                track
            );
        };


        // Atribuir a URL por ultimo,
        // depois de configurar os eventos.
        img.src = imageUrl;
    }


    // =========================================
    // CRIAR CARTÃO DE MÚSICA FAVORITA
    // =========================================

    function renderTrack(item) {

        if (
            !item ||
            typeof item.track !== "string" ||
            typeof item.artist !== "string" ||
            !item.track.trim() ||
            !item.artist.trim()
        ) {
            return;
        }

        const card = document.createElement("article");
        card.className = "favorite-card favorite-track-card";

        const link = document.createElement("a");
        link.className = "favorite-card-link";

        const url = new URL(
            "musica.html",
            window.location.href
        );

        url.searchParams.set("track", item.track);
        url.searchParams.set("artist", item.artist);

        link.href = url.toString();

        const cover = document.createElement("div");
        cover.className = "favorite-track-cover";

        const icon = document.createElement("i");
        icon.className = "bx bx-music";

        cover.appendChild(icon);

        const info = document.createElement("div");
        info.className = "favorite-card-info";

        const title = document.createElement("h2");
        title.textContent = item.track;

        const artist = document.createElement("p");
        artist.textContent = item.artist;

        const type = document.createElement("span");
        type.className = "favorite-track-type";
        type.textContent = "MÚSICA";

        info.append(title, artist, type);
        link.append(cover, info);
        card.appendChild(link);

        grid.appendChild(card);

        // O cartão já está no DOM antes de iniciar
        // o carregamento da imagem.
        loadCover(cover, item.track, item.artist);
    }

    // =========================================
    // CARREGAR FAVORITOS DO BACKEND
    // =========================================

    async function loadTracks() {

        showMessage("Carregando músicas favoritas...");

        try {

            const response = await fetch(
                "/favorites/tracks",
                {
                    credentials: "same-origin",
                    headers: {
                        Accept: "application/json"
                    }
                }
            );

            if (
                response.status === 401 ||
                response.status === 403
            ) {
                showMessage(
                    "Faça login para visualizar suas músicas favoritas."
                );
                return;
            }

            if (!response.ok) {
                throw new Error(
                    "Favoritos HTTP " + response.status
                );
            }

            const items = await response.json();

            if (!Array.isArray(items)) {
                throw new Error("Resposta inesperada.");
            }

            grid.replaceChildren();

            const validItems = items.filter(item =>
                typeof item?.track === "string" &&
                item.track.trim() &&
                typeof item?.artist === "string" &&
                item.artist.trim()
            );

            counter.textContent =
                `${validItems.length} ${
                    validItems.length === 1
                        ? "música salva"
                        : "músicas salvas"
                }`;

            if (!validItems.length) {
                showMessage(
                    "Você ainda não favoritou nenhuma música."
                );
                return;
            }

            validItems.forEach(renderTrack);

            feedback.hidden = true;

        } catch (error) {

            console.error(
                "[Synchro] Erro nos favoritos:",
                error
            );

            showMessage(
                "Não foi possível carregar suas músicas favoritas."
            );
        }
    }

    loadTracks();
});
