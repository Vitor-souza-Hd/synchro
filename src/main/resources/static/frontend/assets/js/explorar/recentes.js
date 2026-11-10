
(() => {
    "use strict";

    const STORAGE_KEY = "synchro:recentes:v1";
    const MAX_ITEMS = 30;

    function readHistory() {
        try {
            const data = JSON.parse(
                localStorage.getItem(STORAGE_KEY) || "[]"
            );

            return Array.isArray(data) ? data : [];
        } catch {
            return [];
        }
    }

    function saveHistory(items) {
        try {
            localStorage.setItem(
                STORAGE_KEY,
                JSON.stringify(items)
            );
        } catch (error) {
            console.warn(
                "[Synchro] Histórico indisponível:",
                error
            );
        }
    }

    function trackPage(params = new URLSearchParams(location.search)) {
        try {
            if (
                localStorage.getItem(
                    "synchro:history-enabled:v1"
                ) === "false"
            ) {
                return;
            }
        } catch {
            // Mantem o comportamento padrao.
        }
        const file = location.pathname;


        const artist = params.get("artist")?.trim();
        const track = params.get("track")?.trim();
        const album = params.get("album")?.trim();

        let item;

        if (file === "/frontend/pages/musica.html" && track && (artist || params.has('id'))) {
            item = {
                type: "Música",
                title: track,
                subtitle: artist || 'Artista não informado',
                url: params.has('id')
                    ? '/frontend/pages/musica.html?id=' + encodeURIComponent(params.get('id'))
                    : `/frontend/pages/musica.html?artist=${encodeURIComponent(artist)}&track=${encodeURIComponent(track)}`
            };
        } else if (
            ["/frontend/pages/album-explorar.html", "/frontend/pages/album.html"].includes(file) &&
            album && (artist || params.has("id"))
        ) {
            item = {
                type: "Álbum",
                title: album,
                subtitle: artist || "",
                url: params.has("id") ? "/frontend/pages/album.html?id=" + encodeURIComponent(params.get("id"))
                    : `/frontend/pages/album-explorar.html?artist=${encodeURIComponent(artist)}&album=${encodeURIComponent(album)}`
            };
        } else if (["/frontend/pages/artista.html", "/frontend/pages/detalhes-artista.html"].includes(file) && artist) {
            item = {
                type: "Artista",
                title: artist,
                subtitle: "Artista",
                url: params.has('id') ? '/frontend/pages/artista.html?id=' + encodeURIComponent(params.get('id'))
                    : `/frontend/pages/artista.html?artist=${encodeURIComponent(artist)}`
            };
        }

        if (!item) return;

        const history = readHistory().filter(
            previous => previous.url !== item.url && !(item.type === 'Artista' && previous.type === 'Artista'
                && previous.title?.trim().toLowerCase() === item.title.trim().toLowerCase())
        );

        history.unshift({
            ...item,
            visitedAt: Date.now()
        });

        saveHistory(history.slice(0, MAX_ITEMS));
    }

    function renderHistory() {
        const grid = document.getElementById("recentesGrid");
        const empty = document.getElementById("recentesEmpty");
        const count = document.getElementById("recentesCount");
        const clear = document.getElementById("limparRecentes");

        if (!grid || !empty || !count || !clear) return;

        const history = readHistory();

        grid.replaceChildren();

        count.textContent =
            `${history.length} ${history.length === 1
                ? "item recente"
                : "itens recentes"}`;

        empty.hidden = history.length > 0;
        clear.disabled = history.length === 0;

        const icons = {
            "Música": "bx bxs-music",
            "Álbum": "bx bxs-album",
            "Artista": "bx bxs-microphone"
        };

        for (const item of history) {

            if (
                !item?.url ||
                !["Música", "Álbum", "Artista"]
                    .includes(item.type)
            ) {
                continue;
            }

            // Permite somente as páginas conhecidas.
            const url = new URL(item.url, location.href);
            if (item.type === 'Música' && url.pathname === '/frontend/pages/detalhes-musica.html') {
                url.pathname = '/frontend/pages/musica.html';
            }
            if (item.type === 'Artista' && url.pathname === '/frontend/pages/detalhes-artista.html') {
                url.pathname = '/frontend/pages/artista.html';
            }
            if (item.type === "Álbum" && url.pathname === "/frontend/pages/detalhes-album.html") {
                url.pathname = "/frontend/pages/album.html";
            }
            const file = url.pathname;

            const allowed = {
                "Música": "/frontend/pages/musica.html",
                "Álbum": "/frontend/pages/album-explorar.html",
                "Artista": "/frontend/pages/artista.html"
            };

            if (
                url.origin !== location.origin ||
                (file !== allowed[item.type] && !(item.type === "Álbum" && file === "/frontend/pages/album.html"))
            ) {
                continue;
            }

            const link = document.createElement("a");
            link.className = "recentes-card";
            link.href = url.toString();

            const iconArea = document.createElement("div");
            iconArea.className = "recentes-cover";

            const icon = document.createElement("i");
            icon.className = icons[item.type];

            iconArea.appendChild(icon);

            const info = document.createElement("div");
            info.className = "recentes-info";

            const title = document.createElement("h3");
            title.textContent = item.title || "Sem título";

            const subtitle = document.createElement("p");
            subtitle.textContent = item.subtitle || "";

            const kind = document.createElement("small");
            kind.textContent = item.type;

            info.append(title, subtitle, kind);
            link.append(iconArea, info);
            grid.appendChild(link);

            const imageKinds = {"Música": "track", "Álbum": "album", "Artista": "artist"};
            const artist = item.type === "Artista" ? title.textContent :
                url.searchParams.get("artist") || (item.subtitle === "Artista não informado" ? "" : item.subtitle) || "";
            window.SynchroFavoriteImages?.show(iconArea, {
                kind: imageKinds[item.type], title: title.textContent, artist, id: url.searchParams.get("id"),
                className: "recentes-image", alt: ""
            });
        }

        clear.onclick = () => {
            if (!confirm("Limpar seu histórico de recentes?")) {
                return;
            }

            try {
                localStorage.removeItem(STORAGE_KEY);
            } catch {
                return;
            }

            renderHistory();
        };
    }

    document.addEventListener("synchro:album", ({detail: {id, album}}) => trackPage(new URLSearchParams({
        id, album: album.titulo, artist: album.lastFm?.artistName || album.artistas?.[0]?.nome || ""
    })));
    document.addEventListener("synchro:song", ({detail: {params}}) => trackPage(params));
    document.addEventListener('synchro:artist', ({detail}) => trackPage(new URLSearchParams({artist: detail.name, id: detail.id})));
    document.addEventListener("DOMContentLoaded", () => {
        if (!['/musica.html', '/artista.html'].some(page => location.pathname.endsWith(page))) trackPage();
        renderHistory();
    });
})();
