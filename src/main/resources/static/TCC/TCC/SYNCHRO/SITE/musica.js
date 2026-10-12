
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

    carregarMusica();

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
    // CARREGAR INFORMAÇÕES
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

            // ÁLBUM
            const albumElement =
                document.getElementById("musicAlbum");

            if (track.album?.title) {
                albumElement.textContent =
                    `Álbum: ${track.album.title}`;
            } else {
                albumElement.textContent =
                    "Álbum não informado";
            }

            // CAPA
            carregarImagem(track.album?.image);

            // ESTATÍSTICAS
            document.getElementById("musicListeners").textContent =
                formatarNumero(track.listeners);

            document.getElementById("musicPlaycount").textContent =
                formatarNumero(track.playcount);

            document.getElementById("musicDuration").textContent =
                formatarDuracao(track.duration);

            // GÊNEROS
            carregarTags(track.toptags?.tag);

            // BIOGRAFIA
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
                "Erro ao carregar música:",
                error
            );

            musicNameElement.textContent =
                "Erro ao carregar música";

            biographyElement.textContent =
                "Não foi possível carregar as informações da música. Verifique o nome e tente novamente.";
        }
    }

    // =========================================
    // CARREGAR CAPA
    // =========================================

    function carregarImagem(images) {

        const img = document.getElementById("musicImage");
        const placeholder =
            document.getElementById("musicPlaceholder");

        if (!Array.isArray(images)) {
            return;
        }

        const imagem =
            images.find(
                item =>
                    item.size === "extralarge" &&
                    item["#text"]
            ) ||
            images.find(item => item["#text"]);

        if (!imagem) {
            return;
        }

        img.onload = () => {
            img.hidden = false;
            placeholder.hidden = true;
        };

        img.onerror = () => {
            img.hidden = true;
            placeholder.hidden = false;
        };

        img.src = imagem["#text"];
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

            const span = document.createElement("span");

            span.textContent = tag.name || "";

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
    // VALIDAR LINK EXTERNO
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
