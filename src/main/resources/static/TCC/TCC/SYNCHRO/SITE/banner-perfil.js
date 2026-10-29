
document.addEventListener("DOMContentLoaded", () => {

    const banner = document.querySelector(".banner-background");
    const edit = document.getElementById("editBannerButton");
    const editor = document.getElementById("bannerEditor");
    const input = document.getElementById("bannerUrlInput");
    const save = document.getElementById("saveBannerButton");
    const remove = document.getElementById("removeBannerButton");
    const cancel = document.getElementById("cancelBannerButton");
    const feedback = document.getElementById("bannerFeedback");

    if (!banner || !edit || !editor || !input ||
        !save || !remove || !cancel || !feedback) {
        return;
    }

    let currentUrl = "";

    function validUrl(value) {
        try {
            const url = new URL(value);
            return url.protocol === "https:" &&
                !!url.hostname &&
                !url.username &&
                !url.password &&
                value.length <= 2000;
        } catch {
            return false;
        }
    }

    function render(url) {
        currentUrl = validUrl(url) ? url : "";

        banner.style.backgroundImage = currentUrl
            ? `url("${currentUrl.replace(/"/g, "%22")}")`
            : "none";

        input.value = currentUrl;
        remove.disabled = !currentUrl;
    }

    async function update(url) {
        const response = await fetch("/users/me/banner", {
            method: "PUT",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                Accept: "application/json"
            },
            body: JSON.stringify({ imageUrl: url })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        return response.json();
    }

    edit.addEventListener("click", () => {
        editor.hidden = false;
        edit.hidden = true;
        feedback.textContent = "";
        input.focus();
    });

    cancel.addEventListener("click", () => {
        input.value = currentUrl;
        editor.hidden = true;
        edit.hidden = false;
        feedback.textContent = "";
    });

    save.addEventListener("click", async () => {
        const url = input.value.trim();

        if (!validUrl(url)) {
            feedback.textContent =
                "Informe uma URL HTTPS válida.";
            return;
        }

        save.disabled = true;
        feedback.textContent = "Salvando...";

        try {
            const data = await update(url);
            render(data.bannerImageUrl);
            feedback.textContent = "";
            editor.hidden = true;
            edit.hidden = false;
        } catch (error) {
            console.error(error);
            feedback.textContent =
                "Não foi possível salvar o banner.";
        } finally {
            save.disabled = false;
        }
    });

    remove.addEventListener("click", async () => {
        remove.disabled = true;
        feedback.textContent = "Removendo...";

        try {
            const data = await update("");
            render(data.bannerImageUrl);
            feedback.textContent = "";
            editor.hidden = true;
            edit.hidden = false;
        } catch (error) {
            console.error(error);
            feedback.textContent =
                "Não foi possível remover o banner.";
        } finally {
            remove.disabled = false;
            remove.disabled = !currentUrl;
        }
    });

    async function load() {
        try {
            const response = await fetch("/users/me/banner", {
                credentials: "same-origin"
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const data = await response.json();
            render(data.bannerImageUrl);
        } catch (error) {
            console.error("Erro ao carregar banner:", error);
            render("");
        }
    }

    render("");
    load();
});
