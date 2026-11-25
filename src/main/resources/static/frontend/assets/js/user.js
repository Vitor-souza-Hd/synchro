document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("retryProfile")?.addEventListener("click", loadCurrentUserProfile);
    loadCurrentUserProfile();
});
document.addEventListener("DOMContentLoaded", initializeProfileMenu);

function initializeProfileMenu() {
    const button = document.querySelector('.menu-toggle');
    const sidebar = document.getElementById('profileSidebar');
    const page = document.getElementById('profilePage');
    if (!button || !sidebar || !page) return;
    const mobile = window.matchMedia('(max-width: 768px)');
    const preferenceKey = 'synchro:profile-sidebar:collapsed:v1';
    let desktopCollapsed = false;
    let mobileOpen = false;
    try { desktopCollapsed = localStorage.getItem(preferenceKey) === 'true'; } catch { /* Use the expanded menu when storage is unavailable. */ }
    const isOpen = () => mobile.matches ? mobileOpen : !desktopCollapsed;
    function render() {
        const open = isOpen();
        page.classList.toggle('sidebar-collapsed', !open);
        sidebar.classList.toggle('active', mobile.matches && open);
        sidebar.inert = !open;
        sidebar.setAttribute('aria-hidden', String(!open));
        button.setAttribute('aria-expanded', String(open));
        button.setAttribute('aria-label', mobile.matches
            ? open ? 'Fechar menu' : 'Abrir menu'
            : open ? 'Recolher menu' : 'Expandir menu');
    }
    function setOpen(open) {
        if (mobile.matches) mobileOpen = open;
        else {
            desktopCollapsed = !open;
            try { localStorage.setItem(preferenceKey, String(desktopCollapsed)); } catch { /* The current page still works without saving the preference. */ }
        }
        render();
    }
    button.addEventListener('click', () => setOpen(!isOpen()));
    document.querySelector('.profile-menu-close')?.addEventListener('click', () => {
        setOpen(false);
        button.focus();
    });
    sidebar.addEventListener('click', event => {
        if (mobile.matches && event.target.closest('a, [data-create-playlist]')) setOpen(false);
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && isOpen() && !document.querySelector('dialog[open]')) {
            setOpen(false);
            button.focus();
        }
    });
    mobile.addEventListener('change', () => { mobileOpen = false; render(); });
    render();
}

const isPublicProfile = document.body.dataset.profile === 'public';
// Os destaques e o relatório usam a mesma consulta para cada ranking.
const profileRankings = new Map();
function fetchProfileRanking(type, period, publicProfileId, latest = false) {
    const key = (publicProfileId ?? 'me') + ':' + type + ':' + period;
    let entry = profileRankings.get(key);
    if (!entry || Date.now() >= entry.expires) {
        entry = {expires: Infinity};
        const privateRanking = publicProfileId == null;
        const path = privateRanking ? '/last-fm/me/rankings/' + type
            : '/last-fm/profiles/' + encodeURIComponent(publicProfileId) + '/top/' + type;
        entry.request = readRanking(path, {period}).then(async response => {
            if (response.status === 204) {
                if (!privateRanking) return {['top' + type]: {}};
                return (await readRanking(path, {period, refresh: 'true'})).json();
            }
            const saved = await response.json();
            if (privateRanking && response.headers.get('X-Synchro-Ranking-Stale') === 'true') {
                entry.refresh = readRanking(path, {period, refresh: 'true'}).then(response => response.json()).then(data => {
                    if (profileRankings.get(key) === entry) {
                        entry.request = Promise.resolve(data);
                        entry.expires = Date.now() + 60000;
                        document.dispatchEvent(new CustomEvent('synchro:profile-ranking-updated', {detail: {type, period}}));
                    }
                    return data;
                }).catch(error => {
                    entry.expires = 0;
                    throw error;
                });
                // Exibir o ranking salvo continua possível se a atualização falhar.
                entry.refresh.catch(() => {});
            }
            return saved;
        }).then(value => {
            entry.expires = Date.now() + 60000;
            return value;
        }).catch(error => {
            if (profileRankings.get(key) === entry) profileRankings.delete(key);
            throw error;
        });
        profileRankings.set(key, entry);
    }
    return latest ? entry.request.then(data => entry.refresh ?? data) : entry.request;
}

async function readRanking(path, params) {
    const response = await fetch(path + '?' + new URLSearchParams(params), {
        credentials: 'same-origin', cache: 'no-store', headers: {Accept: 'application/json'},
        signal: AbortSignal.timeout(18000)
    });
    if (!response.ok) {
        const error = new Error('Ranking indisponível.');
        error.status = response.status;
        throw error;
    }
    return response;
}
window.addEventListener('pagehide', event => {
    if (!isPublicProfile && event.persisted) document.getElementById('profilePage').hidden = true;
});
window.addEventListener('pageshow', event => {
    if (!isPublicProfile && event.persisted) {
        window.location.reload();
        return;
    }
    // The user may return without authorizing, using the browser's Back button.
    const button = document.getElementById('connectLastFm');
    if (button?.disabled) {
        button.disabled = false;
        button.textContent = 'Conectar ao lastFM';
        setLastFmStatus('Conexão não concluída. Clique em Conectar ao lastFM para tentar novamente.');
    }
});

function whenProfileSectionNear(section, load) {
    if (!section || typeof IntersectionObserver !== 'function') {
        setTimeout(load, 0);
        return;
    }
    const observer = new IntersectionObserver(entries => {
        if (!entries.some(entry => entry.isIntersecting)) return;
        observer.disconnect();
        load();
    }, {rootMargin: '240px'});
    observer.observe(section);
}

async function loadCurrentUserProfile() {
    if (window.location.protocol === "file:") {
        showProfileError("Abra o Synchro pelo servidor em http://localhost:8080 para acessar seu perfil.");
        return;
    }

    document.getElementById("profileStatus").hidden = false;
    document.getElementById("profileError").hidden = true;
    const retry = document.getElementById("retryProfile");
    if (retry) retry.hidden = true;

    try {
        const publicUser = new URLSearchParams(window.location.search).get('user');
        if (isPublicProfile && !/^\d+$/.test(publicUser)) throw new Error("Perfil não encontrado.");
        const endpoint = isPublicProfile ? `/catalog/users/${encodeURIComponent(publicUser)}` : "/users/me";
        const response = await fetch(endpoint, {
            credentials: "same-origin",
            cache: "no-store",
            headers: { Accept: "application/json" },
            signal: AbortSignal.timeout(10000)
        });

        if (!isPublicProfile && [401, 403, 404].includes(response.status)) {
            window.location.replace("login.html");
            return;
        }

        if (!response.ok) {
            throw new Error(await getResponseMessage(response));
        }

        const user = await response.json();
        renderCurrentUser(user);
        const posts = document.getElementById('profilePostsLink');
        if (posts) {
            posts.href = 'publicacoes.html?' + new URLSearchParams({user: user.id});
            posts.hidden = false;
        }
        document.getElementById("profileStatus").hidden = true;
        document.getElementById("profilePage").hidden = false;
        initializeFollowProfile(user.id);
        if (isPublicProfile) {
            document.dispatchEvent(new CustomEvent('synchro:public-profile', {detail: user}));
        } else {
            const publicLink = document.getElementById('viewPublicProfile');
            if (/^\d+$/.test(user.id)) {
                publicLink.href = 'perfil-publico.html?' + new URLSearchParams({user: user.id});
                publicLink.hidden = false;
            }
            document.getElementById('editProfileLink').onclick = () => {
                const settings = document.getElementById('profileSettings');
                settings.open = true;
                settings.querySelector('summary').focus({preventScroll: true});
            };
            document.getElementById('connectLastFm').addEventListener('click', connectLastFm);
            document.getElementById('syncLastFm').addEventListener('click', syncLastFm);
            showLastFmCallbackResult();
            document.documentElement.classList.add("own-profile");
            document.dispatchEvent(new CustomEvent("synchro:own-profile", {detail: user}));
            const lastUpdate = Date.parse(user.data?.lastFmUpdatedAt);
            if (user.lastFmConnected && !(Date.now() - lastUpdate < 30 * 60 * 1000)) {
                setTimeout(() => refreshLastFmSummary().catch(() => {}), 0);
            }
        }
    } catch (error) {
        const timedOut = error.name === 'TimeoutError';
        showProfileError(timedOut ? "O perfil demorou para responder." : error.message || "Não foi possível carregar seu perfil.");
        if (retry) retry.hidden = false;
    }
}

async function initializeFollowProfile(profileId) {
    const button = document.getElementById('followProfile');
    const status = document.getElementById('followProfileStatus');
    const url = '/follow/' + encodeURIComponent(profileId);
    const request = method => fetch(url, {method, credentials: 'same-origin', cache: 'no-store'});
    let following = false;

    function render(state) {
        setProfileText('profileFollowers', formatCount(state.followers));
        setProfileText('profileFollowing', formatCount(state.followingCount));
        following = state.following;
        if (button) {
            button.hidden = !state.canFollow;
            button.textContent = following ? 'Deixar de seguir' : 'Seguir';
        }
    }

    try {
        const response = await request('GET');
        if (!response.ok) throw new Error();
        const state = await response.json();
        render(state);
        if (!button || !status || !state.canFollow) return;
    } catch {
        if (status) status.textContent = 'Não foi possível consultar se você segue este perfil.';
        return;
    }

    button.addEventListener('click', async () => {
        button.disabled = true;
        status.textContent = '';
        try {
            const response = await request(following ? 'DELETE' : 'POST');
            if (response.status === 401) {
                button.hidden = true;
                status.textContent = 'Sua sessão expirou. Entre novamente para seguir perfis.';
                return;
            }
            if (!response.ok) throw new Error();
            render(await response.json());
            status.textContent = following ? 'Você está seguindo este perfil.' : 'Você deixou de seguir este perfil.';
        } catch {
            status.textContent = 'Não foi possível atualizar o status. Tente novamente.';
        } finally {
            button.disabled = false;
        }
    });
}

function renderCurrentUser(user) {
    const data = user.data || {};
    const handle = user.username ? `@${user.username}` : "";
    const displayName = (isPublicProfile ? handle : data.fullName?.trim() || user.username) || "Usuário";
    document.title = `SYNCHRO | ${displayName}`;
    if (!isPublicProfile) {
        setProfileText("profileHeading", handle || displayName);
        setProfileText("profileName", displayName);
        setProfileText("profileUsername", handle);
        const genderNames = {female: "Feminino", male: "Masculino", other: "Outro"};
        setProfileText("profileEmail", user.email ? `E-mail: ${user.email}` : "E-mail não informado");
        setProfileText("profileBirthDay", `Nascimento: ${formatBirthDay(user.birthDay)}`);
        setProfileText("profileGender", `Gênero: ${genderNames[data.gender] || "Não informado"}`);
    }
    setProfileText("profileLastFm", user.lastFmConnected
        ? `Last.fm conectado: ${user.lastFmUsername}`
        : "Last.fm não conectado");
    setProfileText("profileScrobbles", formatCount(data.scrobbles));
    setProfileText("profileArtists", formatCount(data.artistas));
    const image = document.getElementById("profileImage");
    image.alt = `Foto de perfil de ${displayName}`;
    if (isPublicProfile) renderPublicAppearance(user.appearance || {}, displayName, image);
    else image.dataset.customPhoto = publicImageUrl(data.profileImageUrl);
    renderLastFm(user);
    renderProfileRole(user.role);
}

function renderProfileRole(role) {
    const heading = document.getElementById("profileHeading");
    heading.querySelector(".profile-role-badge")?.remove();
    if (role === "VIP" || role === "VVIP") {
        const badge = document.createElement("span");
        badge.className = "profile-role-badge";
        badge.dataset.role = role;
        badge.textContent = role;
        heading.append(badge);
    }
}

function renderPublicAppearance(appearance, displayName, photo) {
    setProfileText('profileHeading', displayName);
    photo.dataset.customPhoto = publicImageUrl(appearance.profileImageUrl);

    const banner = document.getElementById('publicProfileBanner');
    const bannerUrl = publicImageUrl(appearance.bannerImageUrl);
    if (bannerUrl) {
        banner.addEventListener('load', () => { banner.hidden = false; }, {once: true});
        banner.src = bannerUrl;
    }

    const biography = document.getElementById('profileBiography');
    biography.textContent = appearance.biography || '';
    biography.hidden = !biography.textContent.trim();

    const list = document.getElementById('musicProfileGenres');
    for (const genre of appearance.genres || []) {
        const item = document.createElement('li');
        item.textContent = genre;
        list.append(item);
    }
    document.getElementById('publicMusicGenres').hidden = !list.childElementCount;
}

function publicImageUrl(value) {
    try {
        const url = new URL(value);
        return url.protocol === 'https:' && !url.username && !url.password ? url.href : '';
    } catch {
        return '';
    }
}

function renderLastFm(user) {
    const data = user.data || {};
    const connected = user.lastFmConnected === true;
    if (!isPublicProfile) {
        document.getElementById('lastFmActions').hidden = false;
        document.getElementById('connectLastFm').hidden = connected;
        document.getElementById('syncLastFm').hidden = !connected;
        document.getElementById('lastFmAccountInfo').hidden = !connected;
        setOptionalLastFmText('lastFmRealName', data.lastFmRealName ? `Nome no Last.fm: ${data.lastFmRealName}` : '');
        setOptionalLastFmText('lastFmCountry', data.lastFmCountry ? `País: ${data.lastFmCountry}` : '');
        const updated = new Date(data.lastFmUpdatedAt || '');
        setOptionalLastFmText('lastFmUpdatedAt', Number.isNaN(updated.getTime()) ? ''
            : `Atualizado em ${updated.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}`);
    }
    document.getElementById('lastFmDetails').hidden = !connected;
    document.getElementById('lastFmTracksStat').hidden = !connected || data.lastFmTracks == null;
    document.getElementById('lastFmAlbumsStat').hidden = !connected || data.lastFmAlbums == null;
    setProfileText('profileTracks', formatCount(data.lastFmTracks));
    setProfileText('profileAlbums', formatCount(data.lastFmAlbums));
    const image = document.getElementById('profileImage');
    const fallback = '../assets/images/user/userphoto.jpg';
    image.dataset.defaultPhoto = connected ? publicImageUrl(data.lastFmImageUrl) || fallback : fallback;
    showProfilePhoto(image);
    if (connected) {
        document.getElementById('lastFmProfileLink').href = `https://www.last.fm/user/${encodeURIComponent(user.lastFmUsername)}`;
    }
}

function showProfilePhoto(image) {
    const fallback = '../assets/images/user/userphoto.jpg';
    const sources = [...new Set([image.dataset.customPhoto, image.dataset.defaultPhoto, fallback].filter(Boolean))];
    const next = () => {
        if (sources.length === 0) { image.onerror = null; return; }
        image.src = sources.shift();
    };
    image.onerror = next;
    next();
}

function setOptionalLastFmText(id, value) {
    setProfileText(id, value);
    document.getElementById(id).hidden = !value;
}

function setLastFmStatus(message, error = false) {
    const status = document.getElementById('lastFmStatus');
    status.textContent = message;
    status.hidden = !message;
    status.classList.toggle('is-error', error);
}

function showLastFmCallbackResult() {
    const url = new URL(window.location.href);
    const result = url.searchParams.get('lastfm');
    if (!result) return;
    const messages = {
        connected: 'Last.fm conectado! Seus dados já estão no perfil.',
        sync_pending: 'Last.fm conectado. Não foi possível importar os dados agora. Clique em Atualizar dados do Last.fm para tentar novamente.',
        cancelled: 'A conexão com o Last.fm foi cancelada. Você pode tentar novamente.',
        invalid_state: 'Esta tentativa de conexão expirou ou não é válida. Clique em Conectar ao lastFM novamente.',
        session_expired: 'Sua sessão expirou. Entre novamente e conecte o Last.fm.',
        token_expired: 'A autorização do Last.fm expirou ou já foi utilizada. Clique em Conectar ao lastFM novamente.',
        not_authorized: 'O acesso ainda não foi autorizado no Last.fm. Conecte novamente e confirme a autorização.',
        api_configuration: 'O Last.fm recusou as credenciais do Synchro. A configuração da integração precisa ser corrigida.',
        unavailable: 'O Last.fm está temporariamente indisponível. Tente conectar novamente em alguns instantes.',
        error: 'Não foi possível conectar ao Last.fm. Tente novamente e autorize o acesso.'
    };
    if (messages[result]) setLastFmStatus(messages[result], result !== 'connected');
    url.searchParams.delete('lastfm');
    window.history.replaceState(null, '', url.pathname + url.search + url.hash);
}

async function lastFmRequest(path, options = {}) {
    const response = await fetch(path, {
        ...options, credentials: 'same-origin', headers: { Accept: 'application/json' }
    });
    if (response.status === 401) {
        window.location.replace('login.html');
        throw new Error('Sua sessão expirou. Entre novamente.');
    }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || data.error || 'Não foi possível acessar o Last.fm. Tente novamente.');
    return data;
}

async function connectLastFm() {
    const button = document.getElementById('connectLastFm');
    if (button.disabled) return;
    button.disabled = true;
    button.textContent = 'Conectando…';
    setLastFmStatus('Você será levado ao Last.fm para autorizar a conexão.');
    try {
        const data = await lastFmRequest('/last-fm/auth/login');
        const authUrl = new URL(data.authUrl);
        if (authUrl.protocol !== 'https:' || authUrl.hostname !== 'www.last.fm' || authUrl.username || authUrl.password) {
            throw new Error('Não foi possível iniciar a conexão com o Last.fm.');
        }
        window.location.assign(authUrl.href);
    } catch (error) {
        setLastFmStatus(error.message || 'Não foi possível conectar ao Last.fm. Tente novamente.', true);
        button.disabled = false;
        button.textContent = 'Conectar ao lastFM';
    }
}

let lastFmSync;
function refreshLastFmSummary() {
    if (!lastFmSync) {
        lastFmSync = lastFmRequest('/last-fm/sync', {method: 'POST', signal: AbortSignal.timeout(18000)})
            .then(user => {
                setProfileText("profileScrobbles", formatCount(user.data?.scrobbles));
                setProfileText("profileArtists", formatCount(user.data?.artistas));
                renderLastFm(user);
            }).finally(() => { lastFmSync = null; });
    }
    return lastFmSync;
}

async function syncLastFm() {
    const button = document.getElementById('syncLastFm');
    if (button.disabled) return;
    button.disabled = true;
    button.textContent = 'Atualizando…';
    setLastFmStatus('Buscando seus dados no Last.fm…');
    try {
        await refreshLastFmSummary();
        setLastFmStatus('Dados do Last.fm atualizados!');
    } catch (error) {
        setLastFmStatus(error.message || 'Não foi possível atualizar. Seus dados salvos continuam disponíveis.', true);
    } finally {
        button.disabled = false;
        button.textContent = 'Atualizar dados do Last.fm';
    }
}

function setProfileText(id, value) {
    const element = document.getElementById(id);
    if (element) {
        element.textContent = value;
    }
}

function formatBirthDay(value) {
    if (!value) {
        return "Não informado";
    }

    const [year, month, day] = value.split("-");
    return year && month && day ? `${day}/${month}/${year}` : value;
}

function formatCount(value) {
    return new Intl.NumberFormat("pt-BR").format(value ?? 0);
}

async function getResponseMessage(response) {
    if (response.status >= 500) {
        return "Não foi possível carregar seu perfil agora. O servidor falhou ao buscar os dados da sua conta.";
    }

    const body = await response.text();

    if (!body) {
        return "Não foi possível carregar seu perfil.";
    }

    try {
        const error = JSON.parse(body);
        return error.message || error.detail || error.error || "Não foi possível carregar seu perfil.";
    } catch {
        return body;
    }
}

function showProfileError(message) {
    document.getElementById("profileStatus").hidden = true;

    const errorElement = document.getElementById("profileError");
    errorElement.textContent = message;
    errorElement.hidden = false;
}
