document.addEventListener("synchro:own-profile", async ({ detail: user }) => {
    const lastFmUser = user.lastFmConnected;
    const artistGrid = document.querySelector('.artist');
    const reviewGrid = document.querySelector('.review');
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

            reviews.forEach(review => {

                const card = document.createElement("div");
                card.className = "review-card";

                const content = document.createElement("div");

                const title = document.createElement("h3");
                title.textContent =
                    review.midiaTitulo || "Mídia sem título";

                const type = document.createElement("h5");
                type.textContent =
                    review.midiatype === "Musica"
                        ? "Música"
                        : review.midiatype === "Album"
                            ? "Álbum"
                            : "Mídia";

                const comment = document.createElement("p");
                comment.textContent = review.review || "";

                const rating = document.createElement("h3");

                rating.textContent =
                    review.rating !== null &&
                    review.rating !== undefined
                        ? `${review.rating}/10`
                        : "Sem nota";

                content.append(title, type, comment, rating);
                card.appendChild(content);

                reviewGrid.appendChild(card);
            });

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

    carregarMinhasAvaliacoes();


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
        return '/frontend/pages/detalhes-artista.html?artist=' +
            encodeURIComponent(name);
    }

    function trackLink(artist, track) {
        return '/frontend/pages/detalhes-musica.html?artist=' +
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

        return Array.isArray(result)
            ? result
            : [result];
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

            const imageUrl =
                Array.isArray(item.image) &&
                item.image
                    .map(i => i?.['#text'])
                    .filter(Boolean)
                    .pop();

            if (
                imageUrl &&
                /^https:\/\//i.test(imageUrl)
            ) {
                const image = document.createElement('img');
                image.src = imageUrl;
                image.alt = 'Foto de ' + item.name;
                image.loading = 'lazy';

                link.appendChild(image);
            }

            link.appendChild(info);
            card.appendChild(link);
            artistGrid.appendChild(card);
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

        items.forEach((item, index) => {
            if (!item?.name) {
                return;
            }

            const artist =
                typeof item.artist === 'string'
                    ? item.artist
                    : item.artist?.name || '';

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

        items.forEach((item, index) => {
            if (!item?.name) {
                return;
            }

            const artist =
                typeof item.artist === 'string'
                    ? item.artist
                    : item.artist?.name || '';

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

    async function carregarRankings() {
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

        if (rankingStatus) {
            rankingStatus.textContent =
                'Carregando rankings...';
        }

        const requestNumber = ++latestRankingRequest;
        retryButton.hidden = true;
        let unavailable = false;
        let timedOut = false;
        async function load(type, itemName, render, containers) {
            try {
                const data = await fetchProfileRanking(type, period);
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
            user.data?.artistas == null ? fetchProfileRanking("artists", "overall") : null,
            user.data?.lastFmAlbums == null ? fetchProfileRanking("albums", "overall") : null
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

    await Promise.all([
        carregarRankings(),
        carregarTotaisMusicais()
    ]);

});


// =========================================================
// BOTÃO CONECTAR LAST.FM
// =========================================================


// =========================================================
// BIOGRAFIA EDITAVEL DO SYNCHRO
// =========================================================

document.addEventListener("synchro:own-profile", () => {

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

    let savedBiography = "";

    async function carregarBiografia() {
        try {
            const response = await fetch(
                "/users/me/profile",
                {
                    credentials: "same-origin",
                    headers: {
                        Accept: "application/json"
                    }
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

        } catch (error) {
            console.error(
                "Erro ao carregar biografia:",
                error
            );

            biography.textContent = "";

            feedback.textContent =
                "Não foi possível carregar sua biografia.";
        }
    }

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

    carregarBiografia();
});
