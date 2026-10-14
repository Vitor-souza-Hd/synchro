const passwordIcons = document.querySelectorAll('.password-icon');

passwordIcons.forEach(icon => {
    icon.addEventListener('click', function () {
        const input = this.parentElement.querySelector('.form-control');
        input.type = input.type === 'password' ? 'text' : 'password';
        this.classList.toggle('fa-eye');
    })
})
const form = document.getElementById("form");

form.addEventListener("submit", function(event) {
    event.preventDefault();

    const name =
        document.getElementById("name").value;

    const username =
        document.getElementById("user").value;

    const birthdate =
        document.getElementById("birthdate").value;

    const email =
        document.getElementById("email").value;

    const password =
        document.getElementById("password").value;

    const confirmPassword =
        document.getElementById("confirm_password").value;

    if (password !== confirmPassword) {
        alert("As senhas não coincidem.");
        return;
    }

    const gender =
        document.querySelector(
            'input[name="gender"]:checked'
        )?.value || "";

    const user = {
        name,
        username,
        birthdate,
        email,
        password,
        gender
    };

    localStorage.setItem(
        "synchroUser",
        JSON.stringify(user)
    );

    alert("Conta criada com sucesso!");

    window.location.href = "../login.html";
});