
document.addEventListener("DOMContentLoaded", () => {

    const home = "index2.html";

    const routes = new Map([
        ["explorar", home],
        ["inicio", home],
        ["gêneros", home + "#generos"],
        ["álbuns", home + "#albuns"],
        ["artistas", home + "#artistas"],
        ["favoritos", "favoritos.html"],
        ["recentes", "recentes.html"],
        ["músicas", home + "#pesquisa"],
        ["sobre o synchro", home + "#sobre"]
    ]);

    function normalize(text) {
        return String(text || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .trim()
            .toLowerCase();
    }

    // Apenas links de navegação.
    // Não altera links de músicas, cartões
    // de álbuns, avaliações ou favoritos.

    const links = document.querySelectorAll(
        ".sidebar a, .nav-footer a, " +
        ".search-bar .nav-links a"
    );

    links.forEach(link => {

        const label = normalize(link.textContent);

        // Evita alterar "Artistas" do cabeçalho
        // de maneira diferente da barra lateral.
        const match = [...routes.entries()].find(
            ([name]) => normalize(name) === label
        );

        if (!match) return;

        const destination = match[1];

        // Só altera destinos conhecidos.
        link.href = destination;
        link.removeAttribute("aria-disabled");
    });

    // =========================================
    // LINK GLOBAL DE CONFIGURACOES
    // =========================================

    const sidebar = document.querySelector(".sidebar");

    if (
        sidebar &&
        !sidebar.querySelector(
            'a[href="configuracoes.html"]'
        )
    ) {
        const menus = sidebar.querySelectorAll(".menu");

        const libraryMenu = [...menus].find(menu =>
            normalize(
                menu.querySelector("h5")?.textContent
            ) === "sua biblioteca"
        );

        const list =
            libraryMenu?.querySelector("ul") ||
            menus[0]?.querySelector("ul");

        if (list) {
            const item = document.createElement("li");

            const icon = document.createElement("i");
            icon.className = "bx bxs-cog";

            const link = document.createElement("a");
            link.href = "configuracoes.html";
            link.textContent = "Configurações";

            item.append(icon, link);
            list.appendChild(item);
        }
    }

    // A barra de pesquisa da página inicial
    // recebe foco quando acessada por #pesquisa.

    function focusSearch() {

        if (location.hash !== "#pesquisa") return;

        const search = document.querySelector(
            ".search"
        );

        search?.focus();
    }

    window.addEventListener("hashchange", focusSearch);

    if (document.readyState === "complete") {
        focusSearch();
    } else {
        window.addEventListener("load", focusSearch, {
            once: true
        });
    }
});
