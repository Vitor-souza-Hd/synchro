document.addEventListener('synchro:song', async ({detail: {params, song}}) => {
    const button=document.getElementById('addToPlaylistBtn');
    const status=document.getElementById('playlistResolveStatus');

    const title=params.get('track'),artist=params.get('artist');
    document.getElementById('catalogTitle').textContent=title||'Música';
    if(song){
        button.dataset.midiaId=String(song.id);
        button.disabled=false;
        return;
    }
    if(!title||!artist)return;
    const query=new URLSearchParams({titulo:title,artista:artist});
    button.addEventListener('click',async event=>{
        if(button.dataset.midiaId)return;
        event.stopImmediatePropagation();
        button.disabled=true;status.textContent='Preparando música…';
        try{
            const response=await fetch('/musica/registrar?'+query,{method:'POST',credentials:'same-origin'});
            if(response.status===401)throw new Error('Entre na sua conta para adicionar esta música a uma playlist.');
            if(!response.ok)throw new Error('Não foi possível cadastrar esta música agora. Tente novamente.');
            const song=await response.json();button.dataset.midiaId=song.id;status.textContent='';
            button.disabled=false;button.click();
        }catch(error){status.textContent=error.message;}finally{button.disabled=false;}
    },true);
    try{
        const response=await fetch('/musica/identificar?'+query);
        if(response.status===404){
            button.disabled=false;
            return;
        }
        if(!response.ok)throw new Error();
        const song=await response.json();button.dataset.midiaId=song.id;button.disabled=false;
    }catch{status.textContent='Não foi possível identificar a música no catálogo para adicioná-la à playlist.';}
});
