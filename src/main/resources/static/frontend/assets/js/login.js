const loginForm = document.querySelector(".login");
const usernameInput = loginForm.querySelector('[name="username"]');
const passwordInput = loginForm.querySelector('[name="password"]');
const rememberInput = loginForm.querySelector('#remember-me');
const passwordToggle = loginForm.querySelector('.password-toggle');
const messageBox = loginForm.querySelector('#login-message');
const rememberedUserKey = 'synchro.login.username';

// Remember only the identifier; passwords and session tokens stay out of local storage.
try {
    const rememberedUser = localStorage.getItem(rememberedUserKey);
    if (rememberedUser) {
        usernameInput.value = rememberedUser;
        rememberInput.checked = true;
    }
} catch { /* Login remains available when browser storage is blocked. */ }

rememberInput.addEventListener('change', () => {
    if (!rememberInput.checked) {
        try { localStorage.removeItem(rememberedUserKey); } catch { /* Optional preference. */ }
    }
});

passwordToggle.addEventListener('click', () => {
    const show = passwordInput.type === 'password';
    passwordInput.type = show ? 'text' : 'password';
    passwordToggle.textContent = show ? 'Ocultar' : 'Mostrar';
    passwordToggle.setAttribute('aria-label', show ? 'Ocultar senha' : 'Mostrar senha');
    passwordToggle.setAttribute('aria-pressed', String(show));
});

function showMessage(message) {
    messageBox.textContent = message;
    messageBox.hidden = !message;
}

loginForm.addEventListener('input', () => showMessage(''));

loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submitButton = loginForm.querySelector('button[type="submit"]');
    if (submitButton.disabled) return;
    showMessage('');

    if (window.location.protocol === "file:") {
        showMessage("Para entrar, inicie o Synchro e abra http://localhost:8080/index.html no navegador.");
        return;
    }

    const username = usernameInput.value.trim();
    if (!username) {
        showMessage('Digite seu usuário ou e-mail.');
        usernameInput.focus();
        return;
    }

    submitButton.disabled = true;
    submitButton.textContent = 'Entrando…';
    loginForm.setAttribute('aria-busy', 'true');

    try {
        const response = await fetch("/auth/login", {
            method: "POST",
            credentials: "same-origin",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password: passwordInput.value }),
            signal: AbortSignal.timeout(15000)
        });

        if (!response.ok) {
            const fallback = response.status >= 500
                ? 'Não foi possível entrar agora. Tente novamente em instantes.'
                : 'Usuário, e-mail ou senha incorretos.';
            showMessage(await getResponseMessage(response, fallback));
            return;
        }

        try {
            if (rememberInput.checked) localStorage.setItem(rememberedUserKey, username);
            else localStorage.removeItem(rememberedUserKey);
        } catch { /* Saving a preference must not prevent a successful login. */ }
        window.location.assign("../../index.html");
    } catch (error) {
        showMessage(error.name === 'TimeoutError'
            ? 'O servidor demorou para responder. Tente novamente.'
            : 'Não foi possível conectar ao servidor. Tente novamente.');
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = 'Entrar';
        loginForm.removeAttribute('aria-busy');
    }
});

async function getResponseMessage(response, fallback) {
    // HTML error pages and internal server errors are not useful form messages.
    if (response.status >= 500) return fallback;
    const body = await response.text();
    try {
        const error = JSON.parse(body);
        const message = error.message || error.detail;
        return typeof message === 'string' ? message : fallback;
    } catch {
        return fallback;
    }
}
