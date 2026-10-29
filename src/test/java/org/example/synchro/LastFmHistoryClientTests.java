package org.example.synchro;

import com.sun.net.httpserver.HttpServer;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.importer.LastFmHistoryClient;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class LastFmHistoryClientTests {
    @Test
    void encodesArtistAndTitleAndDistinguishesMissingTagsFromInvalidCredentials() throws Exception {
        var body = new AtomicReference<>("{\"toptags\":{\"tag\":[{\"name\":\"rock\"}]}}");
        var received = new HashMap<String, String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.clear();
            for (String field : exchange.getRequestURI().getRawQuery().split("&")) {
                var parts = field.split("=", 2);
                received.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            var config = new LastFmConfig();
            config.setApiKey("test-key");
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            var client = new LastFmHistoryClient(config);
            assertTrue(client.trackTags("AC/DC + Música & Artista", "Song + # ? &").has("tag"));
            assertEquals("AC/DC + Música & Artista", received.get("artist"));
            assertEquals("Song + # ? &", received.get("track"));
            assertEquals("track.getTopTags", received.get("method"));
            body.set("{\"error\":6,\"message\":\"Track not found\"}");
            assertTrue(client.trackTags("Artist", "Missing").isEmpty());
            body.set("{\"error\":10,\"message\":\"Invalid API key\"}");
            assertThrows(IllegalStateException.class, () -> client.trackTags("Artist", "Track"));
            body.set("{\"recenttracks\":{\"track\":[],\"@attr\":{\"page\":1,\"totalPages\":0}}}");
            assertTrue(client.page("jinka070", 0, 100, 1).has("track"));
        } finally { server.stop(0); }
    }
}
