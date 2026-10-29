const artistsData = {

    "Bad Bunny": {
        name: "Bad Bunny",
        image: "../assets/images/badbunny.jpg",
        genre: ["Reggaeton", "Trap", "Latin"],

        bio: "Bad Bunny é um cantor, rapper e produtor porto-riquenho conhecido por sua contribuição para o reggaeton e para a música latina contemporânea.",

        tracks: [
            "DtMF",
            "BAILE INoLVIDABLE",
            "NUEVAYoL",
            "MONACO",
            "WHERE SHE GOES"
        ],

        albums: [
            {
                name: "Debí Tirar Más Fotos",
                image: "../assets/images/DebíTirarMásFotos.png"
            },
            {
                name: "Un Verano Sin Ti",
                image: "../assets/images/un-verano-sin-ti.png"
            },
            {
                name: "Nadie Sabe Lo Que Va a Pasar Mañana",
                image: "../assets/images/nadie-sabe.png"
            }
        ]
    },


    "Rihanna": {
        name: "Rihanna",
        image: "../assets/images/rihanna.jpg",
        genre: ["Pop", "R&B", "Hip-Hop"],

        bio: "Rihanna é uma cantora, compositora, atriz e empresária barbadense, conhecida por sua influência na música pop e R&B.",

        tracks: [
            "Umbrella",
            "Diamonds",
            "We Found Love",
            "Love on the Brain",
            "Work"
        ],

        albums: [
            {
                name: "Anti",
                image: "../assets/images/anti.png"
            },
            {
                name: "Good Girl Gone Bad",
                image: "../assets/images/good-girl.jpg"
            },
            {
                name: "Loud",
                image: "../assets/images/loud.png"
            }
        ]
    },


    "Justin Bieber": {
        name: "Justin Bieber",
        image: "../assets/images/justin.jpg",
        genre: ["Pop", "R&B"],

        bio: "Justin Bieber é um cantor canadense que alcançou reconhecimento internacional ainda jovem e se tornou um dos principais nomes da música pop.",

        tracks: [
            "Baby",
            "Sorry",
            "Love Yourself",
            "Peaches",
            "What Do You Mean?"
        ],

        albums: [
            {
                name: "Justice",
                image: "../assets/images/justice.png"
            },
            {
                name: "Purpose",
                image: "../assets/images/purpose.png"
            },
            {
                name: "Believe",
                image: "../assets/images/belive.jpg"
            }
        ]
    },


    "Billie Eilish": {
        name: "Billie Eilish",
        image: "../assets/images/billieeilish.jpg",
        genre: ["Pop", "Alternative", "Electropop"],

        bio: "Billie Eilish é uma cantora e compositora norte-americana conhecida por seu estilo musical alternativo e sua estética marcante.",

        tracks: [
            "BIRDS OF A FEATHER",
            "bad guy",
            "lovely",
            "Happier Than Ever",
            "when the party's over"
        ],

        albums: [
            {
                name: "HIT ME HARD AND SOFT",
                image: "../assets/images/hit-me-hard.jpg"
            },
            {
                name: "Happier Than Ever",
                image: "../assets/images/happier-than-ever.jpg"
            },
            {
                name: "WHEN WE ALL FALL ASLEEP, WHERE DO WE GO?",
                image: "../assets/images/when-we-fall-asleep.png"
            }
        ]
    },


    "Kendrick Lamar": {
        name: "Kendrick Lamar",
        image: "../assets/images/kendricklamar.jpg",
        genre: ["Hip-Hop", "Rap", "West Coast"],

        bio: "Kendrick Lamar é um rapper e compositor norte-americano conhecido por suas letras socialmente conscientes e por sua influência no hip-hop.",

        tracks: [
            "Not Like Us",
            "HUMBLE.",
            "DNA.",
            "Money Trees",
            "Alright"
        ],

        albums: [
            {
                name: "GNX",
                image: "../assets/images/gnx.png"
            },
            {
                name: "DAMN.",
                image: "../assets/images/damn.png"
            },
            {
                name: "good kid, m.A.A.d city",
                image: "../assets/images/good-kid.jpg"
            }
        ]
    },

    "The Weeknd": {
        name: "The Weeknd",
        image: "../assets/images/theweeknd.jpg",
        genre: ["R&B", "Soul Pop", "Dance"],

        bio: "Abel Makkonen Tesfaye, conhecido profissionalmente como The Weeknd, é um cantor, compositor, ator e produtor musical canadense.",

        tracks: [
            "Starboy",
            "One Of The Girls",
            "Blinding Lights",
            "Die For You",
            "Timeless (feat Playboi Carti)"
        ],

        albums: [
            {
                name: "After Hours",
                image: "../assets/images/after-hours.png"
            },
            {
                name: "Hurry Up Tomorrow",
                image: "../assets/images/hurry-up.png"
            },
            {
                name: "Starboy",
                image: "../assets/images/starboy.png"
            }
        ]
    }

};


// =====================================================
// PEGAR O ARTISTA PELA URL
// =====================================================

const params = new URLSearchParams(window.location.search);
document.querySelector('.menu-toggle')?.addEventListener('click', () => {
    document.querySelector('.sidebar')?.classList.toggle('active');
});

const artistNameFromUrl = (params.get("artist") || '').trim();
let artistName = artistNameFromUrl || '';

let artist = Object.values(artistsData).find(item => item.name.toLowerCase() === artistNameFromUrl.toLowerCase());
const artistStatus = document.createElement('p');
artistStatus.setAttribute('role', 'status');
artistStatus.style.color = '#cbc4d6';
document.querySelector('.artist-header').before(artistStatus);

function setArtistLoading(loading) {
    document.querySelector('.artist-header').setAttribute('aria-busy', String(loading));
    document.querySelectorAll('.artist-section').forEach(section => { section.hidden = loading; });
    document.getElementById('favoriteBtn').disabled = loading;
    document.querySelectorAll('.open-review').forEach(button => { button.disabled = loading; });
}

async function loadCatalogArtist() {
    const id = params.get('id');
    if (!id && !artistNameFromUrl) {
        artistStatus.textContent = 'Selecione um artista nos resultados da pesquisa.';
        return;
    }
    setArtistLoading(true);
    artistStatus.textContent = 'Carregando informações do artista…';
    document.getElementById('artistName').textContent = 'Carregando...';
    document.getElementById('artistImage').src = '../assets/images/catalog-placeholder.svg';
    try {
        if (id) await fetch(`/catalog/artists/${encodeURIComponent(id)}/refresh`, { method: 'POST' }).catch(() => {});
        const endpoint = id ? `/catalog/artists/${encodeURIComponent(id)}`
            : `/catalog/artists/by-name?name=${encodeURIComponent(artistNameFromUrl)}`;
        const response = await fetch(endpoint, { headers: { Accept: 'application/json' }, cache: 'no-store' });
        if (response.status === 404 && artist && !id) {
            carregarPaginaArtista(artist);
            setArtistLoading(false);
            artistStatus.textContent = '';
            return;
        }
        if (!response.ok) throw new Error(response.status === 404 ? 'Artista não encontrado' : 'Não foi possível carregar o artista.');
        const data = await response.json();
        const saved = data.artist;
        artistName = saved.nome;
        artist = {
            name: saved.nome,
            image: saved.imgUrl || artistsData[saved.nome]?.image || '../assets/images/catalog-placeholder.svg',
            bio: new DOMParser().parseFromString(saved.biografia || 'Biografia não disponível no catálogo.', 'text/html').body.textContent,
            genre: saved.lastFm?.tags || [],
            tracks: data.musicas.map(item => ({ id: item.id, name: item.titulo })),
            albums: data.albums.map(item => ({ id: item.id, name: item.titulo, image: item.lastFm?.imageUrl || '../assets/images/catalog-placeholder.svg' }))
        };
        carregarPaginaArtista(artist);
        setArtistLoading(false);
        artistStatus.textContent = '';
        atualizarBotaoFavorito();
        artistLabel.textContent = artistName;
    } catch (error) {
        document.querySelector('.artist-header').setAttribute('aria-busy', 'false');
        document.getElementById('artistName').textContent = error.message;
        artistStatus.textContent = error.message === 'Artista não encontrado'
            ? 'Este artista não está disponível. Volte à pesquisa e selecione um resultado atualizado.'
            : 'Ocorreu uma falha ao carregar o artista. Recarregue a página para tentar novamente.';
    }
}
// Run after the rest of the page's event handlers and labels have been initialized.
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', loadCatalogArtist);
else queueMicrotask(loadCatalogArtist);


// =====================================================
// VERIFICAR SE O ARTISTA EXISTE
// =====================================================

if (!artist) {

        const artistNameElement =
        document.getElementById("artistName");

    if (artistNameElement) {
        artistNameElement.textContent =
            params.has('id') || artistNameFromUrl ? "Carregando..." : "Selecione um artista";
    }

} else {

    carregarPaginaArtista(artist);

}


// =====================================================
// CARREGAR PÁGINA DO ARTISTA
// =====================================================

function carregarPaginaArtista(artist) {

    document.title =
        `SYNCHRO - ${artist.name}`;


    // NOME DO ARTISTA

    const artistNameElement =
        document.getElementById("artistName");

    if (artistNameElement) {

        artistNameElement.textContent =
            artist.name;

    }


    // IMAGEM DO ARTISTA

    const artistImage =
        document.getElementById("artistImage");

    if (artistImage) {

        artistImage.src =
            artist.image;

        artistImage.alt =
            artist.name;
        artistImage.onerror = () => { artistImage.onerror = null; artistImage.src = '../assets/images/catalog-placeholder.svg'; };

    }


    // BIOGRAFIA

    const biography =
        document.getElementById("biography");

    if (biography) {

        biography.textContent =
            artist.bio;

    }


    // GÊNEROS / TAGS

    carregarTags(
        artist.genre
    );


    // MÚSICAS

    carregarMusicas(
        artist.tracks,
        artist.name
    );


    // ÁLBUNS

    carregarAlbuns(
        artist.albums,
        artist.name
    );

}


// =====================================================
// CARREGAR TAGS
// =====================================================

function carregarTags(tags) {

    const container =
        document.getElementById("artistTags");

    if (!container) {
        return;
    }

    container.innerHTML = "";


    tags.forEach(tag => {

        const span =
            document.createElement("span");

        span.textContent =
            tag;

        container.appendChild(span);

    });

}


// =====================================================
// CARREGAR MÚSICAS
// =====================================================

function carregarMusicas(tracks, artistName) {

    const container =
        document.getElementById("tracks");

    if (!container) {
        return;
    }

    container.innerHTML = "";


    tracks.forEach((track, index) => {
        if (typeof track === 'object') {
            const link = document.createElement('a');
            link.className = 'track catalog-link';
            link.href = `musica.html?id=${encodeURIComponent(track.id)}`;
            link.textContent = `${String(index + 1).padStart(2, '0')} · ${track.name}`;
            container.append(link);
            return;
        }

        const div =
            document.createElement("div");

        div.className =
            "track";


        div.innerHTML = `

            <span class="track-number">
                ${String(index + 1).padStart(2, "0")}
            </span>

            <div class="track-info">

                <strong>
                    ${track}
                </strong>

                <span>
                    ${artistName}
                </span>

            </div>

            <button
                type="button"
                class="play-button"
                title="Reproduzir"
            >

                <i class="bx bx-play"></i>

            </button>

        `;


        container.appendChild(div);

    });
    if (!tracks.length) container.textContent = 'Nenhuma música cadastrada para este artista.';

}


// =====================================================
// CARREGAR ÁLBUNS
// =====================================================

function carregarAlbuns(albums, artistName) {

    const container =
        document.getElementById("albums");

    if (!container) {
        return;
    }

    container.innerHTML = "";


    albums.forEach(album => {
        if (album.id != null) {
            const link = document.createElement('a');
            link.className = 'album-card catalog-link';
            link.href = `album.html?id=${encodeURIComponent(album.id)}`;
            const image = document.createElement('img');
            image.src = album.image;
            image.alt = '';
            const title = document.createElement('h3');
            title.textContent = album.name;
            link.append(image, title);
            container.append(link);
            return;
        }

        const div =
            document.createElement("div");

        div.className =
            "album-card";


        div.innerHTML = `

            <img
                src="${album.image}"
                onerror="this.onerror=null;this.src='../assets/images/music.png';"
                alt="${album.name}"
            >

            <h3>
                ${album.name}
            </h3>

            <p>
                ${artistName}
            </p>

        `;


        container.appendChild(div);

    });
    if (!albums.length) container.textContent = 'Nenhum álbum cadastrado para este artista.';

}

// =====================================================
// BOTÃO FAVORITAR
// =====================================================

const favoriteBtn =
    document.getElementById("favoriteBtn");

if (favoriteBtn) {

    atualizarBotaoFavorito();


    favoriteBtn.addEventListener("click", () => {
        if (!artist) return;

        let favorites =
            JSON.parse(
                localStorage.getItem("synchroFavorites")
            ) || [];


        // Se ainda não está favoritado
        if (!favorites.includes(artist.name)) {

            favorites.push(artist.name);

        }

        // Se já está favoritado
        else {

            favorites =
                favorites.filter(
                    favorite => favorite !== artist.name
                );

        }


        // Salvar novamente
        localStorage.setItem(
            "synchroFavorites",
            JSON.stringify(favorites)
        );


        // Atualizar aparência
        atualizarBotaoFavorito();

    });

}


// =====================================================
// ATUALIZAR BOTÃO
// =====================================================

function atualizarBotaoFavorito() {

    if (!favoriteBtn || !artist) {
        return;
    }


    const favorites =
        JSON.parse(
            localStorage.getItem("synchroFavorites")
        ) || [];


    if (favorites.includes(artist.name)) {

        favoriteBtn.innerHTML = `
            <i class="bx bxs-heart"></i>
            Favoritado
        `;

        favoriteBtn.classList.add("favorited");

    }

    else {

        favoriteBtn.innerHTML = `
            <i class="bx bx-heart"></i>
            Favoritar
        `;

        favoriteBtn.classList.remove("favorited");

    }

}

//Janela modal de Reviews

const modal = document.getElementById("reviewModal");
const form = document.getElementById("reviewForm");
const artistLabel = document.getElementById("reviewArtist");
const ratingInput = document.getElementById("reviewRating");
const reviewText = document.getElementById("reviewText");
const charCount = document.getElementById("charCount");
const reviewError = document.getElementById("reviewError");
const stars = document.querySelectorAll(".star-rating button");

artistLabel.textContent = artistName;

const openButtons = document.querySelectorAll(".open-review");

function openReview() {
    artistLabel.textContent = artistName;
    reviewError.textContent = "";
    modal.classList.add("active");
    modal.setAttribute("aria-hidden", "false");
    document.body.style.overflow = "hidden";
    document.getElementById("closeReview").focus();
}

function closeReview() {
    modal.classList.remove("active");
    modal.setAttribute("aria-hidden", "true");
    document.body.style.overflow = "";
}

openButtons.forEach(button => {
    button.addEventListener("click", openReview);
});

document.getElementById("closeReview")
    .addEventListener("click", closeReview);

document.getElementById("cancelReview")
    .addEventListener("click", closeReview);

modal.addEventListener("click", event => {
    if (event.target === modal) closeReview();
});

document.addEventListener("keydown", event => {
    if (event.key === "Escape" && modal.classList.contains("active")) {
        closeReview();
    }
});

// notas/estrelas
function setRating(value) {
    ratingInput.value = value;

    stars.forEach(star => {
        const selected = Number(star.dataset.rating) <= value;
        star.classList.toggle("selected", selected);
        star.setAttribute("aria-pressed", String(
            Number(star.dataset.rating) === value
        ));
    });
}

stars.forEach(star => {
    star.addEventListener("click", () => {
        setRating(Number(star.dataset.rating));
    });
});

// Contador de caracteres
reviewText.addEventListener("input", () => {
    charCount.textContent = `${reviewText.value.length}/500`;
});

//Publicação de teste
form.addEventListener("submit", event => {
    event.preventDefault();
    reviewError.textContent = "";

    const rating = Number(ratingInput.value);
    const text = reviewText.value.trim();

    if (rating < 1 || rating > 5) {
        reviewError.textContent = "Selecione uma nota de 1 a 5.";
        return;
    }

    if (!text) {
        reviewError.textContent = "Escreva seu review.";
        return;
    }

    const review = {
        id: crypto.randomUUID(),
        artist: artistName,
        rating,
        text,
        favorite: document.getElementById("reviewFavorite").checked,
        date: new Date().toISOString()
    };

    try {
        const saved = JSON.parse(
            localStorage.getItem("synchroReviews") || "[]"
        );

        saved.push(review);
        localStorage.setItem("synchroReviews", JSON.stringify(saved));

        alert("Review salvo com sucesso!");

        form.reset();
        setRating(0);
        charCount.textContent = "0/500";
        closeReview();
    } catch (error) {
        reviewError.textContent =
            "Não foi possível salvar o review neste navegador.";
    }
});
