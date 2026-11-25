(() => {
    const sidebar = document.querySelector('aside.sidebar');
    if (!sidebar) return;

    if (!sidebar.querySelector('a[aria-current="page"]')) {
        const current = location.pathname === '/index.html' || location.pathname === '/'
            ? '/frontend/pages/explorar.html' : location.pathname;
        const selected = [...sidebar.querySelectorAll('.menu a[href]')].find(link => {
            if (link.getAttribute('href').startsWith('#')) return false;
            const url = new URL(link.href);
            return url.origin === location.origin && url.pathname === current && !url.hash;
        });
        selected?.setAttribute('aria-current', 'page');
    }

    const toggle = document.querySelector('[data-site-menu-toggle]');
    if (!toggle) return;

    sidebar.id ||= 'synchroSidebar';
    toggle.setAttribute('aria-controls', sidebar.id);
    function setOpen(open) {
        sidebar.classList.toggle('active', open);
        toggle.setAttribute('aria-expanded', String(open));
    }

    toggle.addEventListener('click', () => setOpen(!sidebar.classList.contains('active')));
    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape' || !sidebar.classList.contains('active')) return;
        setOpen(false);
        toggle.focus();
    });
})();
