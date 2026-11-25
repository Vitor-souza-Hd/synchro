(() => {
    const dateFormat = new Intl.DateTimeFormat('pt-BR', {dateStyle: 'short', timeStyle: 'short'});
    const query = new URLSearchParams(window.location.search);

    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text;
        return node;
    }

    function button(text, className, click) {
        const control = element('button', className, text);
        control.type = 'button';
        control.addEventListener('click', click);
        return control;
    }

    function status() {
        const message = element('p', 'post-discussion-status');
        message.setAttribute('role', 'status');
        return message;
    }

    function linkToComment(postId, commentId) {
        return '/frontend/pages/publicacoes.html?' + new URLSearchParams({post: postId, comment: commentId});
    }

    function authorName(author) {
        return '@' + author.username;
    }

    function timestamp(value) {
        const date = new Date(value);
        const time = element('time', 'post-discussion-meta', dateFormat.format(date));
        time.dateTime = value;
        return time;
    }

    async function request(path, options = {}) {
        const response = await fetch(path, {
            cache: 'no-store', credentials: 'same-origin', signal: AbortSignal.timeout(10000), ...options
        });
        if (!response.ok) {
            const error = new Error('Interação indisponível.');
            error.status = response.status;
            throw error;
        }
        return response.status === 204 ? null : response.json();
    }

    function attach(article, post, signedIn) {
        const path = '/posts/' + encodeURIComponent(post.id) + '/comments';
        const panel = element('details', 'post-discussion');
        const toggle = element('summary', 'post-discussion-toggle', 'Comentários');
        const loadStatus = status();
        const writeStatus = status();
        const list = element('ol', 'post-comment-list');
        list.setAttribute('aria-label', 'Comentários da publicação');
        const more = button('Ver comentários anteriores', 'post-comment-more', () => load());
        more.hidden = true;
        const form = element('form', 'post-comment-form');
        const label = element('label', null, 'Escreva um comentário');
        const text = element('textarea', 'post-comment-text');
        const textId = 'post-comment-text-' + post.id;
        label.htmlFor = textId;
        text.id = textId;
        text.rows = 2;
        text.required = true;
        text.maxLength = 1000;
        const actions = element('div', 'post-discussion-actions');
        const send = element('button', 'post-primary post-comment-submit', 'Comentar');
        send.type = 'submit';
        const cancel = button('Cancelar resposta', 'post-comment-cancel', () => {
            setReply(null);
            text.focus();
        });
        cancel.hidden = true;
        const login = element('a', 'post-comment-login', 'Entre na sua conta para comentar');
        login.href = '/frontend/pages/login.html';
        login.hidden = signedIn;
        form.hidden = !signedIn;
        actions.append(cancel, send);
        form.append(label, text, actions);
        window.SynchroPostMentions?.initialize(text);
        panel.append(toggle, form, login, writeStatus, loadStatus, list, more);
        article.append(panel);

        const shown = new Map();
        const received = new Map();
        const pending = new Map();
        let nextBefore = null;
        let reply = null;
        let editing = null;
        let draft = '';
        let loaded = false;
        let loading;
        let sending = false;

        function setReply(comment) {
            if (editing) {
                text.value = draft;
                draft = '';
                editing = null;
            }
            reply = comment;
            label.textContent = comment ? 'Responder a ' + authorName(comment.author) : 'Escreva um comentário';
            send.textContent = comment ? 'Responder' : 'Comentar';
            cancel.textContent = 'Cancelar resposta';
            cancel.hidden = !comment;
        }

        function startEdit(comment) {
            if (sending) return;
            if (!editing) draft = text.value;
            reply = null;
            editing = comment;
            text.value = comment.body;
            label.textContent = 'Editar comentário';
            send.textContent = 'Salvar';
            cancel.textContent = 'Cancelar edição';
            cancel.hidden = false;
            writeStatus.textContent = '';
            text.focus();
        }

        function commentUrl(id) {
            return path + '/' + encodeURIComponent(id);
        }

        function setBusy(busy) {
            sending = send.disabled = text.disabled = cancel.disabled = busy;
        }

        async function remove(comment) {
            if (sending || !window.confirm('Excluir este comentário?')) return;
            setBusy(true);
            writeStatus.textContent = '';
            try {
                await request(commentUrl(comment.id), {method: 'DELETE'});
                if (editing?.id === comment.id || reply?.id === comment.id) setReply(null);
                render({...comment, author: null, own: false, deleted: true, body: ''}, true).focus();
                writeStatus.textContent = 'Comentário excluído.';
            } catch (error) {
                if (error.status === 401) login.hidden = false;
                writeStatus.textContent = error.status === 401 ? 'Sua sessão expirou.' : 'Não foi possível excluir o comentário.';
            } finally {
                setBusy(false);
            }
        }

        function showComment(id) {
            const key = String(id);
            if (shown.has(key)) return Promise.resolve(shown.get(key));
            if (!pending.has(key)) {
                const data = received.has(key)
                    ? Promise.resolve(received.get(key))
                    : request(commentUrl(key));
                pending.set(key, data.then(async comment => {
                    if (comment.parentId != null) await showComment(comment.parentId);
                    return render(comment);
                }).finally(() => pending.delete(key)));
            }
            return pending.get(key);
        }

        function render(comment, replace = false) {
            const id = String(comment.id);
            const previous = shown.get(id);
            if (previous && !replace) return previous;
            const item = element('li', 'post-comment');
            item.dataset.commentId = id;
            item.id = 'post-comment-' + id;
            item.tabIndex = -1;
            const header = element('div', 'post-comment-header');
            if (comment.author) {
                const author = element('a', 'post-comment-author', authorName(comment.author));
                author.href = '/frontend/pages/perfil-publico.html?' + new URLSearchParams({user: comment.author.id});
                header.append(author);
            }
            header.append(timestamp(comment.createdAt));
            if (comment.editedAt && !comment.deleted) header.append(element('span', 'post-discussion-meta', 'Editado'));
            item.append(header);
            if (comment.parentId != null) {
                const parent = element('a', 'post-discussion-meta',
                    comment.replyTo ? 'Em resposta a ' + authorName(comment.replyTo) : 'Ver comentário original');
                parent.href = linkToComment(post.id, comment.parentId);
                item.append(parent);
            }
            item.append(element('p', 'post-comment-body', comment.deleted ? 'Comentário excluído.' : comment.body));
            if (signedIn && !comment.deleted) {
                const tools = element('div', 'post-comment-tools');
                const respond = button('Responder', 'post-comment-reply', () => {
                    if (sending) return;
                    setReply(comment);
                    writeStatus.textContent = '';
                    text.focus();
                });
                respond.setAttribute('aria-label', 'Responder ao comentário de ' + authorName(comment.author));
                tools.append(respond);
                if (comment.own) {
                    tools.append(button('Editar comentário', 'post-comment-edit', () => startEdit(comment)),
                        button('Excluir comentário', 'post-comment-delete', () => remove(comment)));
                }
                item.append(tools);
            }
            const replies = previous?.lastElementChild || element('ol', 'post-comment-list post-comment-children');
            item.append(replies);
            shown.set(id, item);
            if (previous) {
                if (previous.classList.contains('post-comment-target')) item.classList.add('post-comment-target');
                previous.replaceWith(item);
            } else {
                const siblings = comment.parentId == null
                    ? list
                    : shown.get(String(comment.parentId)).lastElementChild;
                const next = [...siblings.children].find(sibling => Number(sibling.dataset.commentId) < comment.id);
                siblings.insertBefore(item, next || null);
            }
            return item;
        }

        function load() {
            if (loading) return loading;
            more.disabled = true;
            loadStatus.textContent = 'Carregando comentários…';
            const params = nextBefore == null ? '' : '?' + new URLSearchParams({before: nextBefore});
            loading = request(path + params).then(async page => {
                for (const comment of page.items) received.set(String(comment.id), comment);
                try {
                    await Promise.all(page.items.map(comment => showComment(comment.id)));
                } finally {
                    received.clear();
                }
                loaded = true;
                nextBefore = page.nextBefore;
                more.hidden = nextBefore == null;
                more.textContent = 'Ver comentários anteriores';
                loadStatus.textContent = shown.size ? '' : 'Ainda não há comentários.';
            }).catch(() => {
                loadStatus.textContent = 'Não foi possível carregar os comentários.';
                more.hidden = false;
                more.textContent = 'Tentar novamente';
            }).finally(() => {
                more.disabled = false;
                loading = null;
            });
            return loading;
        }

        panel.addEventListener('toggle', () => {
            if (panel.open && !loaded) load();
        });

        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (sending || !signedIn) return;
            const message = text.value.trim();
            if (!message) {
                writeStatus.textContent = 'Escreva um comentário antes de enviar.';
                return;
            }
            setBusy(true);
            writeStatus.textContent = editing ? 'Salvando…' : 'Enviando…';
            try {
                const focused = editing;
                const parentId = reply?.id ?? null;
                const comment = await request(focused ? commentUrl(focused.id) : path, {
                    method: focused ? 'PUT' : 'POST', headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(focused ? {text: message} : {text: message, parentId})
                });
                if (!focused) text.value = '';
                setReply(null);
                render(comment, Boolean(focused));
                if (loaded) loadStatus.textContent = '';
                writeStatus.textContent = focused ? 'Comentário atualizado.' :
                    parentId == null ? 'Comentário publicado.' : 'Resposta publicada.';
            } catch (error) {
                if (error.status === 401) {
                    login.hidden = false;
                    writeStatus.textContent = 'Sua sessão expirou.';
                } else {
                    writeStatus.textContent = 'Não foi possível enviar. Tente novamente.';
                }
            } finally {
                setBusy(false);
            }
        });

        const focusId = query.get('comment');
        if (query.get('post') === String(post.id) && /^\d+$/.test(focusId || '')) {
            panel.open = true;
            load().then(async () => {
                try {
                    const target = await showComment(focusId);
                    target.classList.add('post-comment-target');
                    target.focus();
                } catch {
                    writeStatus.textContent = 'Este comentário não está mais disponível.';
                }
            });
        }
    }

    window.SynchroPostDiscussion = {attach};
})();
