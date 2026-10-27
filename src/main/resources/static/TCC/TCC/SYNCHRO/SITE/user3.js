
document.addEventListener('DOMContentLoaded', async () => {

    console.log("USER.JS NOVO CARREGADO!!!");

    // Buscar o nome cadastrado no Synchro.
    let synchroUsername = "Usuário Synchro";

    try {
        const response = await fetch("/users/me", {
            credentials: "same-origin"
        });

        if (response.ok) {
            const user = await response.json();

            if (typeof user.username === "string" &&
                user.username.trim()) {
                synchroUsername = user.username.trim();
            }
        } else {
            console.warn(
                "Não foi possível consultar o usuário Synchro:",
                response.status
            );
        }
    } catch (error) {
        console.error(
            "Erro ao buscar usuário Synchro:",
            error
        );
    }


    // =====================================================
    // DADOS DO USUÁRIO - LAST.FM
    // =====================================================

    let lastFmUser = null;

    try {

        const response = await fetch("/last-fm/me");

        console.log("STATUS:", response.status);

        const texto = await response.text();

        console.log("RESPOSTA BRUTA:");
        console.log(texto);

        if (!response.ok) {
            throw new Error(
                "Não foi possível carregar os dados do Last.fm."
            );
        }

        const data = JSON.parse(texto);

        lastFmUser = data.user;

        // Exibir o status somente quando o backend
        // confirmar os dados da conta Last.fm.

        if (lastFmUser && lastFmUser.name) {

            const conectarBotao =
                document.getElementById("lastfm-connect");

            if (conectarBotao) {

                const conectado = document.createElement("div");

                conectado.id = "lastfm-status";
                conectado.setAttribute("role", "status");
                conectado.textContent = "Last.fm conectado!";

                conectado.style.cssText = [
                    "display: inline-flex",
                    "align-items: center",
                    "width: fit-content",
                    "margin-top: 18px",
                    "padding: 11px 18px",
                    "border: 1px solid rgba(86, 211, 130, 0.42)",
                    "border-radius: 12px",
                    "background: rgba(86, 211, 130, 0.12)",
                    "color: #d4ffe1",
                    "font-family: inherit",
                    "font-size: 14px",
                    "font-weight: 600"
                ].join("; ");

                conectarBotao.replaceWith(conectado);
            }
        }

        console.log("DADOS DO LAST.FM:", lastFmUser);

    } catch (error) {

        console.error("Erro ao carregar Last.fm:", error);

    }


    // =====================================================
    // CABEÇALHO DO USUÁRIO
    // =====================================================

    const userGrid = document.querySelector('.user');

    if (lastFmUser) {

        const userCard = document.createElement('div');

        userCard.classList.add('user-card');

        // Avatar neutro se o Last.fm nao retornar foto.
        const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"><rect width="200" height="200" fill="#292929"/><circle cx="100" cy="74" r="34" fill="#777"/><path d="M34 184c0-38 30-65 66-65s66 27 66 65" fill="#777"/></svg>`;
        let userImage = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
        const images = Array.isArray(lastFmUser.image) ? lastFmUser.image : [];
        const availableImage = images.map(img => img?.["#text"]).filter(Boolean).pop();
        if (availableImage && /^https:\/\//i.test(availableImage)) userImage = availableImage;

        const userInfo = document.createElement('div');
        const nameElement = document.createElement('h3');
        nameElement.textContent = lastFmUser.name || synchroUsername;
        const sourceElement = document.createElement('p');
        sourceElement.textContent = 'Usuário Last.fm';
        userInfo.append(nameElement, sourceElement);
        const avatarElement = document.createElement('img');
        avatarElement.src = userImage;
        avatarElement.alt = 'Foto de ' + (lastFmUser.name || synchroUsername);
        userCard.append(userInfo, avatarElement);
        userGrid.appendChild(userCard);


        // =================================================
        // ESTATÍSTICAS REAIS DO LAST.FM
        // =================================================

        const scrobblesElement =
            document.getElementById("profileScrobbles");

        const artistsElement =
            document.getElementById("profileArtists");

        const albumsElement =
            document.getElementById("profileAlbums");

        // Exibe apenas valores realmente fornecidos pela API.
        function formatarEstatistica(valor) {
            if (
                valor === undefined ||
                valor === null ||
                valor === ""
            ) {
                return "—";
            }

            const numero = Number(valor);

            if (!Number.isFinite(numero) || numero < 0) {
                return "—";
            }

            return numero.toLocaleString("pt-BR");
        }

        if (scrobblesElement) {
            scrobblesElement.textContent =
                formatarEstatistica(lastFmUser.playcount);
        }


        // Os totais de artistas e albuns serao obtidos
        // pelos rankings gerais do Last.fm.
        // Mantemos o valor neutro ate receber a resposta.

        if (artistsElement) {
            artistsElement.textContent = "—";
        }

        if (albumsElement) {
            albumsElement.textContent = "—";
        }


    } else {

        // Perfil Synchro sem Last.fm conectado.
        const userCard = document.createElement("div");
        userCard.classList.add("user-card");

        const userInfo = document.createElement("div");

        const usernameElement = document.createElement("h3");
        usernameElement.textContent = synchroUsername;

        const statusElement = document.createElement("p");
        statusElement.textContent = "Last.fm não conectado";

        userInfo.append(usernameElement, statusElement);

        // Avatar neutro enquanto o usuario nao
        // escolhe uma fotografia.
        const userImage = document.createElement("img");
        userImage.alt = "Avatar padrão do usuário";

        const avatarSvg = `
        <svg xmlns="http://www.w3.org/2000/svg"
             viewBox="0 0 200 200">
            <rect width="200" height="200" fill="#292929"/>
            <circle cx="100" cy="74" r="34" fill="#777"/>
            <path d="M34 184c0-38 30-65 66-65s66 27 66 65"
                  fill="#777"/>
        </svg>
    `;

        userImage.src =
            "data:image/svg+xml;charset=utf-8," +
            encodeURIComponent(avatarSvg);

        userCard.append(userInfo, userImage);

        if (userGrid) {
            userGrid.replaceChildren(userCard);
        }
    }


    // =====================================================
    // RANKINGS REAIS - LAST.FM
    // =====================================================

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


    empty(
        playlistGrid,
        'Você ainda não criou playlists no Synchro.'
    );



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
        return 'artista.html?artist=' +
            encodeURIComponent(name);
    }

    function trackLink(artist, track) {
        return 'musica.html?artist=' +
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

    async function fetchRanking(type, period) {
        const response = await fetch(
            '/last-fm/me/top/' +
            type +
            '?period=' +
            encodeURIComponent(period),
            {
                credentials: 'same-origin',
                headers: {
                    Accept: 'application/json'
                }
            }
        );

        if (!response.ok) {
            throw new Error(
                'Ranking ' +
                type +
                ': HTTP ' +
                response.status
            );
        }

        return response.json();
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

        const results = await Promise.allSettled([
            fetchRanking('artists', period),
            fetchRanking('albums', period),
            fetchRanking('tracks', period)
        ]);

        const [artists, albums, tracks] = results;

        if (artists.status === 'fulfilled') {
            renderArtists(
                topArray(
                    artists.value,
                    'topartists',
                    'artist'
                )
            );
        } else {
            empty(
                artistGrid,
                'Não foi possível consultar os artistas.'
            );

            empty(
                reportArtistGrid,
                'Não foi possível consultar os artistas.'
            );
        }

        if (albums.status === 'fulfilled') {
            renderAlbums(
                topArray(
                    albums.value,
                    'topalbums',
                    'album'
                )
            );
        } else {
            empty(
                reportAlbumGrid,
                'Não foi possível consultar os álbuns.'
            );
        }

        if (tracks.status === 'fulfilled') {
            renderTracks(
                topArray(
                    tracks.value,
                    'toptracks',
                    'track'
                )
            );
        } else {
            empty(
                reportMusicGrid,
                'Não foi possível consultar as músicas.'
            );
        }

        if (rankingStatus) {
            rankingStatus.textContent =
                results.every(
                    r => r.status === 'fulfilled'
                )
                    ? ''
                    : 'Alguns rankings não puderam ser carregados.';
        }
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
            fetchRanking("artists", "overall"),
            fetchRanking("albums", "overall")
        ]);

        const artistas = resultados[0];
        const albuns = resultados[1];

        if (artistas.status === "fulfilled") {

            const totalArtistas =
                artistas.value?.topartists?.["@attr"]?.total;

            if (artistsCounter) {
                artistsCounter.textContent =
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
                albumsCounter.textContent =
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

const lastfmButton =
    document.getElementById("lastfm-connect");

if (lastfmButton) {

    lastfmButton.addEventListener(
        "click",
        async () => {

            try {

                const response =
                    await fetch("/last-fm/auth/login");

                if (!response.ok) {

                    throw new Error(
                        "Erro ao iniciar autenticação Last.fm."
                    );

                }

                const data =
                    await response.json();

                window.location.href =
                    data.authUrl;

            } catch (error) {

                console.error(
                    "Erro ao conectar Last.fm:",
                    error
                );

                alert(
                    "Não foi possível conectar ao Last.fm."
                );

            }

        }
    );
}


// =========================================================
// BIOGRAFIA EDITAVEL DO SYNCHRO
// =========================================================

document.addEventListener("DOMContentLoaded", () => {

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
