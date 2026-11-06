
document.addEventListener("synchro:own-profile", () => {

    const editButton = document.getElementById("editPhotoButton");
    const editor = document.getElementById("photoEditor");
    const input = document.getElementById("photoUrlInput");
    const saveButton = document.getElementById("savePhotoButton");
    const removeButton = document.getElementById("removePhotoButton");
    const cancelButton = document.getElementById("cancelPhotoButton");
    const feedback = document.getElementById("photoFeedback");

    if (!editButton || !editor || !input ||
        !saveButton || !removeButton ||
        !cancelButton || !feedback) {
        return;
    }

    const profileImage = document.getElementById("profileImage");
    let customImageUrl = null;
    let busy = false;

    function mensagem(texto) {
        feedback.textContent = texto;
    }

    function validarUrl(valor) {
        try {
            const url = new URL(valor);

            return url.protocol === "https:" &&
                !!url.hostname &&
                !url.username &&
                !url.password &&
                valor.length <= 2000;
        } catch {
            return false;
        }
    }

    function atualizarBotoes() {
        saveButton.disabled = busy;
        removeButton.disabled = busy || !customImageUrl;
        cancelButton.disabled = busy;
        editButton.disabled = busy;
    }

    function aplicarFotografia() {
        profileImage.dataset.customPhoto = customImageUrl || "";
        showProfilePhoto(profileImage);
    }

    async function carregarFotografia() {
        try {
            const response = await fetch("/users/me/profile", {
                credentials: "same-origin",
                headers: {
                    Accept: "application/json"
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const data = await response.json();

            const url = data.profileImageUrl;

            customImageUrl =
                typeof url === "string" && validarUrl(url)
                    ? url
                    : null;

            input.value = customImageUrl || "";
            aplicarFotografia();
            atualizarBotoes();

        } catch (error) {
            console.error(
                "Erro ao carregar fotografia do perfil:",
                error
            );

            mensagem("Nao foi possivel consultar sua fotografia.");
        }
    }

    editButton.addEventListener("click", () => {
        editor.hidden = false;
        editButton.hidden = true;
        input.value = customImageUrl || "";
        mensagem("");
        atualizarBotoes();
        input.focus();
    });

    cancelButton.addEventListener("click", () => {
        editor.hidden = true;
        editButton.hidden = false;
        mensagem("");
    });

    saveButton.addEventListener("click", async () => {
        const url = input.value.trim();

        if (!validarUrl(url)) {
            mensagem("Informe uma URL HTTPS valida.");
            return;
        }

        busy = true;
        atualizarBotoes();
        mensagem("Salvando fotografia...");

        try {
            const response = await fetch(
                "/users/me/profile/image",
                {
                    method: "PUT",
                    credentials: "same-origin",
                    headers: {
                        "Content-Type": "application/json",
                        Accept: "application/json"
                    },
                    body: JSON.stringify({
                        imageUrl: url
                    })
                }
            );

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const data = await response.json();

            customImageUrl = data.profileImageUrl;
            aplicarFotografia();

            mensagem("Fotografia salva com sucesso!");

        } catch (error) {
            console.error("Erro ao salvar fotografia:", error);
            mensagem("Nao foi possivel salvar a fotografia.");

        } finally {
            busy = false;
            atualizarBotoes();
        }
    });

    removeButton.addEventListener("click", async () => {
        busy = true;
        atualizarBotoes();
        mensagem("Removendo fotografia...");

        try {
            const response = await fetch(
                "/users/me/profile/image",
                {
                    method: "DELETE",
                    credentials: "same-origin",
                    headers: {
                        Accept: "application/json"
                    }
                }
            );

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            customImageUrl = null;
            input.value = "";
            aplicarFotografia();

            mensagem("Fotografia personalizada removida!");

        } catch (error) {
            console.error("Erro ao remover fotografia:", error);
            mensagem("Nao foi possivel remover a fotografia.");

        } finally {
            busy = false;
            atualizarBotoes();
        }
    });

    atualizarBotoes();
    carregarFotografia();
});
