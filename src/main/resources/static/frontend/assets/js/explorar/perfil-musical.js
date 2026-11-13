document.addEventListener('synchro:own-profile', ({detail: user}) => {
    const status = document.getElementById('musicProfileStatus');
    const highlights = document.getElementById('musicProfileHighlights');
    const retry = document.getElementById('retryMusicProfile');
    const number = new Intl.NumberFormat('pt-BR');
    const rankings = [
        {type: 'artists', item: 'artist', label: 'Artista mais ouvido'},
        {type: 'tracks', item: 'track', label: 'Música mais ouvida'},
        {type: 'albums', item: 'album', label: 'Álbum mais ouvido'}
    ];

    if (!user.lastFmConnected) {
        status.textContent = 'Conecte sua conta Last.fm nas configurações do perfil para ver seu resumo musical.';
        return;
    }

    function renderHighlight({type, label}, item) {
        const card = document.createElement('div');
        const term = document.createElement('dt');
        term.textContent = label;

        const name = document.createElement('dd');
        const artist = typeof item.artist === 'string' ? item.artist : item.artist?.name;
        if (type === 'artists' || (type === 'tracks' && artist)) {
            const link = document.createElement('a');
            const query = type === 'artists'
                ? new URLSearchParams({artist: item.name})
                : new URLSearchParams({artist, track: item.name});
            link.href = (type === 'artists' ? 'artista.html?' : 'musica.html?') + query;
            link.textContent = item.name;
            name.append(link);
        } else {
            name.textContent = item.name;
        }

        const plays = Number(item.playcount);
        const detail = document.createElement('dd');
        detail.className = 'music-profile-detail';
        detail.textContent = [type === 'artists' ? '' : artist, number.format(plays) + (plays === 1 ? ' reprodução' : ' reproduções')]
            .filter(Boolean).join(' · ');

        card.append(term, name, detail);
        highlights.append(card);
    }

    async function load() {
        retry.hidden = true;
        status.textContent = 'Carregando seu perfil musical…';
        const results = await Promise.allSettled(rankings.map(async ({type, item}) => {
            const data = await fetchProfileRanking(type, '7day');
            const ranking = data?.['top' + type];
            if (!ranking || typeof ranking !== 'object') throw new Error('Ranking indisponível.');
            const entries = ranking[item];
            return (Array.isArray(entries) ? entries : [entries]).find(entry =>
                typeof entry?.name === 'string' && entry.name.trim() &&
                Number.isFinite(Number(entry.playcount)) && Number(entry.playcount) > 0);
        }));

        highlights.replaceChildren();
        let failed = 0;
        results.forEach((result, index) => {
            if (result.status === 'rejected') failed++;
            else if (result.value) renderHighlight(rankings[index], result.value);
        });
        status.textContent = failed
            ? (highlights.childElementCount ? 'Parte dos dados do Last.fm está indisponível.' : 'Não foi possível carregar seu perfil musical agora.')
            : (highlights.childElementCount ? '' : 'O Last.fm ainda não tem reproduções registradas para você neste período.');
        retry.hidden = failed === 0;
    }

    retry.addEventListener('click', load);
    load();
}, {once: true});
