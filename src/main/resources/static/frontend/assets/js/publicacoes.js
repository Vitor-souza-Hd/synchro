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
    const sidebar = document.getElementById('postsSidebar');
    const toggle = document.getElementById('postsMenuToggle');
    const query = new URLSearchParams(window.location.search);
    const authorId = query.get('user');
    const focusedId = query.get('post');
    const attachments = window.SynchroPostAttachments.createPicker();
    const postCards = new Map();
    const dateFormat = new Intl.DateTimeFormat('pt-BR', {dateStyle: 'short', timeStyle: 'short'});
    let posts = [];
    let nextBefore = null;
    let editing = null;
    let draft = '';
    let draftAttachment = null;
    let signedIn = false;
    let writing = false;
    let loading = false;
    let loaded = false;

    function setMenu(open) {
        sidebar.classList.toggle('active', open);
        toggle.setAttribute('aria-expanded', String(open));
        toggle.setAttribute('aria-label', open ? 'Fechar menu' : 'Abrir menu');
    }
    toggle.addEventListener('click', () => setMenu(!sidebar.classList.contains('active')));
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && sidebar.classList.contains('active')) {
            setMenu(false);
            toggle.focus();
        }
    });

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
        if (focusedId !== null) form.hidden = true;
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
        article.append(author, meta, body);
        if (post.attachment) article.append(window.SynchroPostAttachments.card(post.attachment));

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
        feedStatus.textContent = posts.length ? '' : 'Ainda não há publicações.';
    }

    async function load() {
        if (loading || writing) return;
        loading = true;
        more.disabled = true;
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
                window.SynchroPostDiscussion?.initializeNotifications(document.getElementById('postNotifications'), signedIn);
                return;
            }
            const query = new URLSearchParams();
            if (authorId !== null) query.set('user', authorId);
            if (nextBefore != null) query.set('before', nextBefore);
            const data = await request('/posts?' + query);
            posts.push(...data.items);
            nextBefore = data.nextBefore;
            signedIn = data.signedIn;
            if (!loaded) window.SynchroPostDiscussion?.initializeNotifications(document.getElementById('postNotifications'), signedIn);
            loaded = true;
            form.hidden = !data.canPublish;
            login.hidden = data.canPublish || Boolean(data.author);
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
        }
    }

    async function deletePost(post) {
        if (writing || loading || !window.confirm('Excluir esta publicação?')) return;
        writing = true;
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
        }
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (writing || loading || !loaded || (focusedId !== null && !editing)) return;
        const body = text.value.trim();
        if (!body) {
            actionStatus.textContent = 'Escreva um texto para publicar.';
            return;
        }
        const current = editing;
        writing = true;
        save.disabled = cancel.disabled = text.disabled = true;
        attachments.disable(true);
        actionStatus.textContent = '';
        try {
            const post = await request(current ? '/posts/' + encodeURIComponent(current.id) : '/posts', {
                method: current ? 'PUT' : 'POST', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({text: body, attachment: attachments.value()})
            });
            if (current) {
                posts = posts.map(item => item.id === post.id ? post : item);
                closeEditor();
            } else {
                posts = [post, ...posts];
                text.value = '';
                attachments.select(null);
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
            writing = false;
        }
    });
    cancel.addEventListener('click', closeEditor);
    text.addEventListener('input', updateCount);
    more.addEventListener('click', load);
    load();
})();
