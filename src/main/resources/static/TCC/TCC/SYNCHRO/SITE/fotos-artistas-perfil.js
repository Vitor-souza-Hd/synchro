
document.addEventListener("DOMContentLoaded", () => {

    const container = document.querySelector(".artist");

    if (!container) return;

    const processed = new WeakSet();
    const cache = new Map();

    const placeholderSvg = `
        <svg xmlns="http://www.w3.org/2000/svg"
             width="240" height="240"
             viewBox="0 0 240 240">
            <rect width="240" height="240" fill="#252525"/>
            <circle cx="120" cy="92" r="42" fill="#777"/>
            <path d="M39 214c0-48 35-76 81-76
                     s81 28 81 76" fill="#777"/>
        </svg>
    `;

    const placeholder =
        "data:image/svg+xml;charset=utf-8," +
        encodeURIComponent(placeholderSvg);

    function normalize(name) {
        return String(name || "")
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .replace(/_/g, " ")
            .toLowerCase()
            .replace(/\s+/g, " ")
            .trim();
    }

    function validImage(url) {
        try {
            const parsed = new URL(url);

            return parsed.protocol === "https:" &&
                parsed.hostname === "upload.wikimedia.org";
        } catch {
            return false;
        }
    }


    async function wikipediaImage(name, language) {

        const url = new URL(
            `https://${language}.wikipedia.org/w/api.php`
        );

        url.searchParams.set("action", "query");
        url.searchParams.set("titles", name);
        url.searchParams.set("prop", "pageimages|extracts");
        url.searchParams.set("piprop", "thumbnail");
        url.searchParams.set("pithumbsize", "500");
        url.searchParams.set("exintro", "1");
        url.searchParams.set("explaintext", "1");
        url.searchParams.set("exsentences", "3");
        url.searchParams.set("format", "json");
        url.searchParams.set("formatversion", "2");
        url.searchParams.set("redirects", "1");
        url.searchParams.set("origin", "*");

        const response = await fetch(url);

        if (!response.ok) {
            throw new Error(
                `Wikipedia HTTP ${response.status}`
            );
        }

        const data = await response.json();
        const pages = data?.query?.pages || [];

        // Termos que indicam que a pagina
        // descreve uma pessoa ou grupo musical.
        const musicalTerms = [
            "singer",
            "musician",
            "rapper",
            "record producer",
            "songwriter",
            "musical group",
            "music group",
            "rock band",
            "pop band",
            "hip hop group",
            "vocalist",
            "disc jockey",
            "dj",
            "cantor",
            "cantora",
            "músico",
            "música",
            "banda",
            "grupo musical",
            "compositor",
            "compositora",
            "rapper",
            "produtor musical",
            "produtora musical",
            "dupla musical",
            "artista musical"
        ];

        const expected = normalize(name);

        for (const page of pages) {

            if (page.missing) continue;

            const image = page.thumbnail?.source;

            if (!validImage(image)) continue;

            const title = normalize(page.title);

            // Confirma correspondencia do nome.
            if (
                title !== expected &&
                !title.startsWith(expected + " (")
            ) {
                continue;
            }

            const description = normalize(
                page.extract || ""
            );

            // Nao aceita paginas sem descricao suficiente
            // para confirmar que se trata de musica.
            if (!description) continue;

            const isMusical = musicalTerms.some(term => {

                const normalizedTerm = normalize(term);

                // Evita falsos positivos com palavras curtas,
                // como "dj", dentro de outras palavras.
                const escaped = normalizedTerm.replace(
                    /[.*+?^${}()|[\]\\]/g,
                    "\\$&"
                );

                const regex = new RegExp(
                    `(^|[^a-z0-9])${escaped}([^a-z0-9]|$)`,
                    "i"
                );

                return regex.test(description);
            });

            if (!isMusical) {
                console.info(
                    "Imagem descartada: página não confirmada como musical.",
                    name,
                    page.title
                );

                continue;
            }

            return image;
        }

        return null;
    }


    async function findImage(name) {
        if (cache.has(name)) {
            return cache.get(name);
        }

        const pending = (async () => {
            for (const language of ["pt", "en"]) {
                try {
                    const url = await wikipediaImage(
                        name,
                        language
                    );

                    if (url) return url;
                } catch (error) {
                    console.warn(
                        `Foto indisponível (${language}):`,
                        name,
                        error
                    );
                }
            }

            return null;
        })();

        cache.set(name, pending);
        return pending;
    }

    function checkCards() {
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
            img.alt = "Artista: " + name;
            img.loading = "lazy";

            const target = img;

            findImage(name).then(url => {
                if (!target.isConnected) return;

                if (url) {
                    target.onerror = () => {
                        target.onerror = null;
                        target.src = placeholder;
                    };

                    target.src = url;
                }
            });
        });
    }

    // O user3.js carrega rankings de forma assincrona.
    // O observador identifica os cards novos.
    const observer = new MutationObserver(checkCards);

    observer.observe(container, {
        childList: true
    });

    checkCards();
});
