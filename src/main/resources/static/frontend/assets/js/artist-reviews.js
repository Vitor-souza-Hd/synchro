document.addEventListener('synchro:artist', ({detail: artist}) => {
    const section = document.getElementById('artistReviews');
    const average = document.getElementById('artistReviewAverage');
    const status = document.getElementById('artistReviewStatus');
    const list = document.getElementById('artistReviewList');
    const more = document.getElementById('moreArtistReviews');
    const modal = document.getElementById('reviewModal');
    const form = document.getElementById('reviewForm');
    const login = document.getElementById('artistReviewLogin');
    const rating = document.getElementById('reviewRating');
    const comment = document.getElementById('reviewText');
    const formStatus = document.getElementById('reviewError');
    const count = document.getElementById('charCount');
    const publish = form.querySelector('[type="submit"]');
    const open = document.querySelector('.open-review');
    const close = document.getElementById('closeReview');
    const path = '/review/artists/' + encodeURIComponent(artist.id);
    const number = new Intl.NumberFormat('pt-BR', {maximumFractionDigits: 1});
    let own;
    let signedIn;
    let beforeNext;
    let latestLoad = 0;
    let saving = false;

    section.hidden = false;
    document.getElementById('reviewArtist').textContent = artist.name;

    async function request(url, options = {}) {
        const response = await fetch(url, {
            cache: 'no-store', credentials: 'same-origin', signal: AbortSignal.timeout(10000), ...options
        });
        if (!response.ok) {
            const error = new Error('Avaliação indisponível.');
            error.status = response.status;
            throw error;
        }
        return response.status === 204 ? null : response.json();
    }

    function updateCount() {
        count.textContent = comment.value.length + '/' + comment.maxLength;
    }

    function openReview() {
        form.hidden = !signedIn;
        login.hidden = signedIn !== false;
        formStatus.textContent = '';
        if (signedIn) {
            rating.value = own?.rating ?? '';
            comment.value = own?.review ?? '';
            publish.textContent = own ? 'Salvar alterações' : 'Publicar';
            updateCount();
        }
        modal.classList.add('active');
        modal.setAttribute('aria-hidden', 'false');
        document.body.style.overflow = 'hidden';
        (signedIn ? rating : close).focus();
    }

    function closeReview() {
        if (!modal.classList.contains('active')) return;
        modal.classList.remove('active');
        modal.setAttribute('aria-hidden', 'true');
        document.body.style.overflow = '';
        open.focus();
    }

    async function deleteReview() {
        if (saving || !own || !window.confirm('Excluir sua avaliação?')) return;
        saving = true;
        try {
            await request('/review/' + encodeURIComponent(own.id), {method: 'DELETE'});
            own = null;
            await load();
        } catch {
            status.textContent = 'Não foi possível excluir a avaliação.';
        } finally {
            saving = false;
        }
    }

    function reviewCard(review) {
        const card = document.createElement('article');
        card.className = 'artist-review-card';
        const heading = document.createElement('div');
        const author = document.createElement('a');
        author.href = 'perfil-publico.html?' + new URLSearchParams({user: review.authorId});
        author.textContent = '@' + review.username;
        const score = document.createElement('strong');
        score.textContent = number.format(review.rating) + '/10';
        heading.append(author, score);
        if (review.id === own?.id) {
            const edit = document.createElement('button');
            edit.type = 'button';
            edit.textContent = 'Editar';
            edit.addEventListener('click', openReview);
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.textContent = 'Excluir';
            remove.addEventListener('click', deleteReview);
            heading.append(edit, remove);
        }
        card.append(heading);
        if (review.review) {
            const text = document.createElement('p');
            text.textContent = review.review;
            card.append(text);
        }
        return card;
    }

    async function load(before = null) {
        const loadId = ++latestLoad;
        more.disabled = true;
        status.textContent = 'Carregando avaliações…';
        try {
            const query = before == null ? '' : '?' + new URLSearchParams({before});
            const data = await request(path + query);
            if (loadId !== latestLoad) return;
            own = data.own;
            signedIn = data.signedIn;
            const cards = data.reviews.items.map(reviewCard);
            if (before == null) list.replaceChildren(...cards);
            else list.append(...cards);
            average.textContent = data.total ? number.format(data.average) + '/10 · ' + data.total
                + (data.total === 1 ? ' avaliação' : ' avaliações') : '';
            status.textContent = data.total ? '' : 'Este artista ainda não recebeu avaliações.';
            beforeNext = data.reviews.nextBefore;
            more.textContent = 'Ver mais avaliações';
            more.hidden = beforeNext == null;
        } catch {
            if (loadId !== latestLoad) return;
            status.textContent = 'Não foi possível carregar as avaliações.';
            beforeNext = before;
            more.hidden = false;
            more.textContent = 'Tentar novamente';
        } finally {
            if (loadId === latestLoad) more.disabled = false;
        }
    }

    open.addEventListener('click', async () => {
        if (signedIn === undefined) {
            open.disabled = true;
            await load();
            open.disabled = false;
            if (signedIn === undefined) return;
        }
        openReview();
    });
    close.addEventListener('click', closeReview);
    document.getElementById('cancelReview').addEventListener('click', closeReview);
    modal.addEventListener('click', event => { if (event.target === modal) closeReview(); });
    document.addEventListener('keydown', event => { if (event.key === 'Escape') closeReview(); });
    comment.addEventListener('input', updateCount);
    more.addEventListener('click', () => load(beforeNext));

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (saving || !form.reportValidity()) return;
        const text = comment.value.trim();
        if (!text) {
            formStatus.textContent = 'Escreva seu comentário.';
            return;
        }
        publish.disabled = true;
        saving = true;
        try {
            own = await request(path, {
                method: 'POST', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({rating: Number(rating.value), review: text})
            });
            closeReview();
            await load();
        } catch (error) {
            if (error.status === 401) {
                signedIn = false;
                openReview();
            } else {
                formStatus.textContent = 'Não foi possível salvar a avaliação. Tente novamente.';
            }
        } finally {
            saving = false;
            publish.disabled = false;
        }
    });

    load();
}, {once: true});
