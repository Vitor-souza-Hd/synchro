
document.addEventListener("DOMContentLoaded", () => {

    // Quando um link aponta para #pesquisa,
    // coloca o cursor na barra de busca.

    function focusSearch() {
        if (window.location.hash !== "#pesquisa") {
            return;
        }

        const input = document.getElementById("pesquisa");

        if (!input) return;

        input.focus();
    }

    window.addEventListener("hashchange", focusSearch);

    focusSearch();
});
