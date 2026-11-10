const params = new URLSearchParams(window.location.search);
const artistNameFromUrl = (params.get('artist') || '').trim();
let artistName = artistNameFromUrl;
let artist = null;
let historyRecorded = false;
let popularLoaded = false;
const artistPhotoRequests = new Map();
const placeholder = '../assets/images/artist-placeholder.svg';
const artistImages = {
    'bad bunny': '../assets/images/badbunny.jpg', 'rihanna': '../assets/images/rihanna.jpg',
    'justin bieber': '../assets/images/justin.jpg', 'billie eilish': '../assets/images/billieeilish.jpg',
    'kendrick lamar': '../assets/images/kendricklamar.jpg', 'the weeknd': '../assets/images/theweeknd.jpg'
};
const artistStatus = document.createElement('p');
artistStatus.setAttribute('role', 'status');
artistStatus.className = 'artist-status';
document.querySelector('.artist-header').before(artistStatus);
document.querySelector('.menu-toggle')?.addEventListener('click', () => document.querySelector('.sidebar')?.classList.toggle('active'));

function setArtistLoading(loading) {
    document.querySelector('.artist-header').setAttribute('aria-busy', String(loading));
    document.querySelectorAll('.artist-section').forEach(section => {
        if (section.dataset.popular !== 'true') section.hidden = loading;
    });
    document.getElementById('favoriteBtn').disabled = loading;
    document.querySelectorAll('.open-review').forEach(button => { button.disabled = loading; });
}

async function loadCatalogArtist() {
    const id = params.get('id');
    setArtistLoading(true);
    document.getElementById('artistName').textContent = 'Carregando...';
    document.getElementById('artistImage').src = placeholder;
    try {
        if (!id && !artistNameFromUrl) throw new Error('Selecione um artista nos resultados da pesquisa.');
        if (id !== null && !/^\d+$/.test(id)) throw new Error('Identificador de artista inválido.');
        artistStatus.textContent = 'Carregando informações do artista…';
        if (!id && window.SynchroCatalog) await window.SynchroCatalog.record({type: 'artist', name: artistNameFromUrl});
        const endpoint = id ? `/catalog/artists/${encodeURIComponent(id)}`
            : `/catalog/artists/by-name?name=${encodeURIComponent(artistNameFromUrl)}`;
        const response = await fetch(endpoint, {headers: {Accept: 'application/json'}, cache: 'no-store'});
        if (!response.ok) throw new Error(response.status === 404 ? 'Artista não encontrado' : 'Não foi possível carregar o artista.');
        renderCatalogArtist(await response.json());
        refreshCatalogArtist(artist.id, endpoint);
        loadPopularArtistData();
    } catch (error) {
        document.querySelector('.artist-header').setAttribute('aria-busy', 'false');
        document.getElementById('artistName').textContent = error.message;
        document.getElementById('artistLastFmLink').hidden = true;
        artistStatus.textContent = error.message === 'Artista não encontrado'
            ? 'Este artista não está disponível. Volte à pesquisa e selecione outro resultado.' : error.message;
    }
}

async function refreshCatalogArtist(id, endpoint) {
    try {
        const refresh = await fetch(`/catalog/artists/${encodeURIComponent(id)}/refresh`, {method: 'POST'});
        if (!refresh.ok) return;
        const response = await fetch(endpoint, {headers: {Accept: 'application/json'}, cache: 'no-store'});
        if (response.ok) renderCatalogArtist(await response.json());
    } catch { /* Saved content remains usable while external data is unavailable. */ }
}

function plainText(value) {
    const doc = new DOMParser().parseFromString(value || '', 'text/html');
    doc.querySelectorAll('script, style, iframe').forEach(node => node.remove());
    return (doc.body.textContent || '').trim();
}

function usablePhoto(url) {
    return typeof url === 'string' && /^(https?:\/\/|\/frontend\/|\.\.\/assets\/)/i.test(url)
        && !/placeholder\.svg|2a96cbd8b46e442fc41c2b86b821562f|placehold\.co/.test(url);
}

function renderCatalogArtist(data) {
    const saved = data.artist;
    const previousPhoto = artist?.name === saved.nome && usablePhoto(artist.image) ? artist.image : null;
    artistName = saved.nome;
    artist = {id: saved.id, name: saved.nome, lastFmUrl: saved.lastFm?.url,
        image: usablePhoto(saved.imgUrl) ? saved.imgUrl : previousPhoto || artistImages[saved.nome.toLowerCase()] || placeholder,
        bio: plainText(saved.biografia) || 'Biografia não disponível no catálogo.', genre: saved.lastFm?.tags || []};
    document.title = `SYNCHRO - ${artistName}`;
    document.getElementById('artistName').textContent = artistName;
    document.getElementById('biography').textContent = artist.bio;
    artistLabel.textContent = artistName;
    const tags = document.getElementById('artistTags');
    tags.replaceChildren();
    artist.genre.slice(0, 6).forEach(name => { const tag = document.createElement('span'); tag.textContent = name; tags.append(tag); });
    const image = document.getElementById('artistImage');
    image.alt = 'Imagem de ' + artistName;
    image.onerror = () => { image.onerror = null; image.src = placeholder; loadCatalogArtistPhoto(true); };
    image.src = artist.image;
    updateLastFmLink();
    renderStatistics(saved.lastFm || {});
    renderCatalogTracks(data.musicas || []);
    renderCatalogAlbums(data.albums || []);
    setArtistLoading(false);
    artistStatus.textContent = '';
    window.SynchroFeatures.bindFavorite(document.getElementById('favoriteBtn'), {kind: 'artists', artist: artistName});
    if (!usablePhoto(artist.image)) loadCatalogArtistPhoto();
    if (!historyRecorded) {
        historyRecorded = true;
        document.dispatchEvent?.(new CustomEvent('synchro:artist', {detail: {id: saved.id, name: saved.nome}}));
    }
}

function updateLastFmLink() {
    const link = document.getElementById('artistLastFmLink');
    let url = 'https://www.last.fm/music/' + encodeURIComponent(artistName);
    try {
        const provided = new URL(artist.lastFmUrl);
        if (provided.protocol === 'https:' && ['last.fm', 'www.last.fm'].includes(provided.hostname)
            && !provided.username && !provided.password && provided.pathname.startsWith('/music/')) url = provided.href;
    } catch { /* Use the artist name if the provider has not returned a URL. */ }
    link.href = url; link.hidden = false;
}

function renderStatistics(meta) {
    const number = value => value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))
        && Number(value) >= 0 ? Number(value).toLocaleString('pt-BR') : null;
    const listeners = number(meta.listeners), plays = number(meta.playcount);
    const stats = document.getElementById('artistStatistics');
    stats.textContent = [listeners === null ? null : listeners + ' ouvintes', plays === null ? null : plays + ' reproduções'].filter(Boolean).join(' · ');
    stats.hidden = listeners === null && plays === null;
}

function loadCatalogArtistPhoto(refresh = false) {
    const current = artist;
    const key = current.id + ':' + refresh;
    if (!artistPhotoRequests.has(key)) artistPhotoRequests.set(key,
        fetch('/catalog/artists/' + encodeURIComponent(current.id) + '/photo' + (refresh ? '?refresh=true' : ''))
            .then(response => response.ok ? response.json() : null).catch(() => null));
    artistPhotoRequests.get(key).then(photo => {
        if (artist.id !== current.id || photo?.status !== 'found' || !usablePhoto(photo.imgUrl)) return;
        artist.image = photo.imgUrl;
        const image = document.getElementById('artistImage');
        image.onerror = () => { image.onerror = null; image.src = placeholder; };
        image.src = photo.imgUrl;
    });
}

function mediaLink(href, name, className) {
    const link = document.createElement('a'); link.href = href; link.className = className;
    link.textContent = name; return link;
}

function renderCatalogTracks(tracks) {
    const container = document.getElementById('tracks'); container.replaceChildren();
    tracks.forEach((track, index) => container.append(mediaLink('musica.html?id=' + encodeURIComponent(track.id),
        `${String(index + 1).padStart(2, '0')} · ${track.titulo}`, 'track catalog-link')));
    if (!tracks.length) container.textContent = 'Nenhuma música cadastrada para este artista.';
}

function albumCard(href, title, image) {
    const link = document.createElement('a'); link.href = href; link.className = 'album-card catalog-link';
    const cover = document.createElement('img');
    cover.loading = 'lazy';
    cover.decoding = 'async';
    cover.alt = 'Capa de ' + title;
    cover.src = usablePhoto(image) ? image : '../assets/images/catalog-placeholder.svg';
    cover.onerror = () => { cover.onerror = null; cover.src = '../assets/images/catalog-placeholder.svg'; };
    const name = document.createElement('h3'); name.textContent = title;
    link.append(cover, name); return link;
}

function renderCatalogAlbums(albums) {
    const container = document.getElementById('albums'); container.replaceChildren();
    albums.forEach(album => container.append(albumCard('album.html?id=' + encodeURIComponent(album.id), album.titulo, album.lastFm?.imageUrl)));
    if (!albums.length) container.textContent = 'Nenhum álbum cadastrado para este artista.';
}

async function discovery(method) {
    const url = new URL('/last-fm/discovery', window.location.origin);
    url.searchParams.set('method', method); url.searchParams.set('artist', artistName); url.searchParams.set('autocorrect', '1');
    const response = await fetch(url.href, {headers: {Accept: 'application/json'}});
    if (!response.ok) throw new Error('Consulta indisponível');
    const data = await response.json(); if (data.error) throw new Error('Consulta indisponível'); return data;
}

function items(value) { return Array.isArray(value) ? value : value ? [value] : []; }

function loadPopularArtistData() {
    if (popularLoaded) return;
    popularLoaded = true;
    discovery('artist.gettoptracks').then(data => {
        const container = document.getElementById('artistTopTracks'); container.replaceChildren();
        const seen = new Set();
        items(data.toptracks?.track).filter(track => track?.name?.trim()).filter(track => {
            const key = track.name.trim().toLowerCase(); if (seen.has(key)) return false; seen.add(key); return true;
        }).slice(0, 10).forEach((track, index) => {
            const name = typeof track.artist === 'string' ? track.artist : track.artist?.name || artistName;
            container.append(mediaLink('musica.html?' + new URLSearchParams({track: track.name, artist: name}),
                `${String(index + 1).padStart(2, '0')} · ${track.name}`, 'track catalog-link'));
        });
        document.getElementById('artistTopTracksSection').hidden = container.childElementCount === 0;
    }).catch(() => {});
    discovery('artist.gettopalbums').then(data => {
        const container = document.getElementById('artistTopAlbums'); container.replaceChildren();
        const seen = new Set();
        items(data.topalbums?.album).filter(album => album?.name?.trim() && album.name !== '(null)').filter(album => {
            const key = album.name.trim().toLowerCase(); if (seen.has(key)) return false; seen.add(key); return true;
        }).slice(0, 6).forEach(album => {
            const name = typeof album.artist === 'string' ? album.artist : album.artist?.name || artistName;
            const image = items(album.image).map(image => image['#text']).filter(usablePhoto).pop();
            container.append(albumCard('album-explorar.html?' + new URLSearchParams({album: album.name, artist: name}), album.name, image));
        });
        document.getElementById('artistTopAlbumsSection').hidden = container.childElementCount === 0;
    }).catch(() => {});
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', loadCatalogArtist);
else queueMicrotask(loadCatalogArtist);


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
