
const API_KEY = "7c62ff4c30df11f623f6ecd649c5d104";
const API_URL = "https://ws.audioscrobbler.com/2.0/";

document.addEventListener("DOMContentLoaded", () => {

    // =========================================
    // IDENTIFICAR MÚSICA E ARTISTA
    // =========================================

    const params = new URLSearchParams(window.location.search);

    const trackName = params.get("track")?.trim();
    const artistName = params.get("artist")?.trim();

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

    if (!trackName || !artistName) {

        musicNameElement.textContent =
            "Música não encontrada";

        biographyElement.textContent =
            "Informe uma música e um artista para visualizar os detalhes.";

        return;
    }

    // Dados do Last.fm e identificação do álbum
    // são carregados de maneira independente.
    carregarMusica();
    carregarAlbumMusicBrainz();

    // =========================================
    // CONSULTAR LAST.FM
    // =========================================

    async function consultarLastFm(method, parametros = {}) {

        const url = new URL(API_URL);

        url.searchParams.set("method", method);
        url.searchParams.set("api_key", API_KEY);
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
    // CONSULTAR ENDPOINT DO SYNCHRO
    // =========================================

    async function consultarSynchro(caminho, parametros = {}) {

        const url = new URL(caminho, window.location.origin);

        Object.entries(parametros).forEach(([chave, valor]) => {
            url.searchParams.set(chave, valor);
        });

        const response = await fetch(url, {
            headers: {
                "Accept": "application/json"
            }
        });

        if (!response.ok) {
            throw new Error(
                `Erro ao consultar ${caminho}: HTTP ${response.status}`
            );
        }

        return response.json();
    }

    // =========================================
    // CARREGAR INFORMAÇÕES DO LAST.FM
    // =========================================

    async function carregarMusica() {

        try {

            const data = await consultarLastFm("track.getInfo", {
                artist: artistName,
                track: trackName
            });

            const track = data.track;

            if (!track) {
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
                `artista.html?artist=${encodeURIComponent(artista)}`;

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

            musicNameElement.textContent =
                "Erro ao carregar música";

            biographyElement.textContent =
                "Não foi possível carregar as informações da música. Verifique o nome e tente novamente.";
        }
    }

    // =========================================
    // IDENTIFICAR ÁLBUM PELO MUSICBRAINZ
    // =========================================

    async function carregarAlbumMusicBrainz() {

        albumElement.textContent = "Identificando álbum...";
        limparImagem();

        try {

            const resultado = await consultarSynchro(
                "/musicbrainz/track/album",
                {
                    artist: artistName,
                    track: trackName
                }
            );

            if (
                resultado.status !== "found" ||
                !resultado.album?.releaseGroupId ||
                !resultado.album?.title
            ) {

                albumElement.textContent =
                    "Álbum de estúdio não identificado";

                return;
            }

            const album = resultado.album;

            // Exibe o álbum selecionado pelo backend.
            albumElement.textContent =
                `Álbum associado: ${album.title}`;

            // Busca a capa pelo identificador MusicBrainz.
            await carregarCapaCoverArtArchive(
                album.releaseGroupId
            );

        } catch (error) {

            console.warn(
                "Não foi possível identificar o álbum:",
                error
            );

            // Erros externos não devem impedir
            // a exibição das informações da música.
            albumElement.textContent =
                "Álbum indisponível no momento";
        }
    }

    // =========================================
    // BUSCAR CAPA PELO BACKEND
    // =========================================

    async function carregarCapaCoverArtArchive(releaseGroupId) {

        try {

            const response = await fetch(
                `/cover-art/release-group/${encodeURIComponent(releaseGroupId)}`,
                {
                    headers: {
                        "Accept": "application/json"
                    }
                }
            );

            if (!response.ok) {
                throw new Error(
                    `Erro ao buscar capa: HTTP ${response.status}`
                );
            }

            const resultado = await response.json();

            if (
                resultado.status === "found" &&
                urlCapaValida(resultado.coverUrl)
            ) {

                exibirImagem(resultado.coverUrl);

            } else {

                console.info(
                    "Capa não disponível para:",
                    releaseGroupId
                );
            }

        } catch (error) {

            console.warn(
                "Não foi possível carregar a capa:",
                error
            );
        }
    }

    // =========================================
    // VALIDAR URL DA CAPA
    // =========================================

    function urlCapaValida(url) {

        if (typeof url !== "string") {
            return false;
        }

        try {

            const endereco = new URL(url);

            return (
                endereco.protocol === "https:" &&
                (
                    endereco.hostname === "coverartarchive.org" ||
                    endereco.hostname === "archive.org" ||
                    endereco.hostname.endsWith(".archive.org")
                )
            );

        } catch {
            return false;
        }
    }

    // =========================================
    // LIMPAR IMAGEM
    // =========================================

    function limparImagem() {

        const img =
            document.getElementById("musicImage");

        const placeholder =
            document.getElementById("musicPlaceholder");

        img.hidden = true;
        img.removeAttribute("src");

        placeholder.hidden = false;
    }

    // =========================================
    // EXIBIR CAPA DO ÁLBUM
    // =========================================

    function exibirImagem(url) {

        const img =
            document.getElementById("musicImage");

        const placeholder =
            document.getElementById("musicPlaceholder");

        img.onload = () => {

            img.hidden = false;
            placeholder.hidden = true;
        };

        img.onerror = () => {

            img.hidden = true;
            placeholder.hidden = false;

            console.warn(
                "O navegador não conseguiu carregar a capa:",
                url
            );
        };

        img.src = url;
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

        const elemento = document.createElement("div");
        elemento.innerHTML = texto;

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
