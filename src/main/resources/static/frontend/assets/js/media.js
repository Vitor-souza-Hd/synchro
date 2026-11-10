(() => {
    const params = new URLSearchParams(window.location.search);
    let id = params.get('id');
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

    function render(data) {
        const item = data.album;
        document.title = `SYNCHRO | ${item.titulo}`;
        document.getElementById('catalogTitle').textContent = item.titulo;
        document.getElementById('catalogDescription').textContent = plainText(item.descricao) || 'O Last.fm ainda não disponibilizou uma descrição para este registro.';
        cover(document.getElementById('catalogImage'), item.lastFm?.imageUrl || item.album?.imgUrl);
        document.getElementById('catalogImage').alt = `Capa de ${item.titulo}`;
        const artists = document.getElementById('catalogArtists');
        artists.replaceChildren();
        const primary = item.artistas?.find(artist => artist.nome.toLowerCase() === item.lastFm?.artistName?.toLowerCase());
        (primary ? [primary] : item.artistas || []).forEach(artist => artists.append(link('artista', artist, artist.nome)));
        if (!artists.childElementCount) artists.textContent = 'Artista não informado';
        const date = item.dataLancamento ? item.dataLancamento.split('-').reverse().join('/') : 'Lançamento não informado';
        document.getElementById('catalogMeta').textContent = `${date} · ${data.musicas.length} música(s) no catálogo`;
        const tracks = document.getElementById('catalogTracks');
        tracks.replaceChildren();
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
        const button = document.getElementById('favoriteBtn');
        window.SynchroFeatures.bindFavorite(button, {kind: 'albums', title: item.titulo, artist: item.artistas?.[0]?.nome || ''});
        status.hidden = true;
        content.hidden = false;
    }

    async function load() {
        if (!id && params.get('album')?.trim() && params.get('artist')?.trim() && window.SynchroCatalog) {
            try {
                const item = await window.SynchroCatalog.record({type: 'album',
                    name: params.get('album').trim(), artist: params.get('artist').trim()});
                id = String(item.id);
            } catch (error) { status.textContent = error.message; return; }
        }
        if (!id || !/^\d+$/.test(id)) {
            status.textContent = 'Selecione um resultado na pesquisa para abrir esta página.';
            return;
        }
        try {
            const response = await fetch(`/catalog/albums/${encodeURIComponent(id)}`, {
                headers: { Accept: 'application/json' }
            });
            if (!response.ok) throw new Error(response.status === 404 ? 'Registro não encontrado no catálogo.' : 'Não foi possível carregar os dados. Tente novamente.');
            const data = await response.json();
            render(data);
            document.dispatchEvent(new CustomEvent("synchro:album", {detail: {id, album: data.album}}));
            // The saved album stays usable while optional metadata is refreshed.
            refreshAlbum();
        } catch (error) {
            status.textContent = error.message;
        }
    }
    async function refreshAlbum() {
        try {
            const refreshed = await fetch(`/catalog/albums/${encodeURIComponent(id)}/refresh`, {method: 'POST'});
            if (!refreshed.ok) return;
            const response = await fetch(`/catalog/albums/${encodeURIComponent(id)}`, {headers: {Accept: 'application/json'}});
            if (response.ok) render(await response.json());
        } catch { /* Keep the saved album visible when an external service is unavailable. */ }
    }
    load();
})();
