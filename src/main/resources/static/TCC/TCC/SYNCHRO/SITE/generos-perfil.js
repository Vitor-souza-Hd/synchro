
document.addEventListener("DOMContentLoaded", () => {

    // =====================================================
    // TOP GENEROS MUSICAIS - SYNCHRO
    // =====================================================

    const container =
        document.querySelector(".report-genres");

    const periodSelect =
        document.getElementById("rankingPeriod");

    if (!container) {
        return;
    }

    // Normalizacao das tags mais comuns do Last.fm.
    // Tags fora deste catalogo nao entram no ranking.
    const genres = {
        "rock": "Rock",
        "alternative rock": "Rock alternativo",
        "alt rock": "Rock alternativo",
        "indie rock": "Indie rock",
        "indie": "Indie",
        "indie pop": "Indie pop",
        "pop": "Pop",
        "pop rock": "Pop rock",
        "art pop": "Art pop",
        "art rock": "Art rock",
        "dream pop": "Dream pop",
        "shoegaze": "Shoegaze",
        "post punk": "Post-punk",
        "post-punk": "Post-punk",
        "punk": "Punk",
        "punk rock": "Punk rock",
        "post hardcore": "Post-hardcore",
        "post-hardcore": "Post-hardcore",
        "hardcore": "Hardcore",
        "screamo": "Screamo",
        "emo": "Emo",
        "midwest emo": "Midwest emo",
        "emo rap": "Emo rap",
        "metal": "Metal",
        "heavy metal": "Heavy metal",
        "black metal": "Black metal",
        "death metal": "Death metal",
        "nu metal": "Nu metal",
        "grunge": "Grunge",
        "noise rock": "Noise rock",
        "noise pop": "Noise pop",
        "psychedelic rock": "Rock psicodélico",
        "progressive rock": "Rock progressivo",
        "electronic": "Eletrônica",
        "electronica": "Eletrônica",
        "electropop": "Electropop",
        "ambient": "Ambient",
        "experimental": "Experimental",
        "trip hop": "Trip-hop",
        "trip-hop": "Trip-hop",
        "house": "House",
        "techno": "Techno",
        "drum and bass": "Drum and bass",
        "hip hop": "Hip-hop",
        "hip-hop": "Hip-hop",
        "rap": "Rap",
        "trap": "Trap",
        "rnb": "R&B",
        "r&b": "R&B",
        "soul": "Soul",
        "neo soul": "Neo soul",
        "neo-soul": "Neo soul",
        "funk": "Funk",
        "jazz": "Jazz",
        "blues": "Blues",
        "folk": "Folk",
        "folk rock": "Folk rock",
        "country": "Country",
        "reggae": "Reggae",
        "ska": "Ska",
        "bossa nova": "Bossa nova",
        "samba": "Samba",
        "samba rock": "Samba rock",
        "mpb": "MPB",
        "musica popular brasileira": "MPB",
        "brazilian popular music": "MPB",
        "pagode": "Pagode",
        "forro": "Forró",
        "sertanejo": "Sertanejo",
        "baile funk": "Funk brasileiro",
        "funk carioca": "Funk brasileiro",
        "classical": "Música clássica",
        "classical music": "Música clássica"
    };

    let ultimaConsulta = 0;

    function mostrarMensagem(message) {
        container.replaceChildren();

        const paragraph = document.createElement("p");
        paragraph.className = "profile-empty";
        paragraph.textContent = message;

        container.appendChild(paragraph);
    }

    function nomeNormalizado(name) {
        return String(name || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .toLowerCase()
            .trim();
    }

    function obterLista(data, parent, field) {
        const value = data?.[parent]?.[field];

        if (!value) return [];

        return Array.isArray(value) ? value : [value];
    }

    async function buscarJson(url) {
        const response = await fetch(url, {
            credentials: "same-origin",
            headers: {
                Accept: "application/json"
            }
        });

        if (!response.ok) {
            throw new Error("HTTP " + response.status);
        }

        return response.json();
    }

    async function carregarGeneros() {
        const consultaAtual = ++ultimaConsulta;

        mostrarMensagem("Calculando seus gêneros musicais...");

        try {
            const me = await buscarJson("/users/me");

            if (consultaAtual !== ultimaConsulta) return;

            if (!me.lastFmUsername) {
                mostrarMensagem(
                    "Conecte seu Last.fm para visualizar seus gêneros."
                );
                return;
            }

            const periodo = periodSelect?.value || "7day";

            const ranking = await buscarJson(
                "/last-fm/me/top/artists?period=" +
                encodeURIComponent(periodo)
            );

            if (consultaAtual !== ultimaConsulta) return;

            const artistas = obterLista(
                ranking,
                "topartists",
                "artist"
            )
                .filter(artist =>
                    artist?.name &&
                    Number(artist.playcount) > 0
                )
                .slice(0, 8);

            if (!artistas.length) {
                mostrarMensagem(
                    "Nenhum artista ouvido neste período."
                );
                return;
            }

            // Um artista sem tags ou com falha na consulta
            // nao impede os outros de serem calculados.
            const respostas = await Promise.allSettled(
                artistas.map(artist =>
                    buscarJson(
                        "/last-fm/artist/tags?artist=" +
                        encodeURIComponent(artist.name)
                    )
                )
            );

            if (consultaAtual !== ultimaConsulta) return;

            const pontuacoes = new Map();
            let consultasValidas = 0;

            respostas.forEach((resposta, index) => {

                if (resposta.status !== "fulfilled") {
                    return;
                }

                consultasValidas++;

                const tags = obterLista(
                    resposta.value,
                    "toptags",
                    "tag"
                );

                const reproducoes =
                    Number(artistas[index].playcount);

                // Usamos ate 10 tags por artista.
                const tagsRelevantes = tags.slice(0, 10);

                const maiorContagem = Math.max(
                    1,
                    ...tagsRelevantes.map(tag =>
                        Number(tag.count) || 0
                    )
                );

                const generosDoArtista = new Map();

                tagsRelevantes.forEach(tag => {
                    const chave = nomeNormalizado(tag.name);
                    const genero = genres[chave];

                    if (!genero) return;

                    const contagem = Number(tag.count);

                    if (!Number.isFinite(contagem) ||
                        contagem <= 0) return;

                    const relevancia =
                        contagem / maiorContagem;

                    // Evita contar duas tags sinonimas
                    // do mesmo artista duas vezes.
                    generosDoArtista.set(
                        genero,
                        Math.max(
                            generosDoArtista.get(genero) || 0,
                            relevancia
                        )
                    );
                });

                generosDoArtista.forEach(
                    (relevancia, genero) => {

                        const peso =
                            reproducoes * relevancia;

                        pontuacoes.set(
                            genero,
                            (pontuacoes.get(genero) || 0) +
                            peso
                        );
                    }
                );
            });

            if (!consultasValidas) {
                mostrarMensagem(
                    "Não foi possível consultar os gêneros agora."
                );
                return;
            }

            const resultado = [...pontuacoes.entries()]
                .sort((a, b) => b[1] - a[1])
                .slice(0, 5);

            if (!resultado.length) {
                mostrarMensagem(
                    "Não encontramos gêneros classificados para este período."
                );
                return;
            }

            container.replaceChildren();

            const maiorPontuacao = resultado[0][1];

            resultado.forEach(([genero, pontuacao], index) => {

                const card = document.createElement("div");
                card.className = "weekly-card";

                const line = document.createElement("div");
                line.className = "rank-item";

                const place = document.createElement("h3");
                place.textContent = "#" + (index + 1);

                const title = document.createElement("h3");
                title.textContent = genero;

                const description = document.createElement("p");

                const intensidade = Math.round(
                    pontuacao / maiorPontuacao * 100
                );

                description.textContent =
                    intensidade +
                    "% de afinidade relativa ao primeiro";

                line.append(place, title, description);
                card.appendChild(line);
                container.appendChild(card);
            });

            const note = document.createElement("p");
            note.className = "profile-empty";

            note.textContent =
                "Estimativa baseada nas tags dos " +
                artistas.length +
                " artistas mais ouvidos. " +
                "Os percentuais não representam a divisão " +
                "exata dos seus scrobbles.";

            container.appendChild(note);

        } catch (error) {
            if (consultaAtual !== ultimaConsulta) return;

            console.error(
                "Erro ao calcular gêneros:",
                error
            );

            mostrarMensagem(
                "Não foi possível calcular seus gêneros agora."
            );
        }
    }

    if (periodSelect) {
        periodSelect.addEventListener(
            "change",
            carregarGeneros
        );
    }

    carregarGeneros();
});
