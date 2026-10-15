
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

@Service
public class CoverArtService {

    private static final int MAX_EDICOES = 5;

    private final WebClient coverArtClient;
    private final WebClient musicBrainzClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    // Primeira tentativa: capa do grupo de lancamentos.
    // Se nao encontrar, busca nas edicoes oficiais.
    public Mono<Map<String, Object>> getAlbumCover(
            String releaseGroupId
    ) {

        return consultarCapa(
                "/release-group/" + releaseGroupId,
                releaseGroupId
        )
                .onErrorResume(error ->
                        Mono.just(
                                resultadoNaoEncontrado(releaseGroupId)
                        )
                )
                .flatMap(resultado -> {

                    if ("found".equals(resultado.get("status"))) {
                        return Mono.just(resultado);
                    }

                    return buscarCapaEmEdicoes(releaseGroupId);
                });
    }

    // Consulta as edicoes associadas ao album.
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
                })
                .onErrorResume(error ->
                        Mono.just(
                                resultadoNaoEncontrado(releaseGroupId)
                        )
                );
    }

    // Seleciona no maximo cinco edicoes oficiais.
    // Ignora edicoes identificadas como downloads digitais.
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

    // Consulta uma capa no Cover Art Archive.
    // Funciona com release-group e release.
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

    // Converte o JSON retornado pelo MusicBrainz.
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

    // Converte a resposta do Cover Art Archive.
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

    // Procura uma capa frontal aprovada.
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
            resultado.put("releaseGroupId", releaseGroupId);
            resultado.put("coverUrl", url);

            return resultado;
        }

        return resultadoNaoEncontrado(releaseGroupId);
    }

    // Aceita apenas URLs de imagens do Cover Art Archive
    // ou do Internet Archive, sempre usando HTTPS.
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

            return "https://" + url.substring("http://".length());

        } catch (IllegalArgumentException error) {
            return "";
        }
    }

    private Map<String, Object> resultadoNaoEncontrado(
            String releaseGroupId
    ) {

        Map<String, Object> resultado =
                new LinkedHashMap<>();

        resultado.put("status", "not_found");
        resultado.put("releaseGroupId", releaseGroupId);
        resultado.put("coverUrl", null);

        return resultado;
    }
}
