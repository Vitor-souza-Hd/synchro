package org.example.synchro.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.config.LastFmConfig;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Set;
import java.util.Map;

public class LastFmHistoryClient {
    private final LastFmConfig config;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public LastFmHistoryClient(LastFmConfig config) { this.config = config; }

    public JsonNode page(String username, long from, long to, int page) throws Exception {
        return request("user.getRecentTracks", Map.of("user", username, "limit", 200, "extended", 1,
                "from", from, "to", to, "page", page), "recenttracks", false);
    }

    public JsonNode trackTags(String artist, String track) throws Exception {
        return request("track.getTopTags", Map.of("artist", artist, "track", track, "autocorrect", 1), "toptags", true);
    }

    public JsonNode search(String type, String query, int limit) throws Exception {
        return request(type + ".search", Map.of(type, query, "limit", limit), "results", false, 2);
    }

    public JsonNode info(String type, String name, String artist) throws Exception {
        Map<String, ?> params = type.equals("artist") ? Map.of("artist", name, "autocorrect", 1)
                : Map.of(type, name, "artist", artist, "autocorrect", 1);
        return request(type + ".getInfo", params, type, true, 2);
    }

    private long nextCatalogRequest;

    private synchronized void paceCatalogRequest() throws InterruptedException {
        long remaining = nextCatalogRequest - System.nanoTime();
        if (remaining > 0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(remaining);
        nextCatalogRequest = System.nanoTime() + 350_000_000L;
    }

    private JsonNode request(String method, Map<String, ?> parameters, String field, boolean allowMissing) throws Exception {
        return request(method, parameters, field, allowMissing, 6);
    }

    private JsonNode request(String method, Map<String, ?> parameters, String field, boolean allowMissing, int attempts) throws Exception {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("Configure LASTFM_API_KEY antes de importar.");
        }
        var values = new java.util.HashMap<String, Object>(parameters);
        values.put("method", method);
        values.put("api_key", config.getApiKey());
        values.put("format", "json");
        var builder = UriComponentsBuilder.fromUriString(config.getBaseUrl());
        values.forEach((key, value) -> builder.queryParam(key, "{" + key + "}"));
        URI uri = builder.encode().buildAndExpand(values).toUri();
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (attempts == 2) paceCatalogRequest();
            try {
                var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(attempts == 2 ? 8 : 30)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                boolean retry = response.statusCode() == 429 || response.statusCode() >= 500;
                if (!retry && response.statusCode() != 200) {
                    throw new IllegalStateException("Last.fm HTTP " + response.statusCode());
                }
                if (!retry) {
                    JsonNode root = mapper.readTree(response.body());
                    if (!root.has("error")) {
                        if (!root.path(field).isObject()) throw new IllegalStateException("Resposta Last.fm sem " + field + ".");
                        return root.get(field);
                    }
                    int code = root.path("error").asInt();
                    if (allowMissing && Set.of(6, 7).contains(code)) return mapper.createObjectNode();
                    if (!Set.of(8, 11, 16, 29).contains(code)) {
                        throw new IllegalStateException("Last.fm erro " + code + ": " + root.path("message").asText());
                    }
                }
            } catch (java.io.IOException e) {
                // Network failures retry the same page; never print the URI containing the API key.
            }
            if (attempt < attempts - 1) Thread.sleep(1000L << attempt);
        }
        throw new IllegalStateException("Last.fm indisponível após " + attempts + " tentativas. Tente novamente.");
    }
}
