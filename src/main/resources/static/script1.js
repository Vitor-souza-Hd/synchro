const passwordIcons = document.querySelectorAll('.password-icon');

passwordIcons.forEach(icon => {
    icon.addEventListener('click', function () {

        const input =
            this.parentElement.querySelector('.form-control');

        input.type =
            input.type === 'password'
                ? 'text'
                : 'password';

        this.classList.toggle('fa-eye');
        this.classList.toggle('fa-eye-slash');
    });
});


document.getElementById('form').addEventListener('submit', async function (event) {

    event.preventDefault();

    const name =
        document.getElementById('name').value.trim();

    const lastName =
        document.getElementById('last_name').value.trim();

    const birthdate =
        document.getElementById('birthdate').value;

    const email =
        document.getElementById('email').value.trim();

    const password =
        document.getElementById('password').value;

    const confirmPassword =
        document.getElementById('confirm_password').value;


    if (password !== confirmPassword) {

        alert('As senhas não coincidem.');

        return;
    }


    const username =
        `${name} ${lastName}`.trim();


    try {

        const response = await fetch('/users', {

            method: 'POST',

            headers: {
                'Content-Type': 'application/json'
            },

            body: JSON.stringify({

                username: username,
                email: email,
                birthDay: birthdate,
                password: password,
                confirmPassword: confirmPassword,
                lastFmUsername: null

            })

        });


        const data = await response.json();


        if (!response.ok) {

            throw new Error(
                data.message || 'Erro ao criar conta.'
            );

        }


        console.log(
            'Usuário cadastrado:',
            data
        );


        alert(
            'Conta criada com sucesso!'
        );


    } catch (error) {

        console.error(
            'Erro no cadastro:',
            error
        );


        alert(
            error.message ||
            'Erro ao criar conta.'
        );

    }

});