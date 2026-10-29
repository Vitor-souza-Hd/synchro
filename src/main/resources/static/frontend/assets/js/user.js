document.addEventListener("DOMContentLoaded", loadCurrentUserProfile);

async function loadCurrentUserProfile() {
    if (window.location.protocol === "file:") {
        showProfileError("Abra o Synchro pelo servidor em http://localhost:8080 para acessar seu perfil.");
        return;
    }

    try {
        const publicUser = new URLSearchParams(window.location.search).get('user');
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
            document.querySelector('.banner-info h1').textContent = 'Perfil';
            ['profileEmail', 'profileBirthDay', 'profileGender'].forEach(id => document.getElementById(id).hidden = true);
        }

        document.getElementById("profileStatus").hidden = true;
        document.getElementById("profilePage").hidden = false;
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
    setProfileText("profileLastFm", user.lastFmUsername
        ? `Last.fm: ${user.lastFmUsername}`
        : "Last.fm não conectado");
    setProfileText("profileScrobbles", formatCount(data.scrobbles));
    setProfileText("profileArtists", formatCount(data.artistas));
    document.getElementById("profileImage").alt = `Foto de perfil de ${displayName}`;
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
