(() => {
    const form = document.getElementById('peopleSearch');
    const input = document.getElementById('peopleQuery');
    const status = document.getElementById('peopleStatus');
    const results = document.getElementById('peopleResults');
    const pagination = document.getElementById('peoplePages');
    const previous = document.getElementById('peoplePrevious');
    const next = document.getElementById('peopleNext');
    const pageNumber = document.getElementById('peoplePageNumber');
    let controller;
    let currentTerm = '';
    let currentPage = 0;

    function profileLink(person) {
        const link = document.createElement('a');
        link.href = 'perfil-publico.html?' + new URLSearchParams({user: person.id});
        const avatar = document.createElement('span');
        avatar.className = 'person-initials';
        avatar.setAttribute('aria-hidden', 'true');
        const name = (person.fullName || '').trim();
        const initials = (name || person.username).split(/\s+/).slice(0, 2).map(part => Array.from(part)[0]).join('');
        avatar.textContent = initials.toLocaleUpperCase('pt-BR');

        const details = document.createElement('span');
        if (name && name !== person.username) {
            const displayName = document.createElement('strong');
            displayName.textContent = name;
            details.append(displayName);
        }
        const handle = document.createElement('span');
        handle.className = 'person-handle';
        handle.textContent = '@' + person.username;
        details.append(handle);
        link.append(avatar, details);

        const row = document.createElement('li');
        row.append(link);
        return row;
    }

    async function search(term, page = 0) {
        controller?.abort();
        currentTerm = term.trim();
        currentPage = page;
        const url = new URL(window.location.href);
        if (currentTerm) url.searchParams.set('q', currentTerm);
        else url.searchParams.delete('q');
        if (page) url.searchParams.set('page', String(page));
        else url.searchParams.delete('page');
        history.replaceState(null, '', url);
        results.replaceChildren();
        pagination.hidden = true;
        if (!currentTerm || currentTerm === '@') {
            status.textContent = 'Digite um nome ou @usuário para buscar.';
            return;
        }

        controller = new AbortController();
        const {signal} = controller;
        status.textContent = 'Buscando pessoas…';
        try {
            const query = new URLSearchParams({q: currentTerm, page});
            const response = await fetch('/search/users?' + query, {signal});
            if (!response.ok) throw new Error();
            const {items, hasNext} = await response.json();
            if (signal.aborted) return;

            results.replaceChildren(...items.map(profileLink));
            status.textContent = items.length
                ? items.length === 1 ? '1 resultado nesta página.' : items.length + ' resultados nesta página.'
                : 'Nenhum usuário encontrado.';
            previous.disabled = page === 0;
            next.disabled = !hasNext;
            pageNumber.textContent = 'Página ' + (page + 1);
            pagination.hidden = !page && !hasNext;
        } catch {
            if (!signal.aborted) status.textContent = 'Não foi possível buscar usuários. Tente novamente.';
        }
    }

    form.addEventListener('submit', event => {
        event.preventDefault();
        search(input.value);
    });
    previous.addEventListener('click', () => search(currentTerm, currentPage - 1));
    next.addEventListener('click', () => search(currentTerm, currentPage + 1));

    const initial = new URLSearchParams(window.location.search);
    if (initial.has('q')) {
        input.value = initial.get('q').slice(0, 200);
        const page = Number(initial.get('page'));
        search(input.value, Number.isSafeInteger(page) && page > 0 ? page : 0);
    }
})();
