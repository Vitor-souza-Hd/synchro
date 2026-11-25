
const API_URL = (window.location.origin + "/last-fm/discovery");

document.addEventListener("synchro:song", ({detail: {params, song}}) => {

    // =========================================
    // IDENTIFICAR MÚSICA E ARTISTA
    // =========================================

    

    const trackName = params.get("track")?.trim();
    const artistName = params.get("artist")?.trim();
    const sourceAlbum = params.get("album")?.trim();
    const sourceAlbumArtist = params.get("albumArtist")?.trim();
    let trackInfoPromise;
    let coverStarted = false;

    function consultarFaixa() {
        return trackInfoPromise ||= consultarLastFm("track.getInfo", {
            artist: artistName, track: trackName, preview: "true"
        });
    }

    const musicNameElement =
        document.getElementById("musicName");

    const biographyElement =
        document.getElementById("musicBiography");

    const albumElement =
        document.getElementById("musicAlbum");

    // =========================================
    // MENU RESPONSIVO
    // =========================================

    const menuToggle = document.querySelector(".menu-toggle");
    const sidebar = document.querySelector(".sidebar");

    if (menuToggle && sidebar) {
        menuToggle.addEventListener("click", () => {
            sidebar.classList.toggle("active");
        });
    }

    // =========================================
    // VALIDAR PARÂMETROS
    // =========================================

    if (song) {
        renderCatalogSong(song);
        // Mostra primeiro o catálogo local. A atualização externa pode falhar ou demorar.
        atualizarCatalogo(song.id);
        return;
    }
    if (!trackName || !artistName) {

        musicNameElement.textContent =
            "Música não encontrada";

        biographyElement.textContent =
            "Informe uma música e um artista para visualizar os detalhes.";

        return;
    }

    renderTrackCover();

    // Dados do Last.fm e identificação do álbum
    // são carregados de maneira independente.
    carregarMusica();

    // =========================================
    // CONSULTAR LAST.FM
    // =========================================

    function renderTrackCover(item) {
        const title = item?.titulo || trackName;
        const artist = artistName || item?.lastFm?.artistName || item?.artistas?.[0]?.nome;
        const image = document.getElementById('musicImage');
        const albumHint = sourceAlbum ? {title: sourceAlbum, artist: sourceAlbumArtist || artist} : null;
        const options = {kind: 'track', id: item?.id, title, artist, albumHint, alt: 'Capa de ' + title, imageId: 'musicImage'};
        window.SynchroCatalogImage.bind(options, () => document.getElementById('musicImage'));
        if (!coverStarted) {
            coverStarted = true;
            window.SynchroFavoriteImages.show(image.parentElement, options);
        }
        const release = window.SynchroFavoriteImages.confirmedTrackRelease(title, artist);
        if (release) {
            const link = document.createElement('a');
            link.href = 'album-explorar.html?' + new URLSearchParams({album: release.title, artist: release.artist});
            link.textContent = release.title;
            albumElement.replaceChildren(link);
        }
        return release;
    }

    function renderCatalogSong(item) {
        const confirmedRelease = renderTrackCover(item);
        musicNameElement.textContent = item.titulo;
        musicNameElement.dataset.songId = String(item.id);
        document.title = item.titulo + ' | Synchro';
        const artists = document.getElementById('musicArtist');
        artists.textContent = artistName || 'Artista não informado';
        if (artistName) artists.href = 'artista.html?artist=' + encodeURIComponent(artistName);
        else artists.removeAttribute('href');
        if (!confirmedRelease && item.album) {
            const link = document.createElement('a');
            link.href = 'album.html?id=' + encodeURIComponent(item.album.id);
            link.textContent = item.album.titulo;
            albumElement.replaceChildren(link);
        } else if (!confirmedRelease) albumElement.textContent = 'Álbum não informado';
        const meta = item.lastFm || {};
        document.getElementById('musicListeners').textContent = formatarNumero(meta.listeners);
        document.getElementById('musicPlaycount').textContent = formatarNumero(meta.playcount);
        const duration = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?$/.exec(item.duracao || '');
        document.getElementById('musicDuration').textContent = duration ? formatarDuracao(
            1000 * (Number(duration[1] || 0)*3600 + Number(duration[2] || 0)*60 + Number(duration[3] || 0))) : 'Não informada';
        const tags = meta.tags?.length ? meta.tags : (item.genero || '').split(',').map(tag=>tag.trim()).filter(Boolean);
        carregarTags(tags.map(name=>({name})));
        biographyElement.textContent = limparTexto(item.descricao) || 'Descrição não disponível no catálogo.';
        const lastfm = document.getElementById('lastfmLink');
        if (urlLastFmValida(meta.url)) { lastfm.href = meta.url; lastfm.hidden = false; }
    }

    async function atualizarCatalogo(id) {
        try {
            const path = '/catalog/songs/' + encodeURIComponent(id);
            if ((await fetch(path + '/refresh', {method: 'POST'})).ok) {
                const response = await fetch(path);
                if (response.ok) renderCatalogSong(await response.json());
            }
        } catch { /* Os dados já apresentados continuam disponíveis. */ }
    }

    async function consultarLastFm(method, parametros = {}) {

        const url = new URL(API_URL);

        url.searchParams.set("method", method);
        url.searchParams.set("format", "json");
        url.searchParams.set("autocorrect", "1");

        Object.entries(parametros).forEach(([chave, valor]) => {
            url.searchParams.set(chave, valor);
        });

        const response = await fetch(url);

        if (!response.ok) {
            throw new Error(`Erro HTTP ${response.status}`);
        }

        const data = await response.json();

        if (data.error) {
            throw new Error(
                data.message || "Erro na API do Last.fm"
            );
        }

        return data;
    }

    // =========================================
    // CARREGAR INFORMAÇÕES DO LAST.FM
    // =========================================

    async function carregarMusica() {

        try {

            const data = await consultarFaixa();

            const track = data.track;

            if (!window.SynchroFavoriteImages.matchesTrack(track, trackName, artistName)) {
                throw new Error("Música não encontrada");
            }

            const nome = track.name || trackName;

            const artista =
                typeof track.artist === "string"
                    ? track.artist
                    : track.artist?.name || artistName;

            // TÍTULO
            musicNameElement.textContent = nome;

            document.title = `${nome} - Synchro`;

            // ARTISTA
            const artistElement =
                document.getElementById("musicArtist");

            artistElement.textContent = artista;

            artistElement.href =
                `/frontend/pages/artista.html?artist=${encodeURIComponent(artista)}`;

            if (!window.SynchroFavoriteImages.confirmedTrackRelease(trackName, artistName) && track.album?.title) {
                albumElement.textContent = track.album.title;
            }

            // ESTATÍSTICAS
            document.getElementById("musicListeners").textContent =
                formatarNumero(track.listeners);

            document.getElementById("musicPlaycount").textContent =
                formatarNumero(track.playcount);

            document.getElementById("musicDuration").textContent =
                formatarDuracao(track.duration);

            // GÊNEROS
            carregarTags(track.toptags?.tag);

            // DESCRIÇÃO
            biographyElement.textContent =
                limparTexto(track.wiki?.summary) ||
                "O Last.fm ainda não disponibilizou uma descrição para esta música.";

            // LINK DO LAST.FM
            const lastfmLink =
                document.getElementById("lastfmLink");

            if (urlLastFmValida(track.url)) {
                lastfmLink.href = track.url;
                lastfmLink.hidden = false;
            }

        } catch (error) {

            console.error(
                "Erro ao carregar música pelo Last.fm:",
                error
            );

            musicNameElement.textContent = trackName;
            document.getElementById("musicArtist").textContent = artistName;

            biographyElement.textContent =
                "Não foi possível carregar as informações da música. Verifique o nome e tente novamente.";
        }
    }

    // =========================================
    // GÊNEROS MUSICAIS
    // =========================================

    function carregarTags(tags) {

        const container =
            document.getElementById("musicTags");

        container.replaceChildren();

        const lista = Array.isArray(tags)
            ? tags
            : tags ? [tags] : [];

        lista.slice(0, 5).forEach(tag => {

            if (!tag.name) {
                return;
            }

            const span = document.createElement("span");

            span.textContent = tag.name;

            container.appendChild(span);
        });
    }

    // =========================================
    // FORMATAR NÚMEROS
    // =========================================

    function formatarNumero(numero) {

        if (
            numero === undefined ||
            numero === null ||
            numero === ""
        ) {
            return "—";
        }

        const valor = Number(numero);

        if (!Number.isFinite(valor)) {
            return "—";
        }

        return valor.toLocaleString("pt-BR");
    }

    // =========================================
    // FORMATAR DURAÇÃO
    // =========================================

    function formatarDuracao(milissegundos) {

        const duracao = Number(milissegundos);

        if (!Number.isFinite(duracao) || duracao <= 0) {
            return "Não informada";
        }

        const segundos = Math.floor(duracao / 1000);

        const minutos = Math.floor(segundos / 60);
        const resto = segundos % 60;

        return `${minutos}:${String(resto).padStart(2, "0")}`;
    }

    // =========================================
    // LIMPAR TEXTO DA BIOGRAFIA
    // =========================================

    function limparTexto(texto) {

        if (!texto) {
            return "";
        }

        const elemento = new DOMParser().parseFromString(texto, "text/html").body;

        elemento.querySelectorAll("script, style").forEach(
            item => item.remove()
        );

        return (elemento.textContent || "").trim();
    }

    // =========================================
    // VALIDAR LINK DO LAST.FM
    // =========================================

    function urlLastFmValida(url) {

        if (typeof url !== "string") {
            return false;
        }

        try {

            const endereco = new URL(url);

            return (
                endereco.protocol === "https:" &&
                (
                    endereco.hostname === "last.fm" ||
                    endereco.hostname.endsWith(".last.fm")
                )
            );

        } catch {
            return false;
        }
    }

});
