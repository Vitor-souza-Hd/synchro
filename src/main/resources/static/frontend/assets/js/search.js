(() => {
    if (window.SynchroSearch) {
        window.SynchroSearch.initialize();
        return;
    }
    const retryDelays = [1000, 2000, 4000, 6000, 7000, 9000];
    const searchDeadline = 30000;

    function waitForRetry(delay, signal) {
        return new Promise(resolve => {
            const done = () => {
                clearTimeout(timer);
                signal.removeEventListener('abort', done);
                resolve();
            };
            const timer = setTimeout(done, delay);
            signal.addEventListener('abort', done, {once: true});
            if (signal.aborted) done();
        });
    }

    function initializeSearch() {
        document.querySelectorAll('input.search').forEach((input, index) => {
            if (input.dataset.synchroSearchReady === 'true') return;
            input.dataset.synchroSearchReady = 'true';
            const box = input.closest('.search-box');
            if (box) {
                box.dataset.synchroSearchReady = 'true';
                box.querySelectorAll('#lastfm-search-results').forEach(element => element.remove());
            }
            const panel = document.createElement('section');
            panel.id = `synchro-search-results-${index}`;
            panel.className = 'synchro-search-results';
            panel.setAttribute('aria-label', 'Resultados da pesquisa');
            panel.hidden = true;
            document.body.append(panel);
            input.type = 'search';
            input.autocomplete = 'off';
            input.maxLength = 200;
            input.placeholder = 'Buscar música ou pessoas';
            input.setAttribute('aria-label', 'Buscar músicas, artistas, álbuns ou usuários');
            input.setAttribute('aria-controls', panel.id);
            const submit = document.createElement('button');
            submit.type = 'button';
            submit.className = 'synchro-search-submit';
            submit.setAttribute('aria-label', 'Pesquisar');
            submit.title = 'Pesquisar';
            const icon = box?.querySelector('i');
            if (icon) { icon.replaceWith(submit); submit.append(icon); }
            else { submit.textContent = 'Buscar'; input.after(submit); }

            let controller;
            let version = 0;

            function position() {
                const box = input.closest('.search-box') || input;
                const rect = box.getBoundingClientRect();
                const width = Math.min(Math.max(rect.width, 280), window.innerWidth - 24);
                panel.style.width = `${width}px`;
                panel.style.left = `${Math.max(12, Math.min(rect.left, window.innerWidth - width - 12))}px`;
                panel.style.top = `${rect.bottom + 8}px`;
                panel.style.maxHeight = `${Math.max(100, Math.min(480, window.innerHeight - rect.bottom - 24))}px`;
            }

            function message(text) {
                panel.replaceChildren();
                const status = document.createElement('p');
                status.className = 'synchro-search-status';
                status.setAttribute('role', 'status');
                status.textContent = text;
                panel.append(status);
                position();
                panel.hidden = false;
            }

            function cancel() {
                controller?.abort();
                version++;
            }

            function close() {
                cancel();
                panel.hidden = true;
                input.removeAttribute('aria-busy');
            }

            function render(data, term) {
                const groups = [
                    ['Usuários', data.users, item => item.fullName?.trim() || '@' + item.username,
                        item => item.fullName?.trim() ? '@' + item.username : '', 'perfil-publico'],
                    ['Músicas', data.songs, item => item.titulo,
                        item => [item.lastFm?.artistName || item.artistas?.map(artist => artist.nome).join(', '), item.album?.titulo].filter(Boolean).join(' · '), 'musica'],
                    ['Artistas', data.artists, item => item.nome, () => 'Artista', 'artista'],
                    ['Álbuns', data.albums, item => item.titulo,
                        item => item.lastFm?.artistName || item.artistas?.map(artist => artist.nome).join(', ') || 'Álbum', 'album']
                ];
                if (!groups.some(([, items]) => Array.isArray(items) && items.length)) {
                    message(data.refreshing ? 'Pesquisando no Last.fm…' : data.warning ||
                        (term.startsWith('@') ? 'Nenhum usuário encontrado.' : 'Nenhum resultado encontrado.'));
                    return;
                }
                panel.replaceChildren();
                const source = document.createElement('p');
                source.className = 'synchro-search-status';
                source.textContent = data.warning || (data.refreshing
                    ? 'Resultados salvos · buscando mais resultados no Last.fm…'
                    : term.startsWith('@') || data.source === 'local' ? 'Resultados do Synchro' : 'Resultados do Synchro e do Last.fm');
                panel.append(source);
                groups.forEach(([label, items, title, description, page]) => {
                    if (!Array.isArray(items) || !items.length) return;
                    const heading = document.createElement('h3');
                    const morePeople = page === 'perfil-publico' && (data.hasMoreUsers || items.length > 10);
                    heading.textContent = morePeople ? label : `${label} (${items.length})`;
                    const list = document.createElement('ul');
                    items.slice(0, 10).forEach(item => {
                        const row = document.createElement('li');
                        const name = document.createElement('strong');
                        const detail = document.createElement('span');
                        const link = document.createElement('a');
                        link.href = `/frontend/pages/${page}.html?${page === 'perfil-publico' ? 'user' : 'id'}=${encodeURIComponent(item.id)}`;
                        name.textContent = title(item);
                        detail.textContent = description(item);
                        link.append(name);
                        if (detail.textContent) link.append(detail);
                        row.append(link);
                        list.append(row);
                    });
                    if (morePeople) {
                        const row = document.createElement('li');
                        const all = document.createElement('a');
                        all.href = '/frontend/pages/pessoas.html?q=' + encodeURIComponent(term);
                        all.textContent = 'Ver todos os usuários';
                        row.append(all);
                        list.append(row);
                    }
                    panel.append(heading, list);
                    if (!morePeople && items.length > 10) {
                        const note = document.createElement('p');
                        note.className = 'synchro-search-status';
                        note.textContent = 'Mostrando os primeiros 10. Digite mais para refinar a pesquisa.';
                        panel.append(note);
                    }
                });
                position();
                panel.hidden = false;
            }

            async function search(term, requestVersion) {
                const activeController = new AbortController();
                controller = activeController;
                const signal = activeController.signal;
                let timedOut = false;
                const timeout = setTimeout(() => {
                    timedOut = true;
                    activeController.abort();
                }, searchDeadline);
                let latest;
                let previous;
                const showPartial = warning => render({...latest, refreshing: false, warning}, term);
                input.setAttribute('aria-busy', 'true');
                message(term.startsWith('@') ? 'Buscando usuários…' : 'Pesquisando no Synchro e no Last.fm…');
                try {
                    for (let attempt = 0; attempt <= retryDelays.length; attempt++) {
                        const response = await fetch(`/search?q=${encodeURIComponent(term)}`, {
                            signal, headers: { Accept: 'application/json' }
                        });
                        if (!response.ok) throw new Error('Pesquisa indisponível');
                        const data = await response.json();
                        if (requestVersion !== version) return;
                        signal.throwIfAborted();
                        latest = data;
                        const delay = retryDelays[attempt];
                        if (data.refreshing && delay === undefined) break;
                        const snapshot = JSON.stringify(data);
                        if (snapshot !== previous) render(data, term);
                        previous = snapshot;
                        if (!data.refreshing) return;
                        await waitForRetry(delay, signal);
                        signal.throwIfAborted();
                    }
                    showPartial('O Last.fm ainda está respondendo. Os resultados disponíveis já foram exibidos.');
                } catch {
                    if (requestVersion !== version || signal.aborted && !timedOut) return;
                    if (latest) {
                        showPartial(timedOut
                            ? 'O Last.fm demorou para responder. Os resultados disponíveis já foram exibidos.'
                            : 'Não foi possível atualizar a pesquisa. Os resultados disponíveis já foram exibidos.');
                    } else {
                        message(timedOut ? 'A pesquisa demorou para responder. Tente novamente.'
                            : 'Não foi possível pesquisar agora. Tente novamente.');
                    }
                } finally {
                    clearTimeout(timeout);
                    if (requestVersion === version) input.removeAttribute('aria-busy');
                }
            }

            function schedule(immediate = false) {
                cancel();
                input.removeAttribute('aria-busy');
                const term = input.value.trim();
                if (term.length < 2) {
                    if (term) message('Digite pelo menos 2 caracteres.');
                    else panel.hidden = true;
                    return;
                }
                const requestVersion = version;
                if (immediate) search(term, requestVersion);
                else message('Pressione Enter ou clique na lupa para pesquisar.');
            }

            input.addEventListener('input', () => schedule());
            input.addEventListener('focus', () => { if (input.value.trim()) schedule(); });
            submit.addEventListener('click', () => schedule(true));
            input.addEventListener('keydown', event => {
                if (event.key === 'Enter') {
                    event.preventDefault();
                    schedule(true);
                } else if (event.key === 'Escape') close();
            });
            document.addEventListener('pointerdown', event => {
                if (!box?.contains(event.target) && event.target !== input && !panel.contains(event.target)) close();
            });
            document.addEventListener('focusin', event => {
                if (!box?.contains(event.target) && event.target !== input && !panel.contains(event.target)) close();
            });
            window.addEventListener('resize', position);
            window.addEventListener('scroll', position, true);
        });
    }

    window.SynchroSearch = {initialize: initializeSearch};
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initializeSearch, {once: true});
    else initializeSearch();
})();
