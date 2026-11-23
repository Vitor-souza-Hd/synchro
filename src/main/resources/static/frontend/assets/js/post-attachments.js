(() => {
    const kinds = {
        track: {label: 'Música', page: 'musica'},
        album: {label: 'Álbum', page: 'album'},
        artist: {label: 'Artista', page: 'artista'}
    };

    function description(item) {
        return [kinds[item.type].label, item.artist].filter(Boolean).join(' · ');
    }

    function card(item) {
        const link = document.createElement('a');
        link.className = 'post-attachment';
        link.href = '/frontend/pages/' + kinds[item.type].page + '.html?' + new URLSearchParams({id: item.id});
        const artwork = document.createElement('div');
        artwork.className = 'post-attachment-artwork';
        const info = document.createElement('div');
        info.textContent = item.title;
        const detail = document.createElement('small');
        detail.textContent = description(item);
        info.append(detail);
        link.append(artwork, info);

        queueMicrotask(() => {
            if (!link.isConnected) return;
            window.SynchroFavoriteImages?.show(artwork, {
                id: item.id, kind: item.type, title: item.title,
                artist: item.type === 'artist' ? item.title : item.artist,
                preferredImages: [item.coverUrl], alt: ''
            });
        });
        return link;
    }

    function catalogueItems(data) {
        return [
            ['track', data.songs],
            ['album', data.albums],
            ['artist', data.artists]
        ].flatMap(([type, items]) => (items || []).slice(0, 4).map(item => ({
            type, id: item.id, title: item.titulo || item.nome,
            artist: item.lastFm?.artistName || item.artistas?.[0]?.nome
        }))).filter(item => item.id != null && item.title);
    }

    function createPicker() {
        const details = document.getElementById('postAttachmentPicker');
        const query = document.getElementById('postAttachmentQuery');
        const search = document.getElementById('searchPostAttachment');
        const results = document.getElementById('postAttachmentResults');
        const status = document.getElementById('postAttachmentStatus');
        const selected = document.getElementById('postAttachmentSelected');
        const name = document.getElementById('postAttachmentName');
        const clear = document.getElementById('clearPostAttachment');
        let item = null;
        let activeSearch;
        let blocked = false;

        function select(value) {
            activeSearch?.abort();
            activeSearch = null;
            search.disabled = blocked;
            item = value;
            details.open = false;
            query.value = '';
            status.textContent = '';
            results.replaceChildren();
            selected.hidden = !item;
            name.textContent = item ? item.title + ' · ' + description(item) : '';
        }

        function selectResult(item) {
            if (blocked) return;
            select(item);
            clear.focus();
        }

        function render(results, items) {
            results.replaceChildren(...items.map(match => {
                const row = document.createElement('li');
                const option = document.createElement('button');
                option.type = 'button';
                option.textContent = match.title + ' · ' + description(match);
                option.addEventListener('click', () => selectResult(match));
                row.append(option);
                return row;
            }));
        }

        async function find() {
            const term = query.value.trim();
            if (blocked || !term) return;
            activeSearch?.abort();
            const controller = new AbortController();
            activeSearch = controller;
            const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(12000)]);
            search.disabled = true;
            results.replaceChildren();
            status.textContent = 'Buscando no catálogo…';
            try {
                const params = new URLSearchParams({q: term});
                for (let attempt = 0; attempt < 4; attempt++) {
                    const response = await fetch('/search?' + params, {signal});
                    if (!response.ok) throw new Error('Busca indisponível.');
                    const data = await response.json();
                    if (activeSearch !== controller) return;
                    const items = catalogueItems(data);
                    render(results, items);
                    if (!data.refreshing || attempt === 3) {
                        status.textContent = items.length ? '' : 'Nenhum item encontrado para anexar.';
                        return;
                    }
                    status.textContent = 'Buscando mais resultados…';
                    await new Promise(resolve => setTimeout(resolve, 600));
                }
            } catch {
                if (activeSearch === controller) status.textContent = 'Não foi possível buscar no catálogo.';
            } finally {
                if (activeSearch === controller) {
                    activeSearch = null;
                    search.disabled = blocked;
                }
            }
        }

        search.addEventListener('click', find);
        query.addEventListener('keydown', event => {
            if (event.key === 'Enter') {
                event.preventDefault();
                find();
            }
        });
        clear.addEventListener('click', () => {
            select(null);
            details.querySelector('summary').focus();
        });

        function disable(disabled) {
            blocked = disabled;
            search.disabled = disabled || Boolean(activeSearch);
            query.disabled = clear.disabled = disabled;
        }

        return {select, disable, selected: () => item, value: () => item ? {type: item.type, id: item.id} : null};
    }

    window.SynchroPostAttachments = {card, createPicker};
})();
