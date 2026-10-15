
package org.example.synchro.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CoverArtService {

    private final WebClient coverArtClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CoverArtService() {

        // =========================================
        // SEGUIR REDIRECIONAMENTOS HTTP
        // =========================================

        // O Cover Art Archive pode responder com HTTP 307
        // e redirecionar a consulta para archive.org.
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
    }

    // =========================================
    // BUSCAR CAPA POR GRUPO DE LANÇAMENTO
    // =========================================

    public Mono<Map<String, Object>> getAlbumCover(
            String releaseGroupId
    ) {

        return coverArtClient
                .get()
                .uri("/release-group/{id}", releaseGroupId)
                .exchangeToMono(response -> {

                    // Sem imagem cadastrada.
                    if (
                            response.statusCode().value() ==
                                    HttpStatus.NOT_FOUND.value()
                    ) {
                        return Mono.just(
                                resultadoNaoEncontrado(
                                        releaseGroupId
                                )
                        );
                    }

                    // Erros HTTP reais.
                    if (response.statusCode().isError()) {
                        return response.createException()
                                .flatMap(Mono::error);
                    }

                    // Depois de seguir o redirecionamento,
                    // a resposta deve conter o JSON das capas.
                    // Lemos como String para tolerar diferentes
                    // valores de Content-Type.
                    return response
                            .bodyToMono(String.class)
                            .map(texto -> converterResposta(
                                    texto,
                                    releaseGroupId
                            ));
                })
                .timeout(Duration.ofSeconds(25));
    }

    // =========================================
    // CONVERTER RESPOSTA EM JSON
    // =========================================

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
                    "O Cover Art Archive retornou um JSON inválido.",
                    error
            );
        }
    }

    // =========================================
    // SELECIONAR CAPA FRONTAL
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

            // Apenas imagens frontais.
            if (!imagem.path("front").asBoolean(false)) {
                continue;
            }

            // Apenas imagens aprovadas.
            if (!imagem.path("approved").asBoolean(false)) {
                continue;
            }

            JsonNode thumbnails =
                    imagem.path("thumbnails");

            // Preferir resolução de 500 pixels.
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

            if (!url.startsWith("https://")) {
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
    // CAPA NÃO ENCONTRADA
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
