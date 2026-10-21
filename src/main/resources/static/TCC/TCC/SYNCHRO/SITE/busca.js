
document.addEventListener("DOMContentLoaded", () => {

    // Busca compartilhada do Synchro.
    // Utiliza os endpoints existentes do Last.fm.

    const searchInput = document.querySelector(".search");
    const searchBox = document.querySelector(".search-box");

    if (!searchInput || !searchBox) {
        return;
    }

    // Evita inicializar duas vezes na mesma pagina.
    if (searchBox.dataset.synchroSearchReady === "true") {
        return;
    }

    searchBox.dataset.synchroSearchReady = "true";

    const results = document.createElement("div");
    results.id = "lastfm-search-results";
    results.setAttribute("role", "region");
    results.setAttribute("aria-label", "Resultados da pesquisa");

    Object.assign(results.style, {
        position: "absolute",
        top: "calc(100% + 8px)",
        left: "0",
        width: "100%",
        maxHeight: "400px",
        overflowY: "auto",
        backgroundColor: "#181818",
        color: "#ffffff",
        borderRadius: "10px",
        boxShadow: "0 8px 24px rgba(0,0,0,0.5)",
        zIndex: "9999",
        display: "none",
        padding: "10px",
        boxSizing: "border-box"
    });

    searchBox.style.position = "relative";
    searchBox.appendChild(results);

    let timer = null;
    let controller = null;
    let version = 0;

    function ocultarResultados() {
        results.style.display = "none";
    }

    function mostrarMensagem(message) {
        results.replaceChildren();

        const paragraph = document.createElement("p");
        paragraph.textContent = message;

        Object.assign(paragraph.style, {
            padding: "10px",
            margin: "0",
            color: "#cccccc"
        });

        results.appendChild(paragraph);
        results.style.display = "block";
    }

    function criarTitulo(texto) {
        const heading = document.createElement("h4");
        heading.textContent = texto;

        Object.assign(heading.style, {
            padding: "8px",
            margin: "8px 0 0",
            color: "#ffffff"
        });

        results.appendChild(heading);
    }

    function criarResultado(nome, descricao, destino) {
        const item = document.createElement("a");

        // Todas as paginas da busca ficam na mesma pasta.
        item.href = destino;

        Object.assign(item.style, {
            display: "block",
            padding: "10px",
            color: "#ffffff",
            textDecoration: "none",
            borderBottom: "1px solid #333333",
            borderRadius: "5px",
            cursor: "pointer"
        });

        const title = document.createElement("strong");
        title.textContent = nome;
        title.style.display = "block";

        const subtitle = document.createElement("small");
        subtitle.textContent = descricao;
        subtitle.style.color = "#aaaaaa";

        item.append(title, subtitle);

        item.addEventListener("mouseenter", () => {
            item.style.backgroundColor = "#303030";
        });

        item.addEventListener("mouseleave", () => {
            item.style.backgroundColor = "transparent";
        });

        return item;
    }

    async function consultar(endpoint, query, signal) {
        const url = new URL(endpoint, window.location.origin);
        url.searchParams.set("q", query);

        const response = await fetch(url, {
            signal,
            headers: {
                Accept: "application/json"
            }
        });

        if (!response.ok) {
            throw new Error(
                `Erro HTTP ${response.status} em ${endpoint}`
            );
        }

        return response.json();
    }

    async function pesquisar(query) {
        if (controller) {
            controller.abort();
        }

        controller = new AbortController();

        const signal = controller.signal;
        const currentVersion = ++version;

        mostrarMensagem("Pesquisando no Last.fm...");

        const responses = await Promise.allSettled([
            consultar("/last-fm/artist/search", query, signal),
            consultar("/last-fm/track/search", query, signal)
        ]);

        // Ignora respostas de pesquisas antigas.
        if (signal.aborted || currentVersion !== version) {
            return;
        }

        results.replaceChildren();

        let total = 0;

        // =========================================
        // ARTISTAS
        // =========================================

        if (responses[0].status === "fulfilled") {
            const data = responses[0].value;

            const rawArtists =
                data.results?.artistmatches?.artist || [];

            const artists = Array.isArray(rawArtists)
                ? rawArtists
                : [rawArtists];

            const validArtists = artists
                .filter(item =>
                    typeof item.name === "string" &&
                    item.name.trim()
                )
                .slice(0, 5);

            if (validArtists.length > 0) {
                criarTitulo("Artistas");

                validArtists.forEach(item => {
                    const destination =
                        "artista.html?artist=" +
                        encodeURIComponent(item.name);

                    results.appendChild(
                        criarResultado(
                            item.name,
                            "Artista",
                            destination
                        )
                    );

                    total++;
                });
            }
        }

        // =========================================
        // MUSICAS
        // =========================================

        if (responses[1].status === "fulfilled") {
            const data = responses[1].value;

            const rawTracks =
                data.results?.trackmatches?.track || [];

            const tracks = Array.isArray(rawTracks)
                ? rawTracks
                : [rawTracks];

            const validTracks = tracks
                .map(item => ({
                    name: item.name,
                    artist: typeof item.artist === "string"
                        ? item.artist
                        : item.artist?.name
                }))
                .filter(item =>
                    typeof item.name === "string" &&
                    item.name.trim() &&
                    typeof item.artist === "string" &&
                    item.artist.trim()
                )
                .slice(0, 5);

            if (validTracks.length > 0) {
                criarTitulo("Músicas");

                validTracks.forEach(item => {
                    const destination =
                        "musica.html?artist=" +
                        encodeURIComponent(item.artist) +
                        "&track=" +
                        encodeURIComponent(item.name);

                    results.appendChild(
                        criarResultado(
                            item.name,
                            item.artist,
                            destination
                        )
                    );

                    total++;
                });
            }
        }

        if (total > 0) {
            results.style.display = "block";
            return;
        }

        const falharamAmbas = responses.every(
            response => response.status === "rejected"
        );

        mostrarMensagem(
            falharamAmbas
                ? "Não foi possível consultar o Last.fm."
                : "Nenhum resultado encontrado."
        );
    }

    searchInput.addEventListener("input", () => {
        clearTimeout(timer);

        const query = searchInput.value.trim();

        if (query.length < 2) {
            version++;

            if (controller) {
                controller.abort();
            }

            ocultarResultados();
            results.replaceChildren();
            return;
        }

        timer = setTimeout(() => {
            pesquisar(query).catch(error => {
                if (error.name !== "AbortError") {
                    console.error("Erro na pesquisa:", error);
                    mostrarMensagem(
                        "Erro ao realizar a pesquisa."
                    );
                }
            });
        }, 400);
    });

    searchInput.addEventListener("keydown", event => {
        if (event.key === "Escape") {
            ocultarResultados();
        }
    });

    searchInput.addEventListener("focus", () => {
        if (
            searchInput.value.trim().length >= 2 &&
            results.childElementCount > 0
        ) {
            results.style.display = "block";
        }
    });

    document.addEventListener("click", event => {
        if (!searchBox.contains(event.target)) {
            ocultarResultados();
        }
    });
});
