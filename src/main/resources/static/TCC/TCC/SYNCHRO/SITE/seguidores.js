document.addEventListener("DOMContentLoaded", async () => {
    const btnSeguir = document.getElementById("btnSeguir");
    if (!btnSeguir) return;

    // Descobre o ID do usuário do perfil pela URL (ex: user.html?id=5)
    const urlParams = new URLSearchParams(window.location.search);
    const perfilId = urlParams.get("id");

    if (!perfilId) return; // Se não tiver ID na URL, não faz nada

    let meuId = null;

    // 1. Descobre quem é o usuário logado
    try {
        const meRes = await fetch('/auth/me', { credentials: 'same-origin' });
        if (meRes.ok) {
            const me = await meRes.json();
            meuId = me.id;

            // Se o perfil for o do próprio usuário, esconde o botão
            if (meuId == perfilId) {
                btnSeguir.style.display = 'none';
                return;
            }
        }
    } catch { return; }

    // 2. Verifica se já segue para decidir o texto do botão
    let jaSegue = false;
    try {
        const checkRes = await fetch(`/follow/check/${perfilId}`, { credentials: 'same-origin' });
        if (checkRes.ok) {
            jaSegue = await checkRes.json();
            atualizarBotao();
            btnSeguir.style.display = 'block';
        }
    } catch (err) { console.error("Erro ao checar follow", err); }

    // 3. Ação de Clicar no botão
    btnSeguir.addEventListener("click", async () => {
        btnSeguir.disabled = true;
        try {
            if (jaSegue) {
                await fetch(`/follow/${perfilId}`, { method: 'DELETE', credentials: 'same-origin' });
                jaSegue = false;
            } else {
                await fetch(`/follow/${perfilId}`, { method: 'POST', credentials: 'same-origin' });
                jaSegue = true;
            }
            atualizarBotao();
        } catch (e) {
            alert("Erro ao processar. Tente novamente.");
        } finally {
            btnSeguir.disabled = false;
        }
    });

    function atualizarBotao() {
        if (jaSegue) {
            btnSeguir.textContent = "Deixar de Seguir";
            btnSeguir.style.background = "transparent";
            btnSeguir.style.color = "#b3b3b3";
            btnSeguir.style.borderColor = "#b3b3b3";
        } else {
            btnSeguir.textContent = "Seguir";
            btnSeguir.style.background = "#1db954";
            btnSeguir.style.color = "#fff";
            btnSeguir.style.borderColor = "#1db954";
        }
    }
});