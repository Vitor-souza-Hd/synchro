
const API_KEY = "7c62ff4c30df11f623f6ecd649c5d104";

const API_URL = "https://ws.audioscrobbler.com/2.0/";

const params = new URLSearchParams(window.location.search);
const artist = params.get("artist");

// =========================================================
// INICIAR PÁGINA
// =========================================================

if (!artist || !artist.trim()) {
    document.getElementById("artistName").textContent =
        "Artista não encontrado";

    document.getElementById("biography").textContent =
        "Selecione um artista para visualizar suas informações.";
} else {
    carregarArtista();
}

// =========================================================
// CONSULTAR API DO LAST.FM
// =========================================================

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
        throw new Error(data.message || "Erro na API do Last.fm");
    }

    return data;
}

// =========================================================
// INFORMAÇÕES DO ARTISTA
// =========================================================

async function carregarArtista() {

    try {
        const data = await consultarLastFm("artist.getinfo", {
            artist: artist
        });

        const info = data.artist;

        if (!info) {
            throw new Error("Artista não encontrado");
        }

        document.getElementById("artistName").textContent =
            info.name || artist;

        document.title = `${info.name || artist} - Synchro`;

        document.getElementById("biography").textContent =
            limparTexto(info.bio?.summary) ||
            "Biografia não disponível.";

        carregarTags(info.tags?.tag);
        carregarImagem(info.image);

        // Carregar músicas e álbuns independentemente
        await Promise.allSettled([
            carregarMusicas(),
            carregarAlbuns()
        ]);

    } catch (error) {
        console.error("Erro ao carregar artista:", error);

        document.getElementById("artistName").textContent =
            "Erro ao carregar artista";

        document.getElementById("biography").textContent =
            "Não foi possível obter as informações do Last.fm.";
    }
}

// =========================================================
// LIMPAR TEXTO DA BIOGRAFIA
// =========================================================

function limparTexto(texto) {

    if (!texto) {
        return "";
    }

    // Converter HTML em texto simples
    const elemento = document.createElement("div");
    elemento.innerHTML = texto;

    // Remover links acrescentados pelo Last.fm
    elemento.querySelectorAll("a").forEach(link => {
        link.remove();
    });

    return (elemento.textContent || "").trim();
}

// =========================================================
// IMAGEM DO ARTISTA
// =========================================================

function carregarImagem(images) {

    const elemento = document.getElementById("artistImage");

    if (!elemento || !Array.isArray(images)) {
        return;
    }

    const imagem = images.find(
        img => img.size === "extralarge" && img["#text"]
    ) || images.find(img => img["#text"]);

    if (imagem) {
        elemento.src = imagem["#text"];
        elemento.alt = `Imagem do artista ${artist}`;
    }
}

// =========================================================
// MÚSICAS POPULARES
// =========================================================

async function carregarMusicas() {

    const container = document.getElementById("tracks");

    if (!container) {
        return;
    }

    container.replaceChildren();

    try {
        const data = await consultarLastFm("artist.gettoptracks", {
            artist: artist,
            limit: "10"
        });

        const resultado = data.toptracks?.track || [];

        const tracks = Array.isArray(resultado)
            ? resultado
            : [resultado];

        if (tracks.length === 0) {
            container.textContent = "Nenhuma música encontrada.";
            return;
        }

        tracks.forEach((track, index) => {

            const div = document.createElement("div");
            div.className = "track";

            const numero = document.createElement("span");
            numero.className = "track-number";
            numero.textContent = index + 1;

            const info = document.createElement("div");
            info.className = "track-info";

            const nome = document.createElement("strong");
            nome.textContent = track.name || "Sem título";

            const ouvintes = document.createElement("span");
            ouvintes.textContent =
                `${track.listeners || "0"} ouvintes`;

            info.append(nome, ouvintes);

            const botao = document.createElement("button");
            botao.type = "button";
            botao.innerHTML = "<i class='bx bx-play'></i>";
            botao.title = "Abrir música no Last.fm";

            botao.addEventListener("click", () => {
                if (track.url) {
                    window.open(track.url, "_blank", "noopener,noreferrer");
                }
            });

            div.append(numero, info, botao);
            container.appendChild(div);
        });

    } catch (error) {
        console.error("Erro ao carregar músicas:", error);
        container.textContent = "Não foi possível carregar as músicas.";
    }
}

// =========================================================
// ÁLBUNS POPULARES
// =========================================================

async function carregarAlbuns() {

    const container = document.getElementById("albums");

    if (!container) {
        return;
    }

    container.replaceChildren();

    try {
        const data = await consultarLastFm("artist.gettopalbums", {
            artist: artist,
            limit: "6"
        });

        const resultado = data.topalbums?.album || [];

        const albums = Array.isArray(resultado)
            ? resultado
            : [resultado];

        if (albums.length === 0) {
            container.textContent = "Nenhum álbum encontrado.";
            return;
        }

        albums.forEach(album => {

            const imagem = Array.isArray(album.image)
                ? album.image.find(
                    img => img.size === "extralarge" && img["#text"]
                )
                : null;

            const div = document.createElement("div");
            div.className = "album-card";

            const imagemElemento = document.createElement("img");
            imagemElemento.src = imagem?.["#text"] || "";
            imagemElemento.alt = album.name || "Capa do álbum";

            const titulo = document.createElement("h3");
            titulo.textContent = album.name || "Álbum sem título";

            const artistaElemento = document.createElement("p");
            artistaElemento.textContent =
                album.artist?.name || artist;

            div.append(imagemElemento, titulo, artistaElemento);

            container.appendChild(div);
        });

    } catch (error) {
        console.error("Erro ao carregar álbuns:", error);
        container.textContent = "Não foi possível carregar os álbuns.";
    }
}

// =========================================================
// GÊNEROS DO ARTISTA
// =========================================================

function carregarTags(tags) {

    const container = document.getElementById("artistTags");

    if (!container) {
        return;
    }

    container.replaceChildren();

    const lista = Array.isArray(tags)
        ? tags
        : tags ? [tags] : [];

    lista.slice(0, 4).forEach(tag => {

        const span = document.createElement("span");
        span.textContent = tag.name || "";

        container.appendChild(span);
    });
}
