
document.addEventListener("DOMContentLoaded", () => {

    const button = document.getElementById("favoriteTrackBtn");
    const feedback = document.getElementById("favoriteTrackFeedback");

    if (!button) return;

    const params = new URLSearchParams(window.location.search);

    const track = params.get("track")?.trim();
    const artist = params.get("artist")?.trim();

    let isFavorite = false;
    let busy = false;
    let authenticated = false;

    function message(text) {
        if (feedback) feedback.textContent = text;
    }

    function updateButton() {
        button.disabled = busy || !authenticated;

        button.setAttribute("aria-pressed", String(isFavorite));

        button.innerHTML = isFavorite
            ? '<i class="bx bxs-heart"></i> Favoritado'
            : '<i class="bx bx-heart"></i> Favoritar';

        button.classList.toggle("is-favorite", isFavorite);
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

    function buildUrl() {
        const url = new URL(
            "/favorites/tracks",
            window.location.origin
        );

        url.searchParams.set("track", track);
        url.searchParams.set("artist", artist);

        return url;
    }

    async function checkFavorite() {
        busy = true;
        updateButton();

        try {
            const url = buildUrl();
            url.pathname += "/check";

            const data = await request(url);

            authenticated = true;
            isFavorite = data.favorite === true;
            message("");

        } catch (error) {
            authenticated = false;

            if (error.message === "LOGIN_NECESSARIO") {
                message("Entre na sua conta para favoritar músicas.");
            } else {
                message("Não foi possível consultar seus favoritos.");
                console.error(error);
            }

        } finally {
            busy = false;
            updateButton();
        }
    }

    async function toggleFavorite() {
        if (busy || !authenticated) return;

        busy = true;
        message("");
        updateButton();

        try {
            let data;

            if (isFavorite) {
                data = await request(buildUrl(), {
                    method: "DELETE"
                });
            } else {
                data = await request("/favorites/tracks", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({
                        track,
                        artist
                    })
                });
            }

            isFavorite = data.favorite === true;

            message(
                isFavorite
                    ? "Música adicionada aos favoritos!"
                    : "Música removida dos favoritos."
            );

        } catch (error) {
            if (error.message === "LOGIN_NECESSARIO") {
                authenticated = false;
                message("Sua sessão expirou. Faça login novamente.");
            } else {
                message("Não foi possível atualizar o favorito.");
                console.error(error);
            }

        } finally {
            busy = false;
            updateButton();
        }
    }

    if (!track || !artist) {
        message("Música ou artista não informado.");
        updateButton();
        return;
    }

    button.addEventListener("click", toggleFavorite);

    checkFavorite();
});
