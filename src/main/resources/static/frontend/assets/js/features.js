(() => {
    const catalogInteractions = new Map();
    function recordCatalogItem(item) {
        const key = JSON.stringify(item);
        if (catalogInteractions.has(key)) return catalogInteractions.get(key);
        const request = fetch('/catalog/interactions', {
            method: 'POST', keepalive: true,
            headers: {'Content-Type': 'application/json', Accept: 'application/json'},
            body: JSON.stringify(item)
        }).then(async response => {
            if (!response.ok) throw new Error('Não foi possível salvar o item no catálogo.');
            return response.json();
        }).catch(error => { catalogInteractions.delete(key); throw error; });
        catalogInteractions.set(key, request);
        return request;
    }
    function catalogItemFromUrl(url) {
        if (url.origin !== location.origin || url.searchParams.has('id')) return null;
        const page = url.pathname.split('/').pop();
        const artist = url.searchParams.get('artist')?.trim();
        if (['detalhes-musica.html', 'musica.html'].includes(page)) {
            const name = url.searchParams.get('track')?.trim();
            if (name && artist) return {type: 'track', name, artist,
                album: url.searchParams.get('album'), albumArtist: url.searchParams.get('albumArtist')};
        }
        if (['artista.html', 'detalhes-artista.html'].includes(page) && artist) return {type: 'artist', name: artist};
        if (['album-explorar.html', 'detalhes-album.html', 'album.html'].includes(page)) {
            const name = url.searchParams.get('album')?.trim();
            if (name && artist) return {type: 'album', name, artist};
        }
        return null;
    }
    const recordInteraction = item => recordCatalogItem(item).catch(error => console.warn('[Synchro] Catálogo:', error.message));
    window.SynchroCatalog = {record: recordCatalogItem};
    document.addEventListener('click', event => {
        const link = event.target.closest('a[href]');
        if (!link) return;
        const url = new URL(link.href, location.href);
        let item = catalogItemFromUrl(url);
        // Home album cards can open Spotify; retain the selected album as well.
        const card = link.closest('.album-card');
        if (!item && card && url.hostname === 'open.spotify.com') {
            const name = card.querySelector('h3')?.textContent.trim();
            const artist = card.querySelector('p')?.textContent.trim();
            if (name && artist) item = {type: 'album', name, artist};
        }
        if (item) recordInteraction(item);
    }, {capture: true});
    const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    async function bindFavorite(button, data) {
        if(!button)return;
        const key=JSON.stringify(data);
        if(button.dataset.favoriteBinding===key)return;
        button.dataset.favoriteBinding=key;
        let favorite=false;
        const feedback=document.createElement('span');feedback.setAttribute('role','status');
        button.parentElement.querySelector('.favorite-feedback')?.remove();feedback.className='favorite-feedback';button.after(feedback);
        const normalize=s=>String(s||'').trim().toLocaleLowerCase();
        async function payload(){
            if(data.kind==='artists')return {artist:data.artist};
            if(data.kind==='tracks')return {track:data.title,artist:data.artist};
            const response=await fetch('/musicbrainz/album/search?'+new URLSearchParams({artist:data.artist,album:data.title}));
            if(!response.ok)throw new Error('Não foi possível identificar este álbum agora.');
            const result=await response.json();
            const album=result['release-groups']?.find(a=>normalize(a.title)===normalize(data.title)&&a['artist-credit']?.some(c=>normalize(c.artist?.name||c.name)===normalize(data.artist)));
            if(!album)throw new Error('Álbum não encontrado no MusicBrainz.');
            return {releaseGroupId:album.id,album:data.title,artist:data.artist};
        }
        let details;
        function render(){button.setAttribute('aria-pressed',String(favorite));button.textContent=favorite?'♥ Favoritado':'♡ Favoritar';}
        render();
        button.onclick=async()=>{
            button.disabled=true;feedback.textContent='';
            try{
                details??=await payload();
                const query=new URLSearchParams(data.kind==='albums'?{releaseGroupId:details.releaseGroupId}:details);
                const response=await fetch('/favorites/'+data.kind+(favorite?'?'+query:''),{
                    method:favorite?'DELETE':'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},body:favorite?undefined:JSON.stringify(details)
                });
                if(response.status===401)throw new Error('Entre na sua conta para salvar favoritos.');
                if(!response.ok)throw new Error('Não foi possível salvar o favorito. Tente novamente.');
                favorite=!favorite;render();
            }catch(error){feedback.textContent=error.message;}finally{button.disabled=false;}
        };
        try{
            details=await payload();
            const query=new URLSearchParams(data.kind==='albums'?{releaseGroupId:details.releaseGroupId}:details);
            const response=await fetch('/favorites/'+data.kind+'/check?'+query,{credentials:'same-origin'});
            if(response.ok){favorite=(await response.json()).favorite===true;render();}
        }catch{/* The button displays an actionable error if the user tries to save. */}
    }
    window.SynchroFeatures = { escapeHtml, bindFavorite };
    document.addEventListener('DOMContentLoaded', () => {
        const item = catalogItemFromUrl(new URL(location.href));
        if (item) recordInteraction(item);
        const base = '/frontend/pages/';
        const links = [['Início','/index.html'],['Explorar',base+'explorar.html'],['Meu perfil',base+'perfil.html'],['Favoritos',base+'favoritos.html'],['Recentes',base+'recentes.html'],['Playlists',base+'playlists.html'],['Configurações',base+'configuracoes.html']];
        const nav = document.createElement('nav'); nav.className='feature-nav'; nav.setAttribute('aria-label','Biblioteca Synchro');
        for(const [label,href] of links){const a=document.createElement('a');a.textContent=label;a.href=href;if(location.pathname===href)a.setAttribute('aria-current','page');nav.append(a);}
        document.body.prepend(nav);
        const destinations = new Map([['explorar',base+'explorar.html'],['recentes',base+'recentes.html'],['favoritos',base+'favoritos.html'],['avaliações',base+'perfil.html#minhas-avaliacoes'],['suas playlists',base+'playlists.html'],['criar uma nova',base+'playlists.html']]);
        document.querySelectorAll('.sidebar a').forEach(a=>{if(a.hasAttribute('data-create-playlist'))return;const href=destinations.get(a.textContent.trim().toLowerCase());if(href)a.href=href;});
        document.addEventListener('synchro:own-profile', () => {
            const grid = document.querySelector('.user-playlist .playlist');
            if (!grid) return;

            const playlistPage = base + 'playlists.html';
            const status = document.createElement('p');
            status.className = 'profile-empty';
            status.setAttribute('role', 'status');

            function playlistImage(value, className, fallback) {
                const image = document.createElement('img');
                image.className = className;
                image.alt = '';
                image.loading = 'lazy';

                let src;
                if (typeof value === 'string' && value.trim()) {
                    if (/^data:image\/(png|jpeg|webp|gif);base64,/i.test(value)) {
                        src = value;
                    } else {
                        try {
                            const url = new URL(value, location.href);
                            if (['http:', 'https:'].includes(url.protocol)) src = url.href;
                        } catch { /* An invalid URL uses the fallback. */ }
                    }
                }

                image.hidden = !src && !fallback;
                if (!image.hidden) {
                    image.addEventListener('error', () => {
                        if (fallback && image.getAttribute('src') !== fallback) image.src = fallback;
                        else image.hidden = true;
                    });
                    image.src = src || fallback;
                }
                return image;
            }

            function playlistLink(playlist) {
                const link = document.createElement('a');
                link.className = 'feature-detail-link playlist-card';
                link.href = `${playlistPage}?id=${encodeURIComponent(playlist.id)}`;

                const title = document.createElement('h3');
                title.textContent = playlist.titulo || 'Playlist sem título';

                const artwork = document.createElement('div');
                artwork.className = 'profile-playlist-artwork';
                artwork.append(
                    playlistImage(playlist.banner, 'profile-playlist-banner'),
                    playlistImage(playlist.capa, 'profile-playlist-cover', '/frontend/assets/images/catalog-placeholder.svg')
                );
                link.append(artwork, title);
                return link;
            }

            let activeRequest;

            async function loadPlaylists() {
                activeRequest?.abort();
                const request = new AbortController();
                activeRequest = request;
                status.textContent = 'Carregando suas playlists…';
                grid.prepend(status);

                try {
                    const response = await fetch('/playlists/me/summary', {
                        credentials: 'same-origin',
                        cache: 'no-store',
                        headers: { Accept: 'application/json' },
                        signal: request.signal
                    });
                    if (!response.ok) throw new Error(`HTTP ${response.status}`);
                    const playlists = await response.json();
                    if (request.signal.aborted) return;
                    if (!Array.isArray(playlists)) throw new Error('Lista de playlists inválida');

                    const links = playlists.map(playlistLink);
                    grid.replaceChildren(...links);
                    if (!links.length) {
                        status.textContent = 'Você ainda não tem playlists.';
                        grid.prepend(status);
                    }
                } catch {
                    if (request.signal.aborted) return;
                    status.textContent = 'Não foi possível carregar suas playlists. Abra a biblioteca para tentar novamente.';
                }
            }

            document.addEventListener('playlist-saved', loadPlaylists);
            loadPlaylists();
        }, { once: true });
    });
})();
