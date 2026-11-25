(() => {
    const activity = document.getElementById('revisoes');
    const controls = [...activity.querySelectorAll('button[aria-controls]')];
    const reviews = document.getElementById('recentReviewsPanel');
    const publications = document.getElementById('recentPostsPanel');
    const status = document.getElementById('homePostsStatus');
    const feed = document.getElementById('homePostsFeed');
    const retry = document.getElementById('retryHomePosts');
    const previewSize = 3;
    let loading = false;
    let loaded = false;

    function preview(post, signedIn) {
        const article = window.SynchroPostCard.render(post);
        window.SynchroPostPolls.attach(article, post, signedIn);
        article.append(window.SynchroPostReactions.render(post, signedIn));
        window.SynchroPostDiscussion.attach(article, post, signedIn);

        const details = document.createElement('a');
        details.className = 'home-post-details';
        details.href = '/frontend/pages/publicacoes.html?' + new URLSearchParams({post: post.id});
        details.textContent = 'Abrir publicação';
        article.append(details);
        if (signedIn && !post.own) {
            window.SynchroModeration?.addDelete(article, {
                url: '/posts/' + encodeURIComponent(post.id),
                label: 'Excluir publicação', status, onDelete: () => article.remove()
            });
        }
        return article;
    }

    async function loadPosts() {
        if (loading) return;
        loading = true;
        retry.hidden = true;
        status.textContent = 'Carregando publicações…';
        try {
            const url = '/posts?' + new URLSearchParams({limit: previewSize});
            const response = await fetch(url, {
                cache: 'no-store', credentials: 'same-origin', signal: AbortSignal.timeout(10000)
            });
            if (!response.ok) throw new Error('Publicações indisponíveis.');
            const {items, signedIn} = await response.json();
            feed.replaceChildren(...items.map(post => preview(post, signedIn)));
            status.textContent = items.length ? '' : 'Ainda não há publicações.';
            loaded = true;
        } catch {
            status.textContent = 'Não foi possível carregar as publicações agora.';
            retry.hidden = false;
        } finally {
            loading = false;
        }
    }

    function show(panelId) {
        publications.hidden = panelId !== publications.id;
        reviews.hidden = panelId !== reviews.id;
        for (const control of controls) {
            control.setAttribute('aria-pressed', String(control.getAttribute('aria-controls') === panelId));
        }
        if (!publications.hidden && !loaded) loadPosts();
    }

    for (const control of controls) {
        control.addEventListener('click', () => show(control.getAttribute('aria-controls')));
    }
    for (const link of document.querySelectorAll('a[href="#revisoes"]')) {
        link.addEventListener('click', () => show(reviews.id));
    }
    retry.addEventListener('click', loadPosts);

    const preload = () => {
        if (!loaded) loadPosts();
    };
    if (window.requestIdleCallback) window.requestIdleCallback(preload, {timeout: 1500});
    else window.setTimeout(preload, 500);

    window.addEventListener('pageshow', event => {
        if (!event.persisted) return;
        loaded = false;
        if (!publications.hidden) loadPosts();
    });
})();
