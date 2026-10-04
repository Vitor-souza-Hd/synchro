document.addEventListener('DOMContentLoaded', () => {

    const userData = [
        { name: 'OliverCafeinado', subname:'Jovem Galvanista'}
    ];

    const artistsData = [
        {name: 'Carissas Wierd', image: './img/user/carissas.jpg', link:'https://open.spotify.com/intl-pt/artist/5VnYwYnG7QmpzQtxyubIwh?si=QhpZD-msQa2oR0AS-bSMYg'},
        {name: 'FM-Cinco', image: './img/user/fm-cinco.jpg', link:'https://open.spotify.com/intl-pt/artist/6Cx7tvzhROth2WcdTEI32Z?si=-8t_swP7QO23S2PYhgkE7g'},
        {name: 'Car Seat Headrest', image: './img/user/csh.jpg', link:'https://open.spotify.com/intl-pt/artist/5PbpKlxQE0Ktl5lcNABoFf?si=DJiXHyF7SaG1lg4Cx4_HJg'},
        {name: 'Magnólia', image: './img/user/magnolia.jpg', link:'https://open.spotify.com/intl-pt/artist/1UeVL6kabga2Gxp6TfoyUs?si=SY1mATHbQ4KRKLp3kGbrvw'},
        {name: 'Jeff Buckley', image: './img/user/jeffbuckley.jpg', link:'https://open.spotify.com/intl-pt/artist/3nnQpaTvKb5jCQabZefACI?si=77RWn_qnQTusGWXB-UR3Rw'},
        {name: 'Jorge Ben Jor', image: './img/user/jorge.jpg', link:'https://open.spotify.com/intl-pt/artist/5JYtpnUKxAzXfHEYpOeeit?si=hes1fQqfRtanGi5ailJAhA'}

    ];

    const reviewData = [
        {name:'Twin Fantasy', subname:'Car Seat Headrest', 
            review:' "O clássico da discografia de CSH, melancólico e eufórico, é um álbum perfeitamente solitário." ',
            image:'./img/user/twin.jpg', rating:'10/10', link:'https://open.spotify.com/intl-pt/album/6gDtROOIYa6OQxwhDNkDRM?si=c0ce719ff3ce4276'},
        {name:'Juturna', subname:'Circa Survive', 
            review:'"Simplesmente um dos, se não O melhor vocalista emo do post-hardcore. É intenso e belo."',
            image:'./img/user/circa.jpg', rating:'8.5/10', link:'https://open.spotify.com/intl-pt/album/0huXZPw7bhK5vTv7CMYOmP?si=3c856ac0e07e42bc'},
        {name:'Deathconsciousness', subname:'Have A Nice Life', 
            review:'"Um grande álbum com um grande significado, é para mim, o melhor do pós-punk e noise."',
            image:'./img/user/death.jpg', rating:'10/10', link:'https://open.spotify.com/intl-pt/album/6MH3CAXp8AN8ELrbex18dM?si=c95f6f4e1b4047ce'},
        {name:'Blonde', subname:'Frank Ocean', 
        review:'"I would never cry to a Frank Ocean song. I am a MAN!"',
        image:'./img/user/blonde.jpg', rating:'8/10', link:'https://open.spotify.com/intl-pt/album/3mH6qwIy9crq0I9YQbOuDf?si=046157ba2560409b'},
        {name:'Souvlaki', subname:'Slowdive', 
        review:'"Simplesmente os maiores do Shoegaze"',
        image:'./img/user/souvlaki.jpg', rating:'8.5/10', link:'https://open.spotify.com/intl-pt/album/53eHm1f3sFiSzWMaKOl98Z?si=64c525c9411b4647'},
        {name:'Recreio', subname:'Lupe de Lupe', 
        review:'"Uma das minhas favoritas do Lupe, é o 1º álbum, por isso, tão real com letras sentimentais e reais demais"',
        image:'./img/user/recreio.jpg', rating:'9/10', link:'https://open.spotify.com/intl-pt/album/2Tb2i6PX5lr9SBUQqyvBbf?si=f483beffa8814ee3'},
    ];

    const playlistData = [

        {name: 'emohc e screamo br', description: 'mix das melhores bandas e artistas br do emocore/screamo', image: './img/user/emobr.png', link: 'https://open.spotify.com/playlist/5R3IJXfh2Labijk9DZFD2d?si=9d41576a0068424e'},
        {name: 'Pagode Russo', description: 'xote nordestino e sertanejo raiz, para minha iguana nordestina, classicos da russia, para meu interior dostoievskano', image: './img/user/xoterusso.png', link: 'https://open.spotify.com/playlist/4R3brzt610WnomqGWDNXD6?si=de93bb460aef4d67'},
        {name: 'mix romântico', description: 'do mais melancólico para os mais fofos', image: './img/user/mixromantico.png', link: 'https://open.spotify.com/playlist/3J6tTIfrtPQ3gHiTLrhaba?si=76829909f6a1406a'},
        {name: '02/05', description: 'art pop e música experimental', image: './img/user/artpop.png', link: 'https://open.spotify.com/playlist/6Jls0dnu2MeeqpZsoke2yl?si=0808510d00994c2a'},
        {name: '2018core', description: 'majoritariamente indie, os mais nostáligicos de 2018', image: './img/user/2018.png', link: 'https://open.spotify.com/playlist/3IxQ1BlBIZlAiJrRNkcYBv?si=6157570a5e93474f'},
        {name: 'nublado', description: 'midwest emo e emo caipira', image: './img/user/midwest.png', link: 'https://open.spotify.com/playlist/2jFG3G8jDHSN4E6hgiPrGG?si=364691fd18624337'},

    ];

    const weeklyReportArtistData = [
        {place: '#1', name: 'Magnólia', times:'40 Synchs'},
        {place: '#2', name: 'Car Seat Headrest', times:'30 Synchs'},
        {place: '#3', name: 'Jorge Ben Jor', times:'14 Synchs'},
        {place: '#4', name: 'Cap n Jazz', times:'8 Synchs'},
        {place: '#5', name: 'Panchinko', times:'8 Synchs'}
    ];
    const weeklyReportAlbumData = [
        {place: '#1', name: 'alma e pipo', artist: 'Magnólia', times:'11 Synchs'},
        {place: '#2', name: 'a maré carregou nossos corpos', artist:'Magnólia', times:'9 Synchs'},
        {place: '#3', name: 'Analphabeta', artist:'Cap n Jazz', times:'8 Synchs'},
        {place: '#4', name: 'caru e os bixinhos de seu coração', artist:'Magnólia', times:'8 Synchs'},
        {place: '#5', name: 'Sambas De Baden E Vinícius', artist:'Baden Powell', times:'7 Synchs'}
    ];
    
    const weeklyReportMusicData = [
        {place: '#1', name: 'The Book Lover', artist: 'Broadcast', times:'5 Synchs'},
        {place: '#2', name: 'memories last longer', artist:'Noragami', times:'4 Synchs'},
        {place: '#3', name: ' When Im Small ', artist:'Phantogram', times:'4 Synchs'},
        {place: '#4', name: 'Utter Silence Is Fragile', artist:'Suis La Lune', times:'4 Synchs'},
        {place: '#5', name: 'Beautiful Things Are Better Left Dead', artist:'Aprils Love Affair', times:'3 Synchs'}
    ];

    const weeklyReportGenreData = [
        {place: '#1', genre: 'Emo', times:'55 Synchs'},
        {place: '#2', genre: 'Screamo', times:'40 Synchs'},
        {place: '#3', genre: 'MPB', times:'28 Synchs'},
        {place: '#4', genre: 'Emo rap', times:'20 Synchs'},
        {place: '#5', genre: 'Indie', times:'19 Synchs'},

    ];

    const userGrid = document.querySelector('.user')
        const artistGrid = document.querySelector('.artist')
            const reviewGrid = document.querySelector('.review')
                const playlistGrid = document.querySelector('.playlist')
                    const reportArtistGrid = document.querySelector('.report-artists')
                        const reportAlbumGrid = document.querySelector('.report-albums')
                            const reportMusicGrid = document.querySelector('.report-musics')
                                const reportGenreGrid = document.querySelector('.report-genres')

    userData.forEach( user => {
        const userCard = document.createElement('div')
        userCard.classList.add('user-card')

        userCard.innerHTML = `
            <div>
            <h3>${user.name}</h3>
            <p>${user.subname}</p>
            </div>
            
            <img src="./img/user/userphoto.jpg" alt="${user.name}">
        `

        userGrid.appendChild(userCard)

    })

    artistsData.forEach ( artist => {
        const artistCard = document.createElement('div')
        artistCard.classList.add('artist-card')

        artistCard.innerHTML = `

            <a href="${artist.link}" target="_blank">

            <img src="${artist.image}" alt=imagem do "${artist.name}">
            <div>
            <h3>${artist.name}</h3>
            <p>Artista</p>
            </div>
            </a>
        `

        artistGrid.appendChild(artistCard)
    })

     reviewData.forEach( review => {
        const reviewCard = document.createElement('div')
        reviewCard.classList.add('review-card')

        reviewCard.innerHTML = `

            <a href="${review.link}" target="_blank">

            <img src="${review.image}" alt=imagem do "${review.name}">
            <div>
            <h3>${review.name}</h3>
            <h5>${review.subname}</h5>
            <p>${review.review}</p>
            <h3>${review.rating}</h3>
            </div>

            </a>
        `

        reviewGrid.appendChild(reviewCard)

    })

    playlistData.forEach( playlist => {
        const playlistCard = document.createElement('div')

        playlistCard.classList.add('playlist-card')

        playlistCard.innerHTML = `

            <a href="${playlist.link}" target="_blank">

            <img src="${playlist.image}" alt=imagem do "${playlist.name}">
            <div>
            <h3>${playlist.name}</h3>
            <p>${playlist.description}</p>
            </div>

            </a>
        `

        playlistGrid.appendChild(playlistCard)


    })

    weeklyReportArtistData.forEach( reportArtist => {
        const reportArtistCard = document.createElement('div')

        reportArtistCard.classList.add('weekly-card')

        reportArtistCard.innerHTML = `

            <div class="rank-item">
            <h3>${reportArtist.place}</h3>
            <h3>${reportArtist.name}</h3>
            <p>${reportArtist.times}</p>
            </div>
        `

        reportArtistGrid.appendChild(reportArtistCard)
    })

    weeklyReportAlbumData.forEach( reportAlbum => {
        const reportAlbumCard = document.createElement('div')

        reportAlbumCard.classList.add('weekly-card')

        reportAlbumCard.innerHTML = `

            <div class="rank-item">
            <h3>${reportAlbum.place}</h3>
            <h3>${reportAlbum.name}</h3>
            <p>${reportAlbum.artist}</p>
            <p>${reportAlbum.times}</p>
            </div>
        `

        reportAlbumGrid.appendChild(reportAlbumCard)
    })

    weeklyReportMusicData.forEach( reportMusic => {
        const reportMusicCard = document.createElement('div')

        reportMusicCard.classList.add('weekly-card')

        reportMusicCard.innerHTML = `

            <div class="rank-item">
            <h3>${reportMusic.place}</h3>
            <h3>${reportMusic.name}</h3>
            <p>${reportMusic.artist}</p>
            <p>${reportMusic.times}</p>
            </div>
        `

        reportMusicGrid.appendChild(reportMusicCard)
    })

    weeklyReportGenreData.forEach( reportGenre => {
        const reportGenreCard = document.createElement('div')

        reportGenreCard.classList.add('weekly-card')

        reportGenreCard.innerHTML = `

            <div class="rank-item">
            <h3>${reportGenre.place}</h3>
            <h3>${reportGenre.genre}</h3>
            <p>${reportGenre.times}</p>
            </div>
        `

        reportGenreGrid.appendChild(reportGenreCard)
    })


});

const lastfmButton = document.getElementById("lastfm-connect");

lastfmButton.addEventListener("click", () => {

    const apiKey = "SUA_API_KEY";

    const callback = "https://seusite.com/lastfm/callback";

    const authUrl =
        `https://www.last.fm/api/auth/?api_key=${apiKey}`;

    window.location.href = authUrl;
});