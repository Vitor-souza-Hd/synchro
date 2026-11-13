document.addEventListener('DOMContentLoaded', () => {

const artistsData = [
    { name: 'Bad Bunny', image: 'frontend/assets/images/cards/badbunny.webp'},
    { name: 'Rihanna', image: 'frontend/assets/images/cards/rihanna.webp'},
    { name: 'Justin Bieber', image: 'frontend/assets/images/cards/justin.webp' },
    { name: 'Billie Eilish', image: 'frontend/assets/images/cards/billieeilish.webp'},
    { name: 'Kendrick Lamar', image: 'frontend/assets/images/cards/kendricklamar.webp'},
    { name: 'The Weeknd', image: 'frontend/assets/images/cards/theweeknd.webp'},

];

const albumsData = [
    {name: 'Debí Tirar Más Fotos', artist: 'Bad Bunny', image: 'frontend/assets/images/cards/DebíTirarMásFotos.webp', link:'https://open.spotify.com/intl-pt/track/3sK8wGT43QFpWrvNQsrQya?si=68653d6088a04bec' },
    {name: 'Mayhem', artist: 'Lady Gaga', image: 'frontend/assets/images/cards/mayhem.webp', link:'https://open.spotify.com/intl-pt/album/2MHUaRi9OCyTN02SoyRRBJ?si=2182527c0b6240ca' },
    {name: 'Lux', artist: 'Rosalía', image: 'frontend/assets/images/lux.jpg', link:'https://open.spotify.com/intl-pt/album/3SUEJULSGgBDG1j4GQhfYY?si=cd1274117d6848c1' },
    {name: 'Getting Killed', artist: 'Geese', image: 'frontend/assets/images/cards/gettingkilled.webp', link:'https://open.spotify.com/intl-pt/album/0eeXb23yMW6EaIgm63xxPC?si=26e487624a95463d' },
    {name: 'Starboy', artist: 'The Weeknd', image: 'frontend/assets/images/cards/starboy.webp', link:'https://open.spotify.com/intl-pt/album/2ODvWsOgouMbaA5xf0RkJe?si=rlFEEWMEQnG4dz2sIhWL1g' },
    {name: 'Bleeds', artist: 'Wednesday', image: 'frontend/assets/images/cards/bleeds.webp' },

];
const genresData = [
    {name: 'POP', image:'frontend/assets/images/cards/michael.webp'},
    {name: 'MPB', image:'frontend/assets/images/cards/djavan.webp'},
    {name: 'FUNK', image:'frontend/assets/images/funk.jpg'},
    {name: 'ROCK', image:'frontend/assets/images/cards/queen.webp'},
    {name: 'RAP', image:'frontend/assets/images/cards/tupac.webp'}
];
const reviewsData = [
    {user: 'silverzenith', userphoto :'frontend/assets/images/cards/user/users/silverzenith.webp', album: 'frontend/assets/images/cards/user/tcfsr.webp', albumname:'Three Cheers for Sweet Revenge', artist:'My Chemical Romance',
         review: '"Um dos melhores álbuns conceituais da história, com certeza daria um bom filme"', rating:'9/10' },
    {user: 'disintelia', userphoto :'frontend/assets/images/cards/user/users/disintelia.webp', album: 'frontend/assets/images/cards/user/pornography.webp', albumname: 'Pornography', artist:'The Cure',
         review: '"Esse álbum tem uma atmosfera melancólica e fria tão perfeita e, apesar disso, consegue passar uma sensação tão aconchegante. Definitivamente supera o Disintegrated e é o melhor da discografia do The cure."', rating:'10/10' },
    {user: 'jinka070', userphoto :'frontend/assets/images/cards/user/users/jinka070.webp', album: 'frontend/assets/images/cards/user/biohazard.webp', albumname:'BIOHAZARD', artist:'Trash Poll',
         review: '"somente a nata do nu metal brasileiro"', rating:'9.5/10' },
    {user: 'Tavio_OoO', userphoto :'frontend/assets/images/cards/user/users/Tavio_OoO.webp', album: 'frontend/assets/images/cards/user/bythewater.webp', albumname:'By The Water', artist:'Sondae',
         review: '"Ao contrário da maior parte dos álbuns cristãos que já ouvi, este apresenta um grande foco na musicalidade e sonoridade e não somente ao louvor, e só nisso já me ganhou, mas além disso, o conteúdo introspectivo retrata muito mais uma maior conexão com Jesus Cristo que religiosidade, tornando esse álbum uma joia rara em meio a tantos outros"', rating:'8.5/10' },
    {user: 'Saii0710', userphoto :'frontend/assets/images/cards/user/users/Saii0710.webp', album: 'frontend/assets/images/cards/user/SSPSG.webp', albumname:'SE SONHOS PUDESSEM SER GUARDADOS', artist:'myseryswin',
         review: '"O álbum mostra o refinamento de Swin em comparação com seus projetos anteriores. Melhor música: feiticeiro(eu merecia essa dança tanto assim?)"', rating:'9/10' },
    {user: 'Alz7', userphoto :'frontend/assets/images/cards/user/users/Alz7.webp', album: 'frontend/assets/images/cards/user/sonofspergy.webp', albumname:'Son Of Spergy', artist:'Daniel Caesar',
         review: '" é o último álbum lançado de Daniel Caesar. Sendo um álbum que Daniel  foca em contar a jornada de amadurecimento, fé e reconciliação. O álbum explora o peso dessa herança religiosa e familiar, abordando temas como masculinidade, erros do passado e a busca por perdão. O ritmo do projeto é intencionalmente lento, apostando em produções atmosféricas e vocais crus usando muito os corais gospels.    Sendo um retrato honesto de um artista aprendendo a lidar com suas próprias fraquezas."', rating:'9/10' },
]

const artistsGrid = document.querySelector('.artists')
    const albumsGrid = document.querySelector('.albums')
        const genresGrid = document.querySelector('.genres')
            const reviewsGrid = document.querySelector('.reviews')
            

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

    function renderArtists(artists) {
        artistsGrid.replaceChildren();
        for (const artist of artists) {
            const card = document.createElement('div');
            card.className = 'artist-card';
            const link = document.createElement('a');
            const identity = artist.id ? {id: artist.id} : {artist: artist.name};
            link.href = 'frontend/pages/artista.html?' + new URLSearchParams(identity);
            const artwork = document.createElement('div');
            const info = document.createElement('div');
            const name = document.createElement('h3');
            name.textContent = artist.name;
            const label = document.createElement('p');
            label.textContent = artist.genre || 'Artista';
            info.append(name, label);
            link.append(artwork, info);
            card.append(link);
            artistsGrid.append(card);

            if (artist.genre && window.SynchroFavoriteImages) {
                window.SynchroFavoriteImages.show(artwork, {
                    kind: 'artist', title: artist.name, artist: artist.name, id: artist.id,
                    preferredImages: artist.images, alt: ''
                });
            } else {
                const image = document.createElement('img');
                image.loading = 'lazy';
                image.decoding = 'async';
                image.alt = '';
                image.src = artist.image || '/frontend/assets/images/artist-placeholder.svg';
                artwork.append(image);
            }
        }
        const status = document.getElementById('popularArtistsStatus');
        if (status) {
            status.textContent = 'Artistas carregados com sucesso: ' +
                new Intl.ListFormat('pt-BR').format(artists.map(artist => artist.name)) + '.';
        }
    }

    async function loadGenreArtists(genre, tag) {
        const query = new URLSearchParams({method: 'tag.getTopArtists', tag});
        const response = await fetch('/last-fm/discovery?' + query, {signal: AbortSignal.timeout(6000)});
        if (!response.ok) return [];
        const {topartists} = await response.json();
        const items = topartists?.artist;
        return (Array.isArray(items) ? items : [items])
            .filter(item => typeof item?.name === 'string' && item.name.trim())
            .map(item => ({
                name: item.name.trim(),
                id: Number.isSafeInteger(item.catalogId) && item.catalogId > 0 ? item.catalogId : null,
                genre,
                images: Array.isArray(item.image) ? item.image.map(image => image?.['#text']).reverse() : []
            }));
    }

    async function personalizeArtists() {
        try {
            const response = await fetch('/users/me/music-profile', {
                cache: 'no-store', signal: AbortSignal.timeout(5000)
            });
            if (!response.ok) return;
            const {genres} = await response.json();
            if (!Array.isArray(genres)) return;
            const tags = new Map();
            for (const genre of genres) {
                if (typeof genre !== 'string' || !genre.trim()) continue;
                const name = genre.trim().toLowerCase();
                const tag = genreTags.get(name) || name;
                if (!tags.has(tag)) tags.set(tag, genre.trim());
                if (tags.size === 3) break;
            }
            const results = await Promise.allSettled([...tags].map(([tag, genre]) => loadGenreArtists(genre, tag)));
            const groups = results.filter(result => result.status === 'fulfilled').map(result => result.value);
            const artists = new Map();
            // Alternate genres in preference order, keeping Last.fm's order within each genre.
            const rounds = Math.max(0, ...groups.map(group => group.length));
            for (let rank = 0; rank < rounds && artists.size < 6; rank++) {
                for (const group of groups) {
                    const artist = group[rank];
                    if (!artist) continue;
                    const key = artist.name.normalize('NFKC').toLowerCase();
                    if (!artists.has(key)) artists.set(key, artist);
                    if (artists.size === 6) break;
                }
            }
            if (!artists.size) return;
            renderArtists([...artists.values()]);
            document.getElementById('artists').textContent = 'Artistas populares para você';
        } catch {
            // Keep the default artists when the profile or provider is unavailable.
        }
    }

    if (artistsGrid) {
        renderArtists(artistsData);
        personalizeArtists();
    }

     albumsData.forEach( album => {
        const albumCard = document.createElement('div')
        albumCard.classList.add('album-card')

        albumCard.innerHTML = `
            <a href="${album.link}" target="_blank">
            <img loading="lazy" decoding="async" src="${album.image}" alt=imagem do "${album.name}">
            <div>
            <h3>${album.name}</h3>
            <p>${album.artist}</p>
            </div>
            </a>
        `

        albumsGrid.appendChild(albumCard)

    })

    genresData.forEach (genres => {
        const genreCard = document.createElement('div')
        genreCard.classList.add('genre-card')

        genreCard.innerHTML = `
        <a href="${genres.link}" target="_blank">
        <img loading="lazy" decoding="async" src="${genres.image}" alt=imagem do "${genres.name}">
        <div>
        <h3>${genres.name}</h3>
        </div>
        </a>
        `
        genresGrid.appendChild(genreCard)

    })

     reviewsData.forEach( reviews => {
        const reviewsCard = document.createElement('div')
        reviewsCard.classList.add('reviews-card')

        reviewsCard.innerHTML = `

    <div class="review-header">

            <img loading="lazy" decoding="async" class="album-cover" src="${reviews.album}" alt=imagem do "${reviews.albumname}">
        <div class="review-info">
            <h3>${reviews.albumname}</h3>
            <h5>${reviews.artist}</h5>
            
            <div class="rating">
            <h3>${reviews.rating}</h3>
            </div>
        </div>
    </div>

            <p class="review-text">${reviews.review}</p>

        <div class="review-user">
            <img loading="lazy" decoding="async" class="user-avatar" src="${reviews.userphoto}" alt=imagem do "${reviews.user}">
            <span>${reviews.user}</span>
        </div>

        `

        reviewsGrid.appendChild(reviewsCard)

    })
})

const menuToggle = document.querySelector(".menu-toggle");
const sidebar = document.querySelector(".sidebar");

menuToggle.addEventListener("click", () => {
    sidebar.classList.toggle("active");
});
