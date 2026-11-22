
document.addEventListener("DOMContentLoaded", async () => {

    const params = new URLSearchParams(location.search);

    const album = params.get("album")?.trim();
    const artist = params.get("artist")?.trim();

    const nameEl = document.getElementById(
        "albumExploreName"
    );

    const artistEl = document.getElementById(
        "albumExploreArtist"
    );

    const detailsEl = document.getElementById(
        "albumExploreDetails"
    );

    const statusEl = document.getElementById(
        "albumExploreStatus"
    );

    const tracksEl = document.getElementById(
        "albumExploreTracks"
    );

    const coverEl = document.getElementById(
        "albumExploreCover"
    );

    if (!album || !artist) {
        nameEl.textContent = "Álbum não identificado";
        statusEl.textContent =
            "Informe o nome do álbum e do artista.";
        return;
    }

    nameEl.textContent = album;
    artistEl.textContent = artist;

    artistEl.href =
        "/frontend/pages/artista.html?artist=" +
        encodeURIComponent(artist);

    document.title = `${album} - Synchro`;

    // =========================================
    // CARREGAR DADOS DO LAST.FM
    // =========================================

    async function loadAlbum() {

        const url = new URL(
            (window.location.origin + "/last-fm/discovery")
        );

        url.searchParams.set("method", "album.getinfo");
        url.searchParams.set("album", album);
        url.searchParams.set("artist", artist);
        url.searchParams.set("autocorrect", "1");
        url.searchParams.set("format", "json");

        const response = await fetch(url);

        if (!response.ok) {
            throw new Error("Last.fm HTTP " + response.status);
        }

        const data = await response.json();

        if (data.error || !data.album) {
            throw new Error(
                data.message || "Álbum não encontrado."
            );
        }

        return data.album;
    }

    // =========================================
    // EXIBIR FAIXAS
    // =========================================

    function showTracks(data) {

        tracksEl.replaceChildren();

        const raw = data.tracks?.track || [];

        const tracks = Array.isArray(raw)
            ? raw
            : [raw];

        if (!tracks.length) {
            tracksEl.textContent =
                "O Last.fm não informou as faixas deste álbum.";
            return;
        }

        tracks.forEach((track, index) => {

            if (!track?.name) return;

            const link = document.createElement("a");

            link.className = "album-explore-track";

            const url = new URL(
                "/frontend/pages/musica.html",
                location.href
            );

            const trackArtist =
                typeof track.artist === "string"
                    ? track.artist
                    : track.artist?.name || artist;

            url.searchParams.set(
                "artist",
                trackArtist
            );

            url.searchParams.set(
                "track",
                track.name
            );


// Informa a pagina de musica de qual album
// o usuario veio.

            url.searchParams.set(
                "album",
                data.name || album
            );

// O artista principal do album pode ser
// diferente dos creditos de uma faixa.
            const mainAlbumArtist =
                typeof data.artist === "string"
                    ? data.artist
                    : data.artist?.name || artist;

            url.searchParams.set(
                "albumArtist",
                mainAlbumArtist
            );


            link.href = url.toString();

            const number = document.createElement("span");
            number.textContent = index + 1;

            const title = document.createElement("strong");
            title.textContent = track.name;

            link.append(number, title);

            tracksEl.appendChild(link);
        });
    }

    // =========================================
    // BUSCAR CAPA NAS FONTES DISPONÍVEIS
    // =========================================

    function loadCover(data) {
        const normalize = value => String(value || '').normalize('NFKC').trim().replace(/\s+/g, ' ').toLowerCase();
        const returnedArtist = typeof data?.artist === 'string' ? data.artist : data?.artist?.name;
        const sameAlbum = normalize(data?.name) === normalize(album) && normalize(returnedArtist) === normalize(artist);
        const preferredImages = sameAlbum && Array.isArray(data.image)
            ? data.image.slice().reverse().map(image => image?.['#text']) : [];

        window.SynchroFavoriteImages?.show(coverEl, {
            kind: 'album', title: album, artist, preferredImages, alt: `Capa de ${album}`
        });
    }

    let data;
    try {
        data = await loadAlbum();

        nameEl.textContent = data.name || album;

        artistEl.textContent =
            typeof data.artist === "string"
                ? data.artist
                : data.artist?.name || artist;

        const listeners = Number(data.listeners);
        const playcount = Number(data.playcount);

        detailsEl.textContent =
            "Ouvintes: " +
            (Number.isFinite(listeners)
                ? listeners.toLocaleString("pt-BR")
                : "—") +
            " • Reproduções: " +
            (Number.isFinite(playcount)
                ? playcount.toLocaleString("pt-BR")
                : "—");

        showTracks(data);
        statusEl.textContent = "";

    } catch (error) {
        console.error(
            "[Synchro] Erro ao carregar álbum:",
            error
        );

        statusEl.textContent =
            "Não foi possível carregar os detalhes do álbum.";
    }

    // A capa não depende do sucesso da consulta de faixas.
    loadCover(data);
});
