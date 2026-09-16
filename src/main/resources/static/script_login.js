document
    .getElementById("login-form")
    .addEventListener("submit", async (event) => {

        event.preventDefault();

        const email =
            document.getElementById("email").value;

        const password =
            document.getElementById("password").value;

        try {

            const response = await fetch("/auth/login", {
                method: "POST",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify({
                    email: email,
                    password: password
                })
            });

            const message = await response.text();

            if (!response.ok) {
                throw new Error(message);
            }

            console.log("Login realizado:", message);

            alert("Login realizado com sucesso!");

        } catch (error) {

            console.error("Erro no login:", error);

            alert("Erro ao fazer login.");
        }
    });