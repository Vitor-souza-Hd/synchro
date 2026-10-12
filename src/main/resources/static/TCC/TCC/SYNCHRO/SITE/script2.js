
document.addEventListener("DOMContentLoaded", () => {

    // =========================================================
    // DADOS DA PÁGINA INICIAL
    // =========================================================

    const artistsData = [
        { name: "Bad Bunny", image: "./img/badbunny.jpg", link: "https://open.spotify.com/intl-pt/artist/4q3ewBCX7sLwd24euuV69X?si=o0l4pjizSSujyc7xJMkX4Q" },
        { name: "Rihanna", image: "./img/rihanna.jpg", link: "https://open.spotify.com/intl-pt/artist/5pKCCKE2ajJHZ9KAiaK11H?si=cbghCdyZQAOApW6H1qb9KA" },
        { name: "Justin Bieber", image: "./img/justin.jpg", link: "https://open.spotify.com/intl-pt/artist/1uNFoZAHBGtllmzznpCI3s?si=soZDaPLPRS2kYb9CPUCxgw" },
        { name: "Billie Eilish", image: "./img/billieeilish.jpg", link: "https://open.spotify.com/intl-pt/artist/6qqNVTkY8uBg9cP3Jd7DAH?si=r1NI9BmQSRm0TSLkhws8vw" },
        { name: "Kendrick Lamar", image: "./img/kendricklamar.jpg", link: "https://open.spotify.com/intl-pt/artist/2YZyLoL8N0Wb9xBt1NhZWg?si=ZERj3og4RxSMDk8nakQPQg" },
        { name: "The Weekend", image: "./img/theweeknd.jpg" }
    ];

    const albumsData = [
        { name: "Debí Tirar Más Fotos", artist: "Bad Bunny", image: "./img/DebíTirarMásFotos.png", link: "https://open.spotify.com/intl-pt/track/3sK8wGT43QFpWrvNQsrQya?si=68653d6088a04bec" },
        { name: "Mayhem", artist: "Lady Gaga", image: "./img/mayhem.jpg", link: "https://open.spotify.com/intl-pt/album/2MHUaRi9OCyTN02SoyRRBJ?si=2182527c0b6240ca" },
        { name: "Lux", artist: "Rosalía", image: "./img/lux.jpg", link: "https://open.spotify.com/intl-pt/album/3SUEJULSGgBDG1j4GQhfYY?si=cd1274117d6848c1" },
        { name: "Getting Killed", artist: "Geese", image: "./img/gettingkilled.jpg", link: "https://open.spotify.com/intl-pt/album/0eeXb23yMW6EaIgm63xxPC?si=26e487624a95463d" },
        { name: "Starboy", artist: "The Weeknd", image: "./img/starboy.png", link: "https://open.spotify.com/intl-pt/album/2ODvWsOgouMbaA5xf0RkJe?si=rlFEEWMEQnG4dz2sIhWL1g" },
        { name: "Bleeds", artist: "Wednesday", image: "./img/bleeds.png" }
    ];

    const genresData = [
        { name: "POP", image: "./img/michael.jpg", link: "https://open.spotify.com/playlist/37i9dQZF1DX6aTaZa0K6VA?si=9a3f83c6bf56418e" },
        { name: "MPB", image: "./img/djavan.jpg", link: "https://open.spotify.com/playlist/37i9dQZF1DWZlBWh8A1Ygd?si=d4d90b4ba68b4a50" },
        { name: "FUNK", image: "./img/funk.jpg", link: "https://open.spotify.com/playlist/37i9dQZF1DWTkIwO2HDifB?si=70f49f1ffd354005" },
        { name: "ROCK", image: "./img/queen.jpg", link: "https://open.spotify.com/playlist/37i9dQZF1DXcmaoFmN75bi?si=59cadc7d77c84749" },
        { name: "RAP", image: "./img/tupac.jpg", link: "https://open.spotify.com/playlist/37i9dQZF1DX186v583rmzp?si=fa1f1c99a2fd4f91" }
    ];

    const reviewsData = [
        {
            user: "silverzenith",
            userphoto: "./img/user/users/silverzenith.png",
            album: "./img/user/tcfsr.png",
            albumname: "Three Cheers for Sweet Revenge",
            artist: "My Chemical Romance",
            review: '"Um dos melhores álbuns conceituais da história, com certeza daria um bom filme"',
            rating: "9/10"
        },
        {
            user: "disintelia",
            userphoto: "./img/user/users/disintelia.png",
            album: "./img/user/pornography.png",
            albumname: "Pornography",
            artist: "The Cure",
            review: '"Esse álbum tem uma atmosfera melancólica e fria tão perfeita e, apesar disso, consegue passar uma sensação tão aconchegante. Definitivamente supera o Disintegrated e é o melhor da discografia do The cure."',
            rating: "10/10"
        },
        {
            user: "jinka070",
            userphoto: "./img/user/users/jinka070.png",
            album: "./img/user/biohazard.png",
            albumname: "BIOHAZARD",
            artist: "Trash Poll",
            review: '"somente a nata do nu metal brasileiro"',
            rating: "9.5/10"
        },
        {
            user: "Tavio_OoO",
            userphoto: "./img/user/users/Tavio_OoO.png",
            album: "./img/user/bythewater.png",
            albumname: "By The Water",
            artist: "Sondae",
            review: '"Ao contrário da maior parte dos álbuns cristãos que já ouvi, este apresenta um grande foco na musicalidade e sonoridade e não somente ao louvor, e só nisso já me ganhou, mas além disso, o conteúdo introspectivo retrata muito mais uma maior conexão com Jesus Cristo que religiosidade, tornando esse álbum uma joia rara em meio a tantos outros"',
            rating: "8.5/10"
        },
        {
            user: "Saii0710",
            userphoto: "./img/user/users/Saii0710.png",
            album: "./img/user/SSPSG.png",
            albumname: "SE SONHOS PUDESSEM SER GUARDADOS",
            artist: "myseryswin",
            review: '"O álbum mostra o refinamento de Swin em comparação com seus projetos anteriores. Melhor música: feiticeiro(eu merecia essa dança tanto assim?)"',
            rating: "9/10"
        },
        {
            user: "Alz7",
            userphoto: "./img/user/users/Alz7.png",
            album: "./img/user/sonofspergy.png",
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

    // =========================================================
    // EXIBIR ARTISTAS POPULARES
    // =========================================================

    if (artistsGrid) {
        artistsData.forEach(artist => {
            const artistCard = document.createElement("div");
            artistCard.classList.add("artist-card");

            artistCard.innerHTML = `
                <a href="${artist.link || "#"}" target="_blank">
                    <img src="${artist.image}" alt="Imagem de ${artist.name}">
                    <div>
                        <h3>${artist.name}</h3>
                        <p>Artista</p>
                    </div>
                </a>
            `;

            artistsGrid.appendChild(artistCard);
        });
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

    // =========================================================
    // PESQUISA COM API DO LAST.FM
    // =========================================================

    const searchInput = document.querySelector(".search");
    const searchBox = document.querySelector(".search-box");

    if (searchInput && searchBox) {

        // Área de resultados criada pelo próprio JavaScript
        const searchResults = document.createElement("div");
        searchResults.id = "lastfm-search-results";

        // Estilos para não precisar modificar o CSS existente
        Object.assign(searchResults.style, {
            position: "absolute",
            top: "calc(100% + 8px)",
            left: "0",
            width: "100%",
            maxHeight: "400px",
            overflowY: "auto",
            backgroundColor: "#181818",
            color: "#ffffff",
            borderRadius: "10px",
            boxShadow: "0 8px 24px rgba(0,0,0,0.5)",
            zIndex: "9999",
            display: "none",
            padding: "10px",
            boxSizing: "border-box"
        });

        searchBox.style.position = "relative";
        searchBox.appendChild(searchResults);

        let searchTimer;
        let searchController;
        let searchVersion = 0;

        // Mostrar mensagem na área de resultados
        function showMessage(message) {
            searchResults.replaceChildren();

            const paragraph = document.createElement("p");
            paragraph.textContent = message;
            paragraph.style.padding = "10px";
            paragraph.style.margin = "0";

            searchResults.appendChild(paragraph);
            searchResults.style.display = "block";
        }

        // Cria cada item de resultado
        function createResult(name, subtitle, url) {
            const validUrl = typeof url === "string"
                && /^https:\/\/(www\.)?last\.fm\//i.test(url);

            const item = document.createElement(validUrl ? "a" : "div");

            if (validUrl) {
                item.href = url;
                item.target = "_blank";
                item.rel = "noopener noreferrer";
            }

            Object.assign(item.style, {
                display: "block",
                padding: "10px",
                color: "#ffffff",
                textDecoration: "none",
                borderBottom: "1px solid #333333",
                borderRadius: "5px",
                cursor: validUrl ? "pointer" : "default"
            });

            const title = document.createElement("strong");
            title.textContent = name || "Sem nome";
            title.style.display = "block";

            const description = document.createElement("small");
            description.textContent = subtitle;
            description.style.color = "#aaaaaa";

            item.appendChild(title);
            item.appendChild(description);

            if (validUrl) {
                item.addEventListener("mouseenter", () => {
                    item.style.backgroundColor = "#303030";
                });

                item.addEventListener("mouseleave", () => {
                    item.style.backgroundColor = "transparent";
                });
            }

            return item;
        }

        // Faz uma requisição ao backend
        async function fetchLastFmResults(endpoint, query, signal) {
            const response = await fetch(
                `${endpoint}?q=${encodeURIComponent(query)}`,
                { signal }
            );

            if (!response.ok) {
                throw new Error(
                    `Erro na pesquisa: HTTP ${response.status}`
                );
            }

            return response.json();
        }

        // Executa as duas pesquisas
        async function performSearch(query) {

            if (searchController) {
                searchController.abort();
            }

            searchController = new AbortController();
            const signal = searchController.signal;
            const currentVersion = ++searchVersion;

            showMessage("Pesquisando no Last.fm...");

            try {
                const [artistsResponse, tracksResponse] =
                    await Promise.allSettled([
                        fetchLastFmResults(
                            "/last-fm/artist/search",
                            query,
                            signal
                        ),
                        fetchLastFmResults(
                            "/last-fm/track/search",
                            query,
                            signal
                        )
                    ]);

                // Ignorar resultados de pesquisas anteriores
                if (currentVersion !== searchVersion || signal.aborted) {
                    return;
                }

                searchResults.replaceChildren();

                let totalResults = 0;

                // ARTISTAS
                if (artistsResponse.status === "fulfilled") {
                    const artists =
                        artistsResponse.value?.results
                            ?.artistmatches?.artist || [];

                    const artistList = Array.isArray(artists)
                        ? artists
                        : [artists];

                    if (artistList.length > 0) {
                        const heading = document.createElement("h4");
                        heading.textContent = "Artistas";
                        heading.style.padding = "8px";
                        heading.style.margin = "0";

                        searchResults.appendChild(heading);

                        artistList.slice(0, 5).forEach(artist => {
                            searchResults.appendChild(
                                createResult(
                                    artist.name,
                                    "Artista",
                                    artist.url
                                )
                            );
                            totalResults++;
                        });
                    }
                }

                // MÚSICAS
                if (tracksResponse.status === "fulfilled") {
                    const tracks =
                        tracksResponse.value?.results
                            ?.trackmatches?.track || [];

                    const trackList = Array.isArray(tracks)
                        ? tracks
                        : [tracks];

                    if (trackList.length > 0) {
                        const heading = document.createElement("h4");
                        heading.textContent = "Músicas";
                        heading.style.padding = "8px";
                        heading.style.margin = "10px 0 0";

                        searchResults.appendChild(heading);

                        trackList.slice(0, 5).forEach(track => {
                            searchResults.appendChild(
                                createResult(
                                    track.name,
                                    track.artist || "Música",
                                    track.url
                                )
                            );
                            totalResults++;
                        });
                    }
                }

                if (totalResults === 0) {
                    if (
                        artistsResponse.status === "rejected" &&
                        tracksResponse.status === "rejected"
                    ) {
                        showMessage(
                            "Não foi possível consultar o Last.fm."
                        );
                    } else {
                        showMessage(
                            "Nenhum resultado encontrado."
                        );
                    }
                } else {
                    searchResults.style.display = "block";
                }

            } catch (error) {
                if (error.name !== "AbortError") {
                    console.error("Erro na pesquisa:", error);
                    showMessage("Erro ao realizar a pesquisa.");
                }
            }
        }

        // =====================================================
        // EVENTOS DA BARRA DE PESQUISA
        // =====================================================

        searchInput.addEventListener("input", () => {

            clearTimeout(searchTimer);

            const query = searchInput.value.trim();

            if (query.length < 2) {
                searchVersion++;

                if (searchController) {
                    searchController.abort();
                }

                searchResults.style.display = "none";
                searchResults.replaceChildren();
                return;
            }

            // Espera 400 ms após o usuário parar de digitar
            searchTimer = setTimeout(() => {
                performSearch(query);
            }, 400);
        });

        // Fechar ao pressionar Escape
        searchInput.addEventListener("keydown", event => {
            if (event.key === "Escape") {
                searchResults.style.display = "none";
            }
        });

        // Fechar ao clicar fora da pesquisa
        document.addEventListener("click", event => {
            if (!searchBox.contains(event.target)) {
                searchResults.style.display = "none";
            }
        });

        // Reabrir resultados existentes ao focar
        searchInput.addEventListener("focus", () => {
            if (
                searchInput.value.trim().length >= 2 &&
                searchResults.childElementCount > 0
            ) {
                searchResults.style.display = "block";
            }
        });
    }

});
