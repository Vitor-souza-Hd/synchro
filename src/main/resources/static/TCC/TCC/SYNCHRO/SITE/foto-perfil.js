
document.addEventListener("DOMContentLoaded", () => {

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

    let profileImage = null;
    let originalImageUrl = "";
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

    // O user3.js cria a imagem do usuario
    // dinamicamente, depois de consultar o backend.
    // Este observador identifica quando ela aparece.
    function encontrarImagem() {
        const imagem = document.querySelector(".user-card img");

        if (!imagem) {
            return false;
        }

        profileImage = imagem;
        originalImageUrl = imagem.src;

        return true;
    }

    const observer = new MutationObserver(() => {
        if (encontrarImagem()) {
            observer.disconnect();
            aplicarFotografia();
        }
    });

    if (!encontrarImagem()) {
        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    }

    function aplicarFotografia() {
        if (!profileImage) {
            return;
        }

        if (customImageUrl) {
            profileImage.onerror = () => {
                console.warn(
                    "Nao foi possivel exibir a foto personalizada."
                );

                profileImage.onerror = null;
                profileImage.src = originalImageUrl;

                mensagem(
                    "A URL foi salva, mas a imagem nao carregou."
                );
            };

            profileImage.src = customImageUrl;
            profileImage.alt = "Fotografia personalizada do perfil";

        } else {
            profileImage.onerror = null;
            profileImage.src = originalImageUrl;
            profileImage.alt = "Fotografia do perfil";
        }
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
