(() => {
    const pending = new WeakMap();
    const ready = [];
    const maxConcurrent = 4;
    let active = 0;

    function drain() {
        while (active < maxConcurrent && ready.length) {
            const {element, load} = ready.shift();
            if (!element.isConnected) continue;
            active++;
            Promise.resolve().then(load).catch(() => {
                // The card keeps its placeholder if an image lookup fails.
            }).finally(() => {
                active--;
                drain();
            });
        }
    }

    const observer = typeof IntersectionObserver === 'function' ? new IntersectionObserver(entries => {
        for (const entry of entries) {
            if (!entry.isIntersecting) continue;
            const load = pending.get(entry.target);
            pending.delete(entry.target);
            observer.unobserve(entry.target);
            if (load) ready.push({element: entry.target, load});
        }
        drain();
    }, {rootMargin: '180px'}) : null;

    function whenNear(element, load) {
        if (observer) {
            pending.set(element, load);
            observer.observe(element);
        } else {
            ready.push({element, load});
            drain();
        }
    }

    window.SynchroImageVisibility = {whenNear};
})();
