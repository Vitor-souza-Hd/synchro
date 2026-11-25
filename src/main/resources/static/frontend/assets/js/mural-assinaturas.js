(() => {
    const api = '/api/mural-assinaturas';
    const get = id => document.getElementById(id);
    const canvas = get('mural');
    const ctx = canvas.getContext('2d');
    const editor = get('muralEditor');
    const status = get('muralStatus');
    const login = get('muralLogin');
    const save = get('salvar');
    const remove = get('excluir');
    const palette = ['#ffffff', '#ff6588', '#cd2727', '#e87929', '#fbb23e', '#5ed536', '#41ca91', '#2152f1', '#6439ff', '#c423c4'];
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    let color = palette[0];
    let erasing = false;
    let pointer = null;
    let lastPoint;
    let busy = false;
    let own = null;

    const eraser = get('borracha');
    function setEraser(enabled) {
        erasing = enabled;
        eraser.classList.toggle('ativo', enabled);
        eraser.setAttribute('aria-pressed', String(enabled));
    }

    const swatches = palette.map((value, index) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = index ? 'cor' : 'cor ativa';
        button.style.background = value;
        button.setAttribute('aria-label', 'Cor ' + value);
        button.setAttribute('aria-pressed', String(index === 0));
        button.addEventListener('click', () => {
            color = value;
            setEraser(false);
            for (const swatch of swatches) {
                swatch.classList.toggle('ativa', swatch === button);
                swatch.setAttribute('aria-pressed', String(swatch === button));
            }
        });
        return button;
    });
    get('cores').append(...swatches);
    eraser.addEventListener('click', () => setEraser(!erasing));
    get('rolar').addEventListener('click', event => {
        const scrolling = canvas.classList.toggle('rolar');
        event.currentTarget.classList.toggle('ativo', scrolling);
        event.currentTarget.setAttribute('aria-pressed', String(scrolling));
    });

    function point(event) {
        const rect = canvas.getBoundingClientRect();
        return [(event.clientX - rect.left) * canvas.width / rect.width,
            (event.clientY - rect.top) * canvas.height / rect.height];
    }

    canvas.addEventListener('pointerdown', event => {
        if (busy || pointer !== null || canvas.classList.contains('rolar') || event.button !== 0) return;
        pointer = event.pointerId;
        canvas.setPointerCapture(pointer);
        lastPoint = point(event);
        ctx.globalCompositeOperation = erasing ? 'destination-out' : 'source-over';
        ctx.lineWidth = Number(get('espessura').value) * (erasing ? 3 : 1);
        ctx.strokeStyle = ctx.fillStyle = color;
        ctx.beginPath();
        ctx.arc(...lastPoint, ctx.lineWidth / 2, 0, Math.PI * 2);
        ctx.fill();
        event.preventDefault();
    });

    canvas.addEventListener('pointermove', event => {
        if (pointer !== event.pointerId) return;
        const samples = event.getCoalescedEvents?.();
        for (const sample of samples?.length ? samples : [event]) {
            const next = point(sample);
            ctx.beginPath();
            ctx.moveTo(...lastPoint);
            ctx.lineTo(...next);
            ctx.stroke();
            lastPoint = next;
        }
    });

    for (const type of ['pointerup', 'pointercancel', 'lostpointercapture']) {
        canvas.addEventListener(type, event => {
            if (pointer === event.pointerId) pointer = null;
        });
    }

    get('limpar').addEventListener('click', () => ctx.clearRect(0, 0, canvas.width, canvas.height));
    get('baixar').addEventListener('click', () => {
        const link = document.createElement('a');
        link.href = canvas.toDataURL('image/png');
        link.download = 'assinatura-synchro.png';
        link.click();
    });

    const imageUrl = entry => api + '/' + encodeURIComponent(entry.id) + '/imagem?v=' + encodeURIComponent(entry.updatedAt);
    const request = (url, options = {}) => fetch(url, {
        cache: 'no-store', credentials: 'same-origin', signal: AbortSignal.timeout(10000), ...options
    });

    function requireLogin() {
        editor.hidden = true;
        login.hidden = false;
        status.textContent = '';
    }

    async function loadOwn() {
        try {
            const response = await request(api + '/minha');
            if (response.status === 401) return requireLogin();
            if (!response.ok) throw new Error();
            if (response.status !== 204) {
                own = await response.json();
                const image = new Image();
                image.src = imageUrl(own);
                await image.decode();
                ctx.drawImage(image, 0, 0);
                save.textContent = 'Atualizar assinatura';
                remove.hidden = false;
            }
            editor.hidden = false;
            status.textContent = '';
        } catch {
            status.textContent = 'Não foi possível carregar sua assinatura. Recarregue a página para tentar novamente.';
        }
    }

    function setBusy(value) {
        busy = value;
        for (const button of [save, remove, get('limpar')]) button.disabled = value;
    }

    function encodeSignature() {
        return new Promise(resolve => canvas.toBlob(resolve, 'image/png'));
    }

    save.addEventListener('click', async () => {
        if (busy || pointer !== null) return;
        setBusy(true);
        status.textContent = 'Salvando assinatura…';
        try {
            const png = await encodeSignature();
            if (!png) throw new Error('Não foi possível preparar o desenho.');
            const form = new FormData();
            form.append('imagem', png, 'assinatura.png');
            const response = await request(api + '/minha', {method: 'PUT', body: form});
            if (response.status === 401) return requireLogin();
            if (response.status === 400) throw new Error('Desenhe uma assinatura antes de publicar.');
            if (!response.ok) throw new Error('Não foi possível publicar. Tente novamente.');
            own = await response.json();
            save.textContent = 'Atualizar assinatura';
            remove.hidden = false;
            status.textContent = 'Assinatura publicada no mural.';
            loadGallery(true);
        } catch (error) {
            status.textContent = error.message || 'Não foi possível publicar. Tente novamente.';
        } finally {
            setBusy(false);
        }
    });

    function clearOwnSignature() {
        own = null;
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        save.textContent = 'Publicar assinatura';
        remove.hidden = true;
    }

    remove.addEventListener('click', async () => {
        if (busy || !own || !window.confirm('Excluir sua assinatura do mural público?')) return;
        setBusy(true);
        try {
            const response = await request(api + '/minha', {method: 'DELETE'});
            if (response.status === 401) return requireLogin();
            if (!response.ok) throw new Error();
            clearOwnSignature();
            status.textContent = 'Sua assinatura foi excluída.';
            loadGallery(true);
        } catch {
            status.textContent = 'Não foi possível excluir sua assinatura. Tente novamente.';
        } finally {
            setBusy(false);
        }
    });

    const gallery = get('assinaturas');
    const galleryStatus = get('galeriaStatus');
    const more = get('maisAssinaturas');
    let nextBefore = null;
    let loadingGallery;

    function card(entry) {
        const row = document.createElement('li');
        const image = document.createElement('img');
        image.src = imageUrl(entry);
        image.alt = 'Assinatura de @' + entry.username;
        image.width = 720;
        image.height = 300;
        image.loading = 'lazy';
        image.decoding = 'async';
        const author = document.createElement('strong');
        author.textContent = '@' + entry.username;
        row.append(image, author);
        window.SynchroModeration?.addDelete(row, {
            url: api + '/' + encodeURIComponent(entry.id),
            label: 'Excluir assinatura', status: galleryStatus,
            onDelete() {
                if (own?.id === entry.id) clearOwnSignature();
                return loadGallery(true);
            }
        });
        return row;
    }

    async function loadGallery(reset = false) {
        if (loadingGallery && !reset) return;
        loadingGallery?.abort();
        const controller = new AbortController();
        loadingGallery = controller;
        if (reset) nextBefore = null;
        const firstPage = nextBefore == null;
        const query = firstPage ? '' : '?' + new URLSearchParams({before: nextBefore});
        more.disabled = true;
        galleryStatus.textContent = 'Carregando assinaturas…';
        try {
            const response = await request(api + query, {signal: AbortSignal.any([
                controller.signal, AbortSignal.timeout(10000)
            ])});
            if (!response.ok) throw new Error();
            const page = await response.json();
            if (controller.signal.aborted) return;
            const rows = page.items.map(card);
            if (firstPage) gallery.replaceChildren(...rows);
            else gallery.append(...rows);
            nextBefore = page.nextBefore;
            galleryStatus.textContent = gallery.childElementCount ? '' : 'Ainda não há assinaturas.';
            more.textContent = 'Ver mais assinaturas';
            more.hidden = nextBefore == null;
        } catch {
            if (controller.signal.aborted) return;
            galleryStatus.textContent = 'Não foi possível carregar o mural.';
            more.hidden = false;
            more.textContent = 'Tentar novamente';
        } finally {
            if (loadingGallery === controller) {
                loadingGallery = null;
                more.disabled = false;
            }
        }
    }

    more.addEventListener('click', () => loadGallery());
    loadGallery();
    loadOwn();
})();
