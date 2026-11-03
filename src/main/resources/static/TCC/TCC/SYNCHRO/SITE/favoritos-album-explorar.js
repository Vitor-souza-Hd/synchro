
document.addEventListener("DOMContentLoaded", () => {

    // ==========================================
    // SYNCHRO - FAVORITOS DE ALBUNS EXPLORADOS
    // ==========================================

    const area = document.getElementById(
        "albumExploreFavoriteArea"
    );

    const button = document.getElementById(
        "albumExploreFavoriteBtn"
    );

    const feedback = document.getElementById(
        "albumExploreFavoriteFeedback"
    );

    if (!area || !button || !feedback) return;

    const params = new URLSearchParams(
        window.location.search
    );

    const albumName = params.get("album")?.trim();
    const artistName = params.get("artist")?.trim();

    let album = null;
    let isFavorite = false;
    let busy = true;
    let available = false;

    function normalize(value) {
        return String(value || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .replace(/\s+/g, " ")
            .toLowerCase()
            .trim();
    }

    function message(text) {
        feedback.textContent = text;
    }

    function updateButton() {
        button.disabled = busy || !available || !album;

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

    async function requestJson(url, options = {}) {

        const response = await fetch(url, {
            credentials: "same-origin",
            headers: {
                Accept: "application/json",
                ...(options.headers || {})
            },
            ...options
        });

        if (
            response.status === 401 ||
            response.status === 403
        ) {
            throw new Error("LOGIN_NECESSARIO");
        }

        if (!response.ok) {
            throw new Error(
                "HTTP " + response.status
            );
        }

        return response.json();
    }

    function favoriteUrl(path = "") {

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

    function mainArtistMatches(item) {

        const credits = item["artist-credit"];

        if (!Array.isArray(credits)) {
            return false;
        }

        return credits.some(credit => {

            const name =
                credit?.artist?.name ||
                credit?.name;

            return normalize(name) ===
                normalize(artistName);
        });
    }

    // ==========================================
    // IDENTIFICAR O ALBUM NO MUSICBRAINZ
    // ==========================================

    async function findAlbum() {

        const url = new URL(
            "/musicbrainz/album/search",
            location.origin
        );

        url.searchParams.set(
            "artist",
            artistName
        );

        url.searchParams.set(
            "album",
            albumName
        );

        const data = await requestJson(url);

        const groups = data["release-groups"];

        if (!Array.isArray(groups)) {
            return null;
        }

        // Evita favoritar um album diferente
        // que possua titulo semelhante.
        const match = groups.find(item => {

            const exactTitle =
                normalize(item.title) ===
                normalize(albumName);

            const studioAlbum =
                item["primary-type"] === "Album" &&
                (
                    !Array.isArray(
                        item["secondary-types"]
                    ) ||
                    item["secondary-types"].length === 0
                );

            const correctArtist =
                mainArtistMatches(item);

            return item.id &&
                exactTitle &&
                studioAlbum &&
                correctArtist;
        });

        if (!match) return null;

        return {
            releaseGroupId: match.id,
            album: match.title,
            artist: artistName
        };
    }

    // ==========================================
    // VERIFICAR SE JA ESTA FAVORITADO
    // ==========================================

    async function initialize() {

        if (!albumName || !artistName) {
            message("Álbum não identificado.");
            return;
        }

        area.hidden = false;
        busy = true;
        updateButton();

        message("Verificando álbum...");

        try {

            album = await findAlbum();

            if (!album) {

                message(
                    "Não foi possível identificar este álbum " +
                    "com segurança no MusicBrainz."
                );

                return;
            }

            const status = await requestJson(
                favoriteUrl("/check")
            );

            isFavorite = status.favorite === true;
            available = true;

            message("");

        } catch (error) {

            if (error.message === "LOGIN_NECESSARIO") {

                message(
                    "Faça login para favoritar álbuns."
                );

            } else {

                console.warn(
                    "[Synchro] Erro nos favoritos de álbum:",
                    error
                );

                message(
                    "Não foi possível verificar o favorito."
                );
            }

        } finally {

            busy = false;
            updateButton();
        }
    }

    // ==========================================
    // FAVORITAR / DESFAVORITAR
    // ==========================================

    button.addEventListener("click", async () => {

        if (busy || !available || !album) {
            return;
        }

        busy = true;
        updateButton();
        message("");

        try {

            let result;

            if (isFavorite) {

                result = await requestJson(
                    favoriteUrl(),
                    {
                        method: "DELETE"
                    }
                );

            } else {

                result = await requestJson(
                    "/favorites/albums",
                    {
                        method: "POST",
                        headers: {
                            "Content-Type":
                                "application/json"
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

            console.error(
                "[Synchro] Erro ao atualizar favorito:",
                error
            );

            if (error.message === "LOGIN_NECESSARIO") {

                available = false;

                message(
                    "Sua sessão expirou. Faça login novamente."
                );

            } else {

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
