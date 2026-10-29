package org.example.synchro.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.config.LastFmConfig;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Set;

public class LastFmHistoryClient {
    private final LastFmConfig config;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public LastFmHistoryClient(LastFmConfig config) { this.config = config; }

    public JsonNode page(String username, long from, long to, int page) throws Exception {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("Configure LASTFM_API_KEY antes de importar.");
        }
        URI uri = UriComponentsBuilder.fromUriString(config.getBaseUrl())
                .queryParam("method", "user.getRecentTracks").queryParam("user", username)
                .queryParam("api_key", config.getApiKey()).queryParam("format", "json")
                .queryParam("limit", 200).queryParam("extended", 1)
                .queryParam("from", from).queryParam("to", to).queryParam("page", page).build().encode().toUri();
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                boolean retry = response.statusCode() == 429 || response.statusCode() >= 500;
                if (!retry && response.statusCode() != 200) {
                    throw new IllegalStateException("Last.fm HTTP " + response.statusCode());
                }
                if (!retry) {
                    JsonNode root = mapper.readTree(response.body());
                    if (!root.has("error")) {
                        if (!root.path("recenttracks").isObject()) throw new IllegalStateException("Resposta Last.fm sem recenttracks.");
                        return root.get("recenttracks");
                    }
                    int code = root.path("error").asInt();
                    if (!Set.of(8, 11, 16, 29).contains(code)) {
                        throw new IllegalStateException("Last.fm erro " + code + ": " + root.path("message").asText());
                    }
                }
            } catch (java.io.IOException e) {
                // Network failures retry the same page; never print the URI containing the API key.
            }
            if (attempt < 5) Thread.sleep(1000L << attempt);
        }
        throw new IllegalStateException("Last.fm indisponível após 6 tentativas. Execute novamente para retomar.");
    }
}
