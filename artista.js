const API_URL = "http://localhost:8080/last-fm";

// ========================================
// ARTISTA DA URL
// ========================================

const params = new URLSearchParams(
window.location.search
);

const artist = params.get("artist");

// ========================================
// INICIALIZAÇÃO
// ========================================

if (!artist) {


    
const artistName =
    document.getElementById("artistName");

if (artistName) {
    artistName.textContent =
        "Artista não encontrado";
}



} else {


    
document.title =
    `SYNCHRO - ${artist}`;

carregarArtista();



}

// ========================================
// INFORMAÇÕES DO ARTISTA
// ========================================

async function carregarArtista() {


    
try {

    const response = await fetch(
        `${API_URL}/artist/${encodeURIComponent(artist)}`
    );

    if (!response.ok) {

        throw new Error(
            `Erro HTTP: ${response.status}`
        );

    }

    const data =
        await response.json();

    const info =
        data.artist;

    if (!info) {

        throw new Error(
            "Artista não encontrado."
        );

    }


    // ========================================
    // NOME
    // ========================================

    const artistName =
        document.getElementById("artistName");

    if (artistName) {

        artistName.textContent =
            info.name || artist;

    }


    // ========================================
    // BIOGRAFIA
    // ========================================

    const biography =
        document.getElementById("biography");

    if (biography) {

        biography.innerHTML =
            info.bio?.summary ||
            "Não há informações disponíveis sobre este artista.";

    }


    // ========================================
    // TAGS
    // ========================================

    carregarTags(
        info.tags?.tag || []
    );


    // ========================================
    // IMAGEM
    // ========================================

    carregarImagem(
        info.image || []
    );


    // ========================================
    // MÚSICAS
    // ========================================

    carregarMusicas();


    // ========================================
    // ÁLBUNS
    // ========================================

    carregarAlbuns();


} catch (error) {

    console.error(
        "Erro ao carregar artista:",
        error
    );


    const artistName =
        document.getElementById("artistName");

    if (artistName) {

        artistName.textContent =
            "Erro ao carregar artista";

    }


    const biography =
        document.getElementById("biography");

    if (biography) {

        biography.textContent =
            "Não foi possível carregar as informações do artista.";

    }

}



}

// ========================================
// IMAGEM
// ========================================

function carregarImagem(images) {


    
const artistImage =
    document.getElementById("artistImage");


if (!artistImage) {
    return;
}


const imagem =
    images.find(
        img =>
            img.size === "extralarge"
    );


if (
    imagem &&
    imagem["#text"]
) {

    artistImage.src =
        imagem["#text"];

    artistImage.alt =
        artist;

}



}

// ========================================
// TAGS
// ========================================

function carregarTags(tags) {


    
const container =
    document.getElementById("artistTags");


if (!container) {
    return;
}


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


    
const container =
    document.getElementById("tracks");


if (!container) {
    return;
}


try {

    const response = await fetch(
        `${API_URL}/artist/${encodeURIComponent(artist)}/tracks`
    );


    if (!response.ok) {

        throw new Error(
            `Erro HTTP: ${response.status}`
        );

    }


    const data =
        await response.json();


    const tracks =
        data.toptracks?.track || [];


    container.innerHTML = "";


    if (tracks.length === 0) {

        container.innerHTML =
            "<p>Nenhuma música encontrada.</p>";

        return;

    }


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
                        ${Number(
                            track.listeners || 0
                        ).toLocaleString("pt-BR")}
                        ouvintes
                    </span>

                </div>

                <button
                    type="button"
                    title="Reproduzir"
                >
                    <i class="bx bx-play"></i>
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


    container.innerHTML =
        "<p>Não foi possível carregar as músicas.</p>";

}



}

// ========================================
// ÁLBUNS
// ========================================

async function carregarAlbuns() {


    
const container =
    document.getElementById("albums");


if (!container) {
    return;
}


try {

    const response = await fetch(
        `${API_URL}/artist/${encodeURIComponent(artist)}/albums`
    );


    if (!response.ok) {

        throw new Error(
            `Erro HTTP: ${response.status}`
        );

    }


    const data =
        await response.json();


    const albums =
        data.topalbums?.album || [];


    container.innerHTML = "";


    if (albums.length === 0) {

        container.innerHTML =
            "<p>Nenhum álbum encontrado.</p>";

        return;

    }


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


    container.innerHTML =
        "<p>Não foi possível carregar os álbuns.</p>";

}



}

// ========================================
// FAVORITO
// ========================================

const favoriteBtn =
document.getElementById("favoriteBtn");

if (favoriteBtn) {



atualizarBotaoFavorito();


favoriteBtn.addEventListener(
    "click",
    () => {

        let favorites =
            JSON.parse(
                localStorage.getItem(
                    "synchroFavorites"
                )
            ) || [];


        if (!favorites.includes(artist)) {

            favorites.push(artist);


            localStorage.setItem(
                "synchroFavorites",
                JSON.stringify(favorites)
            );


        } else {

            favorites =
                favorites.filter(
                    item =>
                        item !== artist
                );


            localStorage.setItem(
                "synchroFavorites",
                JSON.stringify(favorites)
            );

        }


        atualizarBotaoFavorito();

    }
);



}

// ========================================
// ATUALIZA BOTÃO DE FAVORITO
// ========================================

function atualizarBotaoFavorito() {



if (!favoriteBtn) {
    return;
}


const favorites =
    JSON.parse(
        localStorage.getItem(
            "synchroFavorites"
        )
    ) || [];


if (favorites.includes(artist)) {

    favoriteBtn.innerHTML =
        "<i class='bx bxs-heart'></i> Favoritado";

} else {

    favoriteBtn.innerHTML =
        "<i class='bx bx-heart'></i> Favoritar";

}



}
