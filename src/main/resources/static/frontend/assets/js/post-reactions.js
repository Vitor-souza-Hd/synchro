(() => {
    function button(onClick) {
        const control = document.createElement('button');
        control.type = 'button';
        control.addEventListener('click', onClick);
        return control;
    }

    function render(post, signedIn, {canChange, onPendingChange, onUnsave} = {}) {
        const row = document.createElement('div');
        row.className = 'post-reactions';
        let state = post.interaction || {likes: 0, liked: false, saved: false};

        const login = document.createElement('a');
        login.href = '/frontend/pages/login.html';
        login.textContent = 'Entre para interagir';
        login.hidden = signedIn;
        if (!signedIn) {
            row.append(state.likes + (state.likes === 1 ? ' curtida' : ' curtidas'), login);
            return row;
        }

        const status = document.createElement('p');
        status.className = 'post-reaction-status';
        status.setAttribute('role', 'status');
        const like = button(() => change('like', state.liked));
        const bookmark = button(() => change('bookmark', state.saved));

        function display() {
            like.textContent = (state.liked ? 'Descurtir' : 'Curtir') + ' · ' + state.likes;
            bookmark.textContent = state.saved ? 'Remover das salvas' : 'Salvar';
            like.setAttribute('aria-pressed', String(state.liked));
            bookmark.setAttribute('aria-pressed', String(state.saved));
        }

        async function change(action, active) {
            if (like.disabled || canChange?.() === false) return;
            like.disabled = bookmark.disabled = true;
            onPendingChange?.(1);
            status.textContent = '';
            try {
                const response = await fetch('/posts/' + encodeURIComponent(post.id) + '/' + action, {
                    method: active ? 'DELETE' : 'PUT',
                    credentials: 'same-origin',
                    cache: 'no-store',
                    signal: AbortSignal.timeout(10000)
                });
                if (response.status === 401) {
                    login.hidden = false;
                    status.textContent = 'Sua sessão expirou.';
                    return;
                }
                if (!response.ok) throw new Error('Interação indisponível.');
                login.hidden = true;
                post.interaction = state = await response.json();
                display();
                if (action === 'bookmark') {
                    status.textContent = state.saved ? 'Publicação salva.' : 'Publicação removida das salvas.';
                    if (!state.saved) onUnsave?.();
                }
            } catch {
                status.textContent = 'Não foi possível atualizar a publicação. Tente novamente.';
            } finally {
                like.disabled = bookmark.disabled = false;
                onPendingChange?.(-1);
            }
        }

        display();
        row.append(like, bookmark, login, status);
        return row;
    }

    window.SynchroPostReactions = {render};
})();
