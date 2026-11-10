document.addEventListener("synchro:album", async ({detail: {id: albumId}}) => {
    const escapeHtml = window.SynchroFeatures.escapeHtml;
    const status = document.getElementById("albumReviewStatus");
    const average = document.getElementById("albumReviewAverage");
    const rating = document.getElementById("albumReviewRating");
    const ratingItems = document.querySelectorAll(".rating-item");
    const comment = document.getElementById("albumReviewText");
    const saveButton = document.getElementById("saveAlbumReview");
    const cancelButton = document.getElementById("cancelAlbumReview");
    const feedback = document.getElementById("albumReviewFeedback");
    const list = document.getElementById("albumReviewList");

    let myUsername = null;
    let myReview = null;

    function atualizarBarraVisual(valor) {
        ratingItems.forEach(item => {
            if (parseInt(item.dataset.value) === parseInt(valor)) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });
    }

    ratingItems.forEach(item => {
        item.addEventListener('click', () => {
            const valor = parseInt(item.dataset.value);
            rating.value = valor;
            atualizarBarraVisual(valor);
        });
    });

    atualizarBarraVisual(8);

    async function requestJson(url, options = {}) {
        const response = await fetch(url, { credentials: "same-origin", ...options });
        if (!response.ok) throw new Error("HTTP " + response.status);
        if (response.status === 204) return null;
        return response.json();
    }

    try {
        const me = await requestJson("/users/me");
        myUsername = me.username || null;
    } catch { myUsername = null; }

    function addReviewCard(review) {
        const card = document.createElement("article");
        card.className = "music-review-card";
        card.style.background = "rgba(255, 255, 255, 0.05)";
        card.style.padding = "15px";
        card.style.borderRadius = "8px";
        card.style.marginBottom = "10px";
        card.style.borderLeft = "4px solid #ff1744";

        card.innerHTML = `
            <h3 style="color: white; margin-bottom: 5px;">${escapeHtml(review.author_name || "Usuário")} • ${escapeHtml(review.rating)}/10</h3>
            <p style="color: #b3b3b3;">${escapeHtml(review.review || "")}</p>
        `;

        if (myReview && review.id === myReview.id) {
            const edit = document.createElement("button");
            edit.textContent = "Editar";
            edit.style.marginTop = "10px";
            edit.style.marginRight = "10px";
            edit.style.background = "transparent";
            edit.style.color = "#2196f3";
            edit.style.border = "1px solid #2196f3";
            edit.style.padding = "5px 10px";
            edit.style.borderRadius = "5px";
            edit.style.cursor = "pointer";
            edit.addEventListener("click", () => {
                rating.value = review.rating;
                atualizarBarraVisual(review.rating);
                comment.value = review.review || "";
                window.scrollTo({ top: 400, behavior: 'smooth' });
            });

            const remove = document.createElement("button");
            remove.textContent = "Excluir";
            remove.style.marginTop = "10px";
            remove.style.background = "transparent";
            remove.style.color = "#f44336";
            remove.style.border = "1px solid #f44336";
            remove.style.padding = "5px 10px";
            remove.style.borderRadius = "5px";
            remove.style.cursor = "pointer";
            remove.addEventListener("click", async () => {
                if (!confirm("Excluir sua avaliação?")) return;
                await requestJson("/review/" + review.id, { method: "DELETE" });
                await loadReviews();
                await loadAverage();
            });
            card.append(edit, remove);
        }
        list.appendChild(card);
    }

    async function loadReviews() {
        const [allReviews, mine] = await Promise.all([
            requestJson("/review"),
            myUsername ? requestJson("/review/me").catch(()=>[]) : Promise.resolve([])
        ]);

        const relevant = Array.isArray(allReviews) ? allReviews.filter(r => Number(r.midiaId) === Number(albumId)) : [];
        myReview = Array.isArray(mine) ? mine.find(r => Number(r.midiaId) === Number(albumId)) || null : null;

        list.replaceChildren();
        if (!relevant.length) {
            list.innerHTML = "<p style='color:#b3b3b3;'>Este álbum ainda não possui avaliações.</p>";
        } else {
            relevant.forEach(addReviewCard);
        }
    }

    async function loadAverage() {
        try {
            const data = await requestJson(`/review/midia/${albumId}/rating`);
            average.textContent = data.total > 0
                ? `Nota média: ${data.media}/10 • ${data.total} avaliações`
                : "Ainda sem média de avaliações.";
        } catch {
            average.textContent = "Média indisponível.";
        }
    }

    cancelButton.addEventListener("click", () => {
        rating.value = 8;
        atualizarBarraVisual(8);
        comment.value = "";
        feedback.textContent = "";
    });

    saveButton.addEventListener("click", async () => {
        if (!myUsername) {
            feedback.textContent = "⚠️ Você precisa estar logado para salvar.";
            return;
        }

        const nota = Number(rating.value);
        const texto = comment.value.trim();

        if (nota < 0 || nota > 10) return feedback.textContent = "Nota de 0 a 10.";
        if (!texto) return feedback.textContent = "Escreva um comentário.";

        const editing = !!myReview;
        const url = editing ? `/review/${myReview.id}` : `/review/midia/${albumId}`;

        saveButton.disabled = true;
        feedback.textContent = "Salvando...";

        try {
            await requestJson(url, {
                method: editing ? "PUT" : "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ rating: nota, review: texto })
            });

            feedback.textContent = "";
            await loadReviews();
            await loadAverage();

            rating.value = 8;
            atualizarBarraVisual(8);
            comment.value = "";

            alert("Avaliação salva com sucesso!");
        } catch (error) {
            feedback.textContent = "Não foi possível salvar. Erro: " + error.message;
        } finally {
            saveButton.disabled = false;
        }
    });

    status.textContent = "";
    try {
        await Promise.all([loadReviews(), loadAverage()]);
    } catch {
        status.textContent = "Não foi possível carregar as avaliações.";
    }
}, {once: true});
