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
        { name: "POP", image: "/frontend/assets/images/cards/michael.webp", link: "https://open.spotify.com/playlist/37i9dQZF1DX6aTaZa0K6VA?si=9a3f83c6bf56418e" },
        { name: "MPB", image: "/frontend/assets/images/cards/djavan.webp", link: "https://open.spotify.com/playlist/37i9dQZF1DWZlBWh8A1Ygd?si=d4d90b4ba68b4a50" },
        { name: "FUNK", image: "/frontend/assets/images/funk.jpg", link: "https://open.spotify.com/playlist/37i9dQZF1DWTkIwO2HDifB?si=70f49f1ffd354005" },
        { name: "ROCK", image: "/frontend/assets/images/cards/queen.webp", link: "https://open.spotify.com/playlist/37i9dQZF1DXcmaoFmN75bi?si=59cadc7d77c84749" },
        { name: "RAP", image: "/frontend/assets/images/cards/tupac.webp", link: "https://open.spotify.com/playlist/37i9dQZF1DX186v583rmzp?si=fa1f1c99a2fd4f91" }
    ];

    const reviewsData = [
        {
            user: "silverzenith",
            userphoto: "/frontend/assets/images/cards/user/users/silverzenith.webp",
            album: "/frontend/assets/images/cards/user/tcfsr.webp",
            albumname: "Three Cheers for Sweet Revenge",
            artist: "My Chemical Romance",
            review: '"Um dos melhores álbuns conceituais da história, com certeza daria um bom filme"',
            rating: "9/10"
        },
        {
            user: "disintelia",
            userphoto: "/frontend/assets/images/cards/user/users/disintelia.webp",
            album: "/frontend/assets/images/cards/user/pornography.webp",
            albumname: "Pornography",
            artist: "The Cure",
            review: '"Esse álbum tem uma atmosfera melancólica e fria tão perfeita e, apesar disso, consegue passar uma sensação tão aconchegante. Definitivamente supera o Disintegrated e é o melhor da discografia do The cure."',
            rating: "10/10"
        },
        {
            user: "jinka070",
            userphoto: "/frontend/assets/images/cards/user/users/jinka070.webp",
            album: "/frontend/assets/images/cards/user/biohazard.webp",
            albumname: "BIOHAZARD",
            artist: "Trash Poll",
            review: '"somente a nata do nu metal brasileiro"',
            rating: "9.5/10"
        },
        {
            user: "Tavio_OoO",
            userphoto: "/frontend/assets/images/cards/user/users/Tavio_OoO.webp",
            album: "/frontend/assets/images/cards/user/bythewater.webp",
            albumname: "By The Water",
            artist: "Sondae",
            review: '"Ao contrário da maior parte dos álbuns cristãos que já ouvi, este apresenta um grande foco na musicalidade e sonoridade e não somente ao louvor, e só nisso já me ganhou, mas além disso, o conteúdo introspectivo retrata muito mais uma maior conexão com Jesus Cristo que religiosidade, tornando esse álbum uma joia rara em meio a tantos outros"',
            rating: "8.5/10"
        },
        {
            user: "Saii0710",
            userphoto: "/frontend/assets/images/cards/user/users/Saii0710.webp",
            album: "/frontend/assets/images/cards/user/SSPSG.webp",
            albumname: "SE SONHOS PUDESSEM SER GUARDADOS",
            artist: "myseryswin",
            review: '"O álbum mostra o refinamento de Swin em comparação com seus projetos anteriores. Melhor música: feiticeiro(eu merecia essa dança tanto assim?)"',
            rating: "9/10"
        },
        {
            user: "Alz7",
            userphoto: "/frontend/assets/images/cards/user/users/Alz7.webp",
            album: "/frontend/assets/images/cards/user/sonofspergy.webp",
            albumname: "Son Of Spergy",
            artist: "Daniel Caesar",
            review: '" é o último álbum lançado de Daniel Caesar. Sendo um álbum que Daniel foca em contar a jornada de amadurecimento, fé e reconciliação. O álbum explora o peso dessa herança religiosa e familiar, abordando temas como masculinidade, erros do passado e a busca por perdão. O ritmo do projeto é intencionalmente lento, apostando em produções atmosféricas e vocais crus usando muito os corais gospels. Sendo um retrato honesto de um artista aprendendo a lidar com suas próprias fraquezas."',
            rating: "9/10"
        }
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
            if (!Array.isArray(genres)) return [];
            const tags = recommendationTags(genres);
            if (!tags.size) return [];

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

            const artists = new Map([...nationalArtists].slice(0, brazilianSlots));
            for (const [key, {artist}] of ranked) {
                if (artists.size === recommendationLimit) break;
                if (!artists.has(key)) artists.set(key, nationalArtists.get(key) || artist);
            }
            for (const [key, artist] of nationalArtists) {
                if (artists.size === recommendationLimit) break;
                if (!artists.has(key)) artists.set(key, artist);
            }
            return [...artists.values()].map(artist => ({...artist, nome: artist.name}));
        } catch {
            return [];
        }
    }

    const recommendationProfile = artistsGrid || albumsGrid ? loadRecommendationGenres().catch(() => ({
        message: 'Não foi possível consultar seu perfil musical agora.'
    })) : null;

    if (artistsGrid) {
        const artistsStatus = document.getElementById('popularArtistsStatus');
        const recommendationStatus = document.getElementById('artistRecommendationStatus');
        let personalized = false;
        let dailyRankingShown = false;

        async function carregarCatalogo() {
            let catalog = [];
            try {
                const response = await fetch('/artista', {signal: AbortSignal.timeout(5000)});
                if (response.ok) catalog = await response.json();
            } catch {
                // Use the default artists if the local catalog cannot be reached.
            }
            if (!personalized && !dailyRankingShown) {
                exibirArtistas(Array.isArray(catalog) && catalog.length ? catalog : artistsData);
            }
        }

        async function showDailyArtists() {
            try {
                const response = await fetch('/last-fm/discovery?method=chart.getTopArtists', {
                    signal: AbortSignal.timeout(17000)
                });
                if (!response.ok || personalized) return;
                const {artists} = await response.json();
                const ranked = readRankedArtists(artists?.artist);
                if (!ranked.length || personalized) return;
                dailyRankingShown = true;
                exibirArtistas(ranked.map(artist => ({...artist, nome: artist.name,
                    imgUrl: artist.images.find(usablePhoto)})));
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

                if (artistsStatus) {
                    artistsStatus.textContent = 'Artistas carregados com sucesso: ' +
                        new Intl.ListFormat('pt-BR').format(artistasPopulares.map(artist => artist.nome)) + '.';
                }
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
                if (!artists.length) throw new Error('Recomendações indisponíveis.');
                personalized = true;
                exibirArtistas(artists);
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
                    images: /^https:\/\//i.test(item.capaUrl || '') ? [item.capaUrl] : []
                }));
                renderAlbums(cards, grid);
                feedback.textContent = cards.length === 0 ? 'Ainda não há sugestões disponíveis.'
                    : personalized ? 'Com base nos artistas que você mais ouve no Last.fm.'
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
                <a href="${genre.link || "#"}" target="_blank">
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
        reviewsData.forEach(review => {
            const reviewsCard = document.createElement("div");
            reviewsCard.classList.add("reviews-card");

            reviewsCard.innerHTML = `
                <div class="review-header">
                    <img loading="lazy" decoding="async" class="album-cover" src="${review.album}" alt="Imagem de ${review.albumname}">
                    <div class="review-info">
                        <h3>${review.albumname}</h3>
                        <h5>${review.artist}</h5>
                        <div class="rating">
                            <h3>${review.rating}</h3>
                        </div>
                    </div>
                </div>

                <p class="review-text">${review.review}</p>

                <div class="review-user">
                    <img loading="lazy" decoding="async" class="user-avatar" src="${review.userphoto}" alt="Imagem de ${review.user}">
                    <span>${review.user}</span>
                </div>
            `;

            reviewsGrid.appendChild(reviewsCard);
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
