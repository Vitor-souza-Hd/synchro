
document.addEventListener("DOMContentLoaded", () => {

    const grid = document.getElementById("favoriteAlbumsGrid");
    const feedback = document.getElementById("favoriteAlbumsFeedback");
    const counter = document.getElementById("favoriteAlbumsCount");

    if (!grid || !feedback || !counter) return;

    const coverCache = new Map();

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

    // =====================================
    // CONSULTAR CAPA PELO MUSICBRAINZ ID
    // =====================================

    async function findCover(releaseGroupId) {

        if (coverCache.has(releaseGroupId)) {
            return coverCache.get(releaseGroupId);
        }

        const promise = (async () => {

            const response = await fetch(
                "/cover-art/release-group/" +
                encodeURIComponent(releaseGroupId),
                {
                    credentials: "same-origin",
                    headers: {
                        Accept: "application/json"
                    }
                }
            );

            if (!response.ok) {
                throw new Error(
                    "Cover Art HTTP " + response.status
                );
            }

            const data = await response.json();

            // Verifica a URL diretamente, sem depender
            // exclusivamente do campo "status".
            return validCoverUrl(data.coverUrl)
                ? data.coverUrl
                : null;

        })().catch(error => {

            console.warn(
                "[Synchro] Capa de álbum indisponível:",
                releaseGroupId,
                error
            );

            return null;
        });

        coverCache.set(releaseGroupId, promise);

        return promise;
    }

    // =====================================
    // CARREGAR IMAGEM
    // =====================================

    async function loadCover(
        cover,
        releaseGroupId,
        albumName
    ) {

        const imageUrl = await findCover(releaseGroupId);

        if (!imageUrl || !cover.isConnected) return;

        // Sem loading="lazy": a imagem começa
        // a carregar antes de entrar no DOM.
        const img = new Image();

        img.className = "favorite-album-image";
        img.alt = `Capa do álbum ${albumName}`;
        img.decoding = "async";

        img.onload = () => {

            if (!cover.isConnected) return;

            cover.replaceChildren(img);

            console.log(
                "[Synchro] Capa do álbum carregada:",
                albumName
            );
        };

        img.onerror = () => {
            console.warn(
                "[Synchro] Falha ao exibir capa:",
                albumName
            );
        };

        img.src = imageUrl;
    }

    // =====================================
    // CRIAR CARTÃO DO ÁLBUM
    // =====================================

    function renderAlbum(item) {

        if (
            !item ||
            typeof item.album !== "string" ||
            typeof item.artist !== "string" ||
            typeof item.releaseGroupId !== "string"
        ) {
            return;
        }

        const card = document.createElement("article");
        card.className = "favorite-card favorite-album-card";

        const cover = document.createElement("div");
        cover.className = "favorite-album-cover";

        const icon = document.createElement("i");
        icon.className = "bx bx-album";

        cover.appendChild(icon);

        const info = document.createElement("div");
        info.className = "favorite-card-info";

        const title = document.createElement("h2");
        title.textContent = item.album;

        const artist = document.createElement("p");
        artist.textContent = item.artist;

        const type = document.createElement("span");
        type.className = "favorite-album-type";
        type.textContent = "ÁLBUM";

        // O álbum externo usa um UUID do MusicBrainz.
        // Não usar album.html?id=UUID:
        // essa página espera um ID numérico interno.
        const externalLink = document.createElement("a");
        externalLink.className = "favorite-album-link";
        externalLink.textContent = "Ver no MusicBrainz";

        externalLink.href =
            "https://musicbrainz.org/release-group/" +
            encodeURIComponent(item.releaseGroupId);

        externalLink.target = "_blank";
        externalLink.rel = "noopener noreferrer";

        info.append(
            title,
            artist,
            type,
            externalLink
        );

        card.append(cover, info);
        grid.appendChild(card);

        loadCover(
            cover,
            item.releaseGroupId,
            item.album
        );
    }

    // =====================================
    // CARREGAR FAVORITOS
    // =====================================

    async function loadAlbums() {

        showMessage("Carregando álbuns favoritos...");

        try {

            const response = await fetch(
                "/favorites/albums",
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
                    "Faça login para visualizar seus álbuns favoritos."
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
                throw new Error(
                    "Resposta inesperada do servidor."
                );
            }

            grid.replaceChildren();

            const albums = items.filter(item =>
                typeof item?.album === "string" &&
                item.album.trim() &&
                typeof item?.artist === "string" &&
                item.artist.trim() &&
                typeof item?.releaseGroupId === "string" &&
                /^[0-9a-f-]{36}$/i.test(item.releaseGroupId)
            );

            counter.textContent =
                `${albums.length} ${
                    albums.length === 1
                        ? "álbum salvo"
                        : "álbuns salvos"
                }`;

            if (!albums.length) {
                showMessage(
                    "Você ainda não favoritou álbuns. " +
                    "Explore as músicas e salve seus álbuns preferidos!"
                );
                return;
            }

            albums.forEach(renderAlbum);
            feedback.hidden = true;

        } catch (error) {

            console.error(
                "[Synchro] Erro ao carregar álbuns:",
                error
            );

            showMessage(
                "Não foi possível carregar seus álbuns favoritos."
            );
        }
    }

    loadAlbums();
});
