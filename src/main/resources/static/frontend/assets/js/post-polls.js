(() => {
    const minimumOptions = 2;
    const maximumOptions = 5;
    const maximumQuestionLength = 160;
    const maximumOptionLength = 80;

    function button(label, handler) {
        const element = document.createElement('button');
        element.type = 'button';
        element.textContent = label;
        element.addEventListener('click', handler);
        return element;
    }

    function input(maxLength) {
        const element = document.createElement('input');
        element.type = 'text';
        element.maxLength = maxLength;
        element.autocomplete = 'off';
        return element;
    }

    function createEditor() {
        const element = document.createElement('div');
        element.className = 'post-poll-editor';
        const fields = document.createElement('fieldset');
        fields.id = 'postPollFields';
        fields.hidden = true;
        const legend = document.createElement('legend');
        legend.textContent = 'Enquete musical';

        const questionLabel = document.createElement('label');
        const question = input(maximumQuestionLength);
        questionLabel.append('Pergunta', question);
        const optionList = document.createElement('div');
        optionList.className = 'post-poll-options';
        const actions = document.createElement('div');
        actions.className = 'post-poll-editor-actions';

        const options = [];
        const add = button('Adicionar opção', () => appendOption(true));
        const remove = button('Remover enquete', () => {
            reset();
            toggle.focus();
        });
        const toggle = button('Adicionar enquete', () => {
            setOpen(true);
            question.focus();
        });
        toggle.setAttribute('aria-controls', fields.id);

        actions.append(add, remove);
        fields.append(legend, questionLabel, optionList, actions);
        element.append(toggle, fields);

        function setOpen(open) {
            fields.hidden = !open;
            toggle.hidden = open;
            toggle.setAttribute('aria-expanded', String(open));
        }

        function updateOptions() {
            options.forEach((option, index) => {
                option.caption.textContent = 'Opção ' + (index + 1);
                option.remove.setAttribute('aria-label', 'Remover opção ' + (index + 1));
                option.remove.hidden = options.length <= minimumOptions;
            });
            add.disabled = options.length >= maximumOptions;
        }

        function appendOption(focus = false) {
            if (options.length >= maximumOptions) return;
            const row = document.createElement('div');
            row.className = 'post-poll-option';
            const label = document.createElement('label');
            const caption = document.createElement('span');
            const field = input(maximumOptionLength);
            const item = {row, caption, field, remove: button('Remover', () => {
                if (options.length <= minimumOptions) return;
                const index = options.indexOf(item);
                options.splice(index, 1);
                row.remove();
                updateOptions();
                options[Math.min(index, options.length - 1)].field.focus();
            })};
            label.append(caption, field);
            row.append(label, item.remove);
            options.push(item);
            optionList.append(row);
            updateOptions();
            if (focus) field.focus();
        }

        function reset() {
            question.value = '';
            options.length = 0;
            optionList.replaceChildren();
            for (let index = 0; index < minimumOptions; index++) appendOption();
            setOpen(false);
        }

        function value() {
            if (fields.hidden) return null;
            const title = question.value.trim();
            if (!title || title.length > maximumQuestionLength) {
                question.focus();
                throw new Error('Escreva uma pergunta de até 160 caracteres.');
            }

            const labels = [];
            const distinct = new Set();
            for (const {field} of options) {
                const label = field.value.trim();
                if (!label || label.length > maximumOptionLength) {
                    field.focus();
                    throw new Error('Preencha cada opção com até 80 caracteres.');
                }
                const key = label.normalize('NFKC').replace(/\s+/gu, ' ').toLowerCase();
                if (distinct.has(key)) {
                    field.focus();
                    throw new Error('Use opções diferentes na enquete.');
                }
                distinct.add(key);
                labels.push(label);
            }
            return {question: title, options: labels};
        }

        function disable(disabled) {
            fields.disabled = toggle.disabled = disabled;
        }

        reset();
        return {element, value, reset, disable};
    }

    function voteCount(votes) {
        return votes + (votes === 1 ? ' voto' : ' votos');
    }

    function attach(article, post, signedIn) {
        if (!post.poll) return;
        const poll = document.createElement('section');
        poll.className = 'post-poll';
        const question = document.createElement('h3');
        question.id = 'post-poll-question-' + post.id;
        question.textContent = post.poll.question;
        poll.setAttribute('aria-labelledby', question.id);
        const choices = document.createElement('div');
        choices.className = 'post-poll-results';
        const total = document.createElement('p');
        total.className = 'post-poll-total';
        const status = document.createElement('p');
        status.className = 'post-poll-status';
        status.setAttribute('role', 'status');
        const rows = new Map();
        let busy = false;

        for (const option of post.poll.options) {
            const row = signedIn ? button(option.text, () => vote(option.id)) : document.createElement('div');
            row.className = 'post-poll-result';
            const title = document.createElement('span');
            title.className = 'post-poll-option-text';
            title.textContent = option.text;
            const detail = document.createElement('small');
            const bar = document.createElement('progress');
            bar.max = 100;
            bar.setAttribute('aria-hidden', 'true');
            row.replaceChildren(title, detail, bar);
            rows.set(option.id, {row, detail, bar});
            choices.append(row);
        }

        poll.append(question, choices, total, status);
        if (!signedIn) {
            const login = document.createElement('a');
            login.href = '/frontend/pages/login.html';
            login.textContent = 'Entre para votar';
            poll.append(login);
        }
        article.append(poll);
        update();

        function update() {
            const {options, totalVotes, selectedOption} = post.poll;
            total.textContent = voteCount(totalVotes);
            for (const option of options) {
                const {row, detail, bar} = rows.get(option.id);
                const percent = totalVotes ? Math.round(100 * option.votes / totalVotes) : 0;
                const selected = option.id === selectedOption;
                detail.textContent = voteCount(option.votes) + ' · ' + percent + '%' + (selected ? ' · Seu voto' : '');
                bar.value = percent;
                if (signedIn) row.setAttribute('aria-pressed', String(selected));
            }
        }

        async function vote(optionId) {
            if (busy || post.poll.selectedOption === optionId) return;
            busy = true;
            status.textContent = '';
            for (const {row} of rows.values()) row.setAttribute('aria-disabled', 'true');
            try {
                const response = await fetch('/posts/' + encodeURIComponent(post.id) + '/poll/vote', {
                    method: 'PUT',
                    credentials: 'same-origin',
                    cache: 'no-store',
                    headers: {'Content-Type': 'application/json'},
                    signal: AbortSignal.timeout(10000),
                    body: JSON.stringify({optionId})
                });
                if (response.status === 401) {
                    status.textContent = 'Sua sessão expirou. Entre novamente para votar.';
                    return;
                }
                if (!response.ok) throw new Error();
                post.poll = await response.json();
                update();
                status.textContent = 'Voto registrado.';
            } catch {
                status.textContent = 'Não foi possível votar. Tente novamente.';
            } finally {
                busy = false;
                for (const {row} of rows.values()) row.removeAttribute('aria-disabled');
            }
        }
    }

    window.SynchroPostPolls = {createEditor, attach};
})();
