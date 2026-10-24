document.addEventListener('DOMContentLoaded', () => {

const artistsData = [
    { name: 'Bad Bunny', image: 'frontend/assets/images/badbunny.jpg'},
    { name: 'Rihanna', image: 'frontend/assets/images/rihanna.jpg'},
    { name: 'Justin Bieber', image: 'frontend/assets/images/justin.jpg' },
    { name: 'Billie Eilish', image: 'frontend/assets/images/billieeilish.jpg'},
    { name: 'Kendrick Lamar', image: 'frontend/assets/images/kendricklamar.jpg'},
    { name: 'The Weeknd', image: 'frontend/assets/images/theweeknd.jpg'},

];

const albumsData = [
    {name: 'Debí Tirar Más Fotos', artist: 'Bad Bunny', image: 'frontend/assets/images/DebíTirarMásFotos.png', link:'https://open.spotify.com/intl-pt/track/3sK8wGT43QFpWrvNQsrQya?si=68653d6088a04bec' },
    {name: 'Mayhem', artist: 'Lady Gaga', image: 'frontend/assets/images/mayhem.jpg', link:'https://open.spotify.com/intl-pt/album/2MHUaRi9OCyTN02SoyRRBJ?si=2182527c0b6240ca' },
    {name: 'Lux', artist: 'Rosalía', image: 'frontend/assets/images/lux.jpg', link:'https://open.spotify.com/intl-pt/album/3SUEJULSGgBDG1j4GQhfYY?si=cd1274117d6848c1' },
    {name: 'Getting Killed', artist: 'Geese', image: 'frontend/assets/images/gettingkilled.jpg', link:'https://open.spotify.com/intl-pt/album/0eeXb23yMW6EaIgm63xxPC?si=26e487624a95463d' },
    {name: 'Starboy', artist: 'The Weeknd', image: 'frontend/assets/images/starboy.png', link:'https://open.spotify.com/intl-pt/album/2ODvWsOgouMbaA5xf0RkJe?si=rlFEEWMEQnG4dz2sIhWL1g' },
    {name: 'Bleeds', artist: 'Wednesday', image: 'frontend/assets/images/bleeds.png' },

];
const genresData = [
    {name: 'POP', image:'frontend/assets/images/michael.jpg'},
    {name: 'MPB', image:'frontend/assets/images/djavan.jpg'},
    {name: 'FUNK', image:'frontend/assets/images/funk.jpg'},
    {name: 'ROCK', image:'frontend/assets/images/queen.jpg'},
    {name: 'RAP', image:'frontend/assets/images/tupac.jpg'}
];
const reviewsData = [
    {user: 'silverzenith', userphoto :'frontend/assets/images/user/users/silverzenith.png', album: 'frontend/assets/images/user/tcfsr.png', albumname:'Three Cheers for Sweet Revenge', artist:'My Chemical Romance',
         review: '"Um dos melhores álbuns conceituais da história, com certeza daria um bom filme"', rating:'9/10' },
    {user: 'disintelia', userphoto :'frontend/assets/images/user/users/disintelia.png', album: 'frontend/assets/images/user/pornography.png', albumname: 'Pornography', artist:'The Cure',
         review: '"Esse álbum tem uma atmosfera melancólica e fria tão perfeita e, apesar disso, consegue passar uma sensação tão aconchegante. Definitivamente supera o Disintegrated e é o melhor da discografia do The cure."', rating:'10/10' },
    {user: 'jinka070', userphoto :'frontend/assets/images/user/users/jinka070.png', album: 'frontend/assets/images/user/biohazard.png', albumname:'BIOHAZARD', artist:'Trash Poll',
         review: '"somente a nata do nu metal brasileiro"', rating:'9.5/10' },
    {user: 'Tavio_OoO', userphoto :'frontend/assets/images/user/users/Tavio_OoO.png', album: 'frontend/assets/images/user/bythewater.png', albumname:'By The Water', artist:'Sondae',
         review: '"Ao contrário da maior parte dos álbuns cristãos que já ouvi, este apresenta um grande foco na musicalidade e sonoridade e não somente ao louvor, e só nisso já me ganhou, mas além disso, o conteúdo introspectivo retrata muito mais uma maior conexão com Jesus Cristo que religiosidade, tornando esse álbum uma joia rara em meio a tantos outros"', rating:'8.5/10' },
    {user: 'Saii0710', userphoto :'frontend/assets/images/user/users/Saii0710.png', album: 'frontend/assets/images/user/SSPSG.png', albumname:'SE SONHOS PUDESSEM SER GUARDADOS', artist:'myseryswin',
         review: '"O álbum mostra o refinamento de Swin em comparação com seus projetos anteriores. Melhor música: feiticeiro(eu merecia essa dança tanto assim?)"', rating:'9/10' },
    {user: 'Alz7', userphoto :'frontend/assets/images/user/users/Alz7.png', album: 'frontend/assets/images/user/sonofspergy.png', albumname:'Son Of Spergy', artist:'Daniel Caesar',
         review: '" é o último álbum lançado de Daniel Caesar. Sendo um álbum que Daniel  foca em contar a jornada de amadurecimento, fé e reconciliação. O álbum explora o peso dessa herança religiosa e familiar, abordando temas como masculinidade, erros do passado e a busca por perdão. O ritmo do projeto é intencionalmente lento, apostando em produções atmosféricas e vocais crus usando muito os corais gospels.    Sendo um retrato honesto de um artista aprendendo a lidar com suas próprias fraquezas."', rating:'9/10' },
]

const artistsGrid = document.querySelector('.artists')
    const albumsGrid = document.querySelector('.albums')
        const genresGrid = document.querySelector('.genres')
            const reviewsGrid = document.querySelector('.reviews')
            

    artistsData.forEach( artist => {
        const artistCard = document.createElement('div')
        artistCard.classList.add('artist-card')

         const artistLink =
        `frontend/pages/artista.html?artist=${encodeURIComponent(artist.name)}`;

        artistCard.innerHTML = `
            <a href="${artistLink}">
            <img src="${artist.image}" alt=imagem do "${artist.name}">
            <div>
            <h3>${artist.name}</h3>
            <p>Artista</p>
            </div>
            </a>
        `

        artistsGrid.appendChild(artistCard)

    })

     albumsData.forEach( album => {
        const albumCard = document.createElement('div')
        albumCard.classList.add('album-card')

        albumCard.innerHTML = `
            <a href="${album.link}" target="_blank">
            <img src="${album.image}" alt=imagem do "${album.name}">
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
        <img src="${genres.image}" alt=imagem do "${genres.name}">
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

            <img class="album-cover" src="${reviews.album}" alt=imagem do "${reviews.albumname}">
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
            <img class="user-avatar" src="${reviews.userphoto}" alt=imagem do "${reviews.user}">
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
