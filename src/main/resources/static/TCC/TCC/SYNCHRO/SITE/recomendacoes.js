document.addEventListener("DOMContentLoaded", async () => {
    const container = document.getElementById("recommendations-container");
    if (!container) return;

    try {
        const res = await fetch('/recomendacoes', { credentials: 'same-origin' });

        if (res.status === 401) {
            container.innerHTML = '<p style="color: #b3b3b3;">Faça login para ver suas recomendações.</p>';
            return;
        }

        const data = await res.json();

        if (!Array.isArray(data)) {
            container.innerHTML = '<p style="color: #b3b3b3;">Erro ao carregar recomendações.</p>';
            return;
        }

        if (data.length === 0) {
            container.innerHTML = '<p style="color: #b3b3b3;">Você já avaliou todos os álbuns dos seus artistas favoritos! Explore mais no Synchro.</p>';
            return;
        }

        container.innerHTML = '';

        data.forEach(item => {
            const card = document.createElement('div');
            card.style.cssText = 'background: rgba(255,255,255,0.05); padding: 15px; border-radius: 10px; text-align: center; width: 160px; display: flex; flex-direction: column; align-items: center; cursor: pointer; transition: transform 0.3s;';

            card.onmouseenter = () => card.style.transform = 'translateY(-5px)';
            card.onmouseleave = () => card.style.transform = 'translateY(0)';

            // LÓGICA DE REDIRECIONAMENTO INTELIGENTE:
            card.onclick = () => {
                // Se o ID for numérico, é um álbum do banco
                if (typeof item.id === 'number' || !isNaN(item.id)) {
                    window.location.href = `album.html?id=${item.id}`;
                } else {
                    // Se for string (artista_X), vai para a página do artista
                    window.location.href = `artista.html?artist=${encodeURIComponent(item.nomeArtista)}`;
                }
            };

            const capa = item.capaUrl || 'https://placehold.co/150/333/fff?text=Album';

            card.innerHTML = `
                <img src="${capa}" alt="${item.titulo}" style="width: 120px; height: 120px; border-radius: 8px; object-fit: cover; margin-bottom: 10px;">
                <h4 style="color: white; font-size: 14px; margin: 0 0 5px 0;">${item.titulo}</h4>
                <p style="color: #b3b3b3; font-size: 12px; margin: 0;">${item.nomeArtista}</p>
            `;

            container.appendChild(container.appendChild(card));
        });

    } catch (error) {
        console.error("Erro ao buscar recomendações:", error);
        container.innerHTML = '<p style="color: #ff4d4d;">Erro ao carregar recomendações.</p>';
    }
});