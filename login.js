const loginForm = document.querySelector(".login");

loginForm.addEventListener("submit", function (event) {
    event.preventDefault();

    // Pega os dados digitados
    const email = document.querySelector(
        'input[type="email"]'
    ).value.trim();

    const password = document.querySelector(
        'input[type="password"]'
    ).value;

    // Procura o usuário salvo no navegador
    const user = JSON.parse(
        localStorage.getItem("synchroUser")
    );

    // Verifica se existe uma conta cadastrada
    if (!user) {
        alert("Nenhuma conta foi cadastrada.");
        return;
    }

    // Verifica email e senha
    if (
        email === user.email &&
        password === user.password
    ) {
        // Marca o usuário como logado
        localStorage.setItem(
            "synchroLogged",
            "true"
        );

        // Vai para o SYNCHRO
        window.location.href =
            "../../index.html";

    } else {
        alert("E-mail ou senha incorretos.");
    }
});