(() => {
    const actions = {
        NONE: [{label: 'Adicionar amigo', method: 'POST', success: 'Pedido de amizade enviado.'}],
        SENT: [{label: 'Cancelar pedido', method: 'DELETE', success: 'Pedido cancelado.'}],
        RECEIVED: [
            {label: 'Aceitar pedido', method: 'PATCH', success: 'Pedido aceito.'},
            {label: 'Recusar pedido', method: 'DELETE', success: 'Pedido recusado.'}
        ],
        FRIENDS: [{label: 'Remover amizade', method: 'DELETE', success: 'Amizade removida.'}]
    };

    async function request(userId, method = 'GET', state) {
        const path = userId == null ? '/friendships' : '/friendships/' + encodeURIComponent(userId);
        const params = method === 'DELETE' ? '?' + new URLSearchParams({state}) : '';
        const response = await fetch(path + params, {
            method, credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(12000),
            headers: {Accept: 'application/json'}
        });
        if (!response.ok) {
            const error = new Error('Amizade indisponível.');
            error.status = response.status;
            throw error;
        }
        return response.json();
    }

    function buttons(container, state, userId, {status, update, reload, style, name}) {
        container.replaceChildren();
        for (const action of actions[state] || []) {
            const button = document.createElement('button');
            button.type = 'button';
            if (style) button.className = style;
            button.textContent = action.label;
            if (name) button.setAttribute('aria-label', action.label + ' · ' + name);
            button.addEventListener('click', async () => {
                if (state === 'FRIENDS' && !window.confirm('Remover esta amizade?')) return;
                const currentButtons = [...container.querySelectorAll('button')];
                currentButtons.forEach(control => { control.disabled = true; });
                status.textContent = '';
                try {
                    const next = await request(userId, action.method, state);
                    update(next.state);
                    status.textContent = action.success;
                } catch (error) {
                    if (error.status === 401 || error.status === 409) {
                        await reload();
                        if (error.status === 409) status.textContent = 'O pedido mudou. Confira o estado atual.';
                    } else {
                        status.textContent = 'Não foi possível atualizar a amizade. Tente novamente.';
                    }
                } finally {
                    currentButtons.forEach(control => { control.disabled = false; });
                }
            });
            container.append(button);
        }
    }

    document.addEventListener('synchro:public-profile', ({detail: user}) => {
        const container = document.getElementById('profileFriendshipActions');
        const status = document.getElementById('profileFriendshipStatus');
        if (!container || !status) return;

        const labels = {SENT: 'Pedido de amizade enviado', RECEIVED: 'Pedido de amizade recebido', FRIENDS: 'Amigos'};
        function render(state) {
            buttons(container, state, user.id, {status, update: render, reload: load, style: 'lastfm-button lastfm-button-secondary'});
            if (labels[state]) {
                const label = document.createElement('span');
                label.textContent = labels[state];
                container.prepend(label);
            }
        }
        async function load() {
            status.textContent = '';
            try {
                const result = await request(user.id);
                render(result.state);
            } catch (error) {
                container.replaceChildren();
                if (error.status === 401) {
                    const login = document.createElement('a');
                    login.href = '/frontend/pages/login.html';
                    login.textContent = 'Entre para adicionar amigos';
                    container.append(login);
                    return;
                }
                const retry = document.createElement('button');
                retry.type = 'button';
                retry.className = 'lastfm-button lastfm-button-secondary';
                retry.textContent = 'Tentar novamente';
                retry.disabled = false;
                retry.addEventListener('click', () => {
                    retry.disabled = true;
                    load();
                });
                container.append(retry);
                status.textContent = 'Não foi possível consultar a amizade.';
            }
        }
        load();
    });

    document.addEventListener('DOMContentLoaded', () => {
        const status = document.getElementById('friendshipListStatus');
        if (!status) return;
        const sections = document.getElementById('friendshipLists');
        const login = document.getElementById('friendshipListLogin');
        const retry = document.getElementById('friendshipListRetry');
        const states = ['RECEIVED', 'SENT', 'FRIENDS'];
        let connections = [];

        function render() {
            for (const state of states) {
                const list = sections.querySelector('[data-friendship-list="' + state + '"]');
                const empty = sections.querySelector('[data-friendship-empty="' + state + '"]');
                list.replaceChildren();
                for (const connection of connections.filter(item => item.state === state)) {
                    const {user} = connection;
                    const row = document.createElement('li');
                    const link = document.createElement('a');
                    const controls = document.createElement('div');
                    controls.className = 'friendship-actions';
                    link.href = '/frontend/pages/perfil-publico.html?' + new URLSearchParams({user: user.id});
                    link.textContent = '@' + user.username;
                    buttons(controls, state, user.id, {status, name: user.username, reload: load, update(next) {
                        if (next === 'NONE') connections = connections.filter(item => item !== connection);
                        else connection.state = next;
                        render();
                    }});
                    row.append(link, controls);
                    list.append(row);
                }
                empty.hidden = list.childElementCount > 0;
            }
        }

        async function load() {
            retry.hidden = true;
            login.hidden = true;
            status.textContent = 'Carregando amizades…';
            try {
                connections = await request();
                render();
                sections.hidden = false;
                status.textContent = '';
            } catch (error) {
                if (error.status === 401) {
                    sections.hidden = true;
                    status.textContent = 'Entre para ver seus amigos e pedidos de amizade.';
                    login.hidden = false;
                } else {
                    status.textContent = 'Não foi possível carregar as amizades.';
                    retry.hidden = false;
                }
            }
        }
        retry.addEventListener('click', load);
        load();
    });
})();
