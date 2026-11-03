
(() => {
    "use strict";

    const THEME_KEY = "synchro:theme:v1";

    function getTheme() {
        try {
            return localStorage.getItem(THEME_KEY) === "light"
                ? "light"
                : "dark";
        } catch {
            return "dark";
        }
    }

    function applyTheme() {
        const isLight = getTheme() === "light";

        document.documentElement.classList.toggle(
            "synchro-light",
            isLight
        );

        document.documentElement.classList.toggle(
            "synchro-dark",
            !isLight
        );
    }

    // Executa assim que o script e carregado,
    // antes da renderizacao principal.
    applyTheme();

    // Sincroniza mudancas feitas em outra aba.
    window.addEventListener("storage", event => {
        if (event.key === THEME_KEY) {
            applyTheme();
        }
    });

    window.SynchroTheme = {
        getTheme,
        applyTheme
    };
})();
