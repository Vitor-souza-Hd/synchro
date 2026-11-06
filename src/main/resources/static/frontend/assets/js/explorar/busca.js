// Compatibility for older pages: always use the same search as the profile.
(() => {
    function initialize() {
        if (window.SynchroSearch) {
            window.SynchroSearch.initialize();
            return;
        }
        if (document.querySelector('script[data-synchro-search-loader], script[src*="/assets/js/search.js"]')) return;
        const script = document.createElement('script');
        script.src = '/frontend/assets/js/search.js?v=20261105-single-search-1';
        script.dataset.synchroSearchLoader = 'true';
        document.head.append(script);
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize, {once: true});
    else initialize();
})();
