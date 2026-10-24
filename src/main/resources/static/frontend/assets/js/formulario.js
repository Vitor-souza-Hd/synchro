const passwordIcons = document.querySelectorAll(".password-icon");

passwordIcons.forEach((button) => {
    button.addEventListener("click", () => {
        const input = button.parentElement.querySelector(".form-control");
        const icon = button.querySelector("i");
        const showPassword = input.type === "password";

        input.type = showPassword ? "text" : "password";
        button.setAttribute("aria-label", showPassword ? "Ocultar senha" : "Mostrar senha");
        icon?.classList.toggle("fa-eye", showPassword);
        icon?.classList.toggle("fa-eye-slash", !showPassword);
    });
});

const form = document.getElementById("form");

form?.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (window.location.protocol === "file:") {
        alert("Para cadastrar, inicie o Synchro e abra http://localhost:8080/index.html no navegador.");
        return;
    }

    const fullName = document.getElementById("name").value.trim();
    const username = document.getElementById("user").value.trim();
    const birthDay = document.getElementById("birthdate").value;
    const email = document.getElementById("email").value.trim();
    const password = document.getElementById("password").value;
    const confirmPassword = document.getElementById("confirm_password").value;
    const gender = document.querySelector('input[name="gender"]:checked')?.value || "";

    if (password !== confirmPassword) {
        alert("As senhas não coincidem.");
        return;
    }

    const submitButton = form.querySelector('button[type="submit"]');
    submitButton.disabled = true;

    try {
        const response = await fetch("/auth/registro", {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                fullName,
                username,
                email,
                birthDay,
                password,
                confirmPassword,
                gender
            })
        });

        if (!response.ok) {
            const message = await getResponseMessage(response, "Não foi possível criar a conta.");
            alert(message);
            return;
        }

        alert("Conta criada com sucesso. Você já está conectado.");
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
