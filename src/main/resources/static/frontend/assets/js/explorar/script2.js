document.addEventListener("DOMContentLoaded", () => {

    // =========================================================
    // DADOS DA PÁGINA INICIAL
    // =========================================================
    const artistsData = [
        { nome: 'Bad Bunny', imgUrl: '/frontend/assets/images/cards/badbunny.webp'},
        { nome: 'Rihanna', imgUrl: '/frontend/assets/images/cards/rihanna.webp'},
        { nome: 'Justin Bieber', imgUrl: '/frontend/assets/images/cards/justin.webp' },
        { nome: 'Billie Eilish', imgUrl: '/frontend/assets/images/cards/billieeilish.webp'},
        { nome: 'Kendrick Lamar', imgUrl: '/frontend/assets/images/cards/kendricklamar.webp'},
        { nome: 'The Weeknd', imgUrl: '/frontend/assets/images/cards/theweeknd.webp'},
    ];

    const genresData = [
        { name: "POP", image: "/frontend/assets/images/cards/michael.webp" },
        { name: "MPB", image: "/frontend/assets/images/cards/djavan.webp" },
        { name: "FUNK", image: "/frontend/assets/images/funk.jpg" },
        { name: "ROCK", image: "/frontend/assets/images/cards/queen.webp" },
        { name: "RAP", image: "/frontend/assets/images/cards/tupac.webp" }
    ];

    // =========================================================
    // ELEMENTOS DA PÁGINA
    // =========================================================

    const artistsGrid = document.querySelector(".artists");
    const albumsGrid = document.querySelector(".albums");
    const genresGrid = document.querySelector(".genres");
    const reviewsGrid = document.querySelector(".reviews");
    const photoRequests = new Map();
    const artistPlaceholder = '/frontend/assets/images/artist-placeholder.svg';

    function usablePhoto(url) {
        return typeof url === 'string' && /^(https?:\/\/|\/frontend\/|\.\.\/assets\/|frontend\/assets\/)/i.test(url)
            && !/2a96cbd8b46e442fc41c2b86b821562f|placehold\.co\/|(?:catalog|artist)-placeholder\.svg/.test(url);
    }

    function loadArtistPhoto(image, artist) {
        image.alt = 'Imagem de ' + artist.nome;
        image.loading = 'lazy';
        image.decoding = 'async';
        const fallback = () => { image.onerror = null; image.src = artistPlaceholder; };
        function lookup(refresh = false) {
            image.onerror = fallback;
            if (!Number.isSafeInteger(Number(artist.id)) || Number(artist.id) <= 0) return;
            const url = '/catalog/artists/' + encodeURIComponent(artist.id) + '/photo' + (refresh ? '?refresh=true' : '');
            if (image.isConnected === false) return;
            if (!photoRequests.has(url)) photoRequests.set(url, fetch(url, {headers: {Accept: 'application/json'}})
                .then(response => response.ok ? response.json() : null).catch(() => null));
            return photoRequests.get(url).then(photo => {
                if (image.isConnected === false) return;
                if (photo?.status === 'found' && usablePhoto(photo.imgUrl)) {
                    artist.imgUrl = photo.imgUrl;
                    image.src = photo.imgUrl;
                }
            });
        }
        image.onerror = () => { fallback(); lookup(true); };
        image.src = usablePhoto(artist.imgUrl) ? artist.imgUrl : artistPlaceholder;
        if (!usablePhoto(artist.imgUrl)) {
            const scheduler = window.SynchroImageVisibility;
            if (scheduler) scheduler.whenNear(image, lookup);
            else lookup();
        }
    }

    // =========================================================
    // EXIBIR ARTISTAS POPULARES (BASEADO NO LAST.FM OU BANCO)
    // =========================================================

    const genreTags = new Map([
        ['rock alternativo', 'alternative rock'],
        ['rock psicodélico', 'psychedelic rock'],
        ['rock progressivo', 'progressive rock'],
        ['eletrônica', 'electronic'],
        ['hip-hop', 'hip hop'],
        ['forró', 'forro'],
        ['funk brasileiro', 'baile funk'],
        ['música clássica', 'classical']
    ]);

    const brazilianGenreTags = new Map([
        ...['rock', 'alternative rock', 'indie rock', 'pop rock', 'progressive rock', 'psychedelic rock']
            .map(tag => [tag, 'brazilian rock']),
        ...['hip hop', 'rap', 'trap'].map(tag => [tag, 'rap nacional']),
        ['pop', 'brazilian pop'],
        ...['mpb', 'bossa nova', 'samba', 'pagode', 'forro', 'sertanejo', 'baile funk']
            .map(tag => [tag, tag])
    ]);

    const recommendationLimit = 6;
    const brazilianSlots = 2;
    const artistPoolLimit = 18;
    const artistRotationMs = 2 * 60 * 60 * 1000;
    const genreWeights = [1, 0.7, 0.45, 0.35, 0.25];
    const artistKey = artist => artist.name.normalize('NFKC').replace(/\s+/g, ' ').toLowerCase();

    function rankByTaste(groups, keyFor) {
        const candidates = new Map();
        groups.forEach((group, genreIndex) => {
            const seen = new Set();
            group.forEach((artist, rank) => {
                const key = keyFor(artist);
                if (seen.has(key)) return;
                seen.add(key);
                const score = genreWeights[genreIndex] / (rank + 1);
                const candidate = candidates.get(key);
                if (!candidate) {
                    candidates.set(key, {artist, genreCount: 1, total: score, strongest: score});
                    return;
                }
                candidate.genreCount++;
                candidate.total += score;
                if (score > candidate.strongest) {
                    candidate.artist = artist;
                    candidate.strongest = score;
                }
            });
        });
        return [...candidates.entries()].sort(([, a], [, b]) =>
            b.genreCount - a.genreCount || b.total - a.total || b.strongest - a.strongest);
    }

    async function loadGenreArtists(genre, tag) {
        const query = new URLSearchParams({method: 'tag.getTopArtists', tag});
        const response = await fetch('/last-fm/discovery?' + query, {signal: AbortSignal.timeout(6000)});
        if (!response.ok) return [];
        const {topartists} = await response.json();
        return readRankedArtists(topartists?.artist, genre);
    }

    function readRankedArtists(items, genre) {
        return (Array.isArray(items) ? items : [items])
            .filter(item => typeof item?.name === 'string' && item.name.trim())
            .map(item => ({
                name: item.name.trim(),
                id: Number.isSafeInteger(item.catalogId) && item.catalogId > 0 ? item.catalogId : null,
                genre,
                images: Array.isArray(item.image) ? item.image.map(image => image?.['#text']).reverse() : []
            }));
    }

    function recommendationTags(genres) {
        const tags = new Map();
        for (const genre of genres) {
            if (typeof genre !== 'string' || !genre.trim()) continue;
            const name = genre.trim().toLowerCase();
            const tag = genreTags.get(name) || name;
            if (!tags.has(tag)) tags.set(tag, genre.trim());
            if (tags.size === genreWeights.length) break;
        }
        return tags;
    }

    async function loadRecommendationGenres() {
        const endpoint = '/users/me/music-profile';
        const response = await fetch(endpoint, {
            cache: 'no-store', signal: AbortSignal.timeout(5000)
        });
        if (response.status === 401 || response.status === 403) {
            return {message: 'Entre na sua conta para ver recomendações pelo seu gosto musical.'};
        }
        if (!response.ok) throw new Error('Perfil musical indisponível.');
        const saved = await response.json();
        if (saved.genres?.length) return {genres: saved.genres};

        const userResponse = await fetch('/users/me', {
            cache: 'no-store', signal: AbortSignal.timeout(5000)
        });
        if (!userResponse.ok) throw new Error('Conta indisponível.');
        const user = await userResponse.json();
        if (!user.lastFmConnected) {
            return {message: 'Conecte o Last.fm no Perfil para receber recomendações personalizadas.'};
        }
        if (!window.SynchroMusicGenres) return {message: 'Abra seu Perfil para calcular seus gêneros da semana.'};
        const {resultado, parcial} = await window.SynchroMusicGenres.calculate('7day');
        const genres = resultado.map(([genre]) => genre);
        if (!genres.length) {
            return {message: parcial ? 'Não foi possível identificar seus gêneros agora.'
                : 'Ainda não há gêneros identificados nas suas reproduções da semana.'};
        }
        if (!parcial) {
            try {
                await fetch(endpoint, {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({genres, lastFmUsername: user.lastFmUsername}),
                    signal: AbortSignal.timeout(5000)
                });
            } catch {
                // Recommendations can still use the genres calculated for this visit.
            }
        }
        return {genres};
    }

    async function loadPersonalizedArtists(genres) {
        try {
            if (!Array.isArray(genres)) return null;
            const tags = recommendationTags(genres);
            if (!tags.size) return null;

            const nationalTag = [...tags.keys()].map(tag => brazilianGenreTags.get(tag)).find(Boolean) || 'brazilian';
            const requests = new Map();
            function getArtists(tag) {
                if (!requests.has(tag)) {
                    const label = tags.get(tag) || (tag === 'brazilian' ? 'Música brasileira' : 'Cena brasileira');
                    requests.set(tag, loadGenreArtists(label, tag).catch(() => []));
                }
                return requests.get(tag);
            }
            const [groups, national] = await Promise.all([
                Promise.all([...tags.keys()].map(getArtists)),
                getArtists(nationalTag)
            ]);

            const ranked = rankByTaste(groups, artistKey);
            const tasteScores = new Map(ranked);
            const nationalArtists = new Map();
            function addNationalArtists(items) {
                for (const artist of items) {
                    const key = artistKey(artist);
                    if (!nationalArtists.has(key)) nationalArtists.set(key, artist);
                }
            }
            // Give priority to artists from the matching Brazilian scene and use the
            // global preference scores to order candidates within each Brazilian list.
            function orderNationalArtists(items) {
                return items.map((artist, rank) => ({
                    artist, score: 1 / (rank + 1) + (tasteScores.get(artistKey(artist))?.total ?? 0)
                })).sort((a, b) => b.score - a.score).map(({artist}) => artist);
            }
            addNationalArtists(orderNationalArtists(national));
            if (nationalArtists.size < brazilianSlots && nationalTag !== 'brazilian') {
                addNationalArtists(orderNationalArtists(await getArtists('brazilian')));
            }

            return {ranked: ranked.slice(0, artistPoolLimit).map(([key, {artist}]) => nationalArtists.get(key) || artist),
                national: [...nationalArtists.values()]};
        } catch {
            return null;
        }
    }

    function rotateArtists(items, slot) {
        if (items.length < 2) return items;
        const offset = slot % items.length;
        return items.slice(offset).concat(items.slice(0, offset));
    }

    const recommendationProfile = artistsGrid || albumsGrid ? loadRecommendationGenres().catch(() => ({
        message: 'Não foi possível consultar seu perfil musical agora.'
    })) : null;

    if (artistsGrid) {
        const recommendationStatus = document.getElementById('artistRecommendationStatus');
        let personalized = null;
        let dailyRanking = [];
        let catalogArtists = artistsData;
        let renderedSlot;
        let rotationTimer;
        let visibleArtistNames = '';

        function renderArtistSelection(slot = Math.floor(Date.now() / artistRotationMs)) {
            renderedSlot = slot;
            const selected = new Map();
            function addArtists(items, limit = recommendationLimit) {
                for (const artist of items) {
                    if (selected.size === limit) break;
                    const nome = artist.nome || artist.name;
                    if (!nome) continue;
                    const key = artistKey({name: nome});
                    if (!selected.has(key)) selected.set(key, {...artist, nome});
                }
            }

            if (personalized) {
                addArtists(rotateArtists(personalized.national, slot), brazilianSlots);
                addArtists(rotateArtists(personalized.ranked, slot));
                addArtists(rotateArtists(personalized.national, slot));
            }
            if (selected.size < recommendationLimit) {
                const fallback = new Map();
                for (const artist of [...dailyRanking, ...catalogArtists]) {
                    const nome = artist.nome || artist.name;
                    if (!nome) continue;
                    const key = artistKey({name: nome});
                    if (!fallback.has(key)) fallback.set(key, artist);
                    if (fallback.size === artistPoolLimit) break;
                }
                addArtists(rotateArtists([...fallback.values()], slot));
            }
            const identity = JSON.stringify([...selected.values()].map(({nome, id, genre, imgUrl, images}) =>
                [nome, id, genre, imgUrl, images]));
            if (identity === visibleArtistNames) return;
            visibleArtistNames = identity;
            exibirArtistas([...selected.values()]);
        }

        function scheduleArtistRotation() {
            clearTimeout(rotationTimer);
            const now = Date.now();
            const slot = Math.floor(now / artistRotationMs);
            if (renderedSlot !== slot) renderArtistSelection(slot);
            rotationTimer = setTimeout(scheduleArtistRotation, artistRotationMs - now % artistRotationMs);
        }

        document.addEventListener('visibilitychange', () => {
            if (!document.hidden) scheduleArtistRotation();
        });
        scheduleArtistRotation();

        async function carregarCatalogo() {
            let catalog = [];
            try {
                const response = await fetch('/artista', {signal: AbortSignal.timeout(5000)});
                if (response.ok) catalog = await response.json();
            } catch {
                // Use the default artists if the local catalog cannot be reached.
            }
            if (Array.isArray(catalog) && catalog.length) {
                catalogArtists = catalog;
                renderArtistSelection();
            }
        }

        async function showDailyArtists() {
            try {
                const response = await fetch('/last-fm/discovery?method=chart.getTopArtists', {
                    signal: AbortSignal.timeout(17000)
                });
                if (!response.ok) return;
                const {artists} = await response.json();
                dailyRanking = readRankedArtists(artists?.artist)
                    .map(artist => ({...artist, imgUrl: artist.images.find(usablePhoto)}));
                renderArtistSelection();
            } catch {
                // The catalog remains available while the Last.fm ranking is unavailable.
            }
        }
        function exibirArtistas(artistasParaExibir) {
                artistsGrid.innerHTML = ''; // Limpa a div

                // Pega os 6 primeiros para manter o layout
                const artistasPopulares = artistasParaExibir.slice(0, recommendationLimit);

                artistasPopulares.forEach(artist => {
                    const artistCard = document.createElement("div");
                    artistCard.classList.add("artist-card");
                    const link = document.createElement('a');
                    const identity = artist.id ? {id: artist.id} : {artist: artist.nome};
                    link.href = "/frontend/pages/artista.html?" + new URLSearchParams(identity);
                    const image = document.createElement('img');
                    const info = document.createElement('div');
                    const name = document.createElement('h3');
                    name.textContent = artist.nome;
                    const label = document.createElement('p');
                    label.textContent = artist.genre || 'Artista';
                    info.append(name, label);
                    link.append(image, info);
                    artistCard.append(link);
                    artistsGrid.appendChild(artistCard);
                    if (artist.genre && window.SynchroFavoriteImages) {
                        const artwork = document.createElement("div");
                        link.replaceChild(artwork, image);
                        window.SynchroFavoriteImages.show(artwork, {
                            kind: "artist", title: artist.nome, artist: artist.nome, id: artist.id,
                            preferredImages: artist.images, alt: ""
                        });
                    } else {
                        loadArtistPhoto(image, artist);
                    }
                });
        }
        carregarCatalogo();
        showDailyArtists();
        async function showRecommendations() {
            if (recommendationStatus) recommendationStatus.textContent = 'Buscando recomendações para você…';
            try {
                const {genres, message} = await recommendationProfile;
                if (!genres) {
                    if (recommendationStatus) recommendationStatus.textContent = message;
                    return;
                }
                const artists = await loadPersonalizedArtists(genres);
                if (!artists || (!artists.ranked.length && !artists.national.length)) {
                    throw new Error('Recomendações indisponíveis.');
                }
                personalized = artists;
                renderArtistSelection();
                if (recommendationStatus) recommendationStatus.textContent = 'Para você · ' + genres.join(', ');
            } catch {
                if (recommendationStatus) recommendationStatus.textContent =
                    'Exibindo a lista padrão. Não foi possível personalizar as recomendações agora.';
            }
        }
        showRecommendations();
    }

    // =========================================================
    // EXIBIR ÁLBUNS POPULARES
    // =========================================================

    if (albumsGrid) {
        const status = document.getElementById('albumRecommendationStatus');
        const placeholder = '/frontend/assets/images/catalog-placeholder.svg';
        const defaultAlbums = [
            {name: 'Debí Tirar Más Fotos', artist: 'Bad Bunny', cover: 'cards/DebíTirarMásFotos.webp'},
            {name: 'MAYHEM', artist: 'Lady Gaga', cover: 'cards/mayhem.webp'},
            {name: 'LUX', artist: 'ROSALÍA', cover: 'lux.jpg'},
            {name: 'Getting Killed', artist: 'Geese', cover: 'cards/gettingkilled.webp'},
            {name: 'Starboy', artist: 'The Weeknd', cover: 'cards/starboy.webp'},
            {name: 'Bleeds', artist: 'Wednesday', cover: 'cards/bleeds.webp'}
        ];
        let dailyAlbums = [];
        let recentAlbums = [];
        let visibleAlbums = [];

        function renderPopularAlbums() {
            const next = [];
            const seen = new Set();
            for (const album of [...recentAlbums, ...dailyAlbums, ...defaultAlbums]) {
                const title = album?.name?.trim();
                const artist = album?.artist?.trim();
                if (!title || !artist) continue;
                const key = JSON.stringify([artistKey({name: title}), artistKey({name: artist})]);
                if (seen.has(key)) continue;
                seen.add(key);
                next.push(album);
                if (next.length === recommendationLimit) break;
            }
            if (next.length === visibleAlbums.length && next.every((album, index) => album === visibleAlbums[index])) return;
            visibleAlbums = next;
            renderAlbums(next);
        }

        function renderAlbums(albums, grid = albumsGrid) {
            grid.replaceChildren();
            for (const album of albums) {
                const card = document.createElement('div');
                card.className = 'album-card';
                const link = document.createElement('a');
                const page = album.artistCard ? 'artista' : 'album';
                const identity = album.artistCard ? {artist: album.artist} :
                    album.id ? {id: album.id} : {album: album.name, artist: album.artist};
                link.href = '/frontend/pages/' + page + '.html?' + new URLSearchParams(identity);
                const artwork = document.createElement('div');
                const info = document.createElement('div');
                const name = document.createElement('h3');
                name.textContent = album.name;
                const artist = document.createElement('p');
                artist.textContent = album.artist;
                if (album.releaseDate) artist.textContent += ' · ' + album.releaseDate.split('-').reverse().join('/');
                info.append(name, artist);
                if (album.reason) {
                    const reason = document.createElement('p');
                    reason.textContent = album.reason;
                    info.append(reason);
                }
                link.append(artwork, info);
                card.append(link);
                grid.append(card);
                if (window.SynchroFavoriteImages) {
                    const preferredImages = Array.isArray(album.images) ? [...album.images] : [];
                    if (album.cover) preferredImages.unshift('/frontend/assets/images/' + album.cover);
                    window.SynchroFavoriteImages.show(artwork, {
                        kind: album.artistCard ? 'artist' : 'album',
                        title: album.artistCard ? album.artist : album.name,
                        artist: album.artist, id: album.id, preferredImages, alt: ''
                    });
                } else {
                    const image = document.createElement('img');
                    image.src = album.cover ? '/frontend/assets/images/' + album.cover : placeholder;
                    image.loading = 'lazy';
                    image.decoding = 'async';
                    image.alt = '';
                    artwork.append(image);
                }
            }
        }

        async function showRecentReleases() {
            const {genres, message} = await recommendationProfile;
            const tags = recommendationTags(genres || []);
            if (!tags.size) {
                if (status) status.textContent = 'Seleção padrão · ' +
                    (message || 'Conecte o Last.fm para receber recomendações.');
                return;
            }
            const query = new URLSearchParams();
            for (const tag of tags.keys()) query.append('genre', tag);
            const response = await fetch('/catalog/albums/recommendations?' + query, {
                signal: AbortSignal.timeout(45000)
            });
            if (!response.ok) throw new Error('Recomendações indisponíveis.');
            const {albums, matchedCount = albums.length, unavailable} = await response.json();
            recentAlbums = albums;
            renderPopularAlbums();
            if (!status) return;

            if (matchedCount >= recommendationLimit) {
                status.textContent = 'Lançamentos dos últimos 30 dias · ' + genres.join(', ');
            } else if (matchedCount > 0) {
                status.textContent = genres.join(', ') + ' · Seleção completada com outros destaques.';
            } else if (albums.length) {
                status.textContent = 'Exibindo destaques recentes enquanto não há recomendações por gênero.';
            } else {
                status.textContent = unavailable ? 'Seleção padrão · Recomendações indisponíveis no momento.'
                    : 'Seleção padrão · Nenhum lançamento recente compatível com seus gêneros.';
            }
        }

        async function showDailyAlbums() {
            try {
                const response = await fetch('/catalog/albums/popular', {signal: AbortSignal.timeout(75000)});
                if (!response.ok || recentAlbums.length >= recommendationLimit) return;
                const albums = await response.json();
                if (Array.isArray(albums) && albums.length) {
                    dailyAlbums = albums;
                    renderPopularAlbums();
                }
            } catch {
                // Keep the available selection when the daily chart cannot load.
            }
        }

        async function showTopArtistRecommendations() {
            const grid = document.getElementById('topArtistRecommendations');
            const feedback = document.getElementById('topArtistRecommendationStatus');
            if (!grid || !feedback) return;
            try {
                const response = await fetch('/recomendacoes', {
                    credentials: 'same-origin', signal: AbortSignal.timeout(20000)
                });
                if (response.status === 401) {
                    feedback.textContent = 'Entre no Synchro para ver suas sugestões.';
                    return;
                }
                if (!response.ok) throw new Error();
                const {items, personalized} = await response.json();
                const cards = items.map(item => ({
                    id: item.id,
                    name: item.titulo,
                    artist: item.nomeArtista || '',
                    artistCard: item.id == null,
                    reason: item.motivo,
                    images: /^https:\/\//i.test(item.capaUrl || '') ? [item.capaUrl] : []
                }));
                renderAlbums(cards, grid);
                feedback.textContent = cards.length === 0 ? 'Ainda não há sugestões disponíveis.'
                    : personalized ? 'Uma seleção com base no seu gosto musical.'
                    : 'Sugestões do catálogo do Synchro.';
            } catch {
                feedback.textContent = 'Não foi possível carregar as sugestões.';
            }
        }

        renderPopularAlbums();
        showTopArtistRecommendations();
        if (status) status.textContent = 'Seleção padrão · Buscando lançamentos para você…';
        showDailyAlbums();
        showRecentReleases().catch(() => {
            if (status) status.textContent = 'Seleção padrão · Recomendações indisponíveis no momento.';
        });
    }

    // =========================================================
    // EXIBIR GÊNEROS POPULARES
    // =========================================================

    if (genresGrid) {
        genresData.forEach(genre => {
            const genreCard = document.createElement("div");
            genreCard.classList.add("genre-card");

            genreCard.innerHTML = `
                <a href="/frontend/pages/genero.html?${new URLSearchParams({name: genre.name})}">
                    <img loading="lazy" decoding="async" src="${genre.image}" alt="Imagem de ${genre.name}">
                    <div>
                        <h3>${genre.name}</h3>
                    </div>
                </a>
            `;

            genresGrid.appendChild(genreCard);
        });
    }

    // =========================================================
    // EXIBIR AVALIAÇÕES
    // =========================================================

    if (reviewsGrid) {
        const feedback = document.getElementById('recentReviewsStatus');
        const imageRoot = '/frontend/assets/images/';

        function safeReviewImage(url) {
            if (!usablePhoto(url)) return null;
            try {
                const image = new URL(url, window.location.origin);
                return !image.username && !image.password ? image.href : null;
            } catch {
                return null;
            }
        }

        function showReview(item) {
            const track = item.mediaType === 'track';
            const artistReview = item.mediaType === 'artist';
            const artist = artistReview ? item.title : item.artistName || '';
            const card = document.createElement('article');
            card.className = 'reviews-card';

            const header = document.createElement('div');
            header.className = 'review-header';
            const artwork = document.createElement('div');
            artwork.className = 'review-artwork';
            const info = document.createElement('div');
            info.className = 'review-info';
            const heading = document.createElement('h3');
            const mediaLink = document.createElement('a');
            mediaLink.href = '/frontend/pages/' + (artistReview ? 'artista' : track ? 'musica' : 'album') + '.html?' +
                new URLSearchParams({id: item.mediaId});
            mediaLink.textContent = item.title || 'Mídia sem título';
            heading.append(mediaLink);
            const subtitle = document.createElement('h5');
            subtitle.textContent = artistReview ? 'Artista' : artist || (track ? 'Música' : 'Álbum');
            info.append(heading, subtitle);
            if (item.rating != null) {
                const rating = document.createElement('span');
                rating.className = 'rating';
                rating.textContent = item.rating + '/10';
                info.append(rating);
            }
            header.append(artwork, info);

            const comment = document.createElement('p');
            comment.className = 'review-text';
            comment.textContent = item.review?.trim() || 'Avaliação sem comentário.';

            const author = document.createElement('div');
            author.className = 'review-user';
            const avatar = document.createElement('img');
            avatar.className = 'user-avatar';
            avatar.alt = '';
            avatar.width = 35;
            avatar.height = 35;
            avatar.loading = 'lazy';
            avatar.decoding = 'async';
            avatar.referrerPolicy = 'no-referrer';
            const avatarSources = [...new Set([item.profileImageUrl, item.avatarUrl]
                .map(safeReviewImage).filter(Boolean))];
            function loadAvatar() {
                const source = avatarSources.shift();
                avatar.onerror = source ? loadAvatar : null;
                avatar.src = source || imageRoot + 'user/userphoto.jpg';
            }
            loadAvatar();
            const profile = document.createElement('a');
            profile.href = '/frontend/pages/perfil-publico.html?' + new URLSearchParams({user: item.authorId});
            profile.append(avatar, '@' + item.username);
            window.SynchroModeration?.appendReviewBadge(profile, item.authorVvip);
            author.append(profile);

            card.append(header, comment, author);
            reviewsGrid.append(card);
            window.SynchroModeration?.addDelete(card, {
                url: '/review/' + encodeURIComponent(item.id),
                label: 'Excluir avaliação', status: feedback, onDelete: () => showRecentReviews()
            });

            const cover = safeReviewImage(item.coverUrl);
            if (window.SynchroFavoriteImages) {
                window.SynchroFavoriteImages.show(artwork, {
                    kind: artistReview ? 'artist' : track ? 'track' : 'album', id: item.mediaId, title: item.title, artist,
                    className: 'album-cover', preferredImages: cover ? [cover] : [], alt: ''
                });
            } else {
                const image = document.createElement('img');
                image.className = 'album-cover';
                image.alt = '';
                image.loading = 'lazy';
                const placeholder = imageRoot + (artistReview ? 'artist-placeholder.svg' : 'catalog-placeholder.svg');
                image.onerror = () => { image.onerror = null; image.src = placeholder; };
                image.src = cover || placeholder;
                artwork.append(image);
            }
        }

        async function showRecentReviews() {
            try {
                const response = await fetch('/review/recent', {signal: AbortSignal.timeout(8000)});
                if (!response.ok) throw new Error('Avaliações indisponíveis.');
                const items = await response.json();
                reviewsGrid.replaceChildren();
                items.forEach(showReview);
                if (feedback) feedback.textContent = items.length ? '' : 'Ainda não há avaliações da comunidade.';
            } catch {
                if (feedback) feedback.textContent = 'Não foi possível carregar as avaliações agora.';
            }
        }

        showRecentReviews();
        window.addEventListener('pageshow', event => {
            if (event.persisted) showRecentReviews();
        });
    }

    // =========================================================
    // MENU LATERAL RESPONSIVO
    // =========================================================

    const menuToggle = document.querySelector(".menu-toggle");
    const sidebar = document.querySelector(".sidebar");

    if (menuToggle && sidebar) {
        menuToggle.addEventListener("click", () => {
            sidebar.classList.toggle("active");
        });
    }

    // A pesquisa é inicializada pelo script compartilhado search.js.
});
