(() => {
    const trigger = document.getElementById('addToPlaylistBtn');
    const dialog = document.getElementById('songPlaylistDialog');
    const form = document.getElementById('songPlaylistForm');
    const choices = document.getElementById('songPlaylistChoices');
    const cards = document.getElementById('songPlaylistCards');
    const feedback = document.getElementById('songPlaylistFeedback');
    const login = document.getElementById('songPlaylistLogin');
    const library = document.getElementById('songPlaylistLibrary');
    const submit = document.getElementById('songPlaylistSubmit');
    const close = document.getElementById('songPlaylistClose');
    const retry = document.getElementById('songPlaylistRetry');
    let saving = false;
    let controller;

    function imageUrl(value) {
        if (!value) return null;
        if (/^data:image\/(png|jpeg|webp|gif);base64,/i.test(value)) return value;
        try {
            const url = new URL(value, location.href);
            return ['http:', 'https:'].includes(url.protocol) ? url.href : null;
        } catch { return null; }
    }

    function playlistCard(playlist) {
        const label = document.createElement('label');
        label.className = 'song-playlist-card';
        const radio = document.createElement('input');
        radio.type = 'radio';
        radio.name = 'playlist';
        radio.value = String(playlist.id);
        radio.dataset.title = playlist.titulo;
        radio.required = true;
        const preview = document.createElement('span');
        preview.className = 'song-playlist-preview';
        const banner = document.createElement('img');
        banner.className = 'song-playlist-banner';
        banner.alt = '';
        const bannerUrl = imageUrl(playlist.banner);
        banner.hidden = !bannerUrl;
        if (bannerUrl) banner.src = bannerUrl;
        banner.onerror = () => { banner.hidden = true; };
        const cover = document.createElement('img');
        cover.className = 'song-playlist-cover';
        cover.alt = '';
        cover.src = imageUrl(playlist.capa) || '../assets/images/catalog-placeholder.svg';
        cover.onerror = () => { cover.onerror = null; cover.src = '../assets/images/catalog-placeholder.svg'; };
        const title = document.createElement('strong');
        title.textContent = playlist.titulo;
        const count = document.createElement('span');
        count.className = 'song-playlist-count';
        count.textContent = `${playlist.musicas?.length || 0} música(s)`;
        preview.append(banner, cover);
        label.append(radio, preview, title, count);
        return label;
    }

    function checkSession(response) {
        if (response.status === 401 || response.status === 403) {
            login.hidden = false;
            throw new Error(response.status === 401
                ? 'Entre na sua conta para adicionar músicas às playlists.'
                : 'Você não tem permissão para acessar ou modificar esta playlist.');
        }
    }

    async function loadPlaylists() {
        controller?.abort();
        const request = new AbortController();
        controller = request;
        cards.replaceChildren();
        choices.disabled = true;
        submit.disabled = true;
        login.hidden = true;
        library.hidden = true;
        retry.hidden = true;
        feedback.textContent = 'Carregando suas playlists…';
        try {
            const response = await fetch('/playlists/me', {
                credentials: 'same-origin',
                headers: { Accept: 'application/json' },
                cache: 'no-store',
                signal: request.signal
            });
            checkSession(response);
            if (!response.ok) throw new Error('Não foi possível carregar suas playlists. Tente novamente.');
            const playlists = await response.json();
            if (request.signal.aborted) return;
            if (!playlists.length) {
                feedback.textContent = 'Você ainda não participa de nenhuma playlist. Crie uma no seu perfil para começar.';
                library.hidden = false;
                return;
            }
            playlists.forEach(playlist => cards.append(playlistCard(playlist)));
            choices.disabled = false;
            feedback.textContent = 'Suas playlists e aquelas em que você colabora aparecem aqui.';
            cards.querySelector('input')?.focus();
        } catch (error) {
            if (request.signal.aborted) return;
            feedback.textContent = error.message || 'Não foi possível carregar suas playlists.';
            retry.hidden = !login.hidden;
        }
    }

    trigger.addEventListener('click', () => {
        document.getElementById('songPlaylistDescription').textContent =
            `Escolha onde adicionar “${document.getElementById('catalogTitle').textContent}”.`;
        dialog.showModal();
        loadPlaylists();
    });
    close.addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => controller?.abort());
    dialog.addEventListener('cancel', event => {
        if (saving) event.preventDefault();
    });
    retry.addEventListener('click', loadPlaylists);
    choices.addEventListener('change', () => {
        submit.disabled = saving || !choices.querySelector('input:checked');
        feedback.textContent = '';
    });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const selected = choices.querySelector('input:checked');
        if (saving || !selected || submit.disabled) return;
        const playlistId = selected.value;
        const playlistName = selected.dataset.title;
        saving = true;
        choices.disabled = true;
        submit.disabled = true;
        close.disabled = true;
        login.hidden = true;
        submit.textContent = 'Adicionando…';
        feedback.textContent = 'Adicionando música…';
        let added = false;
        try {
            const response = await fetch(`/playlists/${encodeURIComponent(playlistId)}/midias/${encodeURIComponent(trigger.dataset.midiaId)}`, {
                method: 'POST',
                credentials: 'same-origin',
                headers: { Accept: 'application/json' }
            });
            checkSession(response);
            if (!response.ok) {
                const error = await response.json().catch(() => null);
                throw new Error(error?.message || 'Não foi possível adicionar a música. Tente novamente.');
            }
            added = true;
            feedback.textContent = `Música adicionada à playlist “${playlistName}”!`;
        } catch (error) {
            feedback.textContent = error.message || 'Não foi possível adicionar a música. Tente novamente.';
        } finally {
            saving = false;
            close.disabled = false;
            choices.disabled = false;
            submit.disabled = added || !login.hidden;
            submit.textContent = 'Adicionar';
        }
    });
})();
