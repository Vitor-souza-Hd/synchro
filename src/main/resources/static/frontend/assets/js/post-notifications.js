(() => {
    const types = {
        COMMENT: ' comentou na sua publicação.',
        REPLY: ' respondeu ao seu comentário.',
        LIKE: ' curtiu sua publicação.',
        MENTION: ' mencionou você.'
    };
    const endpoint = '/post-notifications';
    const dateFormat = new Intl.DateTimeFormat('pt-BR', {dateStyle: 'short', timeStyle: 'short'});

    async function request(path, options = {}) {
        const response = await fetch(endpoint + path, {
            cache: 'no-store', credentials: 'same-origin', signal: AbortSignal.timeout(10000), ...options
        });
        if (!response.ok) {
            const error = new Error('Notificações indisponíveis.');
            error.status = response.status;
            throw error;
        }
        return response.status === 204 ? null : response.json();
    }

    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text;
        return node;
    }

    function button(label, className, click) {
        const control = element('button', className, label);
        control.type = 'button';
        control.addEventListener('click', click);
        return control;
    }

    function setUnread(unread) {
        for (const badge of document.querySelectorAll('[data-post-notifications-badge]')) {
            badge.hidden = !unread;
            badge.textContent = unread > 99 ? '99+' : String(unread || '');
            badge.setAttribute('aria-label', unread === 1 ? '1 notificação não lida' : unread + ' notificações não lidas');
        }
    }

    async function refreshBadge() {
        try {
            setUnread((await request('/unread-count')).unread);
        } catch (error) {
            if (error.status === 401) setUnread(0);
        }
    }

    function initializePage(container) {
        let items = [];
        let nextBefore = null;
        let unread = 0;
        let loading = false;

        container.hidden = false;
        const status = element('p', 'post-discussion-status');
        status.setAttribute('role', 'status');
        const login = element('a', null, 'Entrar na sua conta');
        login.href = '/frontend/pages/login.html';
        const list = element('ul', 'post-notification-list');
        list.setAttribute('aria-label', 'Notificações');
        const readAll = button('Marcar todas como lidas', 'post-notifications-read', markAll);
        const more = button('Ver notificações anteriores', 'post-notifications-more', () => load(nextBefore != null));
        const refresh = button('Atualizar notificações', 'post-notifications-refresh', () => load());
        login.hidden = more.hidden = readAll.hidden = true;
        const actions = element('div', 'post-notification-actions');
        actions.append(readAll, refresh);
        container.append(status, login, actions, list, more);

        function render() {
            setUnread(unread);
            readAll.hidden = unread === 0;
            list.replaceChildren(...items.map(item => {
                const row = element('li', 'post-notification');
                const name = item.actor.fullName?.trim() || '@' + item.actor.username;
                const link = element('a', 'post-notification-link', name + (types[item.type] || ' interagiu com uma publicação.'));
                const parameters = new URLSearchParams({post: item.postId});
                if (item.commentId != null) parameters.set('comment', item.commentId);
                link.href = '/frontend/pages/publicacoes.html?' + parameters;
                if (!item.read) {
                    link.append(element('span', 'post-notification-new', ' Nova'));
                    link.addEventListener('click', () => {
                        request('/' + encodeURIComponent(item.id), {method: 'PATCH', keepalive: true})
                            .then(() => {
                                if (!item.read) unread = Math.max(0, unread - 1);
                                item.read = true;
                                render();
                            }).catch(() => {});
                    });
                }
                const time = element('time', 'post-discussion-meta', dateFormat.format(new Date(item.createdAt)));
                time.dateTime = item.createdAt;
                row.append(link, time);
                return row;
            }));
            status.textContent = items.length ? '' : 'Você ainda não tem notificações.';
        }

        async function load(append = false) {
            if (loading) return;
            loading = more.disabled = readAll.disabled = refresh.disabled = true;
            if (!items.length) status.textContent = 'Carregando notificações…';
            try {
                const params = append ? '?' + new URLSearchParams({before: nextBefore}) : '';
                const page = await request(params);
                if (append) {
                    const ids = new Set(items.map(item => item.id));
                    items.push(...page.items.filter(item => !ids.has(item.id)));
                    nextBefore = page.nextBefore;
                } else {
                    items = page.items;
                    nextBefore = page.nextBefore;
                }
                unread = page.unread;
                more.hidden = nextBefore == null;
                login.hidden = true;
                refresh.hidden = false;
                more.textContent = 'Ver notificações anteriores';
                render();
            } catch (error) {
                login.hidden = error.status !== 401;
                more.hidden = true;
                refresh.hidden = !login.hidden;
                status.textContent = error.status === 401 ? 'Entre para ver suas notificações.' : 'Não foi possível carregar as notificações.';
                if (error.status === 401) {
                    items = [];
                    nextBefore = null;
                    unread = 0;
                    list.replaceChildren();
                    readAll.hidden = true;
                    setUnread(0);
                }
            } finally {
                loading = more.disabled = readAll.disabled = refresh.disabled = false;
            }
        }

        async function markAll() {
            if (loading) return;
            loading = more.disabled = readAll.disabled = refresh.disabled = true;
            try {
                await request('/read-all', {method: 'PATCH'});
                items.forEach(item => { item.read = true; });
                unread = 0;
                render();
                status.textContent = 'Notificações marcadas como lidas.';
            } catch {
                status.textContent = 'Não foi possível marcar as notificações como lidas.';
            } finally {
                loading = more.disabled = readAll.disabled = refresh.disabled = false;
            }
        }

        load();
    }

    document.addEventListener('DOMContentLoaded', () => {
        const page = document.getElementById('postNotificationsPage');
        const badge = document.querySelector('[data-post-notifications-badge]');
        if (!page && !badge) return;
        if (page) initializePage(page);
        else refreshBadge();
        if (!badge) return;
        window.addEventListener('focus', refreshBadge);
        window.setInterval(() => { if (!document.hidden) refreshBadge(); }, 60000);
    });
})();
