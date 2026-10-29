document.addEventListener("DOMContentLoaded", async () => {
    const params = new URLSearchParams(location.search);
    const albumId = params.get("id");

    const albumNameEl = document.getElementById("albumName");
    const albumArtistEl = document.getElementById("albumArtist");
    const trackListEl = document.getElementById("trackList");

    // Elementos de Review
    const status = document.getElementById("albumReviewStatus");
    const average = document.getElementById("albumReviewAverage");
    const rating = document.getElementById("albumReviewRating");
    const comment = document.getElementById("albumReviewText");
    const saveButton = document.getElementById("saveAlbumReview");
    const cancelButton = document.getElementById("cancelAlbumReview");
    const feedback = document.getElementById("albumReviewFeedback");
    const list = document.getElementById("albumReviewList");

    let myUsername = null;
    let myReview = null;

    async function requestJson(url, options = {}) {
        const response = await fetch(url, { credentials: "same-origin", ...options });
        if (!response.ok) throw new Error("HTTP " + response.status);
        if (response.status === 204) return null;
        return response.json();
    }

    // 1. Busca o álbum
    try {
        const album = await requestJson(`/album/id/${albumId}`);
        albumNameEl.textContent = album.titulo;
        const artistas = album.artistas ? album.artistas.map(a => a.nome).join(', ') : 'Artista Desconhecido';
        albumArtistEl.textContent = artistas;

        trackListEl.innerHTML = '';
        album.musicas.forEach(musica => {
            const div = document.createElement('div');
            div.style.cssText = 'display:flex; justify-content:space-between; padding:15px; border-bottom:1px solid rgba(255,255,255,0.1);';
            div.innerHTML = `
                <div>
                    <h3 style="color:white;">${musica.titulo}</h3>
                    <p style="color:#b3b3b3; font-size:12px;">${musica.duracao || ''}</p>
                </div>
                <a href="musica.html?artist=${encodeURIComponent(artistas)}&track=${encodeURIComponent(musica.titulo)}" style="color:#1db954;">
                    Ver Música <i class="bx bx-right-arrow-alt"></i>
                </a>
            `;
            trackListEl.appendChild(div);
        });
    } catch (error) {
        albumNameEl.textContent = "Erro ao carregar álbum";
        return;
    }

    // 2. Autenticação
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
            <h3 style="color: white; margin-bottom: 5px;">${review.author_name || "Usuário"} • ${review.rating}/10</h3>
            <p style="color: #b3b3b3;">${review.review || ""}</p>
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

    // Botão Limpar
    cancelButton.addEventListener("click", () => {
        rating.value = 8;
        comment.value = "";
        feedback.textContent = "";
    });

    // Botão Salvar (Aqui estava o erro! Agora ele vai anexar corretamente)
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
            comment.value = "";
            alert("Avaliação salva com sucesso!");
        } catch (error) {
            feedback.textContent = "Não foi possível salvar. Erro: " + error.message;
        } finally {
            saveButton.disabled = false;
        }
    });

    status.textContent = "";
    await Promise.all([loadReviews(), loadAverage()]);
});