(() => {
    const dateFormat = new Intl.DateTimeFormat('pt-BR', {dateStyle: 'short', timeStyle: 'short'});
    const query = new URLSearchParams(window.location.search);
    const notifications = new WeakSet();

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
        return author.fullName?.trim() || '@' + author.username;
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
        panel.append(toggle, form, login, writeStatus, loadStatus, list, more);
        article.append(panel);

        const shown = new Map();
        let nextBefore = null;
        let reply = null;
        let loaded = false;
        let loading;
        let sending = false;

        function setReply(comment) {
            reply = comment;
            label.textContent = comment ? 'Responder a ' + authorName(comment.author) : 'Escreva um comentário';
            send.textContent = comment ? 'Responder' : 'Comentar';
            cancel.hidden = !comment;
        }

        function render(comment, prepend = false) {
            const id = String(comment.id);
            if (shown.has(id)) return shown.get(id);
            const item = element('li', comment.parentId == null ? 'post-comment' : 'post-comment post-comment-nested');
            item.id = 'post-comment-' + id;
            item.tabIndex = -1;
            const header = element('div', 'post-comment-header');
            const author = element('a', 'post-comment-author', authorName(comment.author));
            author.href = '/frontend/pages/perfil-publico.html?' + new URLSearchParams({user: comment.author.id});
            header.append(author, timestamp(comment.createdAt));
            item.append(header);
            if (comment.parentId != null) {
                const parent = element('a', 'post-discussion-meta',
                    comment.replyTo ? 'Em resposta a ' + authorName(comment.replyTo) : 'Ver comentário original');
                parent.href = linkToComment(post.id, comment.parentId);
                item.append(parent);
            }
            item.append(element('p', 'post-comment-body', comment.body));
            if (signedIn) {
                const respond = button('Responder', 'post-comment-reply', () => {
                    setReply(comment);
                    text.focus();
                });
                respond.setAttribute('aria-label', 'Responder ao comentário de ' + authorName(comment.author));
                item.append(respond);
            }
            shown.set(id, item);
            if (prepend) list.prepend(item);
            else list.append(item);
            return item;
        }

        function load() {
            if (loading) return loading;
            more.disabled = true;
            loadStatus.textContent = 'Carregando comentários…';
            const params = nextBefore == null ? '' : '?' + new URLSearchParams({before: nextBefore});
            loading = request(path + params).then(page => {
                page.items.forEach(comment => render(comment));
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
            sending = true;
            send.disabled = text.disabled = cancel.disabled = true;
            writeStatus.textContent = 'Enviando…';
            try {
                const parentId = reply?.id ?? null;
                const comment = await request(path, {
                    method: 'POST', headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({text: message, parentId})
                });
                text.value = '';
                setReply(null);
                render(comment, true);
                if (loaded) loadStatus.textContent = '';
                writeStatus.textContent = parentId == null ? 'Comentário publicado.' : 'Resposta publicada.';
            } catch (error) {
                if (error.status === 401) {
                    login.hidden = false;
                    writeStatus.textContent = 'Sua sessão expirou.';
                } else {
                    writeStatus.textContent = 'Não foi possível enviar. Tente novamente.';
                }
            } finally {
                send.disabled = text.disabled = cancel.disabled = false;
                sending = false;
            }
        });

        const focusId = query.get('comment');
        if (query.get('post') === String(post.id) && /^\d+$/.test(focusId || '')) {
            panel.open = true;
            load().then(async () => {
                try {
                    const target = shown.get(focusId) ||
                        render(await request(path + '/' + encodeURIComponent(focusId)), true);
                    target.classList.add('post-comment-target');
                    target.focus();
                } catch {
                    writeStatus.textContent = 'Este comentário não está mais disponível.';
                }
            });
        }
    }

    function initializeNotifications(container, signedIn) {
        if (!container) return;
        container.hidden = !signedIn;
        if (!signedIn || notifications.has(container)) return;
        notifications.add(container);

        const panel = element('details', 'post-notifications');
        const toggle = element('summary', 'post-notifications-toggle', 'Notificações');
        const count = element('span', 'post-notifications-count');
        count.setAttribute('aria-live', 'polite');
        count.hidden = true;
        toggle.append(count);
        const message = status();
        const list = element('ul', 'post-notification-list');
        list.setAttribute('aria-label', 'Notificações de publicações');
        const more = button('Ver notificações anteriores', 'post-notifications-more', () => load(true));
        const readAll = button('Marcar todas como lidas', 'post-notifications-read', markAll);
        more.hidden = readAll.hidden = true;
        panel.append(toggle, message, readAll, list, more);
        container.append(panel);

        let items = [];
        let nextBefore = null;
        let unread = 0;
        let loading = false;

        function render() {
            count.hidden = unread === 0;
            count.textContent = unread ? String(unread) : '';
            count.setAttribute('aria-label', unread ? unread + ' não lidas' : '');
            readAll.hidden = unread === 0;
            list.replaceChildren(...items.map(item => {
                const row = element('li', 'post-notification');
                const action = item.type === 'REPLY' ? ' respondeu ao seu comentário.' : ' comentou na sua publicação.';
                const link = element('a', 'post-notification-link', authorName(item.actor) + action);
                if (!item.read) link.append(element('span', 'post-notification-new', ' Nova'));
                link.href = linkToComment(item.postId, item.commentId);
                if (!item.read) link.addEventListener('click', () => {
                    request('/post-notifications/' + encodeURIComponent(item.id), {method: 'PATCH', keepalive: true})
                        .then(() => {
                            if (!item.read) unread = Math.max(0, unread - 1);
                            item.read = true;
                            render();
                        }).catch(() => {});
                });
                row.append(link, timestamp(item.createdAt));
                return row;
            }));
            message.textContent = items.length ? '' : 'Você ainda não tem notificações.';
        }

        async function load(append = false) {
            if (loading || container.hidden || (append && nextBefore == null)) return;
            loading = more.disabled = readAll.disabled = true;
            if (!items.length) message.textContent = 'Carregando notificações…';
            try {
                const params = append ? '?' + new URLSearchParams({before: nextBefore}) : '';
                const page = await request('/post-notifications' + params);
                const existingIds = new Set(items.map(item => item.id));
                const latestIds = new Set(page.items.map(item => item.id));
                if (append) {
                    items.push(...page.items.filter(item => !existingIds.has(item.id)));
                    nextBefore = page.nextBefore;
                } else {
                    const last = page.items.at(-1);
                    const older = last ? items.filter(item => item.id < last.id && !latestIds.has(item.id)) : [];
                    items = [...page.items, ...older];
                    if (!older.length) nextBefore = page.nextBefore;
                }
                unread = page.unread;
                more.hidden = nextBefore == null;
                more.textContent = 'Ver notificações anteriores';
                render();
            } catch (error) {
                if (error.status === 401) container.hidden = true;
                else message.textContent = 'Não foi possível carregar as notificações.';
            } finally {
                loading = more.disabled = readAll.disabled = false;
            }
        }

        async function markAll() {
            if (loading) return;
            loading = more.disabled = readAll.disabled = true;
            try {
                await request('/post-notifications/read-all', {method: 'PATCH'});
                items.forEach(item => { item.read = true; });
                unread = 0;
                render();
                message.textContent = 'Notificações marcadas como lidas.';
            } catch {
                message.textContent = 'Não foi possível marcar as notificações como lidas.';
            } finally {
                loading = more.disabled = readAll.disabled = false;
            }
        }

        panel.addEventListener('toggle', () => { if (panel.open) load(); });
        window.addEventListener('focus', () => load());
        load();
    }

    window.SynchroPostDiscussion = {attach, initializeNotifications};
})();
