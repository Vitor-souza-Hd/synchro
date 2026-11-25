(() => {
    const dateFormat = new Intl.DateTimeFormat('pt-BR', {dateStyle: 'short', timeStyle: 'short'});

    function authorName(user) {
        return '@' + user.username;
    }

    function setAvatar(image, sources) {
        const available = [];
        for (const value of sources) {
            try {
                const url = new URL(value);
                if (url.protocol === 'https:' && !url.username && !url.password && !available.includes(url.href)) {
                    available.push(url.href);
                }
            } catch { /* Use the next image when a profile has no valid URL. */ }
        }
        function next() {
            const source = available.shift();
            image.onerror = source ? next : null;
            image.src = source || '/frontend/assets/images/user/userphoto.jpg';
        }
        next();
    }

    function postAttachments(post) {
        return post.attachments || (post.attachment ? [post.attachment] : []);
    }

    function render(post) {
        const article = document.createElement('article');
        article.className = 'post-panel';
        article.id = 'post-' + post.id;

        const author = document.createElement('a');
        author.className = 'post-author';
        author.href = '/frontend/pages/perfil-publico.html?' + new URLSearchParams({user: post.author.id});
        const image = document.createElement('img');
        image.alt = '';
        image.width = image.height = 40;
        image.loading = 'lazy';
        image.referrerPolicy = 'no-referrer';
        setAvatar(image, [post.profileImageUrl, post.lastFmImageUrl]);
        const identity = document.createElement("span");
        identity.className = "post-author-identity";
        identity.textContent = authorName(post.author);
        if (post.authorVvip === true) {
            const badge = document.createElement("span");
            badge.className = "profile-role-badge";
            badge.dataset.role = "VVIP";
            badge.textContent = "VVIP";
            identity.append(badge);
        }
        author.append(image, identity);

        const meta = document.createElement('p');
        meta.className = 'post-meta';
        const time = document.createElement('time');
        time.dateTime = post.createdAt;
        time.textContent = dateFormat.format(new Date(post.createdAt));
        meta.append(time);
        if (post.editedAt) meta.append(' · Editada');

        const body = document.createElement('p');
        body.className = 'post-body';
        body.textContent = post.body;
        article.append(author, meta);
        if (post.body) article.append(body);
        const items = postAttachments(post);
        if (items.length) {
            const group = document.createElement('div');
            group.className = 'post-attachments-list';
            group.append(...items.map(window.SynchroPostAttachments.card));
            article.append(group);
        }
        return article;
    }

    window.SynchroPostCard = {render, authorName, postAttachments};
})();
