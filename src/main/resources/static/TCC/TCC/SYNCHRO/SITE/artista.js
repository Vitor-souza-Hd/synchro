const API_KEY = "7c62ff4c30df11f623f6ecd649c5d104";

const API_URL = "https://ws.audioscrobbler.com/2.0/";

//Pega o artista da URL
const params = new URLSearchParams(window.location.search);

const artist = params.get("artist");

//Se não tiver artista
if (!artist) {
    document.getElementById("artistName").textContent =
        "Artista não encontrado";
} else {
    carregarArtista();
}

// INFORMAÇÕES DO ARTISTA

async function carregarArtista() {
    const url =
        `${API_URL}?method=artist.getinfo` +
        `&artist=${encodeURIComponent(artist)}` +
        `&api_key=${API_KEY}` +
        `&format=json` +
        `&autocorrect=1`;

    try {

        const response = await fetch(url);
        const data = await response.json();
        const info = data.artist;
        document.getElementById("artistName").textContent =
            info.name;
        document.getElementById("biography").textContent =
            limparTexto(info.bio.summary);
        carregarTags(info.tags.tag);
        carregarImagem(info.image);
        carregarMusicas();
        carregarAlbuns();

    } catch (error) {
        console.error(error);
        document.getElementById("artistName").textContent =
            "Erro ao carregar artista";
    }
}

function carregarImagem(images) {
    const imagem = images.find(
        img => img.size === "extralarge"
    );
    if (imagem && imagem["#text"]) {
        document.getElementById("artistImage").src =
            imagem["#text"];
    }
}

async function carregarMusicas() {

    const url =
        `${API_URL}?method=artist.gettoptracks` +
        `&artist=${encodeURIComponent(artist)}` +
        `&api_key=${API_KEY}` +
        `&format=json` +
        `&limit=10`;

    const response = await fetch(url);

    const data = await response.json();

    const tracks = data.toptracks.track;

    const container =
        document.getElementById("tracks");

    container.innerHTML = "";

    tracks.forEach((track, index) => {

        const div = document.createElement("div");

        div.className = "track";

        div.innerHTML = `
            <span class="track-number">
                ${index + 1}
            </span>

            <div class="track-info">
                <strong>${track.name}</strong>
                <span>${track.listeners} ouvintes</span>
            </div>

            <button>
                <i class='bx bx-play'></i>
            </button>
        `;

        container.appendChild(div);

    });
}

async function carregarAlbuns() {

    const url =
        `${API_URL}?method=artist.gettopalbums` +
        `&artist=${encodeURIComponent(artist)}` +
        `&api_key=${API_KEY}` +
        `&format=json` +
        `&limit=6`;

    const response = await fetch(url);

    const data = await response.json();

    const albums = data.topalbums.album;

    const container =
        document.getElementById("albums");

    container.innerHTML = "";

    albums.forEach(album => {

        const imagem = album.image.find(
            img => img.size === "extralarge"
        );

        const div = document.createElement("div");

        div.className = "album-card";

        div.innerHTML = `

            <img
                src="${imagem ? imagem["#text"] : ""}"
                alt="${album.name}"
            >

            <h3>${album.name}</h3>

            <p>${album.artist.name}</p>

        `;

        container.appendChild(div);

    });
}

function carregarTags(tags) {

    const container =
        document.getElementById("artistTags");

    container.innerHTML = "";

    tags.slice(0, 4).forEach(tag => {

        const span = document.createElement("span");

        span.textContent = tag.name;

        container.appendChild(span);

    });
}