(() => {
    const params = new URLSearchParams(location.search);
    const id = params.get('id');
    const share = params.get('share');
    const library = document.getElementById('playlistLibrary');
    const detail = document.getElementById('playlistDetail');
    const grid = document.getElementById('playlistLibraryGrid');
    const status = document.getElementById('playlistPageStatus');
    const login = document.getElementById('playlistPageLogin');
    const retry = document.getElementById('playlistPageRetry');
    const edit = document.getElementById('playlistEdit');
    const detailActions = document.getElementById('playlistDetailActions');
    const placeholder = '../assets/images/catalog-placeholder.svg';
    let current;
    let controller;
    library.hidden = Boolean(id || share);

    document.querySelector('.menu-toggle').addEventListener('click', () => {
        document.querySelector('.sidebar').classList.toggle('active');
    });

    function imageUrl(value) {
        if (!value) return null;
        if (/^data:image\/(png|jpeg|webp|gif);base64,/i.test(value)) return value;
        try {
            const url = new URL(value, location.href);
            return ['http:', 'https:'].includes(url.protocol) ? url.href : null;
        } catch { return null; }
    }

    function setImage(image, value, cover = false) {
        const url = imageUrl(value);
        image.hidden = !url && !cover;
        image.onerror = () => {
            image.onerror = null;
            if (cover) image.src = placeholder;
            else image.hidden = true;
        };
        if (url || cover) image.src = url || placeholder;
        else image.removeAttribute('src');
    }

    function time(seconds) {
        return `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`;
    }

    function seconds(value) {
        const match = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?$/.exec(value || '');
        return match ? Number(match[1] || 0) * 3600 + Number(match[2] || 0) * 60 + Number(match[3] || 0) : null;
    }

    function userLink(user) {
        const link = document.createElement('a');
        link.href = `perfil-publico.html?user=${encodeURIComponent(user.id)}`;
        link.textContent = user.username || 'Usuário';
        return link;
    }

    function card(playlist) {
        const entry = document.createElement('article');
        entry.className = 'playlist-library-entry';
        const link = document.createElement('a');
        link.className = 'playlist-library-card';
        link.href = `playlists.html?id=${encodeURIComponent(playlist.id)}`;
        const artwork = document.createElement('div');
        artwork.className = 'playlist-library-artwork';
        const banner = document.createElement('img');
        banner.loading = 'lazy';
        banner.decoding = 'async';
        banner.className = 'playlist-card-banner';
        banner.alt = '';
        const cover = document.createElement('img');
        cover.loading = 'lazy';
        cover.decoding = 'async';
        cover.className = 'playlist-card-cover';
        cover.alt = '';
        setImage(banner, playlist.banner);
        setImage(cover, playlist.capa, true);
        artwork.append(banner, cover);
        const title = document.createElement('h2');
        title.textContent = playlist.titulo;
        const meta = document.createElement('p');
        meta.textContent = `${playlist.totalMusicas ?? 0} música(s) · ${playlist.criador?.username || 'Usuário'}`;
        link.append(artwork, title, meta);
        entry.append(link);
        entry.append(playlistMenu(playlist));
        return entry;
    }

    function playlistMenu(playlist) {
        const options = document.createElement('details');
        options.className = 'playlist-menu';
        options.name = 'playlist-actions';
        const trigger = document.createElement('summary');
        trigger.textContent = '⋯';
        trigger.setAttribute('aria-label', 'Opções da playlist “' + playlist.titulo + '”');
        const actions = document.createElement('div');
        actions.className = 'playlist-menu-actions';
        options.append(trigger, actions);

        function addAction(label, action, className) {
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = label;
            if (className) button.classList.add(className);
            button.addEventListener('click', () => {
                options.open = false;
                trigger.focus();
                action();
            });
            actions.append(button);
        }
        addAction('Convidar colaborador', () => openInvite(playlist));
        addAction('Compartilhar', () => openShare(playlist));
        if (playlist.podeExcluir === true) {
            addAction('Excluir playlist', () => deletePlaylist(playlist, trigger), 'playlist-delete');
        }
        options.addEventListener('keydown', event => {
            if (event.key === 'Escape' && options.open) {
                options.open = false;
                trigger.focus();
            }
        });
        return options;
    }

    document.addEventListener('click', event => {
        document.querySelectorAll('.playlist-menu[open]').forEach(menu => {
            if (!menu.contains(event.target)) menu.open = false;
        });
    });

    function renderDetail(playlist) {
        current = playlist;
        const colaborador = playlist.colaborador === true;
        edit.hidden = !colaborador || Boolean(share);
        detailActions.replaceChildren();
        if (colaborador && !share) detailActions.append(playlistMenu(playlist));
        document.querySelector('.playlist-people').hidden = Boolean(share);
        document.getElementById('playlistParticipation').textContent = share ? 'Playlist compartilhada' : colaborador
            ? 'Você participa desta playlist e pode editá-la.'
            : 'Você não é colaborador desta playlist.';
        document.title = `SYNCHRO | ${playlist.titulo}`;
        document.getElementById('detailPlaylistTitle').textContent = playlist.titulo;
        document.getElementById('detailPlaylistDescription').textContent = playlist.descricao || 'Sem descrição.';
        setImage(document.getElementById('detailPlaylistBanner'), playlist.banner);
        setImage(document.getElementById('detailPlaylistCover'), playlist.capa, true);
        const songs = playlist.musicas || [];
        const meta = document.getElementById('playlistMeta');
        meta.replaceChildren(document.createTextNode('Criada por '));
        meta.append(playlist.criador ? userLink(playlist.criador) : document.createTextNode('Usuário'));
        const lengths = songs.map(song => seconds(song.duracao));
        const total = lengths.length && lengths.every(length => length !== null)
            ? ` · ${time(lengths.reduce((sum, length) => sum + length, 0))} no total` : '';
        meta.append(document.createTextNode(` · ${songs.length} música(s)${total}`));
        const tracks = document.getElementById('playlistTracks');
        tracks.replaceChildren();
        songs.forEach((song, index) => {
            const row = document.createElement('a');
            row.className = 'playlist-track';
            row.href = `musica.html?id=${encodeURIComponent(song.id)}`;
            const number = document.createElement('span');
            number.className = 'playlist-track-number';
            number.textContent = String(index + 1).padStart(2, '0');
            const title = document.createElement('strong');
            title.textContent = song.titulo;
            const duration = document.createElement('span');
            const length = seconds(song.duracao);
            duration.textContent = length === null ? '—' : time(length);
            row.append(number, title, duration);
            tracks.append(row);
        });
        const emptyTracks = document.getElementById('playlistTracksEmpty');
        emptyTracks.hidden = songs.length > 0;
        if (share) emptyTracks.textContent = 'Esta playlist ainda não tem músicas.';
        const people = document.getElementById('playlistCollaborators');
        people.replaceChildren();
        const users = new Map();
        if (playlist.criador) users.set(playlist.criador.id, playlist.criador);
        (playlist.usuarios || []).forEach(user => users.set(user.id, user));
        users.forEach(user => people.append(userLink(user)));
        library.hidden = true;
        detail.hidden = false;
    }

    async function load() {
        controller?.abort();
        const request = new AbortController();
        controller = request;
        current = null;
        edit.hidden = true;
        detailActions.replaceChildren();
        status.textContent = 'Carregando suas playlists…';
        login.hidden = true;
        retry.hidden = true;
        if (!share && id && !/^\d+$/.test(id)) {
            library.hidden = true;
            status.textContent = 'Playlist não encontrada. Abra uma playlist pela sua biblioteca.';
            return;
        }
        try {
            const endpoint = share ? '/playlists/shared/' + encodeURIComponent(share)
                : id ? '/playlists/me/' + encodeURIComponent(id) : '/playlists/me/summary';
            const response = await fetch(endpoint, {
                credentials: 'same-origin', cache: 'no-store',
                headers: { Accept: 'application/json' }, signal: request.signal
            });
            if (request.signal.aborted) return;
            if (response.status === 401 || response.status === 403) {
                login.hidden = false;
                throw new Error('Entre na sua conta para ver suas playlists.');
            }
            if ((id || share) && [400, 404].includes(response.status)) {
                detail.hidden = true;
                status.textContent = share ? 'Este link de compartilhamento está indisponível.'
                    : 'Playlist não encontrada ou você não participa dela.';
                return;
            }
            if (!response.ok) throw new Error('Não foi possível carregar suas playlists. Tente novamente.');
            const data = await response.json();
            if (request.signal.aborted) return;
            grid.replaceChildren();
            if (id || share) {
                renderDetail(data);
                status.textContent = '';
                return;
            }
            if (!Array.isArray(data)) throw new Error('Não foi possível carregar suas playlists. Tente novamente.');
            data.forEach(playlist => grid.append(card(playlist)));
            document.getElementById('playlistLibraryCount').textContent = `${data.length} playlist(s) na sua biblioteca`;
            status.textContent = data.length ? '' : 'Você ainda não participa de nenhuma playlist. Use “Criar playlist” para começar.';
        } catch (error) {
            if (request.signal.aborted) return;
            detail.hidden = true;
            grid.replaceChildren();
            status.textContent = error.message || 'Não foi possível carregar suas playlists.';
            retry.hidden = !login.hidden;
        }
    }

    edit.addEventListener('click', () => {
        if (current?.colaborador === true) {
            document.dispatchEvent(new CustomEvent('playlist-edit', { detail: current }));
        }
    });
    const inviteDialog = document.getElementById('playlistInviteDialog');
    const inviteForm = document.getElementById('playlistInviteForm');
    const inviteInput = document.getElementById('playlistInviteUsername');
    const inviteStatus = document.getElementById('playlistInviteStatus');
    const shareDialog = document.getElementById('playlistShareDialog');
    const shareStatus = document.getElementById('playlistShareStatus');
    const shareField = document.getElementById('playlistShareField');
    const shareUrl = document.getElementById('playlistShareUrl');
    const generateLink = document.getElementById('playlistShareGenerate');
    const copyLink = document.getElementById('playlistShareCopy');
    const profileVisibility = document.getElementById('playlistProfileVisibility');
    let inviteTarget;
    let shareTarget;

    document.querySelectorAll('[data-close-playlist-dialog]').forEach(button => {
        button.addEventListener('click', () => button.closest('dialog').close());
    });

    function openInvite(playlist) {
        inviteTarget = playlist;
        inviteForm.reset();
        inviteStatus.textContent = '';
        inviteDialog.showModal();
        inviteInput.focus();
    }

    inviteForm.addEventListener('submit', async event => {
        event.preventDefault();
        const playlist = inviteTarget;
        const username = inviteInput.value.trim().replace(/^@/, '').trim();
        if (!username || !playlist) return;
        const button = inviteForm.querySelector('button[type=submit]');
        button.disabled = true;
        inviteStatus.textContent = 'Procurando usuário…';
        try {
            const search = await fetch('/search/users?' + new URLSearchParams({q: username}));
            if (!search.ok) throw new Error('Não foi possível buscar o usuário.');
            const data = await search.json();
            const exact = (data.items || []).filter(user => user.username.toLowerCase() === username.toLowerCase());
            if (exact.length !== 1) throw new Error('Confira o @usuário da conta que quer adicionar.');
            if (!inviteDialog.open || inviteTarget !== playlist) return;
            const response = await fetch('/playlists/update-playlist/' + encodeURIComponent(playlist.id)
                + '/add-user/' + encodeURIComponent(exact[0].id), {method: 'PUT', credentials: 'same-origin'});
            if (response.status === 409) throw new Error('Essa pessoa já participa da playlist.');
            if (!response.ok) throw new Error('Não foi possível adicionar o colaborador.');
            if (inviteDialog.open && inviteTarget === playlist) {
                inviteStatus.textContent = '@' + exact[0].username + ' agora participa da playlist.';
                inviteInput.value = '';
            }
            if (id) load();
        } catch (error) {
            if (inviteDialog.open && inviteTarget === playlist) inviteStatus.textContent = error.message;
        } finally {
            button.disabled = false;
        }
    });

    function showShareLink(name, value) {
        const url = new URL("playlists.html", location.href);
        url.searchParams.set(name, value);
        shareUrl.value = url.href;
        shareField.hidden = false;
        copyLink.hidden = false;
        generateLink.hidden = true;
    }

    function openShare(playlist) {
        shareTarget = playlist;
        const owner = playlist.podeExcluir === true;
        document.getElementById('playlistShareDescription').textContent = owner
            ? 'Qualquer pessoa com o link poderá ver as músicas da playlist.'
            : 'Este link pode ser aberto por quem já participa da playlist.';
        shareStatus.textContent = '';
        shareField.hidden = true;
        copyLink.hidden = true;
        generateLink.hidden = !owner;
        profileVisibility.hidden = !owner;
        if (owner) renderProfileVisibility(playlist);
        if (!owner) showShareLink('id', playlist.id);
        shareDialog.showModal();
    }

    generateLink.addEventListener('click', async () => {
        const playlist = shareTarget;
        generateLink.disabled = true;
        shareStatus.textContent = 'Gerando link…';
        try {
            const response = await fetch('/playlists/me/' + encodeURIComponent(playlist.id) + '/share', {
                method: 'POST', credentials: 'same-origin'
            });
            if (!response.ok) throw new Error();
            const {token} = await response.json();
            if (shareTarget !== playlist || !shareDialog.open) return;
            showShareLink('share', token);
            shareStatus.textContent = '';
            copyLink.focus();
        } catch {
            shareStatus.textContent = 'Não foi possível gerar o link da playlist.';
        } finally {
            generateLink.disabled = false;
        }
    });

    function renderProfileVisibility(playlist) {
        profileVisibility.textContent = playlist.exibirNoPerfil ? 'Remover do perfil público' : 'Mostrar no perfil público';
    }

    profileVisibility.addEventListener('click', async () => {
        const playlist = shareTarget;
        if (playlist?.podeExcluir !== true) return;
        const visible = !playlist.exibirNoPerfil;
        profileVisibility.disabled = true;
        shareStatus.textContent = 'Salvando…';
        try {
            const response = await fetch('/playlists/me/' + encodeURIComponent(playlist.id) + '/profile-visibility', {
                method: 'PUT', credentials: 'same-origin', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({visible}),
            });
            if (!response.ok) throw new Error();
            playlist.exibirNoPerfil = visible;
            if (shareTarget !== playlist || !shareDialog.open) return;
            renderProfileVisibility(playlist);
            shareStatus.textContent = visible ? 'Playlist adicionada ao perfil público.'
                : 'Playlist removida do perfil público. Links compartilhados continuam funcionando.';
        } catch {
            if (shareTarget === playlist && shareDialog.open) {
                shareStatus.textContent = 'Não foi possível atualizar a visibilidade no perfil.';
            }
        } finally {
            profileVisibility.disabled = false;
        }
    });

    copyLink.addEventListener('click', async () => {
        try {
            await navigator.clipboard.writeText(shareUrl.value);
            shareStatus.textContent = 'Link copiado.';
        } catch {
            shareUrl.focus();
            shareUrl.select();
            shareStatus.textContent = 'Selecione e copie o link no campo acima.';
        }
    });

    const deletingPlaylists = new Set();

    async function deletePlaylist(playlist, trigger) {
        if (playlist?.podeExcluir !== true || deletingPlaylists.has(playlist.id)) return;
        if (!window.confirm('Excluir “' + playlist.titulo + '”? Essa ação é permanente.')) return;

        deletingPlaylists.add(playlist.id);
        trigger.setAttribute('aria-busy', 'true');
        if (id) edit.disabled = true;
        status.textContent = 'Excluindo playlist…';
        try {
            const response = await fetch('/playlists/me/' + encodeURIComponent(playlist.id), {
                method: 'DELETE', credentials: 'same-origin'
            });
            if (response.ok || response.status === 404) {
                if (id) location.replace('playlists.html');
                else await load();
                return;
            }
            status.textContent = response.status === 401 || response.status === 403
                ? 'Entre como criador da playlist para excluí-la.'
                : 'Não foi possível excluir a playlist. Tente novamente.';
        } catch {
            status.textContent = 'Não foi possível excluir a playlist. Tente novamente.';
        } finally {
            deletingPlaylists.delete(playlist.id);
            trigger.removeAttribute('aria-busy');
            if (id) edit.disabled = false;
        }
    }
    document.addEventListener('playlist-saved', load);
    retry.addEventListener('click', load);
    load();
})();
