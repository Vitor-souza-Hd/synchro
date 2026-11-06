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
        // Existing catalog links retain their IDs. Rich pages receive artist/title parameters.
        const type=document.body.dataset.mediaType;
        const id=new URLSearchParams(location.search).get('id');
        if(type === 'album' && id){
            const a=document.createElement('a');a.className='feature-detail-link';a.textContent='Ver avaliações, favoritos e detalhes';
            const container=document.querySelector('#catalogContent'); container?.append(a);
            a.href=base+'detalhes-album.html?id='+encodeURIComponent(id);
        }
        document.addEventListener('synchro:own-profile', () => {
            const profile=document.querySelector('.playlist');
            if(profile)fetch('/playlists/me',{credentials:'same-origin'}).then(r=>r.ok?r.json():Promise.reject()).then(items=>{
                profile.replaceChildren();for(const item of items){const a=document.createElement('a');a.className='feature-detail-link';a.href=base+'playlists.html?id='+encodeURIComponent(item.id);a.textContent=item.titulo;profile.append(a);}
                const library=document.createElement('a');library.className='feature-detail-link';library.href=base+'playlists.html';library.textContent=items.length?'Gerenciar playlists':'Criar minha primeira playlist';profile.append(library);
            }).catch(()=>{});
        }, { once: true });
    });
})();
