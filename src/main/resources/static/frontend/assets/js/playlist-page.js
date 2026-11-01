(() => {
    const id = new URLSearchParams(location.search).get('id');
    const library = document.getElementById('playlistLibrary');
    const detail = document.getElementById('playlistDetail');
    const grid = document.getElementById('playlistLibraryGrid');
    const status = document.getElementById('playlistPageStatus');
    const login = document.getElementById('playlistPageLogin');
    const retry = document.getElementById('playlistPageRetry');
    const placeholder = '../assets/images/catalog-placeholder.svg';
    let current;
    let controller;
    library.hidden = Boolean(id);

    document.querySelector('.menu-toggle').addEventListener('click', () => {
        document.querySelector('.sidebar').classList.toggle('active');
    });

    function imageUrl(value) {
        if (!value) return null;
        if (/^data:image\/(png|jpeg|webp|gif);base64,/i.test(value)) return value;
        try {
            const url = new URL(value, location.href);
            return ['http:', 'https:'].includes(url.protocol) ? url.href : null;
        } catch { return null; }
    }

    function setImage(image, value, cover = false) {
        const url = imageUrl(value);
        image.hidden = !url && !cover;
        image.onerror = () => {
            image.onerror = null;
            if (cover) image.src = placeholder;
            else image.hidden = true;
        };
        if (url || cover) image.src = url || placeholder;
        else image.removeAttribute('src');
    }

    function time(seconds) {
        return `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`;
    }

    function seconds(value) {
        const match = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?$/.exec(value || '');
        return match ? Number(match[1] || 0) * 3600 + Number(match[2] || 0) * 60 + Number(match[3] || 0) : null;
    }

    function userLink(user) {
        const link = document.createElement('a');
        link.href = `user.html?user=${encodeURIComponent(user.id)}`;
        link.textContent = user.username || 'Usuário';
        return link;
    }

    function card(playlist) {
        const link = document.createElement('a');
        link.className = 'playlist-library-card';
        link.href = `playlists.html?id=${encodeURIComponent(playlist.id)}`;
        const artwork = document.createElement('div');
        artwork.className = 'playlist-library-artwork';
        const banner = document.createElement('img');
        banner.className = 'playlist-card-banner';
        banner.alt = '';
        const cover = document.createElement('img');
        cover.className = 'playlist-card-cover';
        cover.alt = '';
        setImage(banner, playlist.banner);
        setImage(cover, playlist.capa, true);
        artwork.append(banner, cover);
        const title = document.createElement('h2');
        title.textContent = playlist.titulo;
        const meta = document.createElement('p');
        meta.textContent = `${playlist.musicas?.length || 0} música(s) · ${playlist.criador?.username || 'Usuário'}`;
        link.append(artwork, title, meta);
        return link;
    }

    function renderDetail(playlist) {
        current = playlist;
        const colaborador = playlist.colaborador === true;
        document.getElementById('playlistEdit').hidden = !colaborador;
        document.getElementById('playlistParticipation').textContent = colaborador
            ? 'Você participa desta playlist e pode editá-la.'
            : 'Você não é colaborador desta playlist.';
        document.title = `SYNCHRO | ${playlist.titulo}`;
        document.getElementById('detailPlaylistTitle').textContent = playlist.titulo;
        document.getElementById('detailPlaylistDescription').textContent = playlist.descricao || 'Sem descrição.';
        setImage(document.getElementById('detailPlaylistBanner'), playlist.banner);
        setImage(document.getElementById('detailPlaylistCover'), playlist.capa, true);
        const songs = playlist.musicas || [];
        const meta = document.getElementById('playlistMeta');
        meta.replaceChildren(document.createTextNode('Criada por '));
        meta.append(playlist.criador ? userLink(playlist.criador) : document.createTextNode('Usuário'));
        const lengths = songs.map(song => seconds(song.duracao));
        const total = lengths.length && lengths.every(length => length !== null)
            ? ` · ${time(lengths.reduce((sum, length) => sum + length, 0))} no total` : '';
        meta.append(document.createTextNode(` · ${songs.length} música(s)${total}`));
        const tracks = document.getElementById('playlistTracks');
        tracks.replaceChildren();
        songs.forEach((song, index) => {
            const row = document.createElement('a');
            row.className = 'playlist-track';
            row.href = `musica.html?id=${encodeURIComponent(song.id)}`;
            const number = document.createElement('span');
            number.className = 'playlist-track-number';
            number.textContent = String(index + 1).padStart(2, '0');
            const title = document.createElement('strong');
            title.textContent = song.titulo;
            const duration = document.createElement('span');
            const length = seconds(song.duracao);
            duration.textContent = length === null ? '—' : time(length);
            row.append(number, title, duration);
            tracks.append(row);
        });
        document.getElementById('playlistTracksEmpty').hidden = songs.length > 0;
        const people = document.getElementById('playlistCollaborators');
        people.replaceChildren();
        const users = new Map();
        if (playlist.criador) users.set(playlist.criador.id, playlist.criador);
        (playlist.usuarios || []).forEach(user => users.set(user.id, user));
        users.forEach(user => people.append(userLink(user)));
        library.hidden = true;
        detail.hidden = false;
    }

    async function load() {
        controller?.abort();
        const request = new AbortController();
        controller = request;
        current = null;
        document.getElementById('playlistEdit').hidden = true;
        status.textContent = 'Carregando suas playlists…';
        login.hidden = true;
        retry.hidden = true;
        if (id && !/^\d+$/.test(id)) {
            library.hidden = true;
            status.textContent = 'Playlist não encontrada. Abra uma playlist pela sua biblioteca.';
            return;
        }
        try {
            const response = await fetch('/playlists/me', {
                credentials: 'same-origin', cache: 'no-store',
                headers: { Accept: 'application/json' }, signal: request.signal
            });
            if (request.signal.aborted) return;
            if (response.status === 401 || response.status === 403) {
                login.hidden = false;
                throw new Error('Entre na sua conta para ver suas playlists.');
            }
            if (!response.ok) throw new Error('Não foi possível carregar suas playlists. Tente novamente.');
            const playlists = await response.json();
            if (request.signal.aborted) return;
            if (!Array.isArray(playlists)) throw new Error('Não foi possível carregar suas playlists. Tente novamente.');
            grid.replaceChildren();
            if (id) {
                const playlist = playlists.find(item => String(item.id) === id);
                if (!playlist) {
                    library.hidden = true;
                    detail.hidden = true;
                    status.textContent = 'Playlist não encontrada ou você não participa dela.';
                    return;
                }
                renderDetail(playlist);
            } else {
                playlists.forEach(playlist => grid.append(card(playlist)));
                document.getElementById('playlistLibraryCount').textContent = `${playlists.length} playlist(s) na sua biblioteca`;
            }
            status.textContent = !id && !playlists.length ? 'Você ainda não participa de nenhuma playlist. Use “Criar playlist” para começar.' : '';
        } catch (error) {
            if (request.signal.aborted) return;
            detail.hidden = true;
            grid.replaceChildren();
            status.textContent = error.message || 'Não foi possível carregar suas playlists.';
            retry.hidden = !login.hidden;
        }
    }

    document.getElementById('playlistEdit').addEventListener('click', () => {
        if (current?.colaborador === true) {
            document.dispatchEvent(new CustomEvent('playlist-edit', { detail: current }));
        }
    });
    document.addEventListener('playlist-saved', load);
    retry.addEventListener('click', load);
    load();
})();
