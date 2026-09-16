document
    .getElementById("lastfm-login")
    .addEventListener("click", async () => {

        try {

            const response = await fetch("/last-fm/auth/login");

            if (!response.ok) {
                throw new Error("Erro ao iniciar autenticação");
            }

            const data = await response.json();

            window.location.href = data.authUrl;

        } catch (error) {

            console.error(error);

            alert("Não foi possível conectar ao Last.fm.");
        }
    });