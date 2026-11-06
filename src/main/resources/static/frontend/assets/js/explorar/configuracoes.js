
document.addEventListener("DOMContentLoaded", () => {

    const THEME_KEY = "synchro:theme:v1";
    const HISTORY_KEY = "synchro:recentes:v1";
    const ENABLED_KEY = "synchro:history-enabled:v1";

    const theme = document.getElementById(
        "themePreference"
    );
    const historyEnabled = document.getElementById(
        "historyEnabled"
    );
    const clearHistory = document.getElementById(
        "clearHistory"
    );
    const clearImages = document.getElementById(
        "clearImageCache"
    );
    const feedback = document.getElementById(
        "settingsFeedback"
    );

    function read(key, fallback) {
        try {
            return localStorage.getItem(key) ?? fallback;
        } catch {
            return fallback;
        }
    }

    function save(key, value) {
        try {
            localStorage.setItem(key, value);
            return true;
        } catch {
            feedback.textContent =
                "Não foi possível salvar a preferência.";
            return false;
        }
    }

    function message(text) {
        feedback.textContent = text;
    }

    function applyTheme(value) {
        window.SynchroTheme?.applyTheme();
        document.body.classList.toggle(
            "settings-light",
            value === "light"
        );
    }

    // APARENCIA

    theme.value = read(THEME_KEY, "dark");
    applyTheme(theme.value);

    theme.addEventListener("change", () => {
        if (save(THEME_KEY, theme.value)) {
            applyTheme(theme.value);
            message("Preferência de aparência salva.");
        }
    });

    // HISTORICO

    historyEnabled.checked =
        read(ENABLED_KEY, "true") !== "false";

    historyEnabled.addEventListener("change", () => {
        if (save(
            ENABLED_KEY,
            String(historyEnabled.checked)
        )) {
            message(
                historyEnabled.checked
                    ? "Registro de Recentes ativado."
                    : "Registro de Recentes desativado."
            );
        }
    });

    clearHistory.addEventListener("click", () => {
        if (!confirm("Apagar todo o histórico de Recentes?")) {
            return;
        }

        try {
            localStorage.removeItem(HISTORY_KEY);
            message("Histórico local apagado.");
        } catch {
            message("Não foi possível apagar o histórico.");
        }
    });

    // CACHE DE IMAGENS

    clearImages.addEventListener("click", () => {
        if (!confirm(
            "Limpar as URLs de capas salvas nesta aba?"
        )) {
            return;
        }

        let removed = 0;

        try {
            const keys = [];

            for (let i = 0; i < sessionStorage.length; i++) {
                const key = sessionStorage.key(i);

                if (
                    key?.startsWith("synchro:recent-cover:") ||
                    key?.startsWith("synchro:track-cover:") ||
                    key?.startsWith("synchro:album-cover:")
                ) {
                    keys.push(key);
                }
            }

            keys.forEach(key => {
                sessionStorage.removeItem(key);
                removed++;
            });

            message(
                `${removed} registros de capas removidos.`
            );

        } catch {
            message(
                "Não foi possível limpar o cache de capas."
            );
        }
    });
});
