package org.example.synchro.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MusicBrainzService {

    private final WebClient musicBrainzClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_CACHE_ENTRIES = 500;
    private static final Duration CACHE_POSITIVO = Duration.ofHours(6);
    private static final Duration CACHE_VAZIO = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, Mono<String>> cachePesquisas =
            new ConcurrentHashMap<>();

    private final MusicBrainzRateLimiter rateLimiter;

    public MusicBrainzService(MusicBrainzRateLimiter rateLimiter) {

        this.rateLimiter = rateLimiter;

        // Permitir respostas de até 2 MB.
        // Esta configuração afeta apenas o MusicBrainz.
        ExchangeStrategies strategies =
                ExchangeStrategies.builder()
                        .codecs(configurer ->
                                configurer.defaultCodecs()
                                        .maxInMemorySize(2 * 1024 * 1024)
                        )
                        .build();

        this.musicBrainzClient = WebClient.builder()
                .baseUrl("https://musicbrainz.org/ws/2")
                .exchangeStrategies(strategies)
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "SynchroTCC/0.1 (https://github.com/Vitor-souza-Hd/synchro)"
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    // =========================================
    // PESQUISAR ÁLBUNS
    // =========================================

    public Mono<String> searchAlbums(
            String artistName,
            String albumName
    ) {

        String query =
                "releasegroup:\"" + limparPesquisa(albumName) + "\"" +
                        " AND artist:\"" + limparPesquisa(artistName) + "\"" +
                        " AND primarytype:album";

        return consultarMusicBrainz(
                "/release-group/",
                query,
                10
        );
    }

    // =========================================
    // PESQUISAR GRAVAÇÕES
    // =========================================

    public Mono<String> searchTracks(
            String artistName,
            String trackName
    ) {

        String query = montarPesquisaGravacao(
                artistName,
                trackName
        );

        return consultarMusicBrainz(
                "/recording/",
                query,
                10
        );
    }

    // =========================================
    // IDENTIFICAR ÁLBUM DE UMA MÚSICA
    // =========================================

    public Mono<Map<String, Object>> findTrackAlbum(
            String artistName,
            String trackName
    ) {

        String query = montarPesquisaGravacao(
                artistName,
                trackName
        );

        return consultarMusicBrainz(
                "/recording/",
                query,
                50
        ).map(json -> selecionarAlbum(
                json,
                artistName,
                trackName
        ));
    }

    // =========================================
    // CONSULTAR API
    // =========================================

    private Mono<String> consultarMusicBrainz(
            String caminho,
            String query,
            int limite
    ) {

        String chave = caminho + "|" + limite + "|" + query;

        // O limite protege a memoria da aplicacao.
        // A limpeza so afeta o cache, nunca os dados persistidos.
        if (cachePesquisas.size() >= MAX_CACHE_ENTRIES
                && !cachePesquisas.containsKey(chave)) {
            cachePesquisas.clear();
        }

        return cachePesquisas.computeIfAbsent(chave, ignorada ->
                Mono.defer(() -> rateLimiter.aguardarPermissao()
                        .then(musicBrainzClient
                                .get()
                                .uri(uriBuilder -> uriBuilder
                                        .path(caminho)
                                        .queryParam("query", query)
                                        .queryParam("fmt", "json")
                                        .queryParam("limit", limite)
                                        .build())
                                .retrieve()
                                .bodyToMono(String.class)
                                .timeout(Duration.ofSeconds(20))))
                        .cache(
                                json -> respostaVazia(json)
                                        ? CACHE_VAZIO : CACHE_POSITIVO,
                                erro -> Duration.ZERO,
                                () -> Duration.ZERO
                        )
        );
    }

    private boolean respostaVazia(String json) {
        try {
            JsonNode resposta = objectMapper.readTree(json);
            JsonNode gravacoes = resposta.path("recordings");
            JsonNode albuns = resposta.path("release-groups");
            return (gravacoes.isArray() && gravacoes.isEmpty())
                    || (albuns.isArray() && albuns.isEmpty());
        } catch (JsonProcessingException error) {
            // Resposta inesperada: nao prolongar o cache.
            return true;
        }
    }

    // =========================================
    // MONTAR PESQUISA DE GRAVAÇÕES
    // =========================================

    private String montarPesquisaGravacao(
            String artistName,
            String trackName
    ) {

        return "recording:\"" +
                limparPesquisa(trackName) +
                "\" AND artist:\"" +
                limparPesquisa(artistName) +
                "\"";
    }

    // =========================================
    // SELECIONAR ÁLBUM
    // =========================================

    private Map<String, Object> selecionarAlbum(
            String json,
            String artistName,
            String trackName
    ) {

        JsonNode resposta;

        try {

            resposta = objectMapper.readTree(json);

        } catch (JsonProcessingException error) {

            throw new IllegalStateException(
                    "Resposta inválida do MusicBrainz.",
                    error
            );
        }

        JsonNode gravacoes = resposta.path("recordings");

        Map<String, Map<String, String>> albuns =
                new LinkedHashMap<>();

        for (JsonNode gravacao : gravacoes) {

            if (!mesmoNome(
                    gravacao.path("title").asText(),
                    trackName
            )) {
                continue;
            }

            if (!artistaPrincipalCorreto(
                    gravacao.path("artist-credit"),
                    artistName
            )) {
                continue;
            }

            String observacao = normalizar(
                    gravacao.path("disambiguation").asText()
            );

            if (
                    observacao.contains("live") ||
                            observacao.contains("remix") ||
                            observacao.contains("demo") ||
                            observacao.contains("karaoke") ||
                            observacao.contains("instrumental")
            ) {
                continue;
            }

            for (JsonNode lancamento : gravacao.path("releases")) {

                if (!"Official".equalsIgnoreCase(
                        lancamento.path("status").asText()
                )) {
                    continue;
                }

                if (!artistaPrincipalCorreto(
                        lancamento.path("artist-credit"),
                        artistName
                )) {
                    continue;
                }

                JsonNode grupo =
                        lancamento.path("release-group");

                if (!"Album".equalsIgnoreCase(
                        grupo.path("primary-type").asText()
                )) {
                    continue;
                }

                JsonNode tiposSecundarios =
                        grupo.path("secondary-types");

                if (
                        tiposSecundarios.isArray() &&
                                !tiposSecundarios.isEmpty()
                ) {
                    continue;
                }

                String id = grupo.path("id").asText();
                String titulo = grupo.path("title").asText();
                String data = lancamento.path("date").asText();

                if (id.isBlank() || titulo.isBlank()) {
                    continue;
                }

                String dataOrdenacao =
                        data.isBlank() ? "9999" : data;

                Map<String, String> candidato =
                        new LinkedHashMap<>();

                candidato.put("title", titulo);
                candidato.put("releaseGroupId", id);
                candidato.put("releaseDate", data);
                candidato.put("sortDate", dataOrdenacao);

                Map<String, String> anterior =
                        albuns.get(id);

                if (
                        anterior == null ||
                                dataOrdenacao.compareTo(
                                        anterior.get("sortDate")
                                ) < 0
                ) {
                    albuns.put(id, candidato);
                }
            }
        }

        List<Map<String, String>> candidatos =
                new ArrayList<>(albuns.values());

        candidatos.sort(
                Comparator.comparing(
                        album -> album.get("sortDate")
                )
        );

        Map<String, Object> resultado =
                new LinkedHashMap<>();

        resultado.put("artist", artistName);
        resultado.put("track", trackName);

        if (candidatos.isEmpty()) {

            resultado.put("status", "not_found");
            resultado.put("album", null);
            resultado.put(
                    "message",
                    "Nenhum álbum de estúdio confirmado nos resultados consultados."
            );

            return resultado;
        }

        Map<String, String> escolhido =
                candidatos.get(0);

        Map<String, String> album =
                new LinkedHashMap<>();

        album.put("title", escolhido.get("title"));
        album.put(
                "releaseGroupId",
                escolhido.get("releaseGroupId")
        );
        album.put(
                "releaseDate",
                escolhido.get("releaseDate")
        );

        resultado.put("status", "found");
        resultado.put("album", album);

        List<Map<String, String>> alternativas =
                new ArrayList<>();

        for (
                int i = 0;
                i < Math.min(5, candidatos.size());
                i++
        ) {

            Map<String, String> item =
                    new LinkedHashMap<>(candidatos.get(i));

            item.remove("sortDate");

            alternativas.add(item);
        }

        resultado.put("candidates", alternativas);

        return resultado;
    }

    // =========================================
    // VALIDAR ARTISTA PRINCIPAL
    // =========================================

    private boolean artistaPrincipalCorreto(
            JsonNode creditos,
            String artistaProcurado
    ) {

        if (!creditos.isArray() || creditos.isEmpty()) {
            return false;
        }

        JsonNode primeiro = creditos.get(0);

        String nome = primeiro.path("artist")
                .path("name")
                .asText();

        if (nome.isBlank()) {
            nome = primeiro.path("name").asText();
        }

        return mesmoNome(nome, artistaProcurado);
    }

    // =========================================
    // COMPARAÇÃO DE NOMES
    // =========================================

    private boolean mesmoNome(
            String primeiro,
            String segundo
    ) {

        String nome1 = normalizar(primeiro);
        String nome2 = normalizar(segundo);

        return !nome1.isBlank() && nome1.equals(nome2);
    }

    private String normalizar(String texto) {

        if (texto == null) {
            return "";
        }

        return Normalizer
                .normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    // =========================================
    // LIMPAR ENTRADA
    // =========================================

    private String limparPesquisa(String valor) {

        if (valor == null) {
            return "";
        }

        String texto = valor
                .replace("\\", " ")
                .replace("\"", " ")
                .trim();

        if (texto.length() > 120) {
            texto = texto.substring(0, 120);
        }

        return texto;
    }
}
