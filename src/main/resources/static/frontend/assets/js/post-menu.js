(() => {
    const sidebar = document.getElementById('postsSidebar');
    const toggle = document.getElementById('postsMenuToggle');
    if (!sidebar || !toggle) return;

    function setOpen(open) {
        sidebar.classList.toggle('active', open);
        toggle.setAttribute('aria-expanded', String(open));
        toggle.setAttribute('aria-label', open ? 'Fechar menu' : 'Abrir menu');
    }

    toggle.addEventListener('click', () => setOpen(!sidebar.classList.contains('active')));
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && sidebar.classList.contains('active')) {
            setOpen(false);
            toggle.focus();
        }
    });
})();
