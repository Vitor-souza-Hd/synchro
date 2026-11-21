document.addEventListener("DOMContentLoaded", async () => {
    const container = document.getElementById("recommendations-container");
    if (!container) return;

    container.classList.add("genres");

    try {
        const res = await fetch('/recomendacoes', { credentials: 'same-origin' });

        // MENSAGEM DE LOGIN (SEM BOTÃO)
        if (res.status === 401) {
            container.innerHTML = `
                <div style="grid-column: 1 / -1; padding: 20px; background: rgba(255,255,255,0.05); border-radius: 10px; border: 1px solid rgba(255,255,255,0.1);">
                    <p style="color: #b3b3b3; font-size: 1.1rem; margin: 0;">
                        Faça login para ver suas recomendações personalizadas.
                    </p>
                </div>
            `;
            return;
        }

        const data = await res.json();

        if (!Array.isArray(data) || data.length === 0) {
            container.innerHTML = '<p style="color: #b3b3b3; grid-column: 1 / -1;">Você já avaliou todos os álbuns dos seus artistas favoritos!</p>';
            return;
        }

        container.innerHTML = '';

        data.forEach(item => {
            const card = document.createElement('div');
            card.classList.add('genre-card');

            const link = (typeof item.id === 'number' || !isNaN(item.id))
                ? `album.html?id=${item.id}`
                : `artista.html?artist=${encodeURIComponent(item.nomeArtista)}`;

            const capa = item.capaUrl || `https://ui-avatars.com/api/?name=${encodeURIComponent(item.nomeArtista)}&background=181818&color=1db954&size=400`;

            card.innerHTML = `
                <a href="${link}" style="width: 100%;">
                    <img src="${capa}" alt="Imagem de ${item.titulo}" onerror="this.onerror=null; this.src='https://ui-avatars.com/api/?name=${encodeURIComponent(item.nomeArtista)}&background=181818&color=1db954&size=400';">
                    <div style="text-align: center; width: 100%;">
                        <h3 style="text-align: center;">${item.titulo}</h3>
                        <p style="color: rgba(197, 196, 196, 0.669); font-size: 14px; margin-top: 5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; text-align: center;">${item.nomeArtista}</p>
                    </div>
                </a>
            `;

            container.appendChild(card);
        });

    } catch (error) {
        console.error("Erro ao buscar recomendações:", error);
        container.innerHTML = '<p style="color: #ff4d4d; grid-column: 1 / -1;">Erro ao carregar recomendações.</p>';
    }
});