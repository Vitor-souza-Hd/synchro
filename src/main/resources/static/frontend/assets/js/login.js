const loginForm = document.querySelector(".login");

loginForm?.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (window.location.protocol === "file:") {
        alert("Para entrar, inicie o Synchro e abra http://localhost:8080/index.html no navegador.");
        return;
    }

    const username = document.querySelector('.login input[name="username"]').value.trim();
    const password = document.querySelector('.login input[type="password"]').value;
    const submitButton = loginForm.querySelector('button[type="submit"]');
    submitButton.disabled = true;

    try {
        const response = await fetch("/auth/login", {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ username, password })
        });

        if (!response.ok) {
            const message = await getResponseMessage(response, "Usuário, e-mail ou senha incorretos.");
            alert(message);
            return;
        }

        window.location.assign("../../index.html");
    } catch {
        alert("Não foi possível conectar ao servidor. Confira se o Synchro está em execução.");
    } finally {
        submitButton.disabled = false;
    }
});

async function getResponseMessage(response, fallback) {
    const body = await response.text();

    if (!body) {
        return fallback;
    }

    try {
        const error = JSON.parse(body);
        return error.message || error.detail || error.error || fallback;
    } catch {
        return body;
    }
}
