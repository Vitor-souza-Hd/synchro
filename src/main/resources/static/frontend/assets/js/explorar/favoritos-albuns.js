
document.addEventListener("DOMContentLoaded", () => {

    const grid = document.getElementById("favoriteAlbumsGrid");
    const feedback = document.getElementById("favoriteAlbumsFeedback");
    const counter = document.getElementById("favoriteAlbumsCount");

    if (!grid || !feedback || !counter) return;

    function showMessage(text) {
        feedback.textContent = text;
        feedback.hidden = false;
    }

    function renderAlbum(item) {

        if (
            !item ||
            typeof item.album !== "string" ||
            typeof item.artist !== "string" ||
            typeof item.releaseGroupId !== "string" ||
            !item.album.trim() ||
            !item.artist.trim()
        ) {
            return;
        }

        // CARTAO DO ALBUM
        const card = document.createElement("article");
        card.className = "favorite-card favorite-album-card";

        // LINK PARA A PAGINA DO ALBUM NO SYNCHRO
        const link = document.createElement("a");
        link.className = "favorite-card-link";
        link.setAttribute(
            "aria-label",
            `Abrir álbum ${item.album} de ${item.artist}`
        );

        const url = new URL(
            "/frontend/pages/album-explorar.html",
            window.location.href
        );

        url.searchParams.set("album", item.album);
        url.searchParams.set("artist", item.artist);

        link.href = url.toString();

        // CAPA
        const cover = document.createElement("div");
        cover.className = "favorite-album-cover";

        const info = document.createElement("div");
        info.className = "favorite-card-info";

        const title = document.createElement("h2");
        title.textContent = item.album;

        const artist = document.createElement("p");
        artist.textContent = item.artist;

        const type = document.createElement("span");
        type.className = "favorite-album-type";
        type.textContent = "ÁLBUM";

        info.append(title, artist, type);

        // TODO O CARTAO SERA CLICAVEL
        link.append(cover, info);
        card.appendChild(link);

        grid.appendChild(card);

        window.SynchroFavoriteImages.show(cover, {
            kind: "album", title: item.album, artist: item.artist,
            releaseGroupId: item.releaseGroupId,
            className: "favorite-album-image", alt: `Capa do álbum ${item.album}`
        });
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
