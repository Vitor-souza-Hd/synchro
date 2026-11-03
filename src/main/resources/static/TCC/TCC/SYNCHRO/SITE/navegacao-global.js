
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
