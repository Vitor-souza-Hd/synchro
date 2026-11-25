(() => {
    const url = '/vvip-invitations';
    const section = document.getElementById('vvipInvitations');
    const shortcut = document.getElementById('vvipInvitationsLink');
    const outgoing = document.getElementById('vvipOutgoing');
    const incoming = document.getElementById('vvipIncoming');
    const sentRows = document.getElementById('vvipOutgoingRows');
    const receivedRows = document.getElementById('vvipIncomingRows');
    const status = document.getElementById('vvipInvitationStatus');
    const form = document.getElementById('vvipInviteForm');
    const username = document.getElementById('vvipInviteUsername');
    const dateFormat = new Intl.DateTimeFormat('pt-BR', {dateStyle: 'short'});
    const labels = {PENDING: 'Pendente', ACCEPTED: 'Aceito', DECLINED: 'Recusado', CANCELED: 'Cancelado'};
    let busy = false;

    function cell(text) {
        const element = document.createElement('td');
        element.textContent = text;
        return element;
    }

    function sentRow(entry) {
        const row = document.createElement('tr');
        const date = new Date(entry.createdAt);
        row.append(cell('@' + entry.username), cell(labels[entry.status] || entry.status),
            cell(Number.isNaN(date.getTime()) ? '—' : dateFormat.format(date)));
        const actions = cell('');
        if (entry.status === 'PENDING') {
            const cancel = document.createElement('button');
            cancel.type = 'button';
            cancel.textContent = 'Cancelar convite';
            cancel.setAttribute('aria-label', 'Cancelar convite para @' + entry.username);
            cancel.addEventListener('click', () => change(url + '/' + encodeURIComponent(entry.id), 'DELETE', null,
                'Convite cancelado.'));
            actions.append(cancel);
        }
        row.append(actions);
        return row;
    }

    function receivedRow(entry) {
        const row = document.createElement('tr');
        const actions = cell('');
        actions.className = 'vvip-invitation-actions';
        for (const [accept, text] of [[true, 'Aceitar'], [false, 'Recusar']]) {
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = text;
            button.setAttribute('aria-label', text + ' convite de @' + entry.username);
            button.addEventListener('click', () => change(url + '/' + encodeURIComponent(entry.id), 'PUT', {accept},
                accept ? 'Convite aceito. Sua conta agora é VVIP.' : 'Convite recusado.',
                accept ? () => renderProfileRole('VVIP') : undefined));
            actions.append(button);
        }
        row.append(cell('@' + entry.username), actions);
        return row;
    }

    async function request(path, options) {
        const response = await fetch(path, {cache: 'no-store', credentials: 'same-origin',
            signal: AbortSignal.timeout(10000), ...options});
        if (response.ok) return response;
        const body = await response.json().catch(() => ({}));
        const fallback = {
            401: 'Entre novamente para acessar seus convites.',
            403: 'Este convite precisa ser enviado por um VVIP ativo.',
            404: 'Conta ou convite não encontrado. Confira o @usuário.',
            409: 'Esta conta já é VVIP ou tem um convite pendente.'
        };
        throw new Error(body.message || fallback[response.status] || 'Não foi possível atualizar os convites.');
    }

    async function load(keepStatusVisible = false) {
        const response = await request(url);
        const data = await response.json();
        sentRows.replaceChildren(...data.outgoing.map(sentRow));
        if (!data.outgoing.length) {
            const row = document.createElement('tr');
            const message = cell('Nenhum convite enviado.');
            message.colSpan = 4;
            row.append(message);
            sentRows.append(row);
        }
        receivedRows.replaceChildren(...data.incoming.map(receivedRow));
        outgoing.hidden = !data.canInvite;
        incoming.hidden = !data.incoming.length;
        shortcut.hidden = outgoing.hidden && incoming.hidden;
        section.hidden = shortcut.hidden && !keepStatusVisible;
    }

    async function change(path, method, payload, success, afterSave) {
        if (busy) return;
        busy = true;
        section.querySelectorAll('button').forEach(button => { button.disabled = true; });
        status.textContent = 'Salvando…';
        try {
            const options = payload == null ? {method}
                : {method, headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)};
            await request(path, options);
            afterSave?.();
            try {
                await load(true);
                status.textContent = success;
            } catch {
                status.textContent = success + ' Recarregue a página para atualizar a tabela.';
            }
        } catch (error) {
            status.textContent = error.message || 'Não foi possível salvar o convite.';
        } finally {
            busy = false;
            section.querySelectorAll('button').forEach(button => { button.disabled = false; });
        }
    }

    form.addEventListener('submit', event => {
        event.preventDefault();
        const handle = username.value.trim();
        if (!handle) return;
        change(url, 'POST', {username: handle}, 'Convite enviado.', () => { username.value = ''; });
    });

    document.addEventListener('synchro:own-profile', ({detail: user}) => {
        if (user.role === 'VVIP') section.hidden = shortcut.hidden = false;
        load().catch(() => {
            status.textContent = 'Os convites não carregaram. Recarregue a página para tentar novamente.';
        });
    }, {once: true});
})();
