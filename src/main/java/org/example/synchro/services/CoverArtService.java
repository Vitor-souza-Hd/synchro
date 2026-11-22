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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
public class CoverArtService {

    private static final int MAX_EDICOES = 5;
    private static final int MAX_CACHE_ENTRIES = 500;
    private static final Duration TEMPO_GRUPO = Duration.ofSeconds(4);
    private static final Duration TEMPO_EDICAO = Duration.ofSeconds(3);
    private static final Duration CACHE_PARCIAL = Duration.ofSeconds(20);
    private static final Duration PAUSA_COVER_ART = Duration.ofSeconds(15);
    private static final Pattern RELEASE_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final Duration CACHE_CAPA_ENCONTRADA =
            Duration.ofHours(24);

    private static final Duration CACHE_CAPA_NAO_ENCONTRADA =
            Duration.ofMinutes(30);

    private final WebClient coverArtClient;
    private final WebClient musicBrainzClient;
    private final MusicBrainzRateLimiter rateLimiter;
    private final Duration lookupTimeout;
    private final AtomicReference<Instant> coverArtPausedUntil = new AtomicReference<>(Instant.EPOCH);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final BoundedLookupCache<ConsultaCapa, Capas> cacheCapas = new BoundedLookupCache<>(
            MAX_CACHE_ENTRIES, resultado -> {
                if (resultado.parcial()) return CACHE_PARCIAL;
                return resultado.encontrada() ? CACHE_CAPA_ENCONTRADA : CACHE_CAPA_NAO_ENCONTRADA;
            });

    private record ConsultaCapa(String grupoId, boolean alternativas) {}

    private record Capas(List<String> urls, boolean parcial) {
        boolean encontrada() {
            return !urls.isEmpty();
        }

        Capas comoParcial() {
            return new Capas(urls, true);
        }

        Map<String, Object> resposta(String grupoId) {
            Map<String, Object> resultado = new LinkedHashMap<>();
            resultado.put("status", encontrada() ? "found" : "not_found");
            resultado.put("releaseGroupId", grupoId);
            resultado.put("coverUrl", encontrada() ? urls.get(0) : null);
            resultado.put("coverUrls", urls);
            if (parcial) resultado.put("partial", true);
            return resultado;
        }
    }

    private static final Capas SEM_CAPAS = new Capas(List.of(), false);

    private static final class CoverArtPaused extends ResponseStatusException {
        private final long retryAfterSeconds;

        private CoverArtPaused(HttpStatus status, long retryAfterSeconds) {
            super(status, "Cover Art Archive está temporariamente indisponível.");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        @Override
        public HttpHeaders getHeaders() {
            var headers = new HttpHeaders();
            headers.setCacheControl("no-store");
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
            return headers;
        }
    }

    public CoverArtService(MusicBrainzRateLimiter rateLimiter,
            @org.springframework.beans.factory.annotation.Value("${synchro.discovery.timeout:20s}") Duration lookupTimeout) {

        this.rateLimiter = rateLimiter;
        this.lookupTimeout = lookupTimeout;

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

    public Mono<Map<String, Object>> getAlbumCover(String releaseGroupId, boolean alternativas) {
        var consulta = new ConsultaCapa(releaseGroupId, alternativas);
        return cacheCapas.get(consulta, () -> Mono.defer(() -> {
            var pausa = pausaAtiva();
            if (pausa != null) return Mono.error(pausa);
            return alternativas ? buscarCapaEmEdicoes(releaseGroupId, true) : buscarCapaSemCache(releaseGroupId);
        }).timeout(lookupTimeout)).map(capas -> capas.resposta(releaseGroupId));
    }

    // =========================================
    // BUSCA ORIGINAL: GRUPO E EDICOES
    // =========================================

    private Mono<Capas> buscarCapaSemCache(String releaseGroupId) {
        var capaDoGrupo = consultarCapa("/release-group/" + releaseGroupId);
        if (lookupTimeout.compareTo(TEMPO_GRUPO) > 0) capaDoGrupo = capaDoGrupo.timeout(TEMPO_GRUPO);
        return capaDoGrupo.filter(Capas::encontrada)
                .onErrorResume(error -> !(error instanceof CoverArtPaused), error -> buscarCapaEmEdicoes(releaseGroupId, false)
                        .filter(Capas::encontrada)
                        .map(Capas::comoParcial)
                        .switchIfEmpty(Mono.error(error)))
                .switchIfEmpty(Mono.defer(() -> buscarCapaEmEdicoes(releaseGroupId, false)));
    }

    // =========================================
    // BUSCAR CAPA NAS EDICOES OFICIAIS
    // =========================================

    private Mono<Capas> buscarCapaEmEdicoes(String releaseGroupId, boolean alternativas) {
        return rateLimiter.aguardarPermissao()
                .then(Mono.defer(() -> musicBrainzClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/ws/2/release-group/{id}")
                                .queryParam("inc", "releases")
                                .queryParam("fmt", "json")
                                .build(releaseGroupId)
                        )
                        .retrieve()
                        .bodyToMono(String.class)
                        .onErrorMap(WebClientResponseException.class, rateLimiter::tratarFalha)
                ))
                .switchIfEmpty(Mono.error(respostaInvalida("MusicBrainz")))
                .map(this::converterJson)
                .map(this::selecionarEdicoesOficiais)
                .onErrorReturn(WebClientResponseException.NotFound.class, List.of())
                .flatMap(edicoes -> buscarCapasDisponiveis(edicoes, alternativas));
    }

    private Mono<Capas> buscarCapasDisponiveis(List<String> edicoes, boolean alternativas) {
        var falha = new AtomicReference<Throwable>();
        var candidatos = Flux.fromIterable(edicoes)
                .concatMap(releaseId -> consultarCapa("/release/" + releaseId).timeout(TEMPO_EDICAO)
                        .onErrorResume(error -> {
                            if (error instanceof CoverArtPaused) return Mono.error(error);
                            falha.compareAndSet(null, error);
                            return Mono.empty();
                        }))
                .onErrorResume(CoverArtPaused.class, error -> {
                    falha.set(error);
                    return Flux.empty();
                })
                .filter(Capas::encontrada);

        Mono<Capas> encontrados = alternativas
                ? candidatos.concatMapIterable(Capas::urls)
                        .collect(LinkedHashSet<String>::new, LinkedHashSet::add)
                        .filter(urls -> !urls.isEmpty())
                        .map(urls -> new Capas(List.copyOf(urls), false))
                : candidatos.next();

        return encontrados.map(capas -> falha.get() == null ? capas : capas.comoParcial())
                .switchIfEmpty(Mono.defer(() -> {
                    Throwable error = falha.get();
                    return error == null ? Mono.just(SEM_CAPAS) : Mono.error(error);
                }));
    }

    // =========================================
    // SELECIONAR EDICOES
    // =========================================

    private List<String> selecionarEdicoesOficiais(
            JsonNode data
    ) {

        List<String> edicoes = new ArrayList<>();

        JsonNode releases = data.path("releases");

        if (!releases.isArray()) throw respostaInvalida("MusicBrainz");

        List<String> comCapa = new ArrayList<>();

        for (JsonNode release : releases) {

            if (!"Official".equalsIgnoreCase(
                    release.path("status").asText("")
            )) {
                continue;
            }

            String releaseId = release.path("id").asText("");

            if (!RELEASE_ID.matcher(releaseId).matches()) continue;

            if (release.path("cover-art-archive").path("front").asBoolean(false)) {
                comCapa.add(releaseId);
            } else {
                edicoes.add(releaseId);
            }
        }

        comCapa.addAll(edicoes);
        return comCapa.stream().distinct().limit(MAX_EDICOES).toList();
    }

    // =========================================
    // CONSULTAR COVER ART ARCHIVE
    // =========================================

    private Mono<Capas> consultarCapa(String caminho) {
        return Mono.defer(() -> {
            var pausa = pausaAtiva();
            if (pausa != null) return Mono.error(pausa);

            return coverArtClient.get().uri(caminho).exchangeToMono(response -> {
                int status = response.statusCode().value();
                if (status == 404) return response.releaseBody().thenReturn(SEM_CAPAS);
                if (status == 429 || status == 503) {
                    var now = Instant.now();
                    var deadline = RetryAfter.deadline(response.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                            now, PAUSA_COVER_ART);
                    var until = coverArtPausedUntil.accumulateAndGet(deadline,
                            (current, next) -> current.isAfter(next) ? current : next);
                    var error = new CoverArtPaused(HttpStatus.valueOf(status), RetryAfter.secondsRemaining(now, until));
                    return response.releaseBody().then(Mono.error(error));
                }
                if (!response.statusCode().is2xxSuccessful()) {
                    return response.releaseBody().then(Mono.error(new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY, "Cover Art Archive está temporariamente indisponível.")));
                }
                return response.bodyToMono(String.class)
                        .switchIfEmpty(Mono.error(respostaInvalida("Cover Art Archive")))
                        .map(this::converterResposta);
            });
        });
    }

    private CoverArtPaused pausaAtiva() {
        var now = Instant.now();
        var deadline = coverArtPausedUntil.get();
        return deadline.isAfter(now)
                ? new CoverArtPaused(HttpStatus.SERVICE_UNAVAILABLE, RetryAfter.secondsRemaining(now, deadline))
                : null;
    }

    private ResponseStatusException respostaInvalida(String provedor) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta inválida de " + provedor + ".");
    }

    // =========================================
    // PROCESSAMENTO DOS JSONS
    // =========================================

    private JsonNode converterJson(String texto) {

        try {

            return objectMapper.readTree(texto);

        } catch (JsonProcessingException error) {

            throw respostaInvalida("MusicBrainz");
        }
    }

    private Capas converterResposta(String texto) {
        try {
            JsonNode data = objectMapper.readTree(texto);
            if (data == null || !data.path("images").isArray()) {
                throw respostaInvalida("Cover Art Archive");
            }
            return extrairCapas(data.path("images"));
        } catch (JsonProcessingException error) {
            throw respostaInvalida("Cover Art Archive");
        }
    }

    // =========================================
    // SELECIONAR CAPA FRONTAL APROVADA
    // =========================================

    private Capas extrairCapas(JsonNode imagens) {
        var urls = new LinkedHashSet<String>();
        var frentesAlternativas = new ArrayList<JsonNode>();

        for (JsonNode imagem : imagens) {
            if (!imagem.path("approved").asBoolean(false)) continue;
            if (imagem.path("front").asBoolean(false)) {
                adicionarVariantes(imagem, urls);
            } else if (tipoFrontal(imagem)) {
                frentesAlternativas.add(imagem);
            }
        }

        frentesAlternativas.forEach(imagem -> adicionarVariantes(imagem, urls));
        return new Capas(List.copyOf(urls), false);
    }

    private boolean tipoFrontal(JsonNode imagem) {
        for (JsonNode tipo : imagem.path("types")) {
            if ("Front".equalsIgnoreCase(tipo.asText())) return true;
        }
        return false;
    }

    private void adicionarVariantes(JsonNode imagem, LinkedHashSet<String> urls) {
        JsonNode thumbnails = imagem.path("thumbnails");
        for (String tamanho : List.of("500", "large", "1200", "250", "small")) {
            adicionarUrl(thumbnails.path(tamanho).asText(""), urls);
        }
        adicionarUrl(imagem.path("image").asText(""), urls);
    }

    private void adicionarUrl(String valor, LinkedHashSet<String> urls) {
        String url = normalizarUrlImagem(valor);
        if (!url.isBlank()) urls.add(url);
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

            if (esquema == null || host == null || uri.getRawUserInfo() != null) {
                return "";
            }

            if (!"http".equalsIgnoreCase(esquema)
                    && !"https".equalsIgnoreCase(esquema)) {
                return "";
            }

            String hostNormalizado = host.toLowerCase(Locale.ROOT);

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

}

