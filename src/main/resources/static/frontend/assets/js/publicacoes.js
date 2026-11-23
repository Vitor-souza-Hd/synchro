(() => {
    const form = document.getElementById('postForm');
    const text = document.getElementById('postText');
    const count = document.getElementById('postCount');
    const label = document.getElementById('postLabel');
    const save = document.getElementById('savePost');
    const cancel = document.getElementById('cancelPostEdit');
    const actionStatus = document.getElementById('postActionStatus');
    const feedStatus = document.getElementById('postsStatus');
    const list = document.getElementById('postsFeed');
    const more = document.getElementById('morePosts');
    const login = document.getElementById('postLogin');
    const filters = document.getElementById('postFilters');
    const filterButtons = [...filters.querySelectorAll('[data-feed-scope]')];
    const query = new URLSearchParams(window.location.search);
    const authorId = query.get('user');
    const focusedId = query.get('post');
    const attachments = window.SynchroPostAttachments.createPicker();
    const pollEditor = window.SynchroPostPolls.createEditor();
    form.insertBefore(pollEditor.element, form.querySelector('.post-actions'));
    window.SynchroPostMentions?.initialize(text);
    const postCards = new Map();
    const dateFormat = new Intl.DateTimeFormat('pt-BR', {dateStyle: 'short', timeStyle: 'short'});
    let posts = [];
    let nextBefore = null;
    let editing = null;
    let draft = '';
    let draftAttachment = null;
    let signedIn = false;
    let mayPublish = false;
    let scope = 'ALL';
    let reacting = 0;
    let writing = false;
    let loading = false;
    let loaded = false;

    async function request(path, options = {}) {
        const response = await fetch(path, {
            cache: 'no-store', credentials: 'same-origin', signal: AbortSignal.timeout(10000), ...options
        });
        if (!response.ok) {
            const error = new Error('Publicação indisponível.');
            error.status = response.status;
            throw error;
        }
        return response.status === 204 ? null : response.json();
    }

    function updateCount() {
        count.textContent = text.value.length + '/' + text.maxLength;
    }

    function closeEditor() {
        editing = null;
        text.value = draft;
        draft = '';
        attachments.select(draftAttachment);
        draftAttachment = null;
        pollEditor.element.hidden = false;
        if (focusedId !== null || scope !== 'ALL' || authorId !== null && !mayPublish) form.hidden = true;
        label.textContent = 'Escreva uma publicação';
        save.textContent = 'Publicar';
        cancel.hidden = true;
        updateCount();
    }

    function openEditor(post) {
        if (writing || !post.own) return;
        if (!editing) {
            draft = text.value;
            draftAttachment = attachments.selected();
        }
        form.hidden = false;
        pollEditor.element.hidden = true;
        editing = post;
        text.value = post.body;
        attachments.select(post.attachment);
        label.textContent = 'Editar publicação';
        save.textContent = 'Salvar alterações';
        cancel.hidden = false;
        actionStatus.textContent = '';
        updateCount();
        text.focus();
    }

    function authorName(user) {
        return user.fullName?.trim() || '@' + user.username;
    }

    function setAvatar(image, sources) {
        const available = [];
        for (const value of sources) {
            try {
                const url = new URL(value);
                if (url.protocol === 'https:' && !url.username && !url.password && !available.includes(url.href)) {
                    available.push(url.href);
                }
            } catch { /* Use the next image when a profile has no valid URL. */ }
        }
        function next() {
            const source = available.shift();
            image.onerror = source ? next : null;
            image.src = source || '/frontend/assets/images/user/userphoto.jpg';
        }
        next();
    }

    function button(label, click) {
        const element = document.createElement('button');
        element.type = 'button';
        element.textContent = label;
        element.addEventListener('click', click);
        return element;
    }

    function reactions(post) {
        const row = document.createElement('div');
        row.className = 'post-reactions';
        let state = post.interaction || {likes: 0, liked: false, saved: false};
        if (!signedIn) {
            row.textContent = state.likes + (state.likes === 1 ? ' curtida' : ' curtidas');
            return row;
        }
        const like = button('', () => change('like'));
        const bookmark = button('', () => change('bookmark'));
        function display() {
            like.textContent = (state.liked ? 'Descurtir' : 'Curtir') + ' · ' + state.likes;
            bookmark.textContent = state.saved ? 'Remover das salvas' : 'Salvar';
            like.setAttribute('aria-pressed', String(state.liked));
            bookmark.setAttribute('aria-pressed', String(state.saved));
        }

        async function change(action) {
            if (like.disabled || writing) return;
            const active = action === 'like' ? state.liked : state.saved;
            like.disabled = bookmark.disabled = true;
            reacting++;
            syncFilters();
            actionStatus.textContent = '';
            try {
                state = await request('/posts/' + encodeURIComponent(post.id) + '/' + action,
                    {method: active ? 'DELETE' : 'PUT'});
                post.interaction = state;
                display();
                if (action === 'bookmark') {
                    actionStatus.textContent = state.saved ? 'Publicação salva.' : 'Publicação removida das salvas.';
                    if (scope === 'SAVED' && !state.saved) {
                        posts = posts.filter(item => item.id !== post.id);
                        renderPosts();
                    }
                }
            } catch (error) {
                if (error.status === 401) login.hidden = false;
                actionStatus.textContent = 'Não foi possível atualizar a publicação.';
            } finally {
                like.disabled = bookmark.disabled = false;
                reacting--;
                syncFilters();
            }
        }

        display();
        row.append(like, bookmark);
        return row;
    }

    function card(post) {
        const existing = postCards.get(post.id);
        if (existing?.post === post && existing.signedIn === signedIn) return existing.article;
        const article = document.createElement('article');
        article.className = 'post-panel';
        article.id = 'post-' + post.id;

        const author = document.createElement('a');
        author.className = 'post-author';
        author.href = '/frontend/pages/perfil-publico.html?' + new URLSearchParams({user: post.author.id});
        const image = document.createElement('img');
        image.alt = '';
        image.width = image.height = 40;
        image.loading = 'lazy';
        image.referrerPolicy = 'no-referrer';
        setAvatar(image, [post.profileImageUrl, post.lastFmImageUrl]);
        author.append(image, authorName(post.author));

        const meta = document.createElement('p');
        meta.className = 'post-meta';
        const time = document.createElement('time');
        time.dateTime = post.createdAt;
        time.textContent = dateFormat.format(new Date(post.createdAt));
        meta.append(time);
        if (post.editedAt) meta.append(' · Editada');

        const body = document.createElement('p');
        body.className = 'post-body';
        body.textContent = post.body;
        article.append(author, meta);
        if (post.body) article.append(body);
        if (post.attachment) article.append(window.SynchroPostAttachments.card(post.attachment));
        window.SynchroPostPolls.attach(article, post, signedIn);
        article.append(reactions(post));

        if (post.own) {
            const actions = document.createElement('div');
            actions.className = 'post-actions';
            actions.append(button('Editar', () => openEditor(post)), button('Excluir', () => deletePost(post)));
            article.append(actions);
        }
        window.SynchroPostDiscussion?.attach(article, post, signedIn);
        postCards.set(post.id, {post, article, signedIn});
        return article;
    }

    function renderPosts() {
        const ids = new Set(posts.map(post => post.id));
        for (const id of postCards.keys()) {
            if (!ids.has(id)) postCards.delete(id);
        }
        list.replaceChildren(...posts.map(card));
        const empty = scope === 'SAVED' ? 'Você ainda não tem publicações salvas.'
            : scope === 'FOLLOWING' ? 'As pessoas que você segue ainda não publicaram.' : 'Ainda não há publicações.';
        feedStatus.textContent = posts.length ? '' : empty;
    }

    async function load() {
        if (loading || writing) return;
        loading = true;
        more.disabled = true;
        syncFilters();
        feedStatus.textContent = 'Carregando publicações…';
        try {
            if (focusedId !== null) {
                const data = await request('/posts/' + encodeURIComponent(focusedId));
                document.getElementById('postsTitle').textContent = 'Publicação';
                document.getElementById('allPosts').hidden = false;
                posts = [data.post];
                signedIn = data.signedIn;
                loaded = true;
                renderPosts();
                more.hidden = true;
                return;
            }
            const query = new URLSearchParams();
            if (authorId !== null) query.set('user', authorId);
            if (scope !== 'ALL') query.set('scope', scope);
            if (nextBefore != null) query.set('before', nextBefore);
            const data = await request('/posts?' + query);
            posts.push(...data.items);
            nextBefore = data.nextBefore;
            signedIn = data.signedIn;
            loaded = true;
            mayPublish = data.canPublish;
            form.hidden = !mayPublish || (scope !== 'ALL' && !editing);
            login.hidden = signedIn || Boolean(data.author);
            filters.hidden = !signedIn || authorId !== null;
            if (data.author) {
                document.getElementById('postsTitle').textContent = 'Publicações de ' + authorName(data.author);
                document.getElementById('allPosts').hidden = false;
            }
            renderPosts();
            more.textContent = 'Ver mais publicações';
            more.hidden = nextBefore == null;
        } catch (error) {
            if (focusedId !== null) document.getElementById('allPosts').hidden = false;
            feedStatus.textContent = error.status === 404
                ? (focusedId !== null ? 'Publicação não encontrada.' : 'Perfil não encontrado.')
                : 'Não foi possível carregar as publicações.';
            more.hidden = error.status === 404;
            more.textContent = 'Tentar novamente';
        } finally {
            more.disabled = false;
            loading = false;
            syncFilters();
        }
    }

    function syncFilters() {
        filterButtons.forEach(control => {
            control.disabled = loading || writing || reacting > 0;
            control.setAttribute('aria-pressed', String(control.dataset.feedScope === scope));
        });
    }

    for (const control of filterButtons) {
        control.addEventListener('click', () => {
            if (control.dataset.feedScope === scope || loading || writing || reacting) return;
            scope = control.dataset.feedScope;
            if (editing) closeEditor();
            form.hidden = scope !== 'ALL' || !mayPublish;
            posts = [];
            nextBefore = null;
            actionStatus.textContent = '';
            renderPosts();
            load();
        });
    }

    async function deletePost(post) {
        if (writing || loading || reacting || !window.confirm('Excluir esta publicação?')) return;
        writing = true;
        syncFilters();
        actionStatus.textContent = '';
        try {
            await request('/posts/' + encodeURIComponent(post.id), {method: 'DELETE'});
            posts = posts.filter(item => item.id !== post.id);
            if (editing?.id === post.id) closeEditor();
            renderPosts();
            actionStatus.textContent = 'Publicação excluída.';
        } catch {
            actionStatus.textContent = 'Não foi possível excluir a publicação.';
        } finally {
            writing = false;
            syncFilters();
        }
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (writing || loading || reacting || !loaded || (!editing && (focusedId !== null || scope !== 'ALL'))) return;
        const current = editing;
        const body = text.value.trim();
        let poll;
        try {
            poll = current ? null : pollEditor.value();
        } catch (error) {
            actionStatus.textContent = error.message;
            return;
        }
        if (!body && !poll && !current?.poll) {
            actionStatus.textContent = 'Escreva um texto ou adicione uma enquete.';
            return;
        }
        writing = true;
        save.disabled = cancel.disabled = text.disabled = true;
        attachments.disable(true);
        pollEditor.disable(true);
        syncFilters();
        actionStatus.textContent = '';
        try {
            const post = await request(current ? '/posts/' + encodeURIComponent(current.id) : '/posts', {
                method: current ? 'PUT' : 'POST', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({text: body, attachment: attachments.value(), poll})
            });
            if (current) {
                posts = posts.map(item => item.id === post.id ? post : item);
                closeEditor();
            } else {
                posts = [post, ...posts];
                text.value = '';
                attachments.select(null);
                pollEditor.reset();
                updateCount();
            }
            renderPosts();
            actionStatus.textContent = current ? 'Publicação atualizada.' : 'Publicação enviada.';
        } catch (error) {
            if (error.status === 401) login.hidden = false;
            actionStatus.textContent = error.status === 401 ? 'Sua sessão expirou.' : 'Não foi possível salvar a publicação.';
        } finally {
            save.disabled = cancel.disabled = text.disabled = false;
            attachments.disable(false);
            pollEditor.disable(false);
            writing = false;
            syncFilters();
        }
    });
    cancel.addEventListener('click', closeEditor);
    text.addEventListener('input', updateCount);
    more.addEventListener('click', load);
    load();
})();
