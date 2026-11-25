function profileRankingArtist(item) {
    return typeof item.artist === 'string' ? item.artist : item.artist?.name || '';
}

function profileRankingImages(item) {
    const images = Array.isArray(item?.image) ? item.image : [item?.image];
    return images.map(image => typeof image === 'string' ? image : image?.['#text']).filter(Boolean).reverse();
}

function appendProfileRankingLeader(container, item, kind, genre) {
    if (!container || !item?.name) return;
    const artist = kind === 'artist' ? item.name : profileRankingArtist(item);
    const artwork = document.createElement('div');
    artwork.className = 'report-leader-artwork';
    container.append(artwork);
    const alt = genre ? 'Foto de ' + artist + ', artista em destaque em ' + genre
        : kind === 'artist' ? 'Foto de ' + item.name : 'Capa de ' + item.name;
    const preferredImages = [...profileRankingImages(item.album), ...profileRankingImages(item)];
    if (window.SynchroFavoriteImages) {
        window.SynchroFavoriteImages.show(artwork, {kind, title: item.name, artist, preferredImages, alt});
    } else {
        const image = document.createElement('img');
        image.src = kind === 'artist' ? '/frontend/assets/images/artist-placeholder.svg'
            : '/frontend/assets/images/catalog-placeholder.svg';
        image.alt = alt;
        artwork.append(image);
    }
}

document.addEventListener('synchro:own-profile', renderProfileSections);
document.addEventListener('synchro:public-profile', renderProfileSections);

async function renderProfileSections({detail: user, type: eventType}) {
    const isPublic = eventType === 'synchro:public-profile';
    const publicId = isPublic ? user.id : undefined;
    const lastFmUser = user.lastFmConnected;
    const artistGrid = document.querySelector('.artist');
    const reviewGrid = document.querySelector('#minhas-avaliacoes .profile-review-list');
    const playlistGrid = document.querySelector('.playlist');
    const reportArtistGrid = document.querySelector('.report-artists');
    const reportAlbumGrid = document.querySelector('.report-albums');
    const reportMusicGrid = document.querySelector('.report-musics');
    const reportGenreGrid = document.querySelector('.report-genres');
    const periodSelect = document.getElementById('rankingPeriod');
    const rankingStatus = document.getElementById('rankingStatus');

    function empty(container, message) {
        if (!container) return;

        container.replaceChildren();

        const p = document.createElement('p');
        p.className = 'profile-empty';
        p.textContent = message;

        container.appendChild(p);
    }

    // Avaliacoes e playlists devem pertencer ao usuario Synchro.
    // Nao apresentar exemplos como se fossem dados pessoais.

    // =====================================================
    // AVALIACOES REAIS DO USUARIO SYNCHRO
    // =====================================================

    async function carregarMinhasAvaliacoes() {

        if (!reviewGrid) {
            return;
        }

        empty(reviewGrid, "Carregando suas avaliações...");

        try {
            const response = await fetch("/review/me", {
                credentials: "same-origin",
                headers: {
                    Accept: "application/json"
                }
            });

            if (!response.ok) {
                throw new Error(
                    `Erro ao consultar avaliações: ${response.status}`
                );
            }

            const reviews = await response.json();

            if (!Array.isArray(reviews) || reviews.length === 0) {
                empty(
                    reviewGrid,
                    "Você ainda não publicou avaliações no Synchro."
                );
                return;
            }

            reviewGrid.replaceChildren();

            const mediaTypes = {Musica: "track", Album: "album", Artista: "artist"};
            for (const review of reviews) {
                reviewGrid.append(createProfileReviewCard({
                    mediaType: mediaTypes[review.midiatype],
                    mediaId: review.artistId ?? review.midiaId,
                    title: review.midiaTitulo,
                    artistName: review.artistName,
                    coverUrl: review.coverUrl,
                    rating: review.rating,
                    review: review.review
                }));
            }

        } catch (error) {
            console.error(
                "Erro ao carregar avaliações do perfil:",
                error
            );

            empty(
                reviewGrid,
                "Não foi possível carregar suas avaliações."
            );
        }
    }

    if (isPublic) {
        loadProfileFavoriteArtists(user.id);
        loadPublicReviews(user.id);
        document.getElementById('publicTopArtists').hidden = !lastFmUser;
        document.getElementById('publicRankings').hidden = !lastFmUser;
        if (!lastFmUser) return;
    } else {
        whenProfileSectionNear(reviewGrid?.closest('section'), carregarMinhasAvaliacoes);
        whenProfileSectionNear(document.getElementById('profileFavoriteArtists').closest('section'),
            () => loadProfileFavoriteArtists(user.id));
    }


    // A biblioteca de playlists é carregada por features.js usando a conta autenticada.



    const numberText = value => {
        const number = Number(value);

        return value !== undefined &&
        value !== null &&
        value !== '' &&
        Number.isFinite(number) &&
        number >= 0
            ? number.toLocaleString('pt-BR')
            : '—';
    };

    function artistLink(name) {
        return '/frontend/pages/artista.html?artist=' +
            encodeURIComponent(name);
    }

    function trackLink(artist, track) {
        return '/frontend/pages/musica.html?artist=' +
            encodeURIComponent(artist) +
            '&track=' +
            encodeURIComponent(track);
    }

    function makeRankItem(rank, title, subtitle, count, href) {
        const card = document.createElement('div');
        card.className = 'weekly-card';

        const line = document.createElement('div');
        line.className = 'rank-item';

        const place = document.createElement('h3');
        place.textContent = '#' + (rank + 1);

        const name = document.createElement('h3');

        if (href) {
            const link = document.createElement('a');
            link.href = href;
            link.textContent = title;
            name.appendChild(link);
        } else {
            name.textContent = title;
        }

        line.append(place, name);

        if (subtitle) {
            const artist = document.createElement('p');
            artist.textContent = subtitle;
            line.appendChild(artist);
        }

        const plays = document.createElement('p');
        plays.textContent =
            numberText(count) + ' Synchs';

        line.appendChild(plays);
        card.appendChild(line);

        return card;
    }

    function topArray(payload, key, itemKey) {
        const result = payload?.[key]?.[itemKey];

        if (!result) {
            return [];
        }

        return (Array.isArray(result) ? result : [result]).filter(item => item?.name);
    }

    function renderArtists(items) {
        reportArtistGrid?.replaceChildren();
        artistGrid?.replaceChildren();

        if (!items.length) {
            empty(
                reportArtistGrid,
                'Nenhum artista ouvido neste período.'
            );

            empty(
                artistGrid,
                'Nenhum artista ouvido neste período.'
            );

            return;
        }

        appendProfileRankingLeader(reportArtistGrid, items[0], 'artist');

        items.forEach((item, index) => {
            if (!item?.name) {
                return;
            }

            reportArtistGrid?.appendChild(
                makeRankItem(
                    index,
                    item.name,
                    '',
                    item.playcount,
                    artistLink(item.name)
                )
            );

            if (!artistGrid || index >= 6) {
                return;
            }

            const card = document.createElement('div');
            card.className = 'artist-card';

            const link = document.createElement('a');
            link.href = artistLink(item.name);

            const info = document.createElement('div');

            const title = document.createElement('h3');
            title.textContent = item.name;

            const desc = document.createElement('p');
            desc.textContent =
                numberText(item.playcount) + ' Synchs';

            info.append(title, desc);

            const artwork = document.createElement('div');
            artwork.className = 'profile-artist-photo';
            link.append(artwork, info);
            card.append(link);
            artistGrid.append(card);
            const preferredImages = profileRankingImages(item);
            window.SynchroFavoriteImages.show(artwork, {
                kind: 'artist', artist: item.name, title: item.name, preferredImages, alt: ''
            });
        });
    }

    function renderAlbums(items) {
        reportAlbumGrid?.replaceChildren();

        if (!items.length) {
            empty(
                reportAlbumGrid,
                'Nenhum álbum ouvido neste período.'
            );

            return;
        }

        appendProfileRankingLeader(reportAlbumGrid, items[0], 'album');

        items.forEach((item, index) => {
            if (!item?.name) {
                return;
            }

            const artist = profileRankingArtist(item);

            reportAlbumGrid?.appendChild(
                makeRankItem(
                    index,
                    item.name,
                    artist,
                    item.playcount,
                    null
                )
            );
        });
    }

    function renderTracks(items) {
        reportMusicGrid?.replaceChildren();

        if (!items.length) {
            empty(
                reportMusicGrid,
                'Nenhuma música ouvida neste período.'
            );

            return;
        }

        appendProfileRankingLeader(reportMusicGrid, items[0], 'track');

        items.forEach((item, index) => {
            if (!item?.name) {
                return;
            }

            const artist = profileRankingArtist(item);

            reportMusicGrid?.appendChild(
                makeRankItem(
                    index,
                    item.name,
                    artist,
                    item.playcount,
                    artist
                        ? trackLink(artist, item.name)
                        : null
                )
            );
        });
    }

    let latestRankingRequest = 0;
    const retryButton = document.getElementById('retryRankings');
    retryButton.addEventListener('click', carregarRankings);

    async function carregarRankings(announce = true) {
        const period = periodSelect?.value || '7day';

        if (!lastFmUser) {
            empty(
                artistGrid,
                'Conecte seu Last.fm para ver seus artistas.'
            );

            empty(
                reportArtistGrid,
                'Conecte seu Last.fm para ver seu ranking.'
            );

            empty(
                reportAlbumGrid,
                'Conecte seu Last.fm para ver seu ranking.'
            );

            empty(
                reportMusicGrid,
                'Conecte seu Last.fm para ver seu ranking.'
            );

            if (rankingStatus) {
                rankingStatus.textContent =
                    'Last.fm não conectado.';
            }

            return;
        }

        if (announce) {
            rankingStatus.textContent = 'Carregando rankings...';
            retryButton.hidden = true;
        }

        const requestNumber = ++latestRankingRequest;
        let unavailable = false;
        let timedOut = false;
        async function load(type, itemName, render, containers) {
            try {
                const data = await fetchProfileRanking(type, period, publicId);
                if (requestNumber === latestRankingRequest) render(topArray(data, 'top' + type, itemName));
            } catch (error) {
                if (requestNumber !== latestRankingRequest) return;
                unavailable = true;
                timedOut ||= error.status === 504;
                for (const container of containers) empty(container, "Ranking indisponível no momento.");
            }
        }
        await Promise.all([
            load('artists', 'artist', renderArtists, [artistGrid, reportArtistGrid]),
            load('albums', 'album', renderAlbums, [reportAlbumGrid]),
            load('tracks', 'track', renderTracks, [reportMusicGrid])
        ]);
        if (requestNumber !== latestRankingRequest) return;
        retryButton.hidden = !unavailable;
        rankingStatus.textContent = timedOut ? 'O Last.fm demorou para responder.'
                : unavailable ? 'Não foi possível carregar todos os rankings.' : '';
    }

    // =====================================================
    // TOTAIS REAIS DE ARTISTAS E ALBUNS
    // =====================================================

    async function carregarTotaisMusicais() {

        if (!lastFmUser) {
            return;
        }

        const artistsCounter =
            document.getElementById("profileArtists");

        const albumsCounter =
            document.getElementById("profileAlbums");

        // "overall" consulta todo o historico,
        // independentemente do periodo selecionado
        // para o relatorio semanal.

        const resultados = await Promise.allSettled([
            user.data?.artistas == null ? fetchProfileRanking("artists", "overall", publicId) : null,
            user.data?.lastFmAlbums == null ? fetchProfileRanking("albums", "overall", publicId) : null
        ]);

        const artistas = resultados[0];
        const albuns = resultados[1];

        if (artistas.status === "fulfilled") {

            const totalArtistas =
                artistas.value?.topartists?.["@attr"]?.total;

            if (artistsCounter) {
                if (totalArtistas != null) artistsCounter.textContent =
                    numberText(totalArtistas);
            }

        } else {
            console.error(
                "Erro ao obter total de artistas:",
                artistas.reason
            );
        }

        if (albuns.status === "fulfilled") {

            const totalAlbuns =
                albuns.value?.topalbums?.["@attr"]?.total;

            if (albumsCounter) {
                if (totalAlbuns != null) albumsCounter.textContent =
                    numberText(totalAlbuns);
            }

        } else {
            console.error(
                "Erro ao obter total de albuns:",
                albuns.reason
            );
        }
    }

    periodSelect?.addEventListener(
        'change',
        carregarRankings
    );

    if (isPublic) {
        await Promise.all([carregarRankings(), carregarTotaisMusicais()]);
    } else if (lastFmUser) {
        document.addEventListener('synchro:profile-ranking-updated', ({detail}) => {
            if (latestRankingRequest && detail.period === (periodSelect?.value || '7day')) carregarRankings(false);
            if (detail.period === 'overall') carregarTotaisMusicais();
        });
        const loadRankings = () => {
            if (!latestRankingRequest) carregarRankings();
        };
        whenProfileSectionNear(artistGrid?.closest('section'), loadRankings);
        whenProfileSectionNear(reportArtistGrid?.closest('section'), loadRankings);
        whenProfileSectionNear(document.getElementById('profileArtists'), carregarTotaisMusicais);
    } else {
        carregarRankings();
    }

}

async function loadProfileFavoriteArtists(userId) {
    const list = document.getElementById('profileFavoriteArtists');
    const status = document.getElementById('profileFavoriteArtistsStatus');
    try {
        const response = await fetch('/favorites/artists/users/' + encodeURIComponent(userId), {cache: 'no-store'});
        if (!response.ok) throw new Error('Favoritos indisponíveis');
        const artists = await response.json();
        for (const artist of artists) {
            const card = document.createElement('article');
            card.className = 'artist-card';
            const link = document.createElement('a');
            link.href = 'artista.html?' + new URLSearchParams({artist});
            const artwork = document.createElement('div');
            artwork.className = 'profile-artist-photo';
            const name = document.createElement('h3');
            name.textContent = artist;
            link.append(artwork, name);
            card.append(link);
            list.append(card);
            window.SynchroFavoriteImages?.show(artwork, {kind: 'artist', artist, title: artist, alt: ''});
        }
        status.hidden = artists.length > 0;
        status.textContent = artists.length ? '' : 'Este perfil ainda não marcou artistas como favoritos.';
    } catch {
        status.textContent = 'Não foi possível carregar os artistas favoritos.';
    }
}

function createProfileReviewCard(review) {
    const card = document.createElement('article');
    card.className = 'review-card';
    const content = document.createElement('div');
    const title = document.createElement('h3');
    const link = document.createElement('a');
    const types = {artist: ['Artista', 'artista.html'], track: ['Música', 'musica.html'], album: ['Álbum', 'album.html']};
    const [label, page] = types[review.mediaType] || ['Mídia'];
    const name = review.title || 'Mídia sem título';
    if (page && review.mediaId != null) {
        link.href = page + '?' + new URLSearchParams({id: review.mediaId});
        link.textContent = name;
        title.append(link);
    } else {
        title.textContent = name;
    }
    const details = document.createElement('p');
    details.textContent = [label, review.artistName, review.rating == null ? null : review.rating + '/10']
        .filter(Boolean).join(' · ');
    content.append(title, details);
    if (review.review) {
        const comment = document.createElement('p');
        comment.textContent = review.review;
        content.append(comment);
    }
    if (page) {
        const artwork = document.createElement('div');
        artwork.className = 'profile-review-artwork';
        card.append(artwork);
        window.SynchroFavoriteImages?.show(artwork, {
            kind: review.mediaType,
            id: review.mediaId,
            title: review.title,
            artist: review.mediaType === 'artist' ? review.title : review.artistName,
            preferredImages: [review.coverUrl],
            alt: '',
        });
    }
    card.append(content);
    return card;
}

function loadPublicReviews(userId) {
    const list = document.getElementById('publicReviewList');
    const status = document.getElementById('publicReviewStatus');
    const more = document.getElementById('morePublicReviews');
    let before;

    async function load() {
        more.disabled = true;
        status.textContent = 'Carregando avaliações…';
        try {
            const params = before == null ? '' : '?' + new URLSearchParams({before});
            const response = await fetch('/review/users/' + encodeURIComponent(userId) + params, {cache: 'no-store'});
            if (!response.ok) throw new Error('Avaliações indisponíveis');
            const data = await response.json();
            for (const review of data.items) list.append(createProfileReviewCard(review));
            before = data.nextBefore;
            status.textContent = list.childElementCount ? '' : 'Este perfil ainda não publicou avaliações.';
            more.textContent = 'Ver mais avaliações';
            more.hidden = before == null;
        } catch {
            status.textContent = 'Não foi possível carregar as avaliações.';
            more.textContent = 'Tentar novamente';
            more.hidden = false;
        } finally {
            more.disabled = false;
        }
    }

    more.addEventListener('click', load);
    load();
}


// =========================================================
// BOTÃO CONECTAR LAST.FM
// =========================================================


// =========================================================
// BIOGRAFIA EDITAVEL DO SYNCHRO
// =========================================================

document.addEventListener("synchro:own-profile", ({detail: user}) => {

    const biography =
        document.getElementById("profileBiography");

    const editButton =
        document.getElementById("editBiographyButton");

    const editor =
        document.getElementById("biographyEditor");

    const input =
        document.getElementById("biographyInput");

    const saveButton =
        document.getElementById("saveBiographyButton");

    const cancelButton =
        document.getElementById("cancelBiographyButton");

    const feedback =
        document.getElementById("biographyFeedback");

    if (
        !biography ||
        !editButton ||
        !editor ||
        !input ||
        !saveButton ||
        !cancelButton ||
        !feedback
    ) {
        return;
    }

    let savedBiography = user.data?.biography || "";
    biography.textContent = savedBiography;
    biography.hidden = !savedBiography.trim();

    editButton.addEventListener("click", () => {
        input.value = savedBiography;
        feedback.textContent = "";
        editor.hidden = false;
        editButton.hidden = true;
        input.focus();
    });

    cancelButton.addEventListener("click", () => {
        input.value = savedBiography;
        feedback.textContent = "";
        editor.hidden = true;
        editButton.hidden = false;
    });

    saveButton.addEventListener("click", async () => {
        const newBiography = input.value.trim();

        saveButton.disabled = true;
        feedback.textContent = "Salvando...";

        try {
            const response = await fetch(
                "/users/me/profile",
                {
                    method: "PUT",
                    credentials: "same-origin",
                    headers: {
                        "Content-Type": "application/json",
                        Accept: "application/json"
                    },
                    body: JSON.stringify({
                        biography: newBiography
                    })
                }
            );

            if (!response.ok) {
                throw new Error(
                    `Erro HTTP ${response.status}`
                );
            }

            const data = await response.json();

            savedBiography = data.biography || "";
            biography.textContent = savedBiography;
            biography.hidden = !savedBiography.trim();

            feedback.textContent = "";
            editor.hidden = true;
            editButton.hidden = false;

        } catch (error) {
            console.error(
                "Erro ao salvar biografia:",
                error
            );

            feedback.textContent =
                "Não foi possível salvar. Tente novamente.";

        } finally {
            saveButton.disabled = false;
        }
    });

});
