document.addEventListener("DOMContentLoaded", loadCurrentUserProfile);
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

const isPublicProfile = new URLSearchParams(window.location.search).has('user');
// Cada aba do perfil compartilha a resposta entre rankings, gêneros e contadores.
const profileRankings = new Map();
function fetchProfileRanking(type, period) {
    const key = type + ':' + period;
    const cached = profileRankings.get(key);
    if (cached && Date.now() < cached.expires) return cached.request;
    const entry = {expires: Infinity};
    entry.request = fetch('/last-fm/me/top/' + type + '?period=' + encodeURIComponent(period), {
        credentials: 'same-origin', cache: 'no-store', headers: {Accept: 'application/json'}
    }).then(async response => {
        if (!response.ok) {
            const error = new Error('Ranking indisponível.');
            error.status = response.status;
            throw error;
        }
        const data = await response.json();
        entry.expires = Date.now() + 60000;
        return data;
    }).catch(error => {
        profileRankings.delete(key);
        throw error;
    });
    profileRankings.set(key, entry);
    return entry.request;
}
window.addEventListener('pageshow', () => {
    // The user may return without authorizing, using the browser's Back button.
    const button = document.getElementById('connectLastFm');
    if (button?.disabled) {
        button.disabled = false;
        button.textContent = 'Conectar ao lastFM';
        setLastFmStatus('Conexão não concluída. Clique em Conectar ao lastFM para tentar novamente.');
    }
});

async function loadCurrentUserProfile() {
    if (window.location.protocol === "file:") {
        showProfileError("Abra o Synchro pelo servidor em http://localhost:8080 para acessar seu perfil.");
        return;
    }

    try {
        const publicUser = new URLSearchParams(window.location.search).get('user');
        if (isPublicProfile && !/^\d+$/.test(publicUser)) throw new Error("Perfil não encontrado.");
        const response = await fetch(publicUser ? `/catalog/users/${encodeURIComponent(publicUser)}` : "/users/me", {
            credentials: "same-origin",
            headers: { Accept: "application/json" }
        });

        if (!publicUser && (response.status === 401 || response.status === 403 || response.status === 404)) {
            window.location.replace("login.html");
            return;
        }

        if (!response.ok) {
            throw new Error(await getResponseMessage(response));
        }

        const user = await response.json();
        renderCurrentUser(user);
        if (publicUser) {
            document.getElementById('profileHeading').textContent = 'Perfil';
            ['profileEmail', 'profileBirthDay', 'profileGender'].forEach(id => document.getElementById(id).hidden = true);
        }

        document.getElementById("profileStatus").hidden = true;
        document.getElementById("profilePage").hidden = false;
        if (!isPublicProfile) {
            document.getElementById('connectLastFm').addEventListener('click', connectLastFm);
            document.getElementById('syncLastFm').addEventListener('click', syncLastFm);
            showLastFmCallbackResult();
            document.documentElement.classList.add("own-profile");
            document.dispatchEvent(new CustomEvent("synchro:own-profile", {detail: user}));
        }
    } catch (error) {
        showProfileError(error.message || "Não foi possível carregar seu perfil.");
    }
}

function renderCurrentUser(user) {
    const data = user.data || {};
    const displayName = data.fullName?.trim() || user.username || "Usuário";
    const genderNames = {
        female: "Feminino",
        male: "Masculino",
        other: "Outro"
    };

    document.title = `SYNCHRO | ${displayName}`;
    setProfileText("profileName", displayName);
    setProfileText("profileUsername", user.username ? `@${user.username}` : "");
    setProfileText("profileEmail", user.email ? `E-mail: ${user.email}` : "E-mail não informado");
    setProfileText("profileBirthDay", `Nascimento: ${formatBirthDay(user.birthDay)}`);
    setProfileText("profileGender", `Gênero: ${genderNames[data.gender] || "Não informado"}`);
    setProfileText("profileLastFm", user.lastFmConnected
        ? `Last.fm conectado: ${user.lastFmUsername}`
        : "Last.fm não conectado");
    setProfileText("profileScrobbles", formatCount(data.scrobbles));
    setProfileText("profileArtists", formatCount(data.artistas));
    document.getElementById("profileImage").alt = `Foto de perfil de ${displayName}`;
    renderLastFm(user);
}

function renderLastFm(user) {
    const data = user.data || {};
    const connected = user.lastFmConnected === true;
    document.getElementById('lastFmActions').hidden = isPublicProfile;
    document.getElementById('connectLastFm').hidden = isPublicProfile || connected;
    document.getElementById('syncLastFm').hidden = isPublicProfile || !connected;
    document.getElementById('lastFmDetails').hidden = !connected;
    document.getElementById('lastFmAccountInfo').hidden = !connected;
    document.getElementById('lastFmTracksStat').hidden = !connected || data.lastFmTracks == null;
    document.getElementById('lastFmAlbumsStat').hidden = !connected || data.lastFmAlbums == null;
    setProfileText('profileTracks', formatCount(data.lastFmTracks));
    setProfileText('profileAlbums', formatCount(data.lastFmAlbums));
    const image = document.getElementById('profileImage');
    const fallback = '../assets/images/user/userphoto.jpg';
    image.dataset.defaultPhoto = fallback;
    showProfilePhoto(image);
    if (!connected) return;

    document.getElementById('lastFmProfileLink').href = `https://www.last.fm/user/${encodeURIComponent(user.lastFmUsername)}`;
    setOptionalLastFmText('lastFmRealName', data.lastFmRealName ? `Nome no Last.fm: ${data.lastFmRealName}` : '');
    setOptionalLastFmText('lastFmCountry', data.lastFmCountry ? `País: ${data.lastFmCountry}` : '');
    const updated = new Date(data.lastFmUpdatedAt || '');
    setOptionalLastFmText('lastFmUpdatedAt', Number.isNaN(updated.getTime()) ? ''
        : `Atualizado em ${updated.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}`);
    try {
        const url = new URL(data.lastFmImageUrl);
        if (url.protocol === 'https:' && !url.username && !url.password) {
            image.dataset.defaultPhoto = url.href;
            showProfilePhoto(image);
        }
    } catch { /* Keep the default photo when Last.fm has no image. */ }
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

async function syncLastFm() {
    const button = document.getElementById('syncLastFm');
    if (button.disabled) return;
    button.disabled = true;
    button.textContent = 'Atualizando…';
    setLastFmStatus('Buscando seus dados no Last.fm…');
    try {
        renderCurrentUser(await lastFmRequest('/last-fm/sync', { method: 'POST' }));
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
