
console.log("SCRIPT DO CADASTRO CARREGADO!");

document.addEventListener("DOMContentLoaded", () => {

    const form = document.getElementById("form");
    const passwordIcons = document.querySelectorAll(".password-icon");
    const submitButton = form?.querySelector('button[type="submit"]');

    // =========================================
    // MOSTRAR E ESCONDER SENHAS
    // =========================================

    passwordIcons.forEach(button => {

        button.addEventListener("click", () => {

            const input = button.parentElement.querySelector(".password-input");

            if (!input) return;

            const mostrarSenha = input.type === "password";

            input.type = mostrarSenha ? "text" : "password";

            button.setAttribute(
                "aria-label",
                mostrarSenha ? "Ocultar senha" : "Mostrar senha"
            );
        });
    });

    if (!form) {
        console.error("Formulario de cadastro nao encontrado.");
        return;
    }

    // =========================================
    // CADASTRAR USUARIO
    // =========================================

    form.addEventListener("submit", async (event) => {

        event.preventDefault();

        const username = document.getElementById("user").value.trim();
        const email = document.getElementById("email").value.trim();
        const birthDay = document.getElementById("birthdate").value;
        const password = document.getElementById("password").value;
        const confirmPassword = document.getElementById("confirm_password").value;

        if (password !== confirmPassword) {
            alert("As senhas nao coincidem.");
            return;
        }

        const dados = {
            username,
            email,
            birthDay,
            password,
            confirmPassword,
            lastFmUsername: null
        };

        if (submitButton) {
            submitButton.disabled = true;
        }

        try {

            const response = await fetch("/auth/registro", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(dados)
            });

            if (!response.ok) {
                const mensagem = await response.text();

                throw new Error(
                    mensagem || "Nao foi possivel concluir o cadastro."
                );
            }

            console.log("Cadastro realizado com sucesso.");

            alert("Conta criada com sucesso! Faca seu login para continuar.");

            // Redirecionar somente apos o cadastro ser confirmado.
            window.location.replace("../login.html");

        } catch (error) {

            console.error("Erro ao cadastrar:", error);

            alert("Nao foi possivel criar a conta. Verifique os dados e tente novamente.");

        } finally {

            if (submitButton) {
                submitButton.disabled = false;
            }
        }
    });
});
