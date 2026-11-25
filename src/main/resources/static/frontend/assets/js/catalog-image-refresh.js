(() => {
    const tools = document.getElementById('catalogImageTools');
    const button = document.getElementById('refreshCatalogImage');
    const status = document.getElementById('catalogImageStatus');
    let binding;
    let busy = false;

    function bind(options, getImage, afterReplace) {
        const key = JSON.stringify([options.kind, options.id ?? [options.title, options.artist]]);
        if (binding?.key !== key) binding = {key, seen: new Set(), replaced: false};
        Object.assign(binding, {options, getImage, afterReplace});
        tools.hidden = false;
    }

    function displayed(entry) {
        const image = entry.getImage();
        return image?.currentSrc || image?.src;
    }

    button.addEventListener('click', async () => {
        if (!binding || busy) return;
        const current = binding;
        busy = button.disabled = true;
        status.textContent = 'Buscando outra imagem…';
        let timer;
        let expired = false;
        try {
            const timeout = new Promise(resolve => {
                timer = setTimeout(() => {
                    expired = true;
                    resolve(null);
                }, 45000);
            });
            const result = await Promise.race([timeout,
                window.SynchroFavoriteImages.findAlternative(current.options,
                    () => binding === current && !expired,
                    [...current.seen], () => displayed(current))]);
            if (binding !== current) return;
            if (!result) {
                status.textContent = expired ? 'A busca demorou demais. A imagem atual foi mantida.'
                    : 'Nenhuma outra imagem foi encontrada.';
                return;
            }
            const previous = current.getImage();
            if (!previous) throw new Error('Imagem indisponível.');
            current.seen.add(previous.currentSrc || previous.src);
            current.seen.add(result.src);
            const next = previous.cloneNode(false);
            next.hidden = false;
            next.src = result.src;
            window.SynchroFavoriteImages.cancel(previous.parentElement);
            current.replaced = true;
            previous.replaceWith(next);
            current.afterReplace?.();
            status.textContent = 'Nova imagem exibida.';
        } catch {
            if (binding === current) status.textContent = 'Não foi possível buscar outra imagem agora.';
        } finally {
            clearTimeout(timer);
            busy = button.disabled = false;
        }
    });

    window.SynchroCatalogImage = {bind, hasReplacement: () => binding?.replaced === true};
})();
