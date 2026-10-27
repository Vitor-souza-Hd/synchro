
document.addEventListener("DOMContentLoaded", async () => {

    const params = new URLSearchParams(location.search);
    const trackName = params.get("track")?.trim();
    const artistName = params.get("artist")?.trim();

    const status = document.getElementById("musicReviewStatus");
    const controls = document.getElementById("musicReviewControls");
    const average = document.getElementById("musicReviewAverage");
    const openButton = document.getElementById("openMusicReview");
    const editor = document.getElementById("musicReviewEditor");
    const rating = document.getElementById("musicReviewRating");
    const comment = document.getElementById("musicReviewText");
    const saveButton = document.getElementById("saveMusicReview");
    const cancelButton = document.getElementById("cancelMusicReview");
    const feedback = document.getElementById("musicReviewFeedback");
    const list = document.getElementById("musicReviewList");

    if (!status || !controls || !average || !openButton ||
        !editor || !rating || !comment || !saveButton ||
        !cancelButton || !feedback || !list) {
        return;
    }

    let midiaId = null;
    let myUsername = null;
    let myReview = null;

    const normalize = text =>
        String(text || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .trim()
            .toLowerCase();

    function message(text) {
        status.textContent = text;
    }

    async function requestJson(url, options = {}) {
        const response = await fetch(url, {
            credentials: "same-origin",
            ...options
        });

        if (!response.ok) {
            throw new Error("HTTP " + response.status);
        }

        if (response.status === 204) return null;
        return response.json();
    }

    function showFeedback(text) {
        feedback.textContent = text;
    }

    // Identifica a conta Synchro autenticada.
    try {
        const me = await requestJson("/users/me");
        myUsername = me.username || null;
    } catch {
        myUsername = null;
    }

    function artistMatches(item) {
        return Array.isArray(item.artistas) &&
            item.artistas.some(
                a => normalize(a.nome) === normalize(artistName)
            );
    }

    // Identifica a musica pelo titulo E pelo artista.
    // Nunca utiliza IDs arbitrarios.

    async function findRegisteredMusic() {
        const url =
            "/musica/identificar?titulo=" +
            encodeURIComponent(trackName) +
            "&artista=" +
            encodeURIComponent(artistName);

        const response = await fetch(url, {
            credentials: "same-origin",
            headers: {
                Accept: "application/json"
            }
        });

        // A musica ainda nao existe no banco.
        if (response.status === 404) {
            return null;
        }

        if (!response.ok) {
            throw new Error(
                "Erro ao identificar musica: HTTP " +
                response.status
            );
        }

        const music = await response.json();

        if (!Number.isInteger(Number(music.id)) ||
            Number(music.id) <= 0) {
            throw new Error("ID de musica invalido");
        }

        return music;
    }


    function addReviewCard(review) {
        const card = document.createElement("article");
        card.className = "music-review-card";

        const heading = document.createElement("h3");
        heading.textContent =
            `${review.author_name || "Usuário"} • ${review.rating}/10`;

        const text = document.createElement("p");
        text.textContent = review.review || "";

        card.append(heading, text);

        // O backend continua responsavel pela
        // verificacao real de permissao.
        if (myReview && review.id === myReview.id) {
            const edit = document.createElement("button");
            edit.type = "button";
            edit.textContent = "Editar";

            edit.addEventListener("click", () => {
                rating.value = review.rating;
                comment.value = review.review || "";
                editor.hidden = false;
                openButton.hidden = true;
                showFeedback("");
            });

            const remove = document.createElement("button");
            remove.type = "button";
            remove.textContent = "Excluir";

            remove.addEventListener("click", async () => {
                if (!confirm("Excluir sua avaliação?")) return;

                try {
                    await requestJson(
                        "/review/" + review.id,
                        { method: "DELETE" }
                    );

                    await loadReviews();
                    await loadAverage();
                    showFeedback("");

                } catch (error) {
                    showFeedback(
                        "Não foi possível excluir a avaliação."
                    );
                }
            });

            card.append(edit, remove);
        }

        list.appendChild(card);
    }

    async function loadReviews() {
        const [allReviews, mine] = await Promise.all([
            requestJson("/review"),
            myUsername
                ? requestJson("/review/me")
                : Promise.resolve([])
        ]);

        const relevant = Array.isArray(allReviews)
            ? allReviews.filter(
                r => Number(r.midiaId) === Number(midiaId)
            )
            : [];

        myReview = Array.isArray(mine)
            ? mine.find(
            r => Number(r.midiaId) === Number(midiaId)
        ) || null
            : null;

        list.replaceChildren();

        if (!relevant.length) {
            const p = document.createElement("p");
            p.textContent =
                "Esta música ainda não possui avaliações.";
            list.appendChild(p);
        } else {
            relevant.forEach(addReviewCard);
        }

        openButton.textContent = myReview
            ? "Editar minha avaliação"
            : "Avaliar música";
    }

    async function loadAverage() {
        try {
            const data = await requestJson(
                `/review/midia/${midiaId}/rating`
            );

            const count = Number(
                data.total ?? data.totalReviews ??
                data.quantidade ?? 0
            );

            const value =
                data.media ?? data.average ?? data.rating;

            average.textContent =
                value != null
                    ? `Nota média: ${value}/10` +
                    (count > 0 ? ` • ${count} avaliações` : "")
                    : "Ainda sem média de avaliações.";

        } catch {
            average.textContent =
                "Média indisponível no momento.";
        }
    }


    openButton.addEventListener("click", async () => {

        if (!myUsername) {
            message("Faça login no Synchro para avaliar.");
            return;
        }

        openButton.disabled = true;
        message("Preparando avaliação...");

        try {
            // Se a musica ainda nao estiver no banco,
            // solicita seu cadastro ao backend.
            if (!midiaId) {
                const url =
                    "/musica/registrar?titulo=" +
                    encodeURIComponent(trackName) +
                    "&artista=" +
                    encodeURIComponent(artistName);

                const music = await requestJson(url, {
                    method: "POST"
                });

                const id = Number(music?.id);

                if (!Number.isInteger(id) || id <= 0) {
                    throw new Error(
                        "O backend nao retornou um ID valido."
                    );
                }

                midiaId = id;

                // Atualiza avaliacoes e media da musica.
                await Promise.all([
                    loadReviews(),
                    loadAverage()
                ]);
            }

            rating.value = myReview?.rating ?? 8;
            comment.value = myReview?.review || "";

            saveButton.disabled = false;
            editor.hidden = false;
            openButton.hidden = true;

            message("");
            showFeedback("");

        } catch (error) {
            console.error(
                "Erro ao preparar avaliacao:",
                error
            );

            message(
                "Não foi possível preparar a avaliação. " +
                "Tente novamente."
            );

        } finally {
            openButton.disabled = false;
        }
    }
);

    cancelButton.addEventListener("click", () => {
        editor.hidden = true;
        openButton.hidden = false;
        showFeedback("");
    });

    saveButton.addEventListener("click", async () => {
        if (!myUsername || !midiaId) return;

        const nota = Number(rating.value);
        const texto = comment.value.trim();

        if (!Number.isInteger(nota) ||
            nota < 0 || nota > 10) {
            showFeedback("Informe uma nota inteira de 0 a 10.");
            return;
        }

        if (!texto || texto.length > 1000) {
            showFeedback("Escreva um comentário de até 1000 caracteres.");
            return;
        }

        const editing = !!myReview;
        const url = editing
            ? `/review/${myReview.id}`
            : `/review/midia/${midiaId}`;

        saveButton.disabled = true;
        showFeedback("Salvando...");

        try {
            await requestJson(url, {
                method: editing ? "PUT" : "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    rating: nota,
                    review: texto
                })
            });

            editor.hidden = true;
            openButton.hidden = false;
            showFeedback("");

            await loadReviews();
            await loadAverage();

        } catch (error) {
            console.error(error);
            showFeedback(
                "Não foi possível salvar a avaliação."
            );
        } finally {
            saveButton.disabled = false;
        }
    });

    if (!trackName || !artistName) {
        message("Música não identificada.");
        return;
    }

    // Consulta inicial.
    try {
        const music = await findRegisteredMusic();


        if (!music) {
            midiaId = null;

            // A musica ainda nao foi cadastrada,
            // mas o usuario podera iniciar a avaliacao.
            controls.hidden = false;

            average.textContent =
                "Esta música ainda não possui avaliações no Synchro.";

            openButton.textContent = "Avaliar música";

            message(
                "Ao avaliar, esta música será cadastrada " +
                "automaticamente no Synchro."
            );

            return;
        }


        midiaId = Number(music.id);

        controls.hidden = false;
        message("");

        await Promise.all([
            loadReviews(),
            loadAverage()
        ]);

    } catch (error) {
        console.error(
            "Erro ao identificar mídia para avaliação:",
            error
        );

        message(
            "Não foi possível consultar o cadastro da música."
        );
    }
});
