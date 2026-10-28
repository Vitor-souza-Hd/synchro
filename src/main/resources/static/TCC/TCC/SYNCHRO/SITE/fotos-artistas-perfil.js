
document.addEventListener("DOMContentLoaded", () => {

    // =====================================================
    // FOTOS DOS ARTISTAS - WIKIDATA / WIKIMEDIA COMMONS
    // =====================================================

    const container = document.querySelector(".artist");

    if (!container) return;

    const processed = new WeakSet();
    const imageCache = new Map();

    // Avatar neutro quando nao encontramos foto confiavel.
    const avatarSvg = `
        <svg xmlns="http://www.w3.org/2000/svg"
             viewBox="0 0 240 240">
            <rect width="240" height="240" fill="#252525"/>
            <circle cx="120" cy="92" r="42" fill="#777"/>
            <path d="M39 214c0-48 35-76 81-76s81 28 81 76"
                  fill="#777"/>
        </svg>
    `;

    const placeholder =
        "data:image/svg+xml;charset=utf-8," +
        encodeURIComponent(avatarSvg);

    function normalize(text) {
        return String(text || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .replace(/_/g, " ")
            .replace(/\s+/g, " ")
            .toLowerCase()
            .trim();
    }

    // Descricoes de pessoas e grupos musicais.
    // Evitamos termos genericos como "artista" ou "music".
    const musicalTerms = [
        "singer",
        "songwriter",
        "musician",
        "rapper",
        "vocalist",
        "record producer",
        "musical group",
        "music group",
        "musical duo",
        "rock band",
        "pop band",
        "metal band",
        "punk band",
        "indie band",
        "hip hop group",
        "hip-hop group",
        "folk rock band",
        "disc jockey",
        "cantor",
        "cantora",
        "compositor",
        "compositora",
        "musico",
        "musica brasileira",
        "banda",
        "grupo musical",
        "dupla musical",
        "produtor musical",
        "produtora musical"
    ];

    const musicalPattern = new RegExp(
        "(^|[^a-z0-9])(" +
        musicalTerms
            .map(term => normalize(term))
            .map(term =>
                term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
            )
            .join("|") +
        ")([^a-z0-9]|$)",
        "i"
    );

    function hasMusicalDescription(description) {
        return musicalPattern.test(normalize(description));
    }

    function isMatchingName(expected, actual) {
        const a = normalize(expected);
        const b = normalize(actual);

        return b === a ||
            b.startsWith(a + " (");
    }

    async function getJson(url) {
        const response = await fetch(url, {
            headers: {
                Accept: "application/json"
            }
        });

        if (!response.ok) {
            throw new Error("HTTP " + response.status);
        }

        return response.json();
    }

    // =====================================================
    // BUSCAR ENTIDADES NO WIKIDATA
    // =====================================================

    async function searchWikidata(name, language) {
        const url = new URL(
            "https://www.wikidata.org/w/api.php"
        );

        url.searchParams.set("action", "wbsearchentities");
        url.searchParams.set("search", name);
        url.searchParams.set("language", language);
        url.searchParams.set("uselang", language);
        url.searchParams.set("type", "item");
        url.searchParams.set("limit", "10");
        url.searchParams.set("format", "json");
        url.searchParams.set("origin", "*");

        const data = await getJson(url);

        return Array.isArray(data.search)
            ? data.search
            : [];
    }

    // =====================================================
    // CONSULTAR A ENTIDADE E SUA FOTO
    // =====================================================

    async function getWikidataEntity(id) {
        const url = new URL(
            "https://www.wikidata.org/w/api.php"
        );

        url.searchParams.set("action", "wbgetentities");
        url.searchParams.set("ids", id);
        url.searchParams.set(
            "props",
            "labels|descriptions|claims"
        );
        url.searchParams.set("languages", "en|pt");
        url.searchParams.set("format", "json");
        url.searchParams.set("origin", "*");

        const data = await getJson(url);

        return data.entities?.[id] || null;
    }

    function getImageFilename(entity) {
        const claims = entity?.claims?.P18;

        if (!Array.isArray(claims)) return null;

        const claim = claims.find(item =>
            item.rank !== "deprecated" &&
            typeof item.mainsnak?.datavalue?.value ===
            "string"
        );

        return claim?.mainsnak?.datavalue?.value || null;
    }

    function commonsImage(filename) {
        if (!filename || filename.length > 300) return null;

        // Nome de arquivo vindo do Wikidata.
        // Special:FilePath resolve a foto hospedada no Commons.
        return (
            "https://commons.wikimedia.org/wiki/Special:FilePath/" +
            encodeURIComponent(filename) +
            "?width=500"
        );
    }

    function entityDescription(entity) {
        return [
            entity.descriptions?.en?.value,
            entity.descriptions?.pt?.value
        ].filter(Boolean).join(" ");
    }

    function validEntityForArtist(entity, name) {
        const labels = [
            entity.labels?.en?.value,
            entity.labels?.pt?.value
        ].filter(Boolean);

        if (!labels.some(label =>
            isMatchingName(name, label)
        )) {
            return false;
        }

        return hasMusicalDescription(
            entityDescription(entity)
        );
    }

    async function findWikidataPhoto(name) {

        for (const language of ["en", "pt"]) {

            let results;

            try {
                results = await searchWikidata(
                    name,
                    language
                );
            } catch (error) {
                console.warn(
                    "Erro ao buscar artista no Wikidata:",
                    name,
                    error
                );
                continue;
            }

            // Examinamos candidatos com nomes correspondentes
            // e descricoes especificamente musicais.
            const candidates = results.filter(result =>
                isMatchingName(name, result.label) &&
                hasMusicalDescription(result.description)
            );

            for (const candidate of candidates.slice(0, 4)) {

                try {
                    const entity = await getWikidataEntity(
                        candidate.id
                    );

                    if (!entity ||
                        !validEntityForArtist(entity, name)) {
                        continue;
                    }

                    const filename = getImageFilename(entity);

                    if (filename) {
                        return commonsImage(filename);
                    }

                } catch (error) {
                    console.warn(
                        "Erro ao consultar entidade musical:",
                        candidate.id,
                        error
                    );
                }
            }
        }

        return null;
    }

    // =====================================================
    // CACHE DAS FOTOS
    // =====================================================

    async function findPhoto(name) {
        const key = normalize(name);

        if (!imageCache.has(key)) {
            imageCache.set(
                key,
                findWikidataPhoto(name)
                    .catch(error => {
                        console.warn(
                            "Foto indisponivel:",
                            name,
                            error
                        );
                        return null;
                    })
            );
        }

        return imageCache.get(key);
    }

    // =====================================================
    // APLICAR FOTOS NOS CARTOES
    // =====================================================

    function updateArtistCards() {
        const cards = container.querySelectorAll(
            ".artist-card"
        );

        cards.forEach(card => {

            if (processed.has(card)) return;
            processed.add(card);

            const name = card.querySelector("h3")
                ?.textContent?.trim();

            const link = card.querySelector("a");

            if (!name || !link) return;

            let img = card.querySelector("img");

            if (!img) {
                img = document.createElement("img");
                link.prepend(img);
            }

            img.src = placeholder;
            img.alt = "Imagem do artista " + name;
            img.loading = "lazy";
            img.decoding = "async";

            const currentImg = img;

            findPhoto(name).then(url => {

                if (!currentImg.isConnected) return;

                if (!url) {
                    currentImg.src = placeholder;
                    return;
                }

                currentImg.onerror = () => {
                    currentImg.onerror = null;
                    currentImg.src = placeholder;

                    console.info(
                        "Imagem indisponivel no Commons:",
                        name
                    );
                };

                currentImg.src = url;
            });
        });
    }

    // O ranking e carregado de forma assincrona
    // e pode mudar quando o periodo e alterado.
    const observer = new MutationObserver(
        updateArtistCards
    );

    observer.observe(container, {
        childList: true
    });

    updateArtistCards();
});
