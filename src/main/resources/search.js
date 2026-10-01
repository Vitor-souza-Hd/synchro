document.addEventListener('DOMContentLoaded', () => {
    const searchInput = document.getElementById('search-bar');
    const resultsDiv = document.getElementById('search-results');

    if (!searchInput || !resultsDiv) return;

    let timeout = null;

    searchInput.addEventListener('input', (e) => {
        clearTimeout(timeout);

        // Debounce: espera 300ms depois que o usuário para de digitar
        timeout = setTimeout(async () => {
            const query = e.target.value.trim();

            // Só busca se tiver mais de 2 letras
            if (query.length < 2) {
                resultsDiv.innerHTML = '';
                return;
            }

            try {
                // 🔥 AQUI É A MÁGICA: Chama a sua API real
                const response = await fetch(`/api/sugestoes?q=${encodeURIComponent(query)}`);

                if (!response.ok) throw new Error('Erro na rede');

                // Recebe o array de strings do backend
                const sugestoes = await response.json();

                // Renderiza o HTML na tela
                if (sugestoes.length > 0) {
                    resultsDiv.innerHTML = sugestoes.map(texto => `
                        <div class="search-suggestion">
                            <div class="search-suggestion-left">
                                <i class="fa-solid fa-magnifying-glass"></i>
                                <span>${texto}</span>
                            </div>
                            <i class="fa-solid fa-arrow-up-right-from-square"></i>
                        </div>
                    `).join('');
                } else {
                    resultsDiv.innerHTML = `
                        <div class="search-suggestion" style="justify-content: center; color: #b3b3b3;">
                            Nenhuma sugestão encontrada.
                        </div>
                    `;
                }

            } catch (error) {
                console.error('Erro ao buscar sugestões:', error);
                resultsDiv.innerHTML = `
                    <div class="search-suggestion" style="justify-content: center; color: #ff4d4d;">
                        Erro ao conectar com o servidor.
                    </div>
                `;
            }

        }, 300);
    });

    // Fecha as sugestões se clicar fora
    document.addEventListener('click', (e) => {
        if (!searchInput.contains(e.target) && !resultsDiv.contains(e.target)) {
            resultsDiv.innerHTML = '';
        }
    });
});