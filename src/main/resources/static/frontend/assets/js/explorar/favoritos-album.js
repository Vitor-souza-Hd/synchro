
document.addEventListener("synchro:song", ({detail: {params, song}}) => {

    const area = document.getElementById("favoriteAlbumArea");
    const button = document.getElementById("favoriteAlbumBtn");
    const feedback = document.getElementById("favoriteAlbumFeedback");

    if (!area || !button || !feedback) return;

    
    const track = params.get("track")?.trim();
    const artist = params.get("artist")?.trim();

    let album = null;
    let isFavorite = false;
    let busy = false;

    function message(text) {
        feedback.textContent = text;
    }

    function updateButton() {
        button.disabled = busy || !album;

        button.setAttribute(
            "aria-pressed",
            String(isFavorite)
        );

        button.innerHTML = isFavorite
            ? '<i class="bx bxs-heart"></i> Álbum favoritado'
            : '<i class="bx bx-heart"></i> Favoritar álbum';

        button.classList.toggle(
            "is-favorite",
            isFavorite
        );
    }

    async function request(url, options = {}) {
        const response = await fetch(url, {
            credentials: "same-origin",
            ...options
        });

        if (response.status === 401 ||
            response.status === 403) {
            throw new Error("LOGIN_NECESSARIO");
        }

        if (!response.ok) {
            throw new Error("HTTP " + response.status);
        }

        return response.json();
    }

    function albumUrl(path = "") {
        const url = new URL(
            "/favorites/albums" + path,
            location.origin
        );

        url.searchParams.set(
            "releaseGroupId",
            album.releaseGroupId
        );

        return url;
    }

    async function initialize() {
        if (!track || !artist) return;

        try {
            const url = new URL(
                "/musicbrainz/track/album",
                location.origin
            );

            url.searchParams.set("track", track);
            url.searchParams.set("artist", artist);

            const data = await request(url);

            if (
                data.status !== "found" ||
                !data.album?.releaseGroupId ||
                !data.album?.title
            ) {
                return;
            }

            album = {
                releaseGroupId: data.album.releaseGroupId,
                album: data.album.title,
                artist
            };

            area.hidden = false;
            busy = true;
            updateButton();

            const status = await request(
                albumUrl("/check")
            );

            isFavorite = status.favorite === true;
            message("");

        } catch (error) {
            if (error.message === "LOGIN_NECESSARIO") {
                message(
                    "Faça login para favoritar este álbum."
                );
            } else {
                console.warn(
                    "[Synchro] Erro ao consultar álbum favorito:",
                    error
                );

                // O álbum pode continuar disponível mesmo
                // que a verificação de favorito falhe.
                if (album) {
                    message(
                        "Não foi possível verificar o favorito."
                    );
                }
            }

        } finally {
            busy = false;
            updateButton();
        }
    }

    button.addEventListener("click", async () => {

        if (busy || !album) return;

        busy = true;
        updateButton();
        message("");

        try {
            let result;

            if (isFavorite) {
                result = await request(albumUrl(), {
                    method: "DELETE"
                });

            } else {
                result = await request(
                    "/favorites/albums",
                    {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify(album)
                    }
                );
            }

            isFavorite = result.favorite === true;

            message(
                isFavorite
                    ? `"${album.album}" adicionado aos favoritos!`
                    : `"${album.album}" removido dos favoritos.`
            );

        } catch (error) {
            if (error.message === "LOGIN_NECESSARIO") {
                message(
                    "Sua sessão expirou. Faça login novamente."
                );
            } else {
                console.error(
                    "[Synchro] Erro ao salvar álbum:",
                    error
                );
                message(
                    "Não foi possível atualizar o favorito."
                );
            }

        } finally {
            busy = false;
            updateButton();
        }
    });

    initialize();
});
