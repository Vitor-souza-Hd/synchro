document.addEventListener("DOMContentLoaded", async () => {
    const container = document.getElementById("friends-listening-container");
    if (!container) return;

    try {
        // 1. Busca a lista de amigos do backend
        const res = await fetch('/follow/amigos', { credentials: 'same-origin' });

        if (!res.ok) throw new Error("Erro ao buscar amigos");

        const amigos = await res.json();

        if (amigos.length === 0) {
            container.innerHTML = '<p style="color: #b3b3b3;">Você ainda não segue ninguém. Vá para o perfil de outros usuários e clique em "Seguir"!</p>';
            return;
        }

        container.innerHTML = ''; // Limpa o carregando

        // 2. Para cada amigo, busca os dados no Last.fm (se tiver)
        for (const amigo of amigos) {
            const card = document.createElement('div');
            // Adicionando estilos inline para garantir que nada quebre
            card.style.cssText = 'background: rgba(255,255,255,0.05); padding: 15px; border-radius: 10px; text-align: center; width: 160px; display: flex; flex-direction: column; align-items: center; margin: 10px;';

            let topArtistName = "Sem dados do Last.fm";
            let topArtistImage = "https://placehold.co/150/333/fff?text=?";

            if (amigo.lastFmUsername) {
                try {
                    const lastfmRes = await fetch(`/last-fm/user/${encodeURIComponent(amigo.lastFmUsername)}/top/artist`);
                    if (lastfmRes.ok) {
                        const lastfmData = await lastfmRes.json();
                        const topArtists = lastfmData.topartists?.artist || [];

                        if (topArtists.length > 0) {
                            topArtistName = topArtists[0].name;
                            if (topArtists[0].image) {
                                const img = topArtists[0].image.find(i => i.size === 'large' || i.size === 'extralarge');
                                if (img && img['#text']) topArtistImage = img['#text'];
                            }
                        }
                    }
                } catch (e) {
                    console.log("Erro no Last.fm para o amigo:", amigo.username);
                }
            }

            // Estilos inline para garantir que o texto não fique invisível
            card.innerHTML = `
                <img src="${topArtistImage}" alt="${topArtistName}" style="width: 100px; height: 100px; border-radius: 50%; object-fit: cover; margin-bottom: 10px; display: block;">
                <h4 style="color: white !important; margin: 0 0 5px 0; font-size: 16px;">${amigo.username}</h4>
                <p style="color: #b3b3b3 !important; font-size: 12px; margin: 0;">Ouvindo:</p>
                <p style="color: #1db954 !important; font-weight: bold; font-size: 14px; margin: 5px 0 0 0;">${topArtistName}</p>
            `;

            container.appendChild(card);
        }

    } catch (error) {
        console.error("Erro na aba de amigos:", error);
        container.innerHTML = '<p style="color: #ff4d4d;">Erro ao carregar os amigos.</p>';
    }
});