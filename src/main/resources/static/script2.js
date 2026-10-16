document.addEventListener('DOMContentLoaded', () => {

    // ==========================================
    // GÊNEROS vou ver dps

    const genresData = [
        {name: 'POP', image: 'https://placehold.co/300/e91e63/fff?text=POP', link:'https://open.spotify.com/playlist/37i9dQZF1DX6aTaZa0K6VA'},
        {name: 'MPB', image: 'https://placehold.co/300/9c27b0/fff?text=MPB', link:'https://open.spotify.com/playlist/37i9dQZF1DWZlBWh8A1Ygd'},
        {name: 'FUNK', image: 'https://placehold.co/300/ff9800/fff?text=FUNK', link:'https://open.spotify.com/playlist/37i9dQZF1DWTkIwO2HDifB'},
        {name: 'ROCK', image: 'https://placehold.co/300/f44336/fff?text=ROCK', link:'https://open.spotify.com/playlist/37i9dQZF1DXcmaoFmN75bi'},
        {name: 'RAP', image: 'https://placehold.co/300/3f51b5/fff?text=RAP', link:'https://open.spotify.com/playlist/37i9dQZF1DX186v583rmzp'}
    ];

    const artistsGrid = document.querySelector('.artists');
    const albumsGrid = document.querySelector('.albums');
    const genresGrid = document.querySelector('.genres');
    const musicsGrid = document.querySelector('.musics');

    // HELPER: busca o rating de uma mídia
    async function buscarRating(midiaId) {
        try {
            const r = await fetch(`/review/midia/${midiaId}/rating`);
            if (!r.ok) return { media: 0, total: 0 };
            return await r.json();
        } catch {
            return { media: 0, total: 0 };
        }
    }

    // ARTISTAS

    async function carregarArtistas() {
        try {
            const response = await fetch('/artista/');
            if (!response.ok) throw new Error('Erro ao buscar artistas');

            const artistasDoBanco = await response.json();

            if (!artistsGrid) return;
            artistsGrid.innerHTML = '';

            for (const a of artistasDoBanco) {
                const rating = await buscarRating(a.id);

                const artistCard = document.createElement('div');
                artistCard.classList.add('artist-card');

                artistCard.innerHTML = `
                    <a href="#">
                        <img src="https://placehold.co/300/1db954/fff?text=${encodeURIComponent(a.nome)}"
                             alt="imagem do ${a.nome}">
                        <div>
                            <h3>${a.nome}</h3>
                            <p class="rating-media">
                                ${rating.total > 0
                    ? `⭐ ${rating.media} (${rating.total})`
                    : 'Sem avaliações'}
                            </p>
                        </div>
                    </a>
                    <button class="btn-avaliar-card"
                            onclick="event.stopPropagation(); event.preventDefault(); abrirModalReview(${a.id}, '${a.nome}')">
                        <i class="fa-solid fa-star"></i> Avaliar
                    </button>
                `;

                artistsGrid.appendChild(artistCard);
            }
        } catch (error) {
            console.error('Erro ao carregar artistas:', error);
        }
    }

    carregarArtistas();

    // ===============
    // ÁLBUNS
    // ================
    async function carregarAlbuns() {
        try {
            const response = await fetch('/album/');
            if (!response.ok) throw new Error('Erro ao buscar álbuns');

            const albunsDoBanco = await response.json();

            if (!albumsGrid) return;
            albumsGrid.innerHTML = '';

            for (const a of albunsDoBanco) {
                const rating = await buscarRating(a.id);

                const artistaTexto = (a.artistas && a.artistas.length > 0)
                    ? Array.from(a.artistas).map(art => art.nome).join(', ')
                    : 'Artista desconhecido';

                const albumCard = document.createElement('div');
                albumCard.classList.add('album-card');

                albumCard.innerHTML = `
                    <a href="#">
                        <img src="https://placehold.co/300/2196f3/fff?text=${encodeURIComponent(a.titulo)}"
                             alt="imagem do ${a.titulo}">
                        <div>
                            <h3>${a.titulo}</h3>
                            <p>${artistaTexto}</p>
                            <p class="rating-media">
                                ${rating.total > 0
                    ? `⭐ ${rating.media} (${rating.total})`
                    : 'Sem avaliações'}
                            </p>
                        </div>
                    </a>
                    <button class="btn-avaliar-card"
                            onclick="event.stopPropagation(); event.preventDefault(); abrirModalReview(${a.id}, '${a.titulo}')">
                        <i class="fa-solid fa-star"></i> Avaliar
                    </button>
                `;

                albumsGrid.appendChild(albumCard);
            }
        } catch (error) {
            console.error('Erro ao carregar álbuns:', error);
        }
    }

    carregarAlbuns();
    // ==========================================
    // carregar MÚSICAS
    // ==========================================
    async function carregarMusicas() {
        try {
            const response = await fetch('/musica/');
            if (!response.ok) throw new Error('Erro ao buscar músicas');

            const musicasDoBanco = await response.json();

            if (!musicsGrid) return;
            musicsGrid.innerHTML = '';

            for (const m of musicasDoBanco) {
                const rating = await buscarRating(m.id);

                // Converte "PT3M27S" → "3:27"
                let duracaoTexto = 'Música';
                if (m.duracao) {
                    const match = m.duracao.match(/PT(?:(\d+)M)?(?:(\d+)S)?/);
                    if (match) {
                        const min = match[1] ? parseInt(match[1], 10) : 0;
                        const seg = match[2] ? parseInt(match[2], 10) : 0;
                        duracaoTexto = `${min}:${seg.toString().padStart(2, '0')}`;
                    }
                }

                const musicCard = document.createElement('div');
                musicCard.classList.add('album-card');

                musicCard.innerHTML = `
                    <a href="#">
                        <img src="https://placehold.co/300/9c27b0/fff?text=${encodeURIComponent(m.titulo)}"
                             alt="imagem da ${m.titulo}">
                        <div>
                            <h3>${m.titulo}</h3>
                            <p>${duracaoTexto}</p>
                            <p class="rating-media">
                                ${rating.total > 0
                    ? `⭐ ${rating.media} (${rating.total})`
                    : 'Sem avaliações'}
                            </p>
                        </div>
                    </a>
                    <button class="btn-avaliar-card"
                            onclick="event.stopPropagation(); event.preventDefault(); abrirModalReview(${m.id}, '${m.titulo}')">
                        <i class="fa-solid fa-star"></i> Avaliar
                    </button>
                `;

                musicsGrid.appendChild(musicCard);
            }
        } catch (error) {
            console.error('Erro ao carregar músicas:', error);
        }
    }

    carregarMusicas();

    // ==========================================
    // GÊNEROS
    // ==========================================
    if (genresGrid) {
        genresGrid.innerHTML = '';

        genresData.forEach(genres => {
            const genreCard = document.createElement('div');
            genreCard.classList.add('genre-card');

            genreCard.innerHTML = `
                <a href="${genres.link}" target="_blank">
                    <img src="${genres.image}" alt="imagem do ${genres.name}">
                    <div>
                        <h3>${genres.name}</h3>
                    </div>
                </a>
            `;

            genresGrid.appendChild(genreCard);
        });
    }


    const menuToggle = document.querySelector(".menu-toggle");
    const sidebar = document.querySelector(".sidebar");

    if (menuToggle && sidebar) {
        menuToggle.addEventListener("click", () => {
            sidebar.classList.toggle("active");
        });
    }

});