const API_KEY = "SUA_API_KEY";

const API_URL = "https://ws.audioscrobbler.com/2.0/";


// ========================================
// ARTISTA DA URL
// ========================================

const params = new URLSearchParams(
    window.location.search
);

const artist = params.get("artist");


// ========================================
// VERIFICA ARTISTA
// ========================================

if (!artist) {

    document.getElementById("artistName").textContent =
        "Artista não encontrado";

} else {

    document.title = `SYNCHRO - ${artist}`;

    carregarArtista();

}


// ========================================
// INFORMAÇÕES DO ARTISTA
// ========================================

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


        if (data.error) {

            throw new Error(data.message);

        }


        const info = data.artist;


        // NOME

        document.getElementById("artistName").textContent =
            info.name;


        // BIOGRAFIA

        document.getElementById("biography").textContent =
            info.bio?.summary ||
            "Não há informações disponíveis sobre este artista.";


        // TAGS

        carregarTags(
            info.tags?.tag || []
        );


        // IMAGEM

        carregarImagem(
            info.image || []
        );


        // MÚSICAS

        carregarMusicas();


        // ÁLBUNS

        carregarAlbuns();


    } catch (error) {

        console.error(
            "Erro ao carregar artista:",
            error
        );


        document.getElementById("artistName").textContent =
            "Erro ao carregar artista";


        document.getElementById("biography").textContent =
            "Não foi possível carregar as informações.";

    }

}



// ========================================
// IMAGEM
// ========================================

function carregarImagem(images) {

    const imagem =
        images.find(
            img => img.size === "extralarge"
        );


    if (
        imagem &&
        imagem["#text"]
    ) {

        document.getElementById("artistImage").src =
            imagem["#text"];

    }

}



// ========================================
// TAGS
// ========================================

function carregarTags(tags) {

    const container =
        document.getElementById("artistTags");


    container.innerHTML = "";


    tags
        .slice(0, 4)
        .forEach(tag => {

            const span =
                document.createElement("span");


            span.textContent =
                tag.name;


            container.appendChild(span);

        });

}



// ========================================
// MÚSICAS
// ========================================

async function carregarMusicas() {

    const url =
        `${API_URL}?method=artist.gettoptracks` +
        `&artist=${encodeURIComponent(artist)}` +
        `&api_key=${API_KEY}` +
        `&format=json` +
        `&limit=10`;


    try {

        const response =
            await fetch(url);


        const data =
            await response.json();


        const tracks =
            data.toptracks?.track || [];


        const container =
            document.getElementById("tracks");


        container.innerHTML = "";


        tracks.forEach(
            (track, index) => {


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
                            ${track.name}
                        </strong>

                        <span>
                            ${Number(track.listeners || 0).toLocaleString("pt-BR")}
                            ouvintes
                        </span>

                    </div>


                    <button title="Reproduzir">

                        <i class='bx bx-play'></i>

                    </button>

                `;


                container.appendChild(div);

            }
        );


    } catch (error) {

        console.error(
            "Erro ao carregar músicas:",
            error
        );

    }

}



// ========================================
// ÁLBUNS
// ========================================

async function carregarAlbuns() {

    const url =
        `${API_URL}?method=artist.gettopalbums` +
        `&artist=${encodeURIComponent(artist)}` +
        `&api_key=${API_KEY}` +
        `&format=json` +
        `&limit=6`;


    try {

        const response =
            await fetch(url);


        const data =
            await response.json();


        const albums =
            data.topalbums?.album || [];


        const container =
            document.getElementById("albums");


        container.innerHTML = "";


        albums.forEach(
            album => {


                const imagem =
                    album.image?.find(
                        img =>
                            img.size === "extralarge"
                    );


                const div =
                    document.createElement("div");


                div.className =
                    "album-card";


                div.innerHTML = `

                    <img
                        src="${imagem?.["#text"] || ""}"
                        alt="${album.name}"
                    >


                    <h3>
                        ${album.name}
                    </h3>


                    <p>
                        ${album.artist?.name || artist}
                    </p>

                `;


                container.appendChild(div);

            }
        );


    } catch (error) {

        console.error(
            "Erro ao carregar álbuns:",
            error
        );

    }

}