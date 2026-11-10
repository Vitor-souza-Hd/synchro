
document.addEventListener("DOMContentLoaded", () => {

    const grid = document.getElementById("favoriteTracksGrid");
    const feedback = document.getElementById("favoriteTracksFeedback");
    const counter = document.getElementById("favoriteTracksCount");

    if (!grid || !feedback || !counter) {
        console.warn("Elementos da biblioteca não encontrados.");
        return;
    }

    function showMessage(text) {
        feedback.textContent = text;
        feedback.hidden = false;
    }

    function renderTrack(item) {

        if (
            !item ||
            typeof item.track !== "string" ||
            typeof item.artist !== "string" ||
            !item.track.trim() ||
            !item.artist.trim()
        ) {
            return;
        }

        const card = document.createElement("article");
        card.className = "favorite-card favorite-track-card";

        const link = document.createElement("a");
        link.className = "favorite-card-link";

        const url = new URL(
            "/frontend/pages/musica.html",
            window.location.href
        );

        url.searchParams.set("track", item.track);
        url.searchParams.set("artist", item.artist);

        link.href = url.toString();

        const cover = document.createElement("div");
        cover.className = "favorite-track-cover";

        const info = document.createElement("div");
        info.className = "favorite-card-info";

        const title = document.createElement("h2");
        title.textContent = item.track;

        const artist = document.createElement("p");
        artist.textContent = item.artist;

        const type = document.createElement("span");
        type.className = "favorite-track-type";
        type.textContent = "MÚSICA";

        info.append(title, artist, type);
        link.append(cover, info);
        card.appendChild(link);

        grid.appendChild(card);

        window.SynchroFavoriteImages.show(cover, {
            kind: "track", title: item.track, artist: item.artist,
            className: "favorite-track-image", alt: `Capa de ${item.track}`
        });
    }

    // =========================================
    // CARREGAR FAVORITOS DO BACKEND
    // =========================================

    async function loadTracks() {

        showMessage("Carregando músicas favoritas...");

        try {

            const response = await fetch(
                "/favorites/tracks",
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
                    "Faça login para visualizar suas músicas favoritas."
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
                throw new Error("Resposta inesperada.");
            }

            grid.replaceChildren();

            const validItems = items.filter(item =>
                typeof item?.track === "string" &&
                item.track.trim() &&
                typeof item?.artist === "string" &&
                item.artist.trim()
            );

            counter.textContent =
                `${validItems.length} ${
                    validItems.length === 1
                        ? "música salva"
                        : "músicas salvas"
                }`;

            if (!validItems.length) {
                showMessage(
                    "Você ainda não favoritou nenhuma música."
                );
                return;
            }

            validItems.forEach(renderTrack);

            feedback.hidden = true;

        } catch (error) {

            console.error(
                "[Synchro] Erro nos favoritos:",
                error
            );

            showMessage(
                "Não foi possível carregar suas músicas favoritas."
            );
        }
    }

    loadTracks();
});
