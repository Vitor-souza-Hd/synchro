
document.addEventListener("DOMContentLoaded", () => {
    const grid = document.getElementById("favoritesGrid");
    const feedback = document.getElementById("favoritesFeedback");
    const counter = document.getElementById("favoritesCount");
    const sidebar = document.querySelector(".sidebar");
    const toggle = document.getElementById("sidebarToggle");

    toggle?.addEventListener("click", () => {
        sidebar?.classList.toggle("active");
    });

    const placeholder = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(`
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240">
          <rect width="240" height="240" fill="#323038"/>
          <circle cx="120" cy="92" r="38" fill="#77737d"/>
          <path d="M42 217c0-45 35-76 78-76s78 31 78 76" fill="#77737d"/>
        </svg>
    `)}`;

    function showMessage(message, allowHtml = false) {
        if (allowHtml) {
            feedback.innerHTML = message;
        } else {
            feedback.textContent = message;
        }
        feedback.hidden = false;
    }

    function safePhoto(url) {
        try {
            const parsed = new URL(url);
            return parsed.protocol === "https:" &&
                (parsed.hostname === "upload.wikimedia.org" ||
                    parsed.hostname.endsWith(".wikimedia.org"));
        } catch {
            return false;
        }
    }

    function normalize(value) {
        return String(value || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .toLowerCase()
            .replace(/_/g, " ")
            .replace(/\s+/g, " ")
            .trim();
    }

    async function wikiPhoto(name, lang) {
        const url = new URL(`https://${lang}.wikipedia.org/w/api.php`);
        url.searchParams.set("action", "query");
        url.searchParams.set("titles", name);
        url.searchParams.set("prop", "pageimages");
        url.searchParams.set("piprop", "thumbnail");
        url.searchParams.set("pithumbsize", "500");
        url.searchParams.set("format", "json");
        url.searchParams.set("formatversion", "2");
        url.searchParams.set("redirects", "1");
        url.searchParams.set("origin", "*");

        const response = await fetch(url);

        if (!response.ok) return null;

        const data = await response.json();
        const page = data.query?.pages?.[0];

        if (!page || page.missing) return null;

        const validNames = new Set([normalize(name)]);

        for (const redirect of (data.query?.redirects || [])) {
            if (normalize(redirect.from) === normalize(name)) {
                validNames.add(normalize(redirect.to));
            }
        }

        if (!validNames.has(normalize(page.title))) return null;

        return safePhoto(page.thumbnail?.source)
            ? page.thumbnail.source
            : null;
    }

    async function loadPhoto(img, name) {
        for (const lang of ["pt", "en"]) {
            try {
                const url = await wikiPhoto(name, lang);

                if (url) {
                    const test = new Image();

                    test.onload = () => {
                        img.src = url;
                    };

                    test.src = url;
                    return;
                }
            } catch (error) {
                console.warn(
                    "Imagem indisponível:",
                    name,
                    error
                );
            }
        }
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
            `artista.html?artist=${encodeURIComponent(name)}`;

        const img = document.createElement("img");
        img.className = "favorite-card-photo";
        img.src = placeholder;
        img.alt = `Imagem de ${name}`;
        img.loading = "lazy";

        const info = document.createElement("div");
        info.className = "favorite-card-info";

        const title = document.createElement("h2");
        title.textContent = name;

        const kind = document.createElement("p");
        kind.textContent = "Artista • Ver perfil";

        info.append(title, kind);
        link.append(img, info);
        card.append(link);

        grid.append(card);

        loadPhoto(img, name);
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
                    'Sua sessão expirou. <a href="/TCC/TCC/tcc/LOGIN/login.html">Entrar no Synchro</a>',
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
