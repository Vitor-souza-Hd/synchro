document.addEventListener("DOMContentLoaded", () => {

    // =========================================================
    // DADOS DA PÁGINA INICIAL
    // =========================================================
    // (O artistsData foi removido, agora puxamos do banco/Last.fm)

    const albumsData = [
        { name: "Debí Tirar Más Fotos", artist: "Bad Bunny", image: "/frontend/assets/images/DebíTirarMásFotos.png", link: "https://open.spotify.com/intl-pt/track/3sK8wGT43QFpWrvNQsrQya?si=68653d6088a04bec" },
        { name: "Mayhem", artist: "Lady Gaga", image: "/frontend/assets/images/mayhem.jpg", link: "https://open.spotify.com/intl-pt/album/2MHUaRi9OCyTN02SoyRRBJ?si=2182527c0b6240ca" },
        { name: "Lux", artist: "Rosalía", image: "/frontend/assets/images/lux.jpg", link: "https://open.spotify.com/intl-pt/album/3SUEJULSGgBDG1j4GQhfYY?si=cd1274117d6848c1" },
        { name: "Getting Killed", artist: "Geese", image: "/frontend/assets/images/gettingkilled.jpg", link: "https://open.spotify.com/intl-pt/album/0eeXb23yMW6EaIgm63xxPC?si=26e487624a95463d" },
        { name: "Starboy", artist: "The Weeknd", image: "/frontend/assets/images/starboy.png", link: "https://open.spotify.com/intl-pt/album/2ODvWsOgouMbaA5xf0RkJe?si=rlFEEWMEQnG4dz2sIhWL1g" },
        { name: "Bleeds", artist: "Wednesday", image: "/frontend/assets/images/bleeds.png" }
    ];

    const genresData = [
        { name: "POP", image: "/frontend/assets/images/michael.jpg", link: "https://open.spotify.com/playlist/37i9dQZF1DX6aTaZa0K6VA?si=9a3f83c6bf56418e" },
        { name: "MPB", image: "/frontend/assets/images/djavan.jpg", link: "https://open.spotify.com/playlist/37i9dQZF1DWZlBWh8A1Ygd?si=d4d90b4ba68b4a50" },
        { name: "FUNK", image: "/frontend/assets/images/funk.jpg", link: "https://open.spotify.com/playlist/37i9dQZF1DWTkIwO2HDifB?si=70f49f1ffd354005" },
        { name: "ROCK", image: "/frontend/assets/images/queen.jpg", link: "https://open.spotify.com/playlist/37i9dQZF1DXcmaoFmN75bi?si=59cadc7d77c84749" },
        { name: "RAP", image: "/frontend/assets/images/tupac.jpg", link: "https://open.spotify.com/playlist/37i9dQZF1DX186v583rmzp?si=fa1f1c99a2fd4f91" }
    ];

    const reviewsData = [
        {
            user: "silverzenith",
            userphoto: "/frontend/assets/images/user/users/silverzenith.png",
            album: "/frontend/assets/images/user/tcfsr.png",
            albumname: "Three Cheers for Sweet Revenge",
            artist: "My Chemical Romance",
            review: '"Um dos melhores álbuns conceituais da história, com certeza daria um bom filme"',
            rating: "9/10"
        },
        {
            user: "disintelia",
            userphoto: "/frontend/assets/images/user/users/disintelia.png",
            album: "/frontend/assets/images/user/pornography.png",
            albumname: "Pornography",
            artist: "The Cure",
            review: '"Esse álbum tem uma atmosfera melancólica e fria tão perfeita e, apesar disso, consegue passar uma sensação tão aconchegante. Definitivamente supera o Disintegrated e é o melhor da discografia do The cure."',
            rating: "10/10"
        },
        {
            user: "jinka070",
            userphoto: "/frontend/assets/images/user/users/jinka070.png",
            album: "/frontend/assets/images/user/biohazard.png",
            albumname: "BIOHAZARD",
            artist: "Trash Poll",
            review: '"somente a nata do nu metal brasileiro"',
            rating: "9.5/10"
        },
        {
            user: "Tavio_OoO",
            userphoto: "/frontend/assets/images/user/users/Tavio_OoO.png",
            album: "/frontend/assets/images/user/bythewater.png",
            albumname: "By The Water",
            artist: "Sondae",
            review: '"Ao contrário da maior parte dos álbuns cristãos que já ouvi, este apresenta um grande foco na musicalidade e sonoridade e não somente ao louvor, e só nisso já me ganhou, mas além disso, o conteúdo introspectivo retrata muito mais uma maior conexão com Jesus Cristo que religiosidade, tornando esse álbum uma joia rara em meio a tantos outros"',
            rating: "8.5/10"
        },
        {
            user: "Saii0710",
            userphoto: "/frontend/assets/images/user/users/Saii0710.png",
            album: "/frontend/assets/images/user/SSPSG.png",
            albumname: "SE SONHOS PUDESSEM SER GUARDADOS",
            artist: "myseryswin",
            review: '"O álbum mostra o refinamento de Swin em comparação com seus projetos anteriores. Melhor música: feiticeiro(eu merecia essa dança tanto assim?)"',
            rating: "9/10"
        },
        {
            user: "Alz7",
            userphoto: "/frontend/assets/images/user/users/Alz7.png",
            album: "/frontend/assets/images/user/sonofspergy.png",
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
            if (!photoRequests.has(url)) photoRequests.set(url, fetch(url, {headers: {Accept: 'application/json'}})
                .then(response => response.ok ? response.json() : null).catch(() => null));
            photoRequests.get(url).then(photo => {
                if (image.isConnected === false) return;
                if (photo?.status === 'found' && usablePhoto(photo.imgUrl)) {
                    artist.imgUrl = photo.imgUrl;
                    image.src = photo.imgUrl;
                }
            });
        }
        image.onerror = () => { fallback(); lookup(true); };
        image.src = usablePhoto(artist.imgUrl) ? artist.imgUrl : artistPlaceholder;
        if (!usablePhoto(artist.imgUrl)) lookup();
    }

    // =========================================================
    // EXIBIR ARTISTAS POPULARES (BASEADO NO LAST.FM OU BANCO)
    // =========================================================

    if (artistsGrid) {
        async function carregarArtistas() {
            try {
                // Render the local catalog before waiting for personalized rankings.
                const dbRes = await fetch('/artista');
                if (!dbRes.ok) throw new Error('Erro na API do banco');
                const artistasDoBanco = await dbRes.json();
                exibirArtistas(artistasDoBanco);

                // 1. Tenta buscar os top artistas do Last.fm do usuário logado
                try {
                    const lastfmRes = await fetch('/last-fm/me/top/artists', { credentials: 'same-origin' });

                    if (lastfmRes.ok) {
                        const lastfmData = await lastfmRes.json();

                        // O Last.fm pode retornar { topartists: { artist: [...] } } ou um array direto
                        const listaLastFm = lastfmData.topartists?.artist || lastfmData;

                        if (Array.isArray(listaLastFm) && listaLastFm.length > 0) {
                            const topArtistasNomes = listaLastFm.map(a => (a.name || a.nome || "").toLowerCase());

                            const personalizados = artistasDoBanco.filter(artista =>
                                topArtistasNomes.includes(artista.nome.toLowerCase()));
                            if (personalizados.length) exibirArtistas(personalizados);
                        }
                    }
                } catch (e) {
                    console.log("Usuário sem dados no Last.fm. Usando artistas gerais.");
                }

            } catch (error) {
                console.error("Erro ao carregar artistas:", error);
                artistsGrid.innerHTML = '<p style="color:#ff4d4d;">Erro ao carregar artistas.</p>';
            }
        }
        function exibirArtistas(artistasParaExibir) {
                artistsGrid.innerHTML = ''; // Limpa a div

                if (artistasParaExibir.length === 0) {
                    artistsGrid.innerHTML = '<p style="color:#b3b3b3;">Nenhum artista cadastrado.</p>';
                    return;
                }

                // Pega os 6 primeiros para manter o layout
                const artistasPopulares = artistasParaExibir.slice(0, 6);

                artistasPopulares.forEach(artist => {
                    const artistCard = document.createElement("div");
                    artistCard.classList.add("artist-card");

                    // Link interno do Synchro
                    const linkInterno = `/frontend/pages/detalhes-artista.html?artist=${encodeURIComponent(artist.nome)}`;

                    const link = document.createElement('a');
                    link.href = linkInterno;
                    const image = document.createElement('img');
                    const info = document.createElement('div');
                    const name = document.createElement('h3');
                    name.textContent = artist.nome;
                    const label = document.createElement('p');
                    label.textContent = 'Artista';
                    info.append(name, label);
                    link.append(image, info);
                    artistCard.append(link);
                    artistsGrid.appendChild(artistCard);
                    loadArtistPhoto(image, artist);
                });

        }
        carregarArtistas();
    }

    // =========================================================
    // EXIBIR ÁLBUNS POPULARES
    // =========================================================

    if (albumsGrid) {
        albumsData.forEach(album => {
            const albumCard = document.createElement("div");
            albumCard.classList.add("album-card");

            albumCard.innerHTML = `
                <a href="${album.link || "#"}" target="_blank">
                    <img src="${album.image}" alt="Imagem de ${album.name}">
                    <div>
                        <h3>${album.name}</h3>
                        <p>${album.artist}</p>
                    </div>
                </a>
            `;

            albumsGrid.appendChild(albumCard);
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
                    <img src="${genre.image}" alt="Imagem de ${genre.name}">
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
                    <img class="album-cover" src="${review.album}" alt="Imagem de ${review.albumname}">
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
                    <img class="user-avatar" src="${review.userphoto}" alt="Imagem de ${review.user}">
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
