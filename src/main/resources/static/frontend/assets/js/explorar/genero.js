(() => {
    "use strict";

    const params = new URLSearchParams(window.location.search);
    const genreName = (params.get("name") || "").trim();

    const title = document.getElementById("genreName");
    const artistsGrid = document.getElementById("genreArtists");
    const albumsGrid = document.getElementById("genreAlbums");
    const artistsStatus = document.getElementById("artistsStatus");
    const albumsStatus = document.getElementById("albumsStatus");

    const artistPlaceholder = "/frontend/assets/images/artist-placeholder.svg";
    const albumPlaceholder = "/frontend/assets/images/catalog-placeholder.svg";

    if (!genreName || genreName.length > 100) {
        title.textContent = "Gênero não identificado";
        artistsStatus.textContent = "Volte à página inicial e selecione um gênero.";
        albumsStatus.textContent = "";
        return;
    }

    title.textContent = genreName;
    document.title = `${genreName} - Synchro`;

    function asList(value) {
        if (Array.isArray(value)) return value;
        return value ? [value] : [];
    }

    function getImage(item, fallback) {
        const rawImages = item?.image || item?.photo;
        const imagesList = Array.isArray(rawImages) ? rawImages : [];

        const urls = imagesList.map(img => {
            if (typeof img === 'string') return img;
            return img?.["#text"] || img?.url || "";
        }).filter(url => /^https?:\/\//i.test(url));

        const validImage = urls.reverse().find(url => !url.includes("2a96cbd8b46e442fc41c2b86b821562f") && url.trim() !== "");

        return validImage || fallback;
    }

    async function discovery(method) {
        const url = new URL("/last-fm/discovery", window.location.origin);

        url.searchParams.set("method", method);
        url.searchParams.set("tag", genreName.toLowerCase());

        const response = await fetch(url, {
            headers: { Accept: "application/json" },
            credentials: "same-origin",
            signal: AbortSignal.timeout(15000)
        });

        if (!response.ok) {
            throw new Error(`Consulta indisponível (${response.status}).`);
        }

        const data = await response.json();

        if (data.error) {
            throw new Error(data.message || "Não foi possível carregar os dados.");
        }

        return data;
    }



    function createCard({ name, subtitle, image, href, alt }) {
        const link = document.createElement("a");
        link.className = "genre-media-card";
        link.href = href;

        const media = document.createElement("div");
        media.className = "genre-media-image";

        const cover = document.createElement("img");
        cover.alt = alt || name;
        cover.loading = "lazy";
        cover.decoding = "async";

        if (alt === "Artista" && window.SynchroFavoriteImages) {
            // Aguarda o cartão ser inserido no documento.
            requestAnimationFrame(() => {
                if (!media.isConnected) return;

                window.SynchroFavoriteImages.show(media, {
                    kind: "artist",
                    title: name,
                    artist: name,
                    alt: name,
                    className: "genre-media-photo",
                    preferredImages: [image]
                });
            });
        } else {
            cover.src = image;

            cover.onerror = () => {
                cover.onerror = null;
                cover.src = alt === "Artista"
                    ? artistPlaceholder
                    : albumPlaceholder;
            };

            media.append(cover);
        }

        const info = document.createElement("div");
        info.className = "genre-media-info";

        const heading = document.createElement("h3");
        heading.textContent = name;

        const description = document.createElement("p");
        description.textContent = subtitle;

        info.append(heading, description);
        link.append(media, info);

        return link;
    }



    async function loadArtists() {
        artistsStatus.textContent = "Buscando artistas relacionados...";

        try {
            const data = await discovery("tag.getTopArtists");
            const artists = asList(data.topartists?.artist)
                .filter(artist => artist?.name)
                .slice(0, 6);

            artistsGrid.replaceChildren();

            if (!artists.length) {
                artistsStatus.textContent = "Nenhum artista encontrado para este gênero.";
                return;
            }

            artists.forEach(artist => {
                const identity = artist.catalogId
                    ? { id: artist.catalogId }
                    : { artist: artist.name };

                const href = "/frontend/pages/artista.html?" +
                    new URLSearchParams(identity);

                const card = createCard({
                    name: artist.name,
                    subtitle: "Artista em destaque",
                    image: getImage(artist, artistPlaceholder),
                    href,
                    alt: "Artista"
                });

                artistsGrid.append(card);
            });

            artistsStatus.textContent = `${artists.length} artistas encontrados.`;

        } catch (error) {
            console.error("[Synchro] Erro ao carregar artistas do gênero:", error);
            artistsStatus.textContent = "Não foi possível carregar os artistas agora.";
        }
    }

    async function loadAlbums() {
        albumsStatus.textContent = "Buscando álbuns relacionados...";

        try {
            const data = await discovery("tag.getTopAlbums");
            const albums = asList(data.albums?.album)
                .filter(album => album?.name && album.name !== "(null)")
                .slice(0, 12);

            albumsGrid.replaceChildren();

            if (!albums.length) {
                albumsStatus.textContent = "Nenhum álbum encontrado para este gênero.";
                return;
            }

            albums.forEach(album => {
                const artistName = typeof album.artist === "string"
                    ? album.artist
                    : album.artist?.name || "Artista desconhecido";

                const href = "/frontend/pages/album-explorar.html?" +
                    new URLSearchParams({
                        album: album.name,
                        artist: artistName
                    });

                const card = createCard({
                    name: album.name,
                    subtitle: artistName,
                    image: getImage(album, albumPlaceholder),
                    href,
                    alt: "Álbum"
                });

                albumsGrid.append(card);
            });

            albumsStatus.textContent = `${albums.length} álbuns encontrados.`;

        } catch (error) {
            console.error("[Synchro] Erro ao carregar álbuns do gênero:", error);
            albumsStatus.textContent = "Não foi possível carregar os álbuns agora.";
        }
    }

    async function initialize() {
        await loadArtists();
        await loadAlbums();
    }

    initialize();
})();