(() => {
    let accountRequest;

    function currentUser() {
        accountRequest ??= fetch('/users/me', {
            credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(10000)
        }).then(response => response.ok ? response.json() : null).catch(() => null);
        return accountRequest;
    }

    window.addEventListener('pageshow', event => {
        if (event.persisted) accountRequest = undefined;
    });

    function addAction(container, label, action) {
        currentUser().then(user => {
            if (user?.role !== 'VVIP' || !container.isConnected) return;
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'moderation-delete';
            button.textContent = label;
            button.addEventListener('click', () => action(button));
            container.append(button);
        });
    }

    function addDelete(container, {url, label, status, onDelete}) {
        addAction(container, label, async button => {
            if (!window.confirm(label + '?')) return;
            button.disabled = true;
            try {
                const response = await fetch(url, {
                    method: 'DELETE', credentials: 'same-origin', signal: AbortSignal.timeout(10000)
                });
                if (!response.ok && response.status !== 404) throw new Error();
                await onDelete();
                if (status) status.textContent = 'Conteúdo excluído.';
            } catch {
                if (status) status.textContent = 'Não foi possível excluir. Tente novamente.';
                else button.textContent = 'Falha ao excluir. Tente novamente.';
            } finally {
                button.disabled = false;
            }
        });
    }

    function appendReviewBadge(container, authorVvip) {
        if (authorVvip !== true) return;
        const badge = document.createElement('span');
        badge.className = 'profile-role-badge review-role-badge';
        badge.dataset.role = 'VVIP';
        badge.textContent = 'VVIP';
        container.append(badge);
    }

    window.SynchroModeration = {currentUser, addAction, addDelete, appendReviewBadge};
})();
