console.log("SCRIPT NOVO DO CADASTRO CARREGADO!");

const passwordIcons = document.querySelectorAll('.password-icon');

passwordIcons.forEach(button => {
    button.addEventListener('click', function () {

        const input = this.parentElement.querySelector('.form-control');

        input.type =
            input.type === 'password'
                ? 'text'
                : 'password';
    });
});


// ==============================
// CADASTRO
// ==============================

const form = document.getElementById("form");

form.addEventListener("submit", async (event) => {

    // Impede o formulário de recarregar a página
    event.preventDefault();

    const username =
        document.getElementById("user").value;

    const email =
        document.getElementById("email").value;

    const birthDay =
        document.getElementById("birthdate").value;

    const password =
        document.getElementById("password").value;

    const confirmPassword =
        document.getElementById("confirm_password").value;


    // Verificação simples antes de chamar o backend
    if (password !== confirmPassword) {
        alert("As senhas não coincidem.");
        return;
    }


    const dados = {
        username: username,
        email: email,
        birthDay: birthDay,
        password: password,
        confirmPassword: confirmPassword,
        lastFmUsername: null
    };


    console.log("Enviando cadastro:", dados);


    try {

        const response = await fetch("/auth/registro", {

            method: "POST",

            headers: {
                "Content-Type": "application/json"
            },

            body: JSON.stringify(dados)

        });


        const resposta = await response.text();

        console.log(
            "Resposta do backend:",
            response.status,
            resposta
        );


        if (!response.ok) {
            throw new Error(resposta);
        }


        alert("Conta criada com sucesso!");

        console.log("Cadastro concluído.");


    } catch (error) {

        console.error(
            "Erro ao criar conta:",
            error
        );

        alert("Não foi possível criar a conta.");

    }

});