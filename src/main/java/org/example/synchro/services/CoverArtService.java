
package org.example.synchro.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CoverArtService {

    private static final int MAX_EDICOES = 5;
    private static final int MAX_CACHE_ENTRIES = 500;

    private static final Duration CACHE_CAPA_ENCONTRADA =
            Duration.ofHours(24);

    private static final Duration CACHE_CAPA_NAO_ENCONTRADA =
            Duration.ofMinutes(30);

    private final WebClient coverArtClient;
    private final WebClient musicBrainzClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Armazena consultas compartilhadas por releaseGroupId.
    private final ConcurrentHashMap<
            String, Mono<Map<String, Object>>
            > cacheCapas = new ConcurrentHashMap<>();

    public CoverArtService() {

        HttpClient httpClient = HttpClient.create()
                .followRedirect(true);

        this.coverArtClient = WebClient.builder()
                .baseUrl("https://coverartarchive.org")
                .clientConnector(
                        new ReactorClientHttpConnector(httpClient)
                )
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "SynchroTCC/0.1 (https://github.com/Vitor-souza-Hd/synchro)"
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .codecs(configurer ->
                        configurer.defaultCodecs()
                                .maxInMemorySize(2 * 1024 * 1024)
                )
                .build();

        this.musicBrainzClient = WebClient.builder()
                .baseUrl("https://musicbrainz.org")
                .clientConnector(
                        new ReactorClientHttpConnector(httpClient)
                )
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "SynchroTCC/0.1 (https://github.com/Vitor-souza-Hd/synchro)"
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .codecs(configurer ->
                        configurer.defaultCodecs()
                                .maxInMemorySize(2 * 1024 * 1024)
                )
                .build();
    }

    // =========================================
    // CONSULTA COM CACHE
    // =========================================

    public Mono<Map<String, Object>> getAlbumCover(
            String releaseGroupId
    ) {

        // Evita crescimento ilimitado do mapa.
        // Se estiver cheio, limpa as entradas antigas
        // e inicia um novo conjunto de consultas.
        if (cacheCapas.size() >= MAX_CACHE_ENTRIES
                && !cacheCapas.containsKey(releaseGroupId)) {
            cacheCapas.clear();
        }

        return cacheCapas.computeIfAbsent(
                releaseGroupId,
                id -> criarConsultaCacheada(id)
        );
    }

    private Mono<Map<String, Object>> criarConsultaCacheada(
            String releaseGroupId
    ) {

        return Mono.defer(
                        () -> buscarCapaSemCache(releaseGroupId)
                )
                .cache(
                        resultado -> {

                            if ("found".equals(
                                    resultado.get("status")
                            )) {
                                return CACHE_CAPA_ENCONTRADA;
                            }

                            return CACHE_CAPA_NAO_ENCONTRADA;
                        },
                        // Falhas reais nao permanecem no cache.
                        erro -> Duration.ZERO,
                        () -> Duration.ZERO
                );
    }

    // =========================================
    // BUSCA ORIGINAL: GRUPO E EDICOES
    // =========================================

    private Mono<Map<String, Object>> buscarCapaSemCache(
            String releaseGroupId
    ) {

        return consultarCapa(
                "/release-group/" + releaseGroupId,
                releaseGroupId
        )
                .onErrorResume(error ->
                        buscarCapaEmEdicoes(releaseGroupId)
                )
                .flatMap(resultado -> {

                    if ("found".equals(resultado.get("status"))) {
                        return Mono.just(resultado);
                    }

                    return buscarCapaEmEdicoes(releaseGroupId);
                });
    }

    // =========================================
    // BUSCAR CAPA NAS EDICOES OFICIAIS
    // =========================================

    private Mono<Map<String, Object>> buscarCapaEmEdicoes(
            String releaseGroupId
    ) {

        return musicBrainzClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/ws/2/release-group/{id}")
                        .queryParam("inc", "releases")
                        .queryParam("fmt", "json")
                        .build(releaseGroupId)
                )
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(20))
                .map(this::converterJson)
                .flatMap(data -> {

                    List<String> edicoes =
                            selecionarEdicoesOficiais(data);

                    if (edicoes.isEmpty()) {
                        return Mono.just(
                                resultadoNaoEncontrado(releaseGroupId)
                        );
                    }

                    return Flux.fromIterable(edicoes)
                            .concatMap(releaseId ->
                                    consultarCapa(
                                            "/release/" + releaseId,
                                            releaseGroupId
                                    )
                                            .onErrorResume(error ->
                                                    Mono.just(
                                                            resultadoNaoEncontrado(
                                                                    releaseGroupId
                                                            )
                                                    )
                                            )
                            )
                            .filter(resultado ->
                                    "found".equals(
                                            resultado.get("status")
                                    )
                            )
                            .next()
                            .defaultIfEmpty(
                                    resultadoNaoEncontrado(releaseGroupId)
                            );
                });
    }

    // =========================================
    // SELECIONAR EDICOES
    // =========================================

    private List<String> selecionarEdicoesOficiais(
            JsonNode data
    ) {

        List<String> edicoes = new ArrayList<>();

        JsonNode releases = data.path("releases");

        if (!releases.isArray()) {
            return edicoes;
        }

        for (JsonNode release : releases) {

            if (!"Official".equalsIgnoreCase(
                    release.path("status").asText("")
            )) {
                continue;
            }

            String releaseId = release.path("id").asText("");

            if (releaseId.isBlank()) {
                continue;
            }

            String descricao = release
                    .path("disambiguation")
                    .asText("")
                    .toLowerCase();

            if (descricao.contains("digital download")) {
                continue;
            }

            edicoes.add(releaseId);

            if (edicoes.size() >= MAX_EDICOES) {
                break;
            }
        }

        return edicoes;
    }

    // =========================================
    // CONSULTAR COVER ART ARCHIVE
    // =========================================

    private Mono<Map<String, Object>> consultarCapa(
            String caminho,
            String releaseGroupId
    ) {

        return coverArtClient
                .get()
                .uri(caminho)
                .exchangeToMono(response -> {

                    if (response.statusCode().value() == 404) {
                        return Mono.just(
                                resultadoNaoEncontrado(releaseGroupId)
                        );
                    }

                    if (response.statusCode().isError()) {
                        return response.createException()
                                .flatMap(Mono::error);
                    }

                    return response
                            .bodyToMono(String.class)
                            .map(texto -> converterResposta(
                                    texto,
                                    releaseGroupId
                            ));
                })
                .timeout(Duration.ofSeconds(20));
    }

    // =========================================
    // PROCESSAMENTO DOS JSONS
    // =========================================

    private JsonNode converterJson(String texto) {

        try {

            return objectMapper.readTree(texto);

        } catch (JsonProcessingException error) {

            throw new IllegalStateException(
                    "O MusicBrainz retornou um JSON invalido.",
                    error
            );
        }
    }

    private Map<String, Object> converterResposta(
            String texto,
            String releaseGroupId
    ) {

        if (texto == null || texto.isBlank()) {
            throw new IllegalStateException(
                    "O Cover Art Archive retornou uma resposta vazia."
            );
        }

        try {

            JsonNode data = objectMapper.readTree(texto);

            if (data == null || !data.isObject()) {
                throw new IllegalStateException(
                        "Resposta inesperada do Cover Art Archive."
                );
            }

            return extrairCapa(data, releaseGroupId);

        } catch (JsonProcessingException error) {

            throw new IllegalStateException(
                    "O Cover Art Archive retornou um JSON invalido.",
                    error
            );
        }
    }

    // =========================================
    // SELECIONAR CAPA FRONTAL APROVADA
    // =========================================

    private Map<String, Object> extrairCapa(
            JsonNode data,
            String releaseGroupId
    ) {

        JsonNode imagens = data.path("images");

        if (!imagens.isArray()) {
            return resultadoNaoEncontrado(releaseGroupId);
        }

        for (JsonNode imagem : imagens) {

            if (!imagem.path("front").asBoolean(false)) {
                continue;
            }

            if (!imagem.path("approved").asBoolean(false)) {
                continue;
            }

            JsonNode thumbnails = imagem.path("thumbnails");

            String url = thumbnails
                    .path("500")
                    .asText("");

            if (url.isBlank()) {
                url = thumbnails
                        .path("large")
                        .asText("");
            }

            if (url.isBlank()) {
                url = imagem
                        .path("image")
                        .asText("");
            }

            url = normalizarUrlImagem(url);

            if (url.isBlank()) {
                continue;
            }

            Map<String, Object> resultado =
                    new LinkedHashMap<>();

            resultado.put("status", "found");
            resultado.put(
                    "releaseGroupId",
                    releaseGroupId
            );
            resultado.put("coverUrl", url);

            return resultado;
        }

        return resultadoNaoEncontrado(releaseGroupId);
    }

    // =========================================
    // NORMALIZAR URL PARA HTTPS
    // =========================================

    private String normalizarUrlImagem(String url) {

        if (url == null || url.isBlank()) {
            return "";
        }

        try {

            URI uri = URI.create(url);

            String esquema = uri.getScheme();
            String host = uri.getHost();

            if (esquema == null || host == null) {
                return "";
            }

            if (!"http".equalsIgnoreCase(esquema)
                    && !"https".equalsIgnoreCase(esquema)) {
                return "";
            }

            String hostNormalizado = host.toLowerCase();

            boolean hostPermitido =
                    hostNormalizado.equals("coverartarchive.org")
                            || hostNormalizado.endsWith(
                            ".coverartarchive.org"
                    )
                            || hostNormalizado.equals("archive.org")
                            || hostNormalizado.endsWith(".archive.org");

            if (!hostPermitido) {
                return "";
            }

            if ("https".equalsIgnoreCase(esquema)) {
                return url;
            }

            return "https://" + url.substring(
                    "http://".length()
            );

        } catch (IllegalArgumentException error) {
            return "";
        }
    }

    // =========================================
    // RESPOSTA SEM CAPA
    // =========================================

    private Map<String, Object> resultadoNaoEncontrado(
            String releaseGroupId
    ) {

        Map<String, Object> resultado =
                new LinkedHashMap<>();

        resultado.put("status", "not_found");
        resultado.put(
                "releaseGroupId",
                releaseGroupId
        );
        resultado.put("coverUrl", null);

        return resultado;
    }
}
