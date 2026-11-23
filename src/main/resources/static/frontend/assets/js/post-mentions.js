(() => {
    const mounted = new WeakSet();

    function initialize(textarea) {
        if (!textarea || mounted.has(textarea)) return;
        mounted.add(textarea);

        const list = document.createElement('ul');
        list.className = 'post-mention-choices';
        list.id = textarea.id + '-mentions';
        list.setAttribute('aria-label', 'Sugestões de menções');
        list.hidden = true;
        textarea.setAttribute('aria-controls', list.id);
        textarea.after(list);

        let timer;
        let pending;
        let currentRange;

        function findRange() {
            const cursor = textarea.selectionStart;
            if (cursor !== textarea.selectionEnd) return null;
            const match = /(?:^|[^\p{L}\p{M}\p{N}_.@-])(@([\p{L}\p{M}\p{N}_.-]{1,100}))$/u
                .exec(textarea.value.slice(0, cursor));
            if (!match) return null;
            const remainder = /^[\p{L}\p{M}\p{N}_.-]*/u.exec(textarea.value.slice(cursor))[0];
            return {start: cursor - match[1].length, end: cursor + remainder.length, q: match[2]};
        }

        function close() {
            clearTimeout(timer);
            pending?.abort();
            pending = null;
            currentRange = null;
            list.hidden = true;
            list.replaceChildren();
        }

        function select(username) {
            const range = currentRange;
            close();
            if (!range) return;
            const tag = /^[\p{L}\p{M}\p{N}_.-]+$/u.test(username) ? '@' + username : '@{' + username + '}';
            const replacement = tag + ' ';
            const newLength = textarea.value.length - (range.end - range.start) + replacement.length;
            if (textarea.maxLength >= 0 && newLength > textarea.maxLength) return;
            textarea.setRangeText(replacement, range.start, range.end, 'end');
            textarea.dispatchEvent(new Event('input', {bubbles: true}));
            textarea.focus();
        }

        async function search(range) {
            const controller = new AbortController();
            pending = controller;
            try {
                const url = '/search/users?' + new URLSearchParams({q: '@' + range.q});
                const response = await fetch(url, {
                    signal: AbortSignal.any([controller.signal, AbortSignal.timeout(6000)]),
                    headers: {Accept: 'application/json'}
                });
                if (!response.ok || pending !== controller) return;
                const data = await response.json();
                if (pending !== controller) return;
                const names = data.items.map(item => item.username)
                    .filter(name => typeof name === 'string' && name.trim() && !/[{}\n\r]/.test(name)).slice(0, 5);
                if (!names.length) return;
                currentRange = range;
                list.replaceChildren(...names.map(username => {
                    const row = document.createElement('li');
                    const button = document.createElement('button');
                    button.type = 'button';
                    button.textContent = '@' + username;
                    button.addEventListener('mousedown', event => event.preventDefault());
                    button.addEventListener('click', () => select(username));
                    row.append(button);
                    return row;
                }));
                list.hidden = false;
            } catch {
                // Typing remains available if user search fails.
            }
        }

        textarea.addEventListener('input', () => {
            close();
            const range = findRange();
            if (range) timer = setTimeout(() => search(range), 220);
        });

        textarea.addEventListener('keydown', event => {
            if (list.hidden) return;
            if (event.key === 'Escape') {
                close();
                event.preventDefault();
                event.stopPropagation();
            } else if (event.key === 'ArrowDown') {
                list.querySelector('button')?.focus();
                event.preventDefault();
            }
        });

        list.addEventListener('keydown', event => {
            if (event.key === 'Escape') {
                close();
                textarea.focus();
                event.preventDefault();
                event.stopPropagation();
            } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                const buttons = [...list.querySelectorAll('button')];
                const index = buttons.indexOf(event.target);
                const next = index + (event.key === 'ArrowDown' ? 1 : -1);
                if (next < 0) textarea.focus();
                else buttons[next]?.focus();
                event.preventDefault();
            }
        });

        function onBlur(event) {
            if (event.relatedTarget === textarea || list.contains(event.relatedTarget)) return;
            close();
        }
        textarea.addEventListener('focusout', onBlur);
        list.addEventListener('focusout', onBlur);
    }

    window.SynchroPostMentions = {initialize};
})();
