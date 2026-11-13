
document.addEventListener("synchro:own-profile", ({detail: me}) => {

    // =====================================================
    // TOP GENEROS MUSICAIS - SYNCHRO
    // =====================================================

    const container =
        document.querySelector(".report-genres");

    const periodSelect =
        document.getElementById("rankingPeriod");

    if (!container) {
        return;
    }

    let ultimaConsulta = 0;

    function mostrarMensagem(message) {
        container.replaceChildren();

        const paragraph = document.createElement("p");
        paragraph.className = "profile-empty";
        paragraph.textContent = message;

        container.appendChild(paragraph);
    }

    const calcularGeneros = periodo => window.SynchroMusicGenres.calculate(periodo, fetchProfileRanking);

    const api = "/users/me/music-profile";
    const weeklyStatus = document.getElementById("musicProfileGenresStatus");
    const weeklyList = document.getElementById("musicProfileGenres");
    const weeklyRetry = document.getElementById("retryMusicGenres");
    let weeklyRequest;
    let savedProfile;

    function renderWeekly(genres) {
        weeklyList.replaceChildren(...genres.map(genre => {
            const item = document.createElement("li");
            item.textContent = genre;
            return item;
        }));
    }

    function loadWeek() {
        if (weeklyRequest) return weeklyRequest;
        weeklyRetry.hidden = true;
        weeklyStatus.textContent = "Calculando seus gêneros da semana…";

        const computation = calcularGeneros("7day");
        weeklyRequest = computation;
        const savedRequest = savedProfile
            ? Promise.resolve(savedProfile)
            : buscarJson(api).catch(() => null);
        savedRequest.then(saved => {
            savedProfile = saved;
            if (saved?.genres?.length) renderWeekly(saved.genres);
        });

        Promise.allSettled([computation, savedRequest]).then(async ([calculated, previous]) => {
            const saved = previous.value;
            if (calculated.status === "rejected" || calculated.value.parcial) {
                if (!saved?.genres?.length && calculated.status === "fulfilled") {
                    renderWeekly(calculated.value.resultado.map(([genre]) => genre));
                }
                weeklyStatus.textContent = saved?.genres?.length
                    ? "Não foi possível atualizar. Exibindo os gêneros da última análise salva."
                    : "Não foi possível completar a análise de gêneros. Tente novamente.";
                weeklyRetry.hidden = false;
                weeklyRequest = null;
                return;
            }

            const genres = calculated.value.resultado.map(([genre]) => genre);
            renderWeekly(genres);
            try {
                // Evita regravar o mesmo perfil quando ele acabou de ser calculado.
                const fresh = Date.now() - Date.parse(saved?.updatedAt) < 3600000;
                if (!fresh || JSON.stringify(saved?.genres) !== JSON.stringify(genres)) {
                    const response = await fetch(api, {
                        method: "PUT",
                        credentials: "same-origin",
                        headers: {"Content-Type": "application/json"},
                        body: JSON.stringify({genres, lastFmUsername: me.lastFmUsername})
                    });
                    if (!response.ok) throw new Error("Perfil musical não salvo.");
                    savedProfile = await response.json();
                }
                weeklyStatus.textContent = genres.length
                    ? "Estimativa pelas tags dos artistas que você mais ouviu no Last.fm."
                    : "Ainda não há gêneros identificados nas suas reproduções dos últimos 7 dias.";
            } catch {
                weeklyStatus.textContent = "Os gêneros foram calculados, mas não foi possível salvar o perfil.";
                weeklyRetry.hidden = false;
                weeklyRequest = null;
            }
        });
        return computation;
    }

    async function carregarGeneros() {
        const consultaAtual = ++ultimaConsulta;
        mostrarMensagem("Calculando seus gêneros musicais...");
        try {
            if (!me.lastFmConnected) {
                mostrarMensagem("Conecte seu Last.fm para visualizar seus gêneros.");
                return;
            }
            const periodo = periodSelect?.value || "7day";
            const {resultado, totalArtistas, parcial} = await (periodo === "7day" ? loadWeek() : calcularGeneros(periodo));
            if (consultaAtual !== ultimaConsulta) return;
            if (!resultado.length) {
                mostrarMensagem(
                    "Não encontramos gêneros classificados para este período."
                );
                return;
            }

            container.replaceChildren();

            const maiorPontuacao = resultado[0][1];

            resultado.forEach(([genero, pontuacao], index) => {

                const card = document.createElement("div");
                card.className = "weekly-card";

                const line = document.createElement("div");
                line.className = "rank-item";

                const place = document.createElement("h3");
                place.textContent = "#" + (index + 1);

                const title = document.createElement("h3");
                title.textContent = genero;

                const description = document.createElement("p");

                const intensidade = Math.round(
                    pontuacao / maiorPontuacao * 100
                );

                description.textContent =
                    intensidade +
                    "% de afinidade relativa ao primeiro";

                line.append(place, title, description);
                card.appendChild(line);
                container.appendChild(card);
            });

            const note = document.createElement("p");
            note.className = "profile-empty";

            note.textContent =
                "Estimativa baseada nas tags dos " +
                totalArtistas +
                " artistas mais ouvidos. " +
                "Os percentuais não representam a divisão " +
                "exata dos seus scrobbles." + (parcial ? " Parte das tags está indisponível." : "");

            container.appendChild(note);

        } catch (error) {
            if (consultaAtual !== ultimaConsulta) return;

            console.error(
                "Erro ao calcular gêneros:",
                error
            );

            mostrarMensagem(
                "Não foi possível calcular seus gêneros agora."
            );
        }
    }

    if (periodSelect) {
        periodSelect.addEventListener(
            "change",
            carregarGeneros
        );
    }


    document.getElementById("retryRankings").addEventListener("click", carregarGeneros);
    weeklyRetry.addEventListener("click", () => {
        if (periodSelect?.value === "7day") carregarGeneros();
        else loadWeek();
    });
    if (me.lastFmConnected) loadWeek();
    else weeklyStatus.textContent = "Conecte seu Last.fm para ver seus gêneros.";
    carregarGeneros();
});
