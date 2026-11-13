(() => {
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
            signal: AbortSignal.timeout(12000),
            headers: {
                Accept: "application/json"
            }
        });

        if (!response.ok) {
            throw new Error("HTTP " + response.status);
        }

        return response.json();
    }


    async function calcularGeneros(periodo, fetchRanking = buscarRanking) {
        const ranking = await fetchRanking("artists", periodo);
        if (!ranking?.topartists) throw new Error("Ranking de artistas indisponível.");
        const artistas = obterLista(ranking, "topartists", "artist")
            .filter(artist => artist?.name && Number.isFinite(Number(artist.playcount)) && Number(artist.playcount) > 0)
            .slice(0, 8);
        const respostas = await Promise.allSettled(artistas.map(artist =>
            buscarJson("/last-fm/artist/tags?artist=" + encodeURIComponent(artist.name))
                .then(data => {
                    if (!data?.toptags) throw new Error("Tags de artista indisponíveis.");
                    return data;
                })
        ));
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
                const genero = Object.hasOwn(genres, chave) ? genres[chave] : null;

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


        if (artistas.length && !consultasValidas) throw new Error("Tags de artistas indisponíveis.");
        const resultado = [...pontuacoes.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5);
        return {resultado, totalArtistas: artistas.length, parcial: consultasValidas < artistas.length};
    }

    function buscarRanking(tipo, periodo) {
        return buscarJson("/last-fm/me/top/" + tipo + "?" + new URLSearchParams({period: periodo}));
    }

    window.SynchroMusicGenres = {calculate: calcularGeneros};
})();
