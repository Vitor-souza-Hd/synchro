
const API_KEY = "7c62ff4c30df11f623f6ecd649c5d104";
const API_URL = "https://ws.audioscrobbler.com/2.0/";

const params = new URLSearchParams(window.location.search);
const artist = params.get("artist")?.trim();

const favoriteBtn = document.getElementById("favoriteBtn");
const artistImage = document.getElementById("artistImage");

let artistName = artist || "";
let isFavorite = false;
let favoriteBusy = false;

// =========================================================
// INICIAR PAGINA
// =========================================================

if (!artist) {
    document.getElementById("artistName").textContent =
        "Artista não encontrado";

    document.getElementById("biography").textContent =
        "Selecione um artista para visualizar suas informações.";

    if (favoriteBtn) favoriteBtn.disabled = true;
    mostrarImagemPadrao();
} else {
    mostrarImagemPadrao();
    iniciarFavoritos();
    carregarArtista();
}

// =========================================================
// LAST.FM
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
        throw new Error(
            data.message || "Erro na API do Last.fm"
        );
    }

    return data;
}

// =========================================================
// FAVORITOS DO SYNCHRO
// =========================================================

async function consultarFavoritos(url, options = {}) {
    const response = await fetch(url, {
        credentials: "same-origin",
        ...options
    });

    if (response.status === 401 ||
        response.status === 403) {
        throw new Error("LOGIN_NECESSARIO");
    }

    if (!response.ok) {
        throw new Error(
            `Erro ao consultar favoritos: ${response.status}`
        );
    }

    return response.json();
}

function atualizarBotaoFavorito() {
    if (!favoriteBtn) return;

    favoriteBtn.disabled = favoriteBusy;
    favoriteBtn.setAttribute(
        "aria-pressed",
        String(isFavorite)
    );

    favoriteBtn.innerHTML = isFavorite
        ? '<i class="bx bxs-heart"></i> Favoritado'
        : '<i class="bx bx-heart"></i> Favoritar';

    favoriteBtn.style.color = isFavorite
        ? "#ff6b81"
        : "";

    favoriteBtn.style.borderColor = isFavorite
        ? "#ff6b81"
        : "";
}

async function verificarFavorito() {
    const url = new URL(
        "/favorites/artists/check",
        window.location.origin
    );

    url.searchParams.set("artist", artistName);

    const data = await consultarFavoritos(url);

    // Nao sobrescrever o estado durante um clique.
    if (!favoriteBusy) {
        isFavorite = data.favorite === true;
        atualizarBotaoFavorito();
    }
}

function iniciarFavoritos() {
    if (!favoriteBtn) return;

    atualizarBotaoFavorito();

    verificarFavorito().catch(error => {
        console.warn(
            "Não foi possível verificar o favorito:",
            error
        );
    });

    favoriteBtn.addEventListener("click", async () => {
        if (favoriteBusy || !artistName) return;

        favoriteBusy = true;
        favoriteBtn.disabled = true;

        const favoritoAnterior = isFavorite;

        try {
            let data;

            if (isFavorite) {
                const url = new URL(
                    "/favorites/artists",
                    window.location.origin
                );

                url.searchParams.set("artist", artistName);

                data = await consultarFavoritos(url, {
                    method: "DELETE"
                });

            } else {
                data = await consultarFavoritos(
                    "/favorites/artists",
                    {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify({
                            artist: artistName
                        })
                    }
                );
            }

            isFavorite = data.favorite === true;

        } catch (error) {
            isFavorite = favoritoAnterior;

            if (error.message === "LOGIN_NECESSARIO") {
                alert(
                    "Faça login no Synchro para favoritar artistas."
                );
            } else {
                console.error(
                    "Erro ao atualizar favorito:",
                    error
                );

                alert(
                    "Não foi possível atualizar o favorito."
                );
            }

        } finally {
            favoriteBusy = false;
            atualizarBotaoFavorito();
        }
    });
}

// =========================================================
// INFORMACOES DO ARTISTA
// =========================================================

async function carregarArtista() {
    try {
        const data = await consultarLastFm(
            "artist.getinfo",
            { artist }
        );

        const info = data.artist;

        if (!info) {
            throw new Error("Artista não encontrado");
        }

        artistName = info.name || artist;

        document.getElementById("artistName").textContent =
            artistName;

        document.title = `${artistName} - Synchro`;

        document.getElementById("biography").textContent =
            limparTexto(info.bio?.summary) ||
            "Biografia não disponível.";

        carregarTags(info.tags?.tag);

        // Usar a Wikipedia para a fotografia.
        // Nao utilizar a imagem generica do Last.fm.
        carregarImagemArtista(artistName);

        verificarFavorito().catch(error => {
            console.warn(
                "Erro ao verificar favorito:",
                error
            );
        });

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
// IMAGEM PADRAO DO SYNCHRO
// =========================================================

function mostrarImagemPadrao() {
    if (!artistImage) return;

    // SVG local gerado pelo navegador, sem dependencias
    // externas nem novas imagens no projeto.
    const svg = `
        <svg xmlns="http://www.w3.org/2000/svg"
             viewBox="0 0 230 230">
            <rect width="230" height="230" fill="#252525"/>
            <circle cx="115" cy="88" r="39" fill="#777"/>
            <path d="M39 204c0-40 34-68 76-68s76 28 76 68"
                  fill="#777"/>
        </svg>
    `;

    artistImage.src =
        "data:image/svg+xml;charset=utf-8," +
        encodeURIComponent(svg);

    artistImage.alt = "Imagem padrão de artista";
}

// =========================================================
// PESQUISAR FOTO NA WIKIPEDIA
// =========================================================

function normalizarNome(texto) {
    return (texto || "")
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .toLowerCase()
        .replace(/_/g, " ")
        .replace(/\s+/g, " ")
        .trim();
}

function urlImagemPermitida(url) {
    try {
        const parsed = new URL(url);

        return parsed.protocol === "https:" &&
            (
                parsed.hostname === "upload.wikimedia.org" ||
                parsed.hostname.endsWith(".wikimedia.org")
            );
    } catch {
        return false;
    }
}

// Uma falha temporaria da Wikipedia nao deve exigir F5 do usuario.
// Repetimos apenas erros de servidor, limite de acesso e falhas de rede.
function esperar(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function buscarImagemWikipedia(nome, idioma) {
    const url = new URL(
        `https://${idioma}.wikipedia.org/w/api.php`
    );

    url.searchParams.set("action", "query");
    url.searchParams.set("titles", nome);
    url.searchParams.set("prop", "pageimages");
    url.searchParams.set("piprop", "thumbnail");
    url.searchParams.set("pithumbsize", "600");
    url.searchParams.set("format", "json");
    url.searchParams.set("formatversion", "2");
    url.searchParams.set("redirects", "1");
    url.searchParams.set("origin", "*");

    let response;

    for (let tentativa = 0; tentativa < 3; tentativa++) {
        try {
            response = await fetch(url);

            if (response.ok) break;

            const status = response.status;
            const temporario = [429, 502, 503, 504].includes(status);

            if (!temporario || tentativa === 2) {
                throw new Error(`Wikipedia respondeu HTTP ${status}`);
            }
        } catch (error) {
            // Erro de rede e temporario; erros HTTP permanentes nao sao.
            if (!(error instanceof TypeError) || tentativa === 2) {
                throw error;
            }
        }

        await esperar(800 * (tentativa + 1));
    }

    const data = await response.json();
    const pagina = data.query?.pages?.[0];

    if (!pagina || pagina.missing) {
        return null;
    }

    // So aceitar a pagina procurada ou um redirecionamento oficial.
    const nomesValidos = new Set([
        normalizarNome(nome),
        ...(data.query?.redirects || [])
            .filter(item =>
                normalizarNome(item.from) === normalizarNome(nome)
            )
            .map(item => normalizarNome(item.to))
    ]);

    if (!nomesValidos.has(normalizarNome(pagina.title))) {
        return null;
    }

    const imagem = pagina.thumbnail?.source;
    return urlImagemPermitida(imagem) ? imagem : null;
}

// Verifica se o navegador realmente consegue exibir a fotografia
// antes de trocar a imagem padrao no perfil.
function testarCarregamentoImagem(url) {
    return new Promise(resolve => {
        const teste = new Image();
        teste.onload = () => resolve(true);
        teste.onerror = () => resolve(false);
        teste.src = url;
    });
}

async function carregarImagemArtista(nome) {
    if (!artistImage) return;

    mostrarImagemPadrao();

    // Se a fotografia em portugues falhar, tentar a Wikipedia inglesa.
    // A imagem padrao continua visivel enquanto a busca acontece.
    for (const idioma of ["pt", "en"]) {
        try {
            const urlFoto = await buscarImagemWikipedia(nome, idioma);
            if (!urlFoto) continue;

            let carregou = await testarCarregamentoImagem(urlFoto);

            // Uma segunda tentativa curta para falhas transitorias de imagem.
            if (!carregou) {
                await esperar(1000);
                carregou = await testarCarregamentoImagem(urlFoto);
            }

            if (carregou) {
                artistImage.src = urlFoto;
                artistImage.alt = `Fotografia de ${nome}`;
                return;
            }

            console.warn(`Foto da Wikipedia ${idioma} nao carregou.`);
        } catch (error) {
            console.warn(`Erro ao buscar foto na Wikipedia ${idioma}:`, error);
        }
    }

    console.info("Fotografia indisponivel; mantendo imagem padrao.");
}

// =========================================================
// LIMPAR TEXTO DA BIOGRAFIA
// =========================================================

function limparTexto(texto) {
    if (!texto) return "";

    const elemento = document.createElement("div");
    elemento.innerHTML = texto;

    elemento.querySelectorAll("a").forEach(link => {
        link.remove();
    });

    return (elemento.textContent || "").trim();
}

// =========================================================
// MUSICAS POPULARES
// =========================================================

async function carregarMusicas() {
    const container = document.getElementById("tracks");
    if (!container) return;

    container.replaceChildren();

    try {
        const data = await consultarLastFm(
            "artist.gettoptracks",
            { artist, limit: "10" }
        );

        const resultado = data.toptracks?.track || [];
        const tracks = Array.isArray(resultado)
            ? resultado
            : [resultado];

        if (!tracks.length) {
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

            const urlMusica = new URL(
                "musica.html",
                window.location.href
            );

            urlMusica.searchParams.set("artist", artistName);
            urlMusica.searchParams.set("track", track.name || "");

            const nome = document.createElement("strong");

            const linkMusica = document.createElement("a");
            linkMusica.href = urlMusica.toString();
            linkMusica.textContent = track.name || "Sem título";
            linkMusica.style.color = "inherit";
            linkMusica.style.textDecoration = "none";

            linkMusica.addEventListener("mouseenter", () => {
                linkMusica.style.textDecoration = "underline";
            });

            linkMusica.addEventListener("mouseleave", () => {
                linkMusica.style.textDecoration = "none";
            });

            nome.appendChild(linkMusica);

            const ouvintes = document.createElement("span");
            const quantidade = Number(track.listeners);

            ouvintes.textContent =
                track.listeners !== undefined &&
                track.listeners !== null &&
                track.listeners !== "" &&
                Number.isFinite(quantidade) &&
                quantidade >= 0
                    ? `${quantidade.toLocaleString("pt-BR")} ouvintes`
                    : "Ouvintes não informados";

            info.append(nome, ouvintes);

            const botao = document.createElement("button");
            botao.type = "button";
            botao.innerHTML = '<i class="bx bx-play"></i>';

            botao.title = "Ver música no Synchro";

            botao.addEventListener("click", () => {
                window.location.href = urlMusica.toString();
            });

            div.append(numero, info, botao);
            container.appendChild(div);
        });

    } catch (error) {
        console.error("Erro ao carregar músicas:", error);
        container.textContent =
            "Não foi possível carregar as músicas.";
    }
}

// =========================================================
// ALBUNS POPULARES
// =========================================================

async function carregarAlbuns() {
    const container = document.getElementById("albums");
    if (!container) return;

    container.replaceChildren();

    try {
        const data = await consultarLastFm(
            "artist.gettopalbums",
            { artist, limit: "6" }
        );

        const resultado = data.topalbums?.album || [];
        const albums = Array.isArray(resultado)
            ? resultado
            : [resultado];

        if (!albums.length) {
            container.textContent = "Nenhum álbum encontrado.";
            return;
        }

        albums.forEach(album => {
            const imagem = Array.isArray(album.image)
                ? album.image.find(
                    img =>
                        img.size === "extralarge" &&
                        img["#text"]
                )
                : null;

            const div = document.createElement("div");
            div.className = "album-card";

            const imagemElemento =
                document.createElement("img");

            imagemElemento.src =
                imagem?.["#text"] || "";

            imagemElemento.alt =
                album.name || "Capa do álbum";

            const titulo = document.createElement("h3");
            titulo.textContent =
                album.name || "Álbum sem título";

            const artistaElemento =
                document.createElement("p");

            artistaElemento.textContent =
                album.artist?.name || artist;

            div.append(
                imagemElemento,
                titulo,
                artistaElemento
            );

            container.appendChild(div);
        });

    } catch (error) {
        console.error("Erro ao carregar álbuns:", error);
        container.textContent =
            "Não foi possível carregar os álbuns.";
    }
}

// =========================================================
// GENEROS DO ARTISTA
// =========================================================

function carregarTags(tags) {
    const container = document.getElementById("artistTags");
    if (!container) return;

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
