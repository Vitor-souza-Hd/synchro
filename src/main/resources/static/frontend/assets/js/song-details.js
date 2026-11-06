document.addEventListener('DOMContentLoaded', async () => {
    const params = new URLSearchParams(location.search);
    const id = params.get('id');
    let song = null;
    try {
        if (id !== null) {
            if (!/^\d+$/.test(id)) throw new Error('Identificador de música inválido.');
            const response = await fetch('/catalog/songs/' + id, {headers: {Accept: 'application/json'}});
            if (!response.ok) throw new Error(response.status === 404
                ? 'Registro não encontrado no catálogo.' : 'Não foi possível carregar a música. Tente novamente.');
            song = await response.json();
            params.set('track', song.titulo);
            params.set('artist', song.lastFm?.artistName || song.artistas?.[0]?.nome || '');
            if (song.album?.titulo) params.set('album', song.album.titulo);
        } else if (!params.get('track')?.trim() || !params.get('artist')?.trim()) {
            throw new Error('Selecione uma música na pesquisa para ver seus detalhes.');
        } else {
            const registered = await window.SynchroCatalog.record({type: 'track',
                name: params.get('track').trim(), artist: params.get('artist').trim(),
                album: params.get('album'), albumArtist: params.get('albumArtist')});
            const response = await fetch('/catalog/songs/' + registered.id, {headers: {Accept: 'application/json'}});
            if (!response.ok) throw new Error('Não foi possível carregar a música salva no catálogo.');
            song = await response.json();
        }
        document.dispatchEvent(new CustomEvent('synchro:song', {detail: {params, song}}));
    } catch (error) {
        document.getElementById('musicName').textContent = error.message;
        document.getElementById('musicBiography').textContent = '';
        document.getElementById('musicArtist').textContent = '';
        document.getElementById('musicReviewStatus').textContent = '';
    }
});
