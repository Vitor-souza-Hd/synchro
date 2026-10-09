const artistsData = {

    "Bad Bunny": {
        name: "Bad Bunny",
        image: "./img/badbunny.jpg",
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
                image: "./img/DebíTirarMásFotos.png"
            },
            {
                name: "Un Verano Sin Ti",
                image: "./img/un-verano-sin-ti.png"
            },
            {
                name: "Nadie Sabe Lo Que Va a Pasar Mañana",
                image: "./img/nadie-sabe.png"
            }
        ]
    },


    "Rihanna": {
        name: "Rihanna",
        image: "./img/rihanna.jpg",
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
                image: "./img/anti.png"
            },
            {
                name: "Good Girl Gone Bad",
                image: "./img/good-girl.jpg"
            },
            {
                name: "Loud",
                image: "./img/loud.png"
            }
        ]
    },


    "Justin Bieber": {
        name: "Justin Bieber",
        image: "./img/justin.jpg",
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
                image: "./img/justice.png"
            },
            {
                name: "Purpose",
                image: "./img/purpose.png"
            },
            {
                name: "Believe",
                image: "./img/belive.jpg"
            }
        ]
    },


    "Billie Eilish": {
        name: "Billie Eilish",
        image: "./img/billieeilish.jpg",
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
                image: "./img/hit-me-hard.jpg"
            },
            {
                name: "Happier Than Ever",
                image: "./img/happier-than-ever.jpg"
            },
            {
                name: "WHEN WE ALL FALL ASLEEP, WHERE DO WE GO?",
                image: "./img/when-we-fall-asleep.png"
            }
        ]
    },


    "Kendrick Lamar": {
        name: "Kendrick Lamar",
        image: "./img/kendricklamar.jpg",
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
                image: "./img/gnx.png"
            },
            {
                name: "DAMN.",
                image: "./img/damn.png"
            },
            {
                name: "good kid, m.A.A.d city",
                image: "./img/good-kid.jpg"
            }
        ]
    },

    "The Weeknd": {
        name: "The Weeknd",
        image: "./img/theweeknd.jpg",
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
                image: "./img/after-hours.png"
            },
            {
                name: "Hurry Up Tomorrow",
                image: "./img/hurry-up.png"
            },
            {
                name: "Starboy",
                image: "./img/starboy.png"
            }
        ]
    }

};


// =====================================================
// PEGAR O ARTISTA PELA URL
// =====================================================

const params = new URLSearchParams(window.location.search);

const artistNameFromUrl = params.get("artist");

const artist = artistsData[artistNameFromUrl];


// =====================================================
// VERIFICAR SE O ARTISTA EXISTE
// =====================================================

if (!artist) {

    const artistNameElement =
        document.getElementById("artistName");

    if (artistNameElement) {
        artistNameElement.textContent =
            "Artista não encontrado";
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

        const div =
            document.createElement("div");

        div.className =
            "album-card";


        div.innerHTML = `

            <img
                src="${album.image}"
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

}

// =====================================================
// BOTÃO FAVORITAR
// =====================================================

const favoriteBtn =
    document.getElementById("favoriteBtn");

if (favoriteBtn && artist) {

    atualizarBotaoFavorito();


    favoriteBtn.addEventListener("click", () => {

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