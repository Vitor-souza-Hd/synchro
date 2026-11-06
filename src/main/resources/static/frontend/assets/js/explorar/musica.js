
const API_URL = (window.location.origin + "/last-fm/discovery");

document.addEventListener("synchro:song", ({detail: {params, song}}) => {

    // =========================================
    // IDENTIFICAR MÚSICA E ARTISTA
    // =========================================

    

    const trackName = params.get("track")?.trim();
    const artistName = params.get("artist")?.trim();
    const sourceAlbum = params.get("album")?.trim();
    const sourceAlbumArtist = params.get("albumArtist")?.trim();
    let trackInfoPromise;
    let coverLookupPromise;

    function consultarFaixa() {
        return trackInfoPromise ||= consultarLastFm("track.getInfo", {
            artist: artistName, track: trackName
        });
    }

    function imagensLastFm(node) {
        const images = Array.isArray(node?.image) ? node.image : [];
        return images.slice().reverse().map(image => image['#text']).filter(url =>
            typeof url === 'string' && /^https?:\/\//i.test(url) &&
            !url.includes('2a96cbd8b46e442fc41c2b86b821562f'));
    }

    async function procurarCapas() {
        if (coverLookupPromise) return coverLookupPromise;
        coverLookupPromise = (async () => {
            // MusicBrainz identifica o lançamento; o Cover Art Archive fornece a capa.
            if (await carregarAlbumMusicBrainz()) return true;
            let album;
            try {
                album = (await consultarFaixa()).track?.album;
                for (const url of imagensLastFm(album)) {
                    if (await exibirImagem(url)) return true;
                }
            } catch (error) { console.warn('Capa da faixa no Last.fm:', error); }
            const names = [...new Set([sourceAlbum, album?.title].filter(Boolean))];
            for (const name of names) {
                try {
                    const data = await consultarLastFm('album.getInfo', {
                        artist: (name === sourceAlbum ? sourceAlbumArtist :
                            (typeof album?.artist === 'string' ? album.artist : album?.artist?.name)) || artistName,
                        album: name
                    });
                    for (const url of imagensLastFm(data.album)) {
                        if (await exibirImagem(url)) return true;
                    }
                } catch (error) { console.warn('Capa do álbum no Last.fm:', error); }
            }
            return false;
        })();
        return coverLookupPromise;
    }

    const musicNameElement =
        document.getElementById("musicName");

    const biographyElement =
        document.getElementById("musicBiography");

    const albumElement =
        document.getElementById("musicAlbum");

    // =========================================
    // MENU RESPONSIVO
    // =========================================

    const menuToggle = document.querySelector(".menu-toggle");
    const sidebar = document.querySelector(".sidebar");

    if (menuToggle && sidebar) {
        menuToggle.addEventListener("click", () => {
            sidebar.classList.toggle("active");
        });
    }

    // =========================================
    // VALIDAR PARÂMETROS
    // =========================================

    if (song) {
        renderCatalogSong(song);
        // Mostra primeiro o catálogo local. A atualização externa pode falhar ou demorar.
        atualizarCatalogo(song.id);
        return;
    }
    if (!trackName || !artistName) {

        musicNameElement.textContent =
            "Música não encontrada";

        biographyElement.textContent =
            "Informe uma música e um artista para visualizar os detalhes.";

        return;
    }

    // Dados do Last.fm e identificação do álbum
    // são carregados de maneira independente.
    carregarMusica();
    procurarCapas();

    // =========================================
    // CONSULTAR LAST.FM
    // =========================================

    function renderCatalogSong(item) {
        musicNameElement.textContent = item.titulo;
        musicNameElement.dataset.songId = String(item.id);
        document.title = item.titulo + ' | Synchro';
        const artists = document.getElementById('musicArtist');
        artists.textContent = artistName || 'Artista não informado';
        if (artistName) artists.href = 'detalhes-artista.html?artist=' + encodeURIComponent(artistName);
        else artists.removeAttribute('href');
        if (item.album) {
            const link = document.createElement('a');
            link.href = 'album.html?id=' + encodeURIComponent(item.album.id);
            link.textContent = item.album.titulo;
            albumElement.replaceChildren(link);
        } else albumElement.textContent = 'Álbum não informado';
        const meta = item.lastFm || {};
        document.getElementById('musicListeners').textContent = formatarNumero(meta.listeners);
        document.getElementById('musicPlaycount').textContent = formatarNumero(meta.playcount);
        const duration = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?$/.exec(item.duracao || '');
        document.getElementById('musicDuration').textContent = duration ? formatarDuracao(
            1000 * (Number(duration[1] || 0)*3600 + Number(duration[2] || 0)*60 + Number(duration[3] || 0))) : 'Não informada';
        const tags = meta.tags?.length ? meta.tags : (item.genero || '').split(',').map(tag=>tag.trim()).filter(Boolean);
        carregarTags(tags.map(name=>({name})));
        biographyElement.textContent = limparTexto(item.descricao) || 'Descrição não disponível no catálogo.';
        const lastfm = document.getElementById('lastfmLink');
        if (urlLastFmValida(meta.url)) { lastfm.href = meta.url; lastfm.hidden = false; }
    }

    async function atualizarCatalogo(id) {
        let current = song;
        let initialCover = false;
        for (const image of [song.lastFm?.imageUrl, song.album?.imgUrl].filter(Boolean)) {
            if (await exibirImagem(image)) { initialCover = true; break; }
        }
        try {
            const path = '/catalog/songs/' + encodeURIComponent(id);
            if ((await fetch(path + '/refresh', {method: 'POST'})).ok) {
                const response = await fetch(path);
                if (response.ok) {
                    current = await response.json();
                    renderCatalogSong(current);
                }
            }
        } catch { /* Os dados já apresentados continuam disponíveis. */ }
        if (initialCover) return;
        const images = [...new Set([
            current.lastFm?.imageUrl, current.album?.imgUrl
        ].filter(Boolean))];
        for (const image of images) {
            if (await exibirImagem(image)) return;
        }
        if (artistName) await procurarCapas();
    }

    async function consultarLastFm(method, parametros = {}) {

        const url = new URL(API_URL);

        url.searchParams.set("method", method);
        url.searchParams.set("format", "json");
        url.searchParams.set("autocorrect", "1");

        Object.entries(parametros).forEach(([chave, valor]) => {
            url.searchParams.set(chave, valor);
        });

        const response = await fetch(url);

        if (!response.ok) {
            throw new Error(`Erro HTTP ${response.status}`);
        }

        const data = await response.json();

        if (data.error) {
            throw new Error(
                data.message || "Erro na API do Last.fm"
            );
        }

        return data;
    }

    // =========================================
    // CONSULTAR ENDPOINT DO SYNCHRO
    // =========================================

    async function consultarSynchro(caminho, parametros = {}) {

        const url = new URL(caminho, window.location.origin);

        Object.entries(parametros).forEach(([chave, valor]) => {
            url.searchParams.set(chave, valor);
        });

        const response = await fetch(url, {
            headers: {
                "Accept": "application/json"
            }
        });

        if (!response.ok) {
            throw new Error(
                `Erro ao consultar ${caminho}: HTTP ${response.status}`
            );
        }

        return response.json();
    }

    // =========================================
    // CARREGAR INFORMAÇÕES DO LAST.FM
    // =========================================

    async function carregarMusica() {

        try {

            const data = await consultarFaixa();

            const track = data.track;

            if (!track) {
                throw new Error("Música não encontrada");
            }

            const nome = track.name || trackName;

            const artista =
                typeof track.artist === "string"
                    ? track.artist
                    : track.artist?.name || artistName;

            // TÍTULO
            musicNameElement.textContent = nome;

            document.title = `${nome} - Synchro`;

            // ARTISTA
            const artistElement =
                document.getElementById("musicArtist");

            artistElement.textContent = artista;

            artistElement.href =
                `/frontend/pages/detalhes-artista.html?artist=${encodeURIComponent(artista)}`;

            // ESTATÍSTICAS
            document.getElementById("musicListeners").textContent =
                formatarNumero(track.listeners);

            document.getElementById("musicPlaycount").textContent =
                formatarNumero(track.playcount);

            document.getElementById("musicDuration").textContent =
                formatarDuracao(track.duration);

            // GÊNEROS
            carregarTags(track.toptags?.tag);

            // DESCRIÇÃO
            biographyElement.textContent =
                limparTexto(track.wiki?.summary) ||
                "O Last.fm ainda não disponibilizou uma descrição para esta música.";

            // LINK DO LAST.FM
            const lastfmLink =
                document.getElementById("lastfmLink");

            if (urlLastFmValida(track.url)) {
                lastfmLink.href = track.url;
                lastfmLink.hidden = false;
            }

        } catch (error) {

            console.error(
                "Erro ao carregar música pelo Last.fm:",
                error
            );

            musicNameElement.textContent = trackName;
            document.getElementById("musicArtist").textContent = artistName;

            biographyElement.textContent =
                "Não foi possível carregar as informações da música. Verifique o nome e tente novamente.";
        }
    }

    // =========================================
    // IDENTIFICAR ÁLBUM PELO MUSICBRAINZ
    // =========================================



    async function carregarAlbumMusicBrainz() {

        albumElement.textContent = "Identificando álbum...";
        limparImagem();

        function normalize(value) {
            return String(value || "")
                .normalize("NFD")
                .replace(/[\u0300-\u036f]/g, "")
                .toLowerCase()
                .replace(/\s+/g, " ")
                .trim();
        }

        // Identifica um album pelo nome e artista,
        // exigindo correspondencia com o MusicBrainz.
        async function buscarAlbumPorNome(
            nome,
            artista,
            tipo = "Album"
        ) {

            if (!nome || !artista) {
                return null;
            }

            const isSingle = tipo === "Single";

            const data = await consultarSynchro(
                isSingle
                    ? "/musicbrainz/single/search"
                    : "/musicbrainz/album/search",
                isSingle
                    ? {
                        single: nome,
                        artist: artista
                    }
                    : {
                        album: nome,
                        artist: artista
                    }
            );

            const groups = data["release-groups"];

            if (!Array.isArray(groups)) {
                return null;
            }

            const match = groups.find(item => {
                if (!item?.id) return false;

                const tituloCorreto =
                    normalize(item.title) === normalize(nome);

                const albumPrincipal =
                    item["primary-type"] === "Album";

                const tipoCorreto =
                    item["primary-type"] === tipo;

                const semTipoSecundario =
                    tipo === "Single" ||
                    !Array.isArray(item["secondary-types"]) ||
                    item["secondary-types"].length === 0;

                const creditos = item["artist-credit"];

                const artistaCorreto =
                    !Array.isArray(creditos) ||
                    creditos.some(credito => {
                        const nomeCredito =
                            credito.artist?.name ||
                            credito.name ||
                            "";

                        return normalize(nomeCredito) ===
                            normalize(artista);
                    });

                return tituloCorreto &&
                    tipoCorreto &&
                    semTipoSecundario &&
                    artistaCorreto;
            });

            if (!match) {
                return null;
            }

            return {
                title: match.title,
                releaseGroupId: match.id,
                type: tipo
            };
        }

        // Alternativa: o Last.fm pode informar
        // diretamente o album associado a faixa.
        async function buscarAlbumPeloLastFm() {

            const data = await consultarFaixa();

            const album = data.track?.album;

            if (!album?.title) {
                return null;
            }

            const artistaAlbum =
                typeof album.artist === "string"
                    ? album.artist
                    : album.artist?.name || artistName;

            return buscarAlbumPorNome(
                album.title,
                artistaAlbum
            );
        }

        try {
            let identifiedAlbum = null;

            // 1. Priorizar o album de origem.
            // Isso e importante quando a faixa pertence
            // a diferentes lancamentos.

            if (sourceAlbum) {
                try {
                    identifiedAlbum = await buscarAlbumPorNome(
                        sourceAlbum,
                        sourceAlbumArtist || artistName
                    );
                } catch (error) {
                    console.warn(
                        "[Synchro] Album de origem:",
                        error
                    );
                }
            }

            // 2. Identificacao habitual pela musica.

            if (!identifiedAlbum) {
                try {
                    const result = await consultarSynchro(
                        "/musicbrainz/track/album",
                        {
                            artist: artistName,
                            track: trackName
                        }
                    );

                    if (
                        result.status === "found" &&
                        result.album?.releaseGroupId &&
                        result.album?.title
                    ) {
                        identifiedAlbum = result.album;
                    }
                } catch (error) {
                    console.warn(
                        "[Synchro] Busca pela faixa:",
                        error
                    );
                }
            }

            // 3. Alternativa: consultar o Last.fm
            // e confirmar o album no MusicBrainz.

            if (!identifiedAlbum) {
                try {
                    identifiedAlbum =
                        await buscarAlbumPeloLastFm();

                } catch (error) {
                    console.warn(
                        "[Synchro] Busca pelo Last.fm:",
                        error
                    );
                }
            }

            // 4. Procurar um single oficial.
            // So tenta depois das buscas por album.

            if (!identifiedAlbum) {
                try {
                    identifiedAlbum =
                        await buscarAlbumPorNome(
                            trackName,
                            artistName,
                            "Single"
                        );

                    if (identifiedAlbum) {
                        console.info(
                            "[Synchro] Single identificado:",
                            identifiedAlbum.title
                        );
                    }

                } catch (error) {
                    console.warn(
                        "[Synchro] Busca pelo single:",
                        error
                    );
                }
            }

            // Nenhuma fonte identificou um album valido.

            if (!identifiedAlbum) {
                albumElement.textContent =
                    "Lançamento não identificado";

                console.info(
                    "[Synchro] Nenhum álbum confirmado para:",
                    artistName,
                    "-",
                    trackName
                );

                return false;
            }

            // Exibe o album identificado.

            albumElement.textContent =
                identifiedAlbum.type === "Single"
                    ? "Single"
                    : `Álbum associado: ${identifiedAlbum.title}`;

            // Utiliza o carregamento de capa existente.

            return await carregarCapaCoverArtArchive(
                identifiedAlbum.releaseGroupId
            );

        } catch (error) {

            console.warn(
                "[Synchro] Erro ao carregar álbum:",
                error
            );

            albumElement.textContent =
                "Álbum indisponível no momento";
            return false;
        }
    }



    // =========================================
    // BUSCAR CAPA PELO BACKEND
    // =========================================

    async function carregarCapaCoverArtArchive(releaseGroupId) {

        try {

            const response = await fetch(
                `/cover-art/release-group/${encodeURIComponent(releaseGroupId)}`,
                {
                    headers: {
                        "Accept": "application/json"
                    }
                }
            );

            if (!response.ok) {
                throw new Error(
                    `Erro ao buscar capa: HTTP ${response.status}`
                );
            }

            const resultado = await response.json();

            if (
                resultado.status === "found" &&
                urlCapaValida(resultado.coverUrl)
            ) {

                return await exibirImagem(resultado.coverUrl);

            } else {

                console.info(
                    "Capa não disponível para:",
                    releaseGroupId
                );
            }

        } catch (error) {

            console.warn(
                "Não foi possível carregar a capa:",
                error
            );
        }
        return false;
    }

    // =========================================
    // VALIDAR URL DA CAPA
    // =========================================

    function urlCapaValida(url) {

        if (typeof url !== "string") {
            return false;
        }

        try {

            const endereco = new URL(url);

            return (
                endereco.protocol === "https:" &&
                (
                    endereco.hostname === "coverartarchive.org" ||
                    endereco.hostname === "archive.org" ||
                    endereco.hostname.endsWith(".archive.org")
                )
            );

        } catch {
            return false;
        }
    }

    // =========================================
    // LIMPAR IMAGEM
    // =========================================

    function limparImagem() {

        const img =
            document.getElementById("musicImage");

        const placeholder =
            document.getElementById("musicPlaceholder");

        img.hidden = true;
        img.removeAttribute("src");

        placeholder.hidden = false;
    }

    // =========================================
    // EXIBIR CAPA DO ÁLBUM
    // =========================================

    function exibirImagem(url) {

        if (typeof url !== 'string' || !/^https?:\/\//i.test(url) ||
            url.includes('2a96cbd8b46e442fc41c2b86b821562f')) return Promise.resolve(false);
        return new Promise(resolve => {

        const img =
            document.getElementById("musicImage");

        const placeholder =
            document.getElementById("musicPlaceholder");

        img.onload = () => {

            img.hidden = false;
            placeholder.hidden = true;
            resolve(true);
        };

        img.onerror = () => {

            img.hidden = true;
            placeholder.hidden = false;

            console.warn(
                "O navegador não conseguiu carregar a capa:",
                url
            );
            resolve(false);
        };

        img.src = url;
        });
    }

    // =========================================
    // GÊNEROS MUSICAIS
    // =========================================

    function carregarTags(tags) {

        const container =
            document.getElementById("musicTags");

        container.replaceChildren();

        const lista = Array.isArray(tags)
            ? tags
            : tags ? [tags] : [];

        lista.slice(0, 5).forEach(tag => {

            if (!tag.name) {
                return;
            }

            const span = document.createElement("span");

            span.textContent = tag.name;

            container.appendChild(span);
        });
    }

    // =========================================
    // FORMATAR NÚMEROS
    // =========================================

    function formatarNumero(numero) {

        if (
            numero === undefined ||
            numero === null ||
            numero === ""
        ) {
            return "—";
        }

        const valor = Number(numero);

        if (!Number.isFinite(valor)) {
            return "—";
        }

        return valor.toLocaleString("pt-BR");
    }

    // =========================================
    // FORMATAR DURAÇÃO
    // =========================================

    function formatarDuracao(milissegundos) {

        const duracao = Number(milissegundos);

        if (!Number.isFinite(duracao) || duracao <= 0) {
            return "Não informada";
        }

        const segundos = Math.floor(duracao / 1000);

        const minutos = Math.floor(segundos / 60);
        const resto = segundos % 60;

        return `${minutos}:${String(resto).padStart(2, "0")}`;
    }

    // =========================================
    // LIMPAR TEXTO DA BIOGRAFIA
    // =========================================

    function limparTexto(texto) {

        if (!texto) {
            return "";
        }

        const elemento = new DOMParser().parseFromString(texto, "text/html").body;

        elemento.querySelectorAll("script, style").forEach(
            item => item.remove()
        );

        return (elemento.textContent || "").trim();
    }

    // =========================================
    // VALIDAR LINK DO LAST.FM
    // =========================================

    function urlLastFmValida(url) {

        if (typeof url !== "string") {
            return false;
        }

        try {

            const endereco = new URL(url);

            return (
                endereco.protocol === "https:" &&
                (
                    endereco.hostname === "last.fm" ||
                    endereco.hostname.endsWith(".last.fm")
                )
            );

        } catch {
            return false;
        }
    }

});
