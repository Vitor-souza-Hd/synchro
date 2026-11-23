(() => {
    const categories = [
        {id: 'lancamentos', url: '/recomendacoes/lancamentos', empty: 'Nenhum lançamento encontrado nos últimos 30 dias.'},
        {id: 'seus-gostos', url: '/recomendacoes?limit=18', empty: 'Ainda não há sugestões para esta conta.'},
        {id: 'populares', url: '/catalog/albums/top', empty: 'Ainda não há álbuns com dados de popularidade no catálogo.'}
    ];
    const imageRoot = '/frontend/assets/images/';
    const menu = document.getElementById('recommendation-sidebar');
    const toggle = document.querySelector('.favorites-menu-toggle');
    function setMenu(open) {
        menu.classList.toggle('active', open);
        toggle.setAttribute('aria-expanded', String(open));
        toggle.setAttribute('aria-label', open ? 'Fechar menu' : 'Abrir menu');
    }
    toggle.addEventListener('click', () => setMenu(!menu.classList.contains('active')));
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && menu.classList.contains('active')) {
            setMenu(false);
            toggle.focus();
        }
    });

    function cardFor(item, list) {
        const personal = Boolean(item.titulo);
        const title = item.titulo || item.name;
        if (!title) return;
        const artist = item.nomeArtista || item.artist || '';
        const artistCard = personal && item.id == null;
        const trackCard = item.type === 'track';
        const page = trackCard ? 'musica' : artistCard ? 'artista' : 'album';
        let params = {album: title, artist};
        if (item.id != null) params = {id: item.id};
        if (artistCard) params = {artist};
        if (trackCard) params = {track: title, artist};
        const row = document.createElement('li');
        const link = document.createElement('a');
        row.className = 'favorite-card favorite-album-card';
        link.className = 'favorite-card-link';
        link.href = page + '.html?' + new URLSearchParams(params);
        const artwork = document.createElement('div');
        artwork.className = 'favorite-album-cover';
        const info = document.createElement('div');
        info.className = 'favorite-card-info';
        const heading = document.createElement('h3');
        heading.textContent = title;
        info.append(heading);

        if (artist) {
            const credit = document.createElement('p');
            credit.textContent = artist;
            info.append(credit);
        }
        if (item.releaseDate) {
            const date = document.createElement('time');
            date.dateTime = item.releaseDate;
            date.textContent = item.releaseDate.split('-').reverse().join('/');
            const release = document.createElement('p');
            release.append(date);
            info.append(release);
        }
        if (item.type) {
            const type = document.createElement('span');
            type.className = 'favorite-album-type';
            type.textContent = trackCard ? 'MÚSICA' : 'ÁLBUM';
            info.append(type);
        }
        if (item.motivo) {
            const reason = document.createElement('p');
            reason.textContent = item.motivo;
            info.append(reason);
        }
        link.append(artwork, info);
        row.append(link);
        list.append(row);

        if (window.SynchroFavoriteImages) {
            window.SynchroFavoriteImages.show(artwork, {
                kind: trackCard ? 'track' : artistCard ? 'artist' : 'album', artist, id: item.id,
                title: artistCard ? artist : title, preferredImages: item.images || [item.capaUrl],
                className: 'favorite-album-image', alt: ''
            });
        } else {
            const image = document.createElement('img');
            image.className = 'favorite-album-image';
            image.alt = '';
            image.src = imageRoot + (artistCard ? 'artist-placeholder.svg' : 'catalog-placeholder.svg');
            artwork.append(image);
        }
    }

    async function load({id, url, empty}) {
        const section = document.getElementById(id);
        const status = section.querySelector('[data-status]');
        const list = section.querySelector('[data-results]');
        try {
            const response = await fetch(url, {signal: AbortSignal.timeout(20000)});
            if (response.status === 401 && id === 'seus-gostos') {
                status.textContent = 'Entre para receber sugestões com base no seu gosto musical.';
                section.querySelector('[data-login]').hidden = false;
                return;
            }
            if (!response.ok) throw new Error('Seção indisponível');
            const data = await response.json();
            const items = id === 'populares' ? data : data.items;
            items.forEach(item => cardFor(item, list));
            status.textContent = '';
            if (!items.length) {
                status.textContent = empty;
            } else if (id === 'lancamentos' && !data.personalized) {
                status.textContent = 'Álbuns e músicas em destaque dos últimos 30 dias.';
            } else if (id === 'seus-gostos' && !data.personalized) {
                status.textContent = 'Exibindo destaques do catálogo enquanto não há dados para personalizar.';
            }
            status.closest('.favorites-feedback').hidden = !status.textContent;
        } catch {
            status.textContent = 'Não foi possível carregar esta categoria agora. Atualize a página para tentar novamente.';
        }
    }

    categories.forEach(load);
})();
