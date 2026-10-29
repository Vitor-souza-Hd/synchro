(() => {
    const type = document.body.dataset.mediaType;
    const id = new URLSearchParams(window.location.search).get('id');
    const status = document.getElementById('catalogStatus');
    const content = document.getElementById('catalogContent');
    const placeholder = '../assets/images/catalog-placeholder.svg';
    function safeUrl(value, lastfmOnly = false) {
        try {
            const url = new URL(value);
            if (!['https:', 'http:'].includes(url.protocol)) return null;
            if (lastfmOnly && url.hostname !== 'www.last.fm' && url.hostname !== 'last.fm') return null;
            return url.href;
        } catch { return null; }
    }

    function plainText(value) {
        // Parse upstream wiki markup inertly and render only its text, never its HTML.
        const doc = new DOMParser().parseFromString(value || '', 'text/html');
        doc.querySelectorAll('script, style, iframe').forEach(node => node.remove());
        return doc.body.textContent.trim();
    }

    function cover(image, url) {
        image.onerror = () => { image.onerror = null; image.src = placeholder; };
        image.src = safeUrl(url) || placeholder;
    }
    document.querySelector('.menu-toggle')?.addEventListener('click', () => {
        document.querySelector('.sidebar')?.classList.toggle('active');
    });

    function link(page, item, name) {
        const element = document.createElement('a');
        element.href = `${page}.html?id=${encodeURIComponent(item.id)}`;
        element.className = 'catalog-link';
        element.textContent = name;
        return element;
    }

    function duration(value) {
        if (!value) return 'Duração não informada';
        const match = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?$/.exec(value);
        if (!match) return 'Duração não informada';
        const seconds = Math.floor(Number(match[1] || 0) * 3600 + Number(match[2] || 0) * 60 + Number(match[3] || 0));
        return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
    }

    function albumCard(album) {
        const card = link('album', album, '');
        card.classList.add('album-card');
        const image = document.createElement('img');
        cover(image, album.imgUrl || album.lastFm?.imageUrl);
        image.alt = '';
        const title = document.createElement('h3');
        title.textContent = album.titulo;
        card.append(image, title);
        return card;
    }

    function render(data) {
        const item = type === 'album' ? data.album : data;
        document.title = `SYNCHRO | ${item.titulo}`;
        document.getElementById('catalogTitle').textContent = item.titulo;
        document.getElementById('catalogDescription').textContent = plainText(item.descricao) || 'O Last.fm ainda não disponibilizou uma descrição para este registro.';
        cover(document.getElementById('catalogImage'), item.lastFm?.imageUrl || item.album?.imgUrl);
        document.getElementById('catalogImage').alt = `Capa de ${item.titulo}`;
        const artists = document.getElementById('catalogArtists');
        const primary = item.artistas?.find(artist => artist.nome.toLowerCase() === item.lastFm?.artistName?.toLowerCase());
        (primary ? [primary] : item.artistas || []).forEach(artist => artists.append(link('artista', artist, artist.nome)));
        if (!artists.childElementCount) artists.textContent = 'Artista não informado';
        if (type === 'song') {
            const meta = document.getElementById('catalogMeta');
            meta.textContent = item.album ? 'Álbum associado: ' : 'Álbum não informado';
            if (item.album) {
                meta.append(link('album', item.album, item.album.titulo));
            }
            const tags = document.getElementById('catalogTags');
            const names = item.lastFm?.tags?.length ? item.lastFm.tags : (item.genero || '').split(',').map(s => s.trim()).filter(Boolean);
            [...new Set(names)].forEach(name => {
                const tag = document.createElement('span');
                tag.textContent = name;
                tags.append(tag);
            });
            const number = value => value == null ? 'Não informado' : new Intl.NumberFormat('pt-BR').format(value);
            document.getElementById('songListeners').textContent = number(item.lastFm?.listeners);
            document.getElementById('songPlaycount').textContent = number(item.lastFm?.playcount);
            document.getElementById('songDuration').textContent = item.duracao ? duration(item.duracao) : 'Não informado';
            const source = safeUrl(item.lastFm?.url, true);
            if (source) {
                document.getElementById('lastFmLink').href = source;
                document.getElementById('lastFmLink').hidden = false;
                document.getElementById('descriptionSource').href = source;
                document.getElementById('songAttribution').hidden = false;
            }
        } else {
            const date = item.dataLancamento ? item.dataLancamento.split('-').reverse().join('/') : 'Lançamento não informado';
            document.getElementById('catalogMeta').textContent = `${date} · ${data.musicas.length} música(s) no catálogo`;
            const tracks = document.getElementById('catalogTracks');
            document.getElementById('catalogTracksSection').hidden = false;
            data.musicas.forEach((song, index) => {
                const row = link('musica', song, '');
                row.classList.add('track');
                const number = document.createElement('span');
                number.className = 'track-number';
                number.textContent = String(index + 1).padStart(2, '0');
                const info = document.createElement('div');
                info.className = 'track-info';
                const title = document.createElement('strong');
                title.textContent = song.titulo;
                const subtitle = document.createElement('span');
                subtitle.textContent = (song.artistas || []).map(artist => artist.nome).join(', ');
                info.append(title, subtitle);
                const length = document.createElement('span');
                length.textContent = duration(song.duracao);
                row.append(number, info, length);
                tracks.append(row);
            });
            if (!data.musicas.length) tracks.textContent = 'Nenhuma faixa cadastrada neste álbum.';
        }
        const button = document.getElementById('favoriteBtn');
        const favoriteKey = `synchroFavorite:${type}:${item.id}`;
        function updateFavorite() {
            let favorite = false;
            try { favorite = localStorage.getItem(favoriteKey) === 'true'; } catch { /* Storage may be disabled. */ }
            button.setAttribute('aria-pressed', String(favorite));
            button.textContent = favorite ? '♥ Favoritado' : '♡ Favoritar';
        }
        button.addEventListener('click', () => {
            try {
                localStorage.setItem(favoriteKey, String(button.getAttribute('aria-pressed') !== 'true'));
                updateFavorite();
            } catch {
                status.hidden = false;
                status.textContent = 'Não foi possível salvar o favorito neste navegador.';
            }
        });
        updateFavorite();
        status.hidden = true;
        content.hidden = false;
    }

    async function load() {
        if (!id || !/^\d+$/.test(id)) {
            status.textContent = 'Selecione um resultado na pesquisa para abrir esta página.';
            return;
        }
        try {
            // Complete older imported records and refresh public statistics at most once per day.
            await fetch(`/catalog/${type === 'album' ? 'albums' : 'songs'}/${encodeURIComponent(id)}/refresh`, { method: 'POST' }).catch(() => {});
            const response = await fetch(`/catalog/${type === 'album' ? 'albums' : 'songs'}/${encodeURIComponent(id)}`, {
                headers: { Accept: 'application/json' }
            });
            if (!response.ok) throw new Error(response.status === 404 ? 'Registro não encontrado no catálogo.' : 'Não foi possível carregar os dados. Tente novamente.');
            render(await response.json());
        } catch (error) {
            status.textContent = error.message;
        }
    }
    load();
})();
