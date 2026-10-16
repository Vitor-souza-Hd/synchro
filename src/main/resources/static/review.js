document.addEventListener('DOMContentLoaded', () => {

    const modal = document.getElementById('review-modal');
    const closeBtn = document.getElementById('close-review-modal');
    const title = document.getElementById('review-modal-title');
    const subtitle = document.getElementById('review-modal-subtitle');
    const ratingInput = document.getElementById('review-rating');
    const textInput = document.getElementById('review-text');
    const submitBtn = document.getElementById('btn-submit-review');
    const feedback = document.getElementById('review-feedback');

    let currentMidiaId = null;       // usado para CRIAR (POST)
    let currentReviewId = null;      // usado para EDITAR (PUT)
    let usuarioLogado = null;        // dados do usuário logado

    // ==========================================
    // busca para ver se o usuario está logado
    // ==========================================
    async function carregarUsuarioLogado() {
        try {
            const r = await fetch('/auth/me');
            if (r.ok) {
                usuarioLogado = await r.json();
                console.log('Usuário logado:', usuarioLogado);
            } else {
                usuarioLogado = null;
            }
        } catch {
            usuarioLogado = null;
        }
    }

    // ==========================================
    // ABRIR modal para nova review
    // ==========================================
    window.abrirModalReview = function (midiaId, titulo) {
        currentMidiaId = midiaId;
        currentReviewId = null;   // modo criar
        title.textContent = 'Avaliar';
        subtitle.textContent = titulo || `Mídia #${midiaId}`;
        ratingInput.value = 8;
        textInput.value = '';
        feedback.textContent = '';
        feedback.className = 'feedback';
        submitBtn.textContent = 'Enviar Review';
        modal.classList.add('active');
    };

    // ==========================================
    // ABRIR modal para editar uma review existente
    // ==========================================
    window.abrirModalEditarReview = function (reviewId, midiaId, titulo, rating, texto) {
        currentReviewId = reviewId;
        currentMidiaId = midiaId;   // usamos para atualizar o rating do card
        title.textContent = 'Editar Avaliação';
        subtitle.textContent = titulo || `Mídia #${midiaId}`;
        ratingInput.value = rating;
        textInput.value = texto;
        feedback.textContent = '';
        feedback.className = 'feedback';
        submitBtn.textContent = 'Salvar Alterações';
        modal.classList.add('active');
    };

    function fecharModal() {
        modal.classList.remove('active');
        currentMidiaId = null;
        currentReviewId = null;
    }

    closeBtn?.addEventListener('click', fecharModal);
    modal?.addEventListener('click', (e) => {
        if (e.target === modal) fecharModal();
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') fecharModal();
    });

    // ==========================================
    // ENVIAR review
    // ==========================================
    submitBtn?.addEventListener('click', async () => {
        if (!currentMidiaId && !currentReviewId) return;

        feedback.textContent = '';
        feedback.className = 'feedback';

        const rating = parseInt(ratingInput.value, 10);
        const review = textInput.value.trim();

        if (isNaN(rating) || rating < 0 || rating > 10) {
            feedback.textContent = 'A nota deve estar entre 0 e 10.';
            feedback.className = 'feedback error';
            return;
        }

        if (review.length === 0) {
            feedback.textContent = 'Escreva algo na review.';
            feedback.className = 'feedback error';
            return;
        }

        submitBtn.disabled = true;
        submitBtn.textContent = 'Enviando...';

        const isEdicao = currentReviewId !== null;
        const url = isEdicao
            ? `/review/${currentReviewId}`
            : `/review/midia/${currentMidiaId}`;
        const method = isEdicao ? 'PUT' : 'POST';

        try {
            const response = await fetch(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ rating, review })
            });

            if (response.ok) {
                const data = await response.json();
                feedback.textContent = isEdicao
                    ? 'Review atualizada com sucesso!'
                    : `Review enviada com sucesso! Obrigado, ${data.author_name}.`;
                feedback.className = 'feedback success';
                textInput.value = '';
                setTimeout(fecharModal, 1500);
                carregarReviews();
            } else if (response.status === 401) {
                feedback.textContent = 'Você precisa fazer login para avaliar.';
                feedback.className = 'feedback error';
            } else if (response.status === 404) {
                feedback.textContent = 'Mídia não encontrada.';
                feedback.className = 'feedback error';
            } else if (response.status === 400) {
                feedback.textContent = 'Dados inválidos. Verifique a nota e o texto.';
                feedback.className = 'feedback error';
            } else {
                feedback.textContent = `Erro ${response.status}. Tente novamente.`;
                feedback.className = 'feedback error';
            }
        } catch (error) {
            console.error('Erro ao enviar review:', error);
            feedback.textContent = 'Erro de conexão com o servidor.';
            feedback.className = 'feedback error';
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = isEdicao ? 'Salvar Alterações' : 'Enviar Review';
        }
    });

    // =============================
    // deletar review
    // ============================
    window.excluirReview = async function (reviewId) {
        if (!confirm('Tem certeza que quer excluir esta review?')) return;

        try {
            const response = await fetch(`/review/${reviewId}`, {
                method: 'DELETE',
                credentials: 'include'
            });

            if (response.ok || response.status === 204) {
                carregarReviews();
            } else if (response.status === 401) {
                alert('Você não tem permissão para excluir esta review.');
            } else {
                alert(`Erro ${response.status} ao excluir.`);
            }
        } catch (error) {
            console.error('Erro ao excluir:', error);
            alert('Erro de conexão.');
        }
    };

    // ==========================================
    // carrega E traz REVIEWS
    // ==========================================
    async function carregarReviews() {
        try {
            const response = await fetch('/review');
            if (!response.ok) return;

            const reviews = await response.json();
            const container = document.querySelector('.reviews');
            if (!container) return;

            if (reviews.length === 0) {
                container.innerHTML = '<p style="color:#b3b3b3;">Nenhuma review ainda.</p>';
                return;
            }

            container.innerHTML = reviews.map(r => {
                // Verifica se a review é do usuário q ta logado
                const isAutor = usuarioLogado && r.author_name === usuarioLogado.username;

                const botoesAcao = isAutor
                    ? `
                        <button class="btn-edit-review"
                                onclick="event.stopPropagation(); event.preventDefault();
                                         abrirModalEditarReview(${r.id}, ${r.midiaId}, '${r.midiaTitulo}', ${r.rating}, '${(r.review || '').replace(/'/g, "\\'")}')">
                            <i class="fa-solid fa-pen"></i> Editar
                        </button>
                        <button class="btn-delete-review"
                                onclick="event.stopPropagation(); event.preventDefault();
                                         excluirReview(${r.id})">
                            <i class="fa-solid fa-trash"></i> Excluir
                        </button>
                    `
                    : '';

                return `
                    <div class="reviews-card">
                        <div class="reviews-header">
                            <div class="review-info">
                                <h3>${r.midiaTitulo || 'Mídia'}</h3>
                                <h5>por ${r.author_name || 'Anônimo'}</h5>
                                <span class="rating">${r.rating}/10</span>
                            </div>
                        </div>
                        <p class="review-text">${r.review}</p>
                        <div class="review-user">
                            <div class="review-actions">
                                ${botoesAcao}
                            </div>
                            <button class="btn-avaliar"
                                    onclick="event.stopPropagation(); event.preventDefault();
                                             abrirModalReview(${r.midiaId || 1}, '${r.midiaTitulo || 'Mídia'}')">
                                <i class="fa-solid fa-star"></i> Avaliar
                            </button>
                        </div>
                    </div>
                `;
            }).join('');
        } catch (err) {
            console.warn('Erro ao carregar reviews:', err);
        }
    }


    (async () => {
        await carregarUsuarioLogado();
        await carregarReviews();
    })();

});