(() => {
    const dialog = document.getElementById('profileSelectionDialog');
    const form = document.getElementById('profileSelectionForm');
    const title = document.getElementById('profileSelectionTitle');
    const items = document.getElementById('profileSelectionItems');
    const choices = document.getElementById('profileSelectionChoices');
    const count = document.getElementById('profileSelectionCount');
    const status = document.getElementById('profileSelectionStatus');
    const save = document.getElementById('saveProfileSelection');
    const limit = 10;
    const endpoints = {
        reviews: '/review/me/profile-selection',
        playlists: '/playlists/me/profile-selection'
    };
    let active;

    function updateCount() {
        const boxes = Array.from(choices.querySelectorAll('input'));
        const selected = boxes.filter(box => box.checked).length;
        count.textContent = selected + ' de ' + limit + ' selecionados';
        for (const box of boxes) box.disabled = !box.checked && selected >= limit;
    }

    async function openSelection(kind, heading) {
        active?.controller.abort();
        const request = {endpoint: endpoints[kind], controller: new AbortController()};
        active = request;
        title.textContent = heading;
        items.hidden = true;
        save.disabled = true;
        choices.replaceChildren();
        status.textContent = 'Carregando itens…';
        dialog.showModal();
        try {
            const response = await fetch(request.endpoint, {
                cache: 'no-store', credentials: 'same-origin', signal: request.controller.signal
            });
            if (!response.ok) throw new Error();
            const data = await response.json();
            if (active !== request) return;
            if (data.items.length <= limit) {
                status.textContent = 'Todos os itens disponíveis já aparecem no perfil.';
                return;
            }
            const selected = new Set(data.selectedIds);
            for (const item of data.items) {
                const label = document.createElement('label');
                label.className = 'profile-selection-choice';
                const box = document.createElement('input');
                box.type = 'checkbox';
                box.name = 'ids';
                box.value = String(item.id);
                box.checked = selected.has(item.id);
                label.append(box, document.createTextNode(item.title || 'Sem título'));
                choices.append(label);
            }
            updateCount();
            items.hidden = false;
            save.disabled = false;
            status.textContent = '';
        } catch {
            if (active === request) status.textContent = 'Não foi possível carregar os itens. Feche e tente novamente.';
        }
    }

    for (const trigger of document.querySelectorAll('[data-profile-selection]')) {
        trigger.addEventListener('click', () => openSelection(trigger.dataset.profileSelection, trigger.textContent));
    }

    choices.addEventListener('change', updateCount);
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (!active || save.disabled) return;
        const request = active;
        const ids = new FormData(form).getAll('ids').map(Number);
        save.disabled = true;
        status.textContent = 'Salvando…';
        try {
            const response = await fetch(request.endpoint, {
                method: 'PUT', credentials: 'same-origin', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ids})
            });
            if (!response.ok) throw new Error();
            if (active === request) status.textContent = 'Seleção salva no perfil público.';
        } catch {
            if (active === request) status.textContent = 'Não foi possível salvar a seleção. Tente novamente.';
        } finally {
            if (active === request) save.disabled = false;
        }
    });

    document.getElementById('closeProfileSelection').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => {
        if (dialog.open) return;
        active?.controller.abort();
        active = undefined;
    });
})();
