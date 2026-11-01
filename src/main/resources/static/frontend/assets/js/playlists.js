document.addEventListener('DOMContentLoaded', () => {
    setupMyPlaylists();
    const triggers = document.querySelectorAll('[data-create-playlist]');
    if (!triggers.length) return;

    const dialog = document.createElement('dialog');
    dialog.className = 'playlist-dialog';
    dialog.setAttribute('aria-labelledby', 'playlistHeading');
    // HTML gerado por IA.
    dialog.innerHTML = `
        <form class="playlist-form">
            <h2 id="playlistHeading">Criar playlist</h2>
            <label for="playlistTitle">Nome da playlist</label>
            <input id="playlistTitle" name="titulo" maxlength="255" required autocomplete="off">
            <label for="playlistDescription">Descrição (opcional)</label>
            <textarea id="playlistDescription" name="descricao" rows="4"></textarea>
            <!-- Gerado por IA: seleção de capa e banner. -->
            <label for="playlistCover">Capa da playlist (opcional)</label>
            <input id="playlistCover" name="capa" type="file" accept="image/png,image/jpeg,image/webp,image/gif" aria-describedby="playlistImageHelp">
            <img class="playlist-cover playlist-cover-preview" alt="Prévia da capa" hidden>
            <label for="playlistBanner">Banner (opcional)</label>
            <input id="playlistBanner" name="banner" type="file" accept="image/png,image/jpeg,image/webp,image/gif" aria-describedby="playlistImageHelp">
            <img class="playlist-banner playlist-banner-preview" alt="Prévia do banner" hidden>
            <!-- Gerado por IA: orientação sobre compactação automática. -->
            <p id="playlistImageHelp">PNG, JPG, WebP ou GIF. Imagens acima de 2 MB serão compactadas automaticamente ao salvar. GIFs mantêm a animação e o tamanho, com redução de cores e qualidade.</p>
            <p class="playlist-feedback" role="status" aria-live="polite" hidden></p>
            <a class="playlist-login" href="/frontend/pages/login.html" hidden>Entrar na minha conta</a>
            <div class="playlist-actions">
                <button type="button" class="playlist-close">Cancelar</button>
                <button type="submit">Criar playlist</button>
            </div>
        </form>`;
    document.body.append(dialog);
    const form = dialog.querySelector('form');
    const title = form.elements.titulo;
    const submit = form.querySelector('[type="submit"]');
    const close = form.querySelector('.playlist-close');
    const feedback = form.querySelector('.playlist-feedback');
    const login = form.querySelector('.playlist-login');
    let saving = false;
    let created = false;
    let editing = null;
    const previews = {
        capa: form.querySelector('.playlist-cover-preview'),
        banner: form.querySelector('.playlist-banner-preview')
    };
    const previewUrls = {};
    function resetPreviews() {
        Object.keys(previews).forEach(key => {
            if (previewUrls[key]) URL.revokeObjectURL(previewUrls[key]);
            delete previewUrls[key];
            previews[key].hidden = true;
            previews[key].removeAttribute('src');
            form.elements[key].setCustomValidity('');
        });
    }
    Object.keys(previews).forEach(key => form.elements[key].addEventListener('change', () => {
        const input = form.elements[key];
        input.setCustomValidity('');
        if (previewUrls[key]) URL.revokeObjectURL(previewUrls[key]);
        const file = input.files[0];
        try {
            if (file) validatePlaylistImage(file);
            const src = file ? (previewUrls[key] = URL.createObjectURL(file)) : editing?.[key];
            previews[key].hidden = !src;
            if (src) previews[key].src = src;
            else previews[key].removeAttribute('src');
        } catch (error) {
            input.setCustomValidity(error.message);
            input.reportValidity();
            previews[key].hidden = true;
        }
    }));

    function openForm(playlist = null) {
        form.reset();
        resetPreviews();
        editing = playlist;
        created = false;
        feedback.hidden = true;
        login.hidden = true;
        submit.hidden = false;
        close.textContent = 'Cancelar';
        title.setCustomValidity('');
        dialog.querySelector('h2').textContent = playlist ? 'Editar playlist' : 'Criar playlist';
        submit.textContent = playlist ? 'Salvar alterações' : 'Criar playlist';
        if (playlist) {
            title.value = playlist.titulo;
            form.elements.descricao.value = playlist.descricao || '';
            Object.keys(previews).forEach(key => {
                if (playlist[key]) {
                    previews[key].src = playlist[key];
                    previews[key].hidden = false;
                }
            });
        }
        dialog.showModal();
        title.focus();
    }
    document.addEventListener('playlist-edit', event => openForm(event.detail));
    triggers.forEach(trigger => trigger.addEventListener('click', event => {
        event.preventDefault();
        openForm();
    }));
    close.addEventListener('click', () => dialog.close());
    dialog.addEventListener('cancel', event => {
        if (saving) event.preventDefault();
    });
    title.addEventListener('input', () => title.setCustomValidity(''));

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (saving || created) return;
        if (!title.value.trim()) {
            title.setCustomValidity('Informe o nome da playlist.');
            title.reportValidity();
            return;
        }
        saving = true;
        submit.disabled = true;
        close.disabled = true;
        submit.textContent = 'Salvando…';
        feedback.hidden = true;
        login.hidden = true;
        const imageFields = ['capa', 'banner'].map(key => form.elements[key]);
        imageFields.forEach(input => input.disabled = true);
        try {
            if (location.protocol === 'file:') {
                throw new Error('Abra o Synchro pelo servidor para criar playlists.');
            }
            const compressed = [];
            if (imageFields.some(input => input.files[0]?.size > PLAYLIST_IMAGE_LIMIT)) {
                feedback.textContent = 'Compactando imagens para até 2 MB…';
                feedback.hidden = false;
            }
            const [capa, banner] = await Promise.all(['capa', 'banner'].map(key =>
                readPlaylistImage(form.elements[key].files[0], blob => {
                    compressed.push(key === 'capa' ? 'capa' : 'banner');
                    if (previewUrls[key]) URL.revokeObjectURL(previewUrls[key]);
                    previews[key].src = previewUrls[key] = URL.createObjectURL(blob);
                    previews[key].hidden = false;
                })));
            const response = await fetch(editing ? `/playlists/update-playlist/${editing.id}` : '/playlists/new-playlist', {
                method: editing ? 'PUT' : 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                body: JSON.stringify({ titulo: title.value.trim(), descricao: form.elements.descricao.value.trim(), capa, banner })
            });
            if (response.status === 401 || response.status === 403) {
                login.hidden = false;
                throw new Error(await playlistResponseError(response));
            }
            if (!response.ok) {
                throw new Error(await playlistResponseError(response));
            }
            const playlist = await response.json();
            created = true;
            document.dispatchEvent(new CustomEvent('playlist-saved', { detail: playlist }));
            feedback.textContent = `Playlist “${playlist.titulo}” ${editing ? 'atualizada' : 'criada'} com sucesso!`;
            if (compressed.length) feedback.textContent += ` Compactação concluída para ${compressed.join(' e ')} (até 2 MB por imagem).`;
            submit.hidden = true;
            close.textContent = 'Concluir';
        } catch (error) {
            feedback.textContent = error.message || 'Não foi possível salvar a playlist. Tente novamente.';
        } finally {
            feedback.hidden = false;
            saving = false;
            submit.disabled = false;
            close.disabled = false;
            imageFields.forEach(input => input.disabled = false);
            submit.textContent = editing ? 'Salvar alterações' : 'Criar playlist';
        }
    });
});

async function playlistResponseError(response) {
    const fallback = response.status === 401 || response.status === 403
        ? 'Sua sessão expirou ou você não tem permissão para editar esta playlist.'
        : 'Não foi possível salvar a playlist. Tente novamente.';
    try {
        const error = await response.json();
        return typeof error.message === 'string' ? error.message : fallback;
    } catch { return fallback; }
}

function setupMyPlaylists() {
    document.querySelectorAll('[data-my-playlists]').forEach(link => {
        link.href = '/frontend/pages/playlists.html';
        link.removeAttribute('aria-haspopup');
    });
}

const PLAYLIST_IMAGE_LIMIT = 2 * 1024 * 1024;

function validatePlaylistImage(file) {
    if (!['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(file.type)) {
        throw new Error('Escolha uma imagem PNG, JPG, WebP ou GIF.');
    }
}

async function readPlaylistImage(file, onCompressed = () => {}) {
    if (!file) return null;
    validatePlaylistImage(file);
    let image = file;
    if (file.size > PLAYLIST_IMAGE_LIMIT) {
        image = await compressPlaylistImage(file);
        onCompressed(image);
    }
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(new Error('Não foi possível ler a imagem. Escolha o arquivo novamente.'));
        reader.readAsDataURL(image);
    });
}

async function compressPlaylistImage(file) {
    if (file.type === 'image/gif') return compressAnimatedPlaylistGif(file);
    const url = URL.createObjectURL(file);
    const canvas = document.createElement('canvas');
    try {
        const image = new Image();
        await new Promise((resolve, reject) => {
            image.onload = resolve;
            image.onerror = () => reject(new Error('Não foi possível abrir a imagem para compactar. Escolha outra imagem.'));
            image.src = url;
        });
        if (!image.naturalWidth || !image.naturalHeight) throw new Error('A imagem não possui dimensões válidas.');
        let scale = Math.min(1, 4096 / Math.max(image.naturalWidth, image.naturalHeight));
        const context = canvas.getContext('2d');
        if (!context) throw new Error('Não foi possível compactar a imagem neste navegador.');
        for (let attempt = 0; attempt < 16; attempt++) {
            canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
            canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
            context.drawImage(image, 0, 0, canvas.width, canvas.height);
            for (const quality of [0.92, 0.82, 0.7, 0.55]) {
                const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/webp', quality));
                if (!blob) throw new Error('Não foi possível compactar a imagem. Escolha outra imagem.');
                if (blob.size <= PLAYLIST_IMAGE_LIMIT) return blob;
            }
            scale *= 0.75;
        }
        throw new Error('Não foi possível reduzir a imagem para 2 MB. Escolha outra imagem.');
    } finally {
        URL.revokeObjectURL(url);
        canvas.width = canvas.height = 1;
    }
}

async function compressAnimatedPlaylistGif(file) {
    try {
        const { default: gifsicle } = await import('/frontend/assets/vendor/gifsicle/dist/gifsicle.min.js');
        // Re-encode the original animation each time: preserve dimensions, delays and loops.
        // Never flatten frames or silently resize an animation to meet the limit.
        for (const [lossy, colors] of [[30, 256], [60, 128], [100, 64], [150, 32], [200, 16]]) {
            const results = await gifsicle.run({
                input: [{ file, name: 'input.gif' }],
                command: [`-O1 --no-comments --lossy=${lossy} --colors=${colors} input.gif -o /out/compact.gif`]
            });
            const output = results?.find(result => result.name === 'compact.gif');
            if (output?.size > 0 && output.size <= PLAYLIST_IMAGE_LIMIT) return output;
        }
    } catch {
        throw new Error('Não foi possível compactar este GIF mantendo a animação. Tente outro arquivo.');
    }
    throw new Error('Este GIF não chegou a 2 MB apenas reduzindo a qualidade. Escolha um GIF menor ou mais curto; a animação foi preservada.');
}
