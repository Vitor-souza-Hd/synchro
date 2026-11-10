
document.addEventListener("DOMContentLoaded", () => {
    const grid = document.getElementById("favoritesGrid");
    const feedback = document.getElementById("favoritesFeedback");
    const counter = document.getElementById("favoritesCount");
    const sidebar = document.querySelector(".sidebar");
    const toggle = document.getElementById("sidebarToggle");

    toggle?.addEventListener("click", () => {
        sidebar?.classList.toggle("active");
    });

    function showMessage(message, allowHtml = false) {
        if (allowHtml) {
            feedback.innerHTML = message;
        } else {
            feedback.textContent = message;
        }
        feedback.hidden = false;
    }

    function renderCard(item) {
        if (
            !item ||
            typeof item.artist !== "string" ||
            !item.artist.trim()
        ) {
            return;
        }

        const name = item.artist.trim();

        const card = document.createElement("article");
        card.className = "favorite-card";

        const link = document.createElement("a");
        link.className = "favorite-card-link";
        link.href =
            `/frontend/pages/artista.html?artist=${encodeURIComponent(name)}`;

        const photo = document.createElement("div");
        const info = document.createElement("div");
        info.className = "favorite-card-info";

        const title = document.createElement("h2");
        title.textContent = name;

        const kind = document.createElement("p");
        kind.textContent = "Artista • Ver perfil";

        info.append(title, kind);
        link.append(photo, info);
        card.append(link);

        grid.append(card);

        window.SynchroFavoriteImages.show(photo, {
            kind: "artist", title: name, artist: name,
            className: "favorite-card-photo", alt: `Imagem de ${name}`
        });
    }

    async function loadFavorites() {
        showMessage("Carregando seus artistas favoritos...");

        try {
            const response = await fetch("/favorites/artists", {
                credentials: "same-origin",
                headers: {
                    Accept: "application/json"
                }
            });

            if (
                response.status === 401 ||
                response.status === 403
            ) {
                showMessage(
                    'Sua sessão expirou. <a href="/TCC/TCC//frontend/pages/login.html">Entrar no Synchro</a>',
                    true
                );
                return;
            }

            if (!response.ok) {
                throw new Error(
                    `Erro HTTP ${response.status}`
                );
            }

            const items = await response.json();

            if (!Array.isArray(items)) {
                throw new Error(
                    "Resposta inesperada do servidor"
                );
            }

            grid.replaceChildren();

            counter.textContent =
                `${items.length} ${
                    items.length === 1
                        ? "artista salvo"
                        : "artistas salvos"
                }`;

            if (!items.length) {
                showMessage(
                    "Você ainda não favoritou artistas. Explore o Synchro, abra a página de um artista e clique em Favoritar."
                );
                return;
            }

            items.forEach(renderCard);

            feedback.hidden = true;

        } catch (error) {
            console.error(
                "Erro ao carregar favoritos:",
                error
            );

            showMessage(
                "Não foi possível carregar os favoritos. Tente atualizar a página."
            );
        }
    }

    loadFavorites();
});
