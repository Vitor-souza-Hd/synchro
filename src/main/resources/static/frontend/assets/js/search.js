(() => {
    function initializeSearch() {
        document.querySelectorAll('input.search').forEach((input, index) => {
            const panel = document.createElement('section');
            panel.id = `synchro-search-results-${index}`;
            panel.className = 'synchro-search-results';
            panel.setAttribute('aria-label', 'Resultados da pesquisa');
            panel.hidden = true;
            document.body.append(panel);
            input.type = 'search';
            input.autocomplete = 'off';
            input.maxLength = 200;
            input.placeholder = 'Pesquisar no Last.fm — pressione Enter';
            input.setAttribute('aria-controls', panel.id);
            const box = input.closest('.search-box');
            const submit = document.createElement('button');
            submit.type = 'button';
            submit.className = 'synchro-search-submit';
            submit.setAttribute('aria-label', 'Pesquisar no Last.fm');
            submit.title = 'Pesquisar no Last.fm';
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

            function render(data) {
                const groups = [
                    ['Músicas', data.songs, item => item.titulo,
                        item => [item.lastFm?.artistName || item.artistas?.map(artist => artist.nome).join(', '), item.album?.titulo].filter(Boolean).join(' · '), 'musica'],
                    ['Artistas', data.artists, item => item.nome, () => 'Artista', 'artista'],
                    ['Álbuns', data.albums, item => item.titulo,
                        item => item.lastFm?.artistName || item.artistas?.map(artist => artist.nome).join(', ') || 'Álbum', 'album'],
                    ['Usuários', data.users, item => item.username, () => 'Usuário', 'user']
                ];
                if (!groups.some(([, items]) => Array.isArray(items) && items.length)) {
                    message(data.warning || 'Nenhum resultado encontrado no Last.fm.');
                    return;
                }
                panel.replaceChildren();
                const source = document.createElement('p');
                source.className = 'synchro-search-status';
                source.textContent = data.warning || 'Resultados do Last.fm · salvos no Synchro';
                panel.append(source);
                groups.forEach(([label, items, title, description, page]) => {
                    if (!Array.isArray(items) || !items.length) return;
                    const heading = document.createElement('h3');
                    heading.textContent = `${label} (${items.length})`;
                    const list = document.createElement('ul');
                    items.slice(0, 10).forEach(item => {
                        const row = document.createElement('li');
                        const name = document.createElement('strong');
                        const detail = document.createElement('span');
                        const link = document.createElement('a');
                        link.href = `/frontend/pages/${page}.html?${page === 'user' ? 'user' : 'id'}=${encodeURIComponent(item.id)}`;
                        name.textContent = title(item);
                        detail.textContent = description(item);
                        link.append(name, detail);
                        row.append(link);
                        list.append(row);
                    });
                    panel.append(heading, list);
                    if (items.length > 10) {
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
                controller = new AbortController();
                input.setAttribute('aria-busy', 'true');
                message('Pesquisando no Last.fm e salvando os detalhes…');
                try {
                    const response = await fetch(`/search?q=${encodeURIComponent(term)}`, {
                        signal: controller.signal,
                        headers: { Accept: 'application/json' }
                    });
                    if (!response.ok) throw new Error('Pesquisa indisponível');
                    const data = await response.json();
                    if (requestVersion === version) render(data);
                } catch (error) {
                    if (error.name !== 'AbortError' && requestVersion === version) {
                        message('Não foi possível pesquisar agora. Tente novamente.');
                    }
                } finally {
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
                else message('Pressione Enter ou clique na lupa para pesquisar no Last.fm.');
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

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initializeSearch);
    else initializeSearch();
})();
