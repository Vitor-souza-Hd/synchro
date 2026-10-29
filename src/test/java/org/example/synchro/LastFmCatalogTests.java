package org.example.synchro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.importer.LastFmHistoryClient;
import org.example.synchro.repositories.*;
import org.example.synchro.resources.*;
import org.example.synchro.services.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LastFmCatalogTests {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.example.synchro.entities")
    @EnableJpaRepositories("org.example.synchro.repositories")
    @Import({LastFmConfig.class, LastFmHistoryClient.class, LastFmCatalogService.class, LastFmCatalogStore.class,
            SearchResource.class, CatalogResource.class, org.example.synchro.resources.exception.ResourceExceptionHandler.class})
    static class Config {}

    @Test
    void searchesEnrichesPersistsAndDeduplicatesWithSafeJsonAndOfflineFallback() throws Exception {
        var mapper = new ObjectMapper();
        var calls = new AtomicInteger();
        var offline = new AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        String longWiki = "Texto com mais de 255 caracteres. ".repeat(20);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            Map<String, String> params = new HashMap<>();
            for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
                var parts = pair.split("=", 2);
                params.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
            String method = params.get("method");
            Object body;
            if (offline.get()) body = Map.of("error", 10, "message", "Test offline");
            else if (method.endsWith(".search")) {
                String type = method.split("\\.")[0];
                String name = type.equals("artist") ? "The Weeknd" : "Starboy";
                // Single-object matches and tag arrays are both supported.
                body = Map.of("results", Map.of(type + "matches", Map.of(type,
                        Map.of("name", name, "artist", params.get(type).equals("different query") ? "Another Artist" : "The Weeknd",
                                "url", "https://www.last.fm/music/search-result"))));
            } else if (method.equals("track.getInfo")) {
                body = Map.of("track", Map.of("name", "Starboy", "mbid", "track-mbid", "artist", Map.of("name", "The Weeknd"),
                        "duration", "230000", "listeners", "2463433", "playcount", "35456784",
                        "album", Map.of("title", "Starboy", "artist", "The Weeknd", "image", List.of(Map.of("#text", "https://example.com/cover.jpg", "size", "extralarge"))),
                        "toptags", Map.of("tag", List.of(Map.of("name", "rnb"), Map.of("name", "pop"), Map.of("name", "2016"))),
                        "wiki", Map.of("content", longWiki), "url", "https://www.last.fm/music/The+Weeknd/_/Starboy"));
            } else if (method.equals("artist.getInfo")) {
                body = Map.of("artist", Map.of("name", "The Weeknd", "bio", Map.of("content", longWiki),
                        "stats", Map.of("listeners", "6000000", "playcount", "5000000000"), "tags", Map.of("tag", Map.of("name", "pop"))));
            } else {
                body = Map.of("album", Map.of("name", "Starboy", "artist", "The Weeknd", "listeners", "1000000", "playcount", "3000000000",
                        "wiki", Map.of("content", longWiki, "published", "12 Jan 2020"),
                        "tracks", Map.of("track", List.of(Map.of("name", "Starboy", "duration", "230", "artist", Map.of("name", "The Weeknd")),
                                Map.of("name", "Party Monster", "duration", "249", "artist", Map.of("name", "The Weeknd"))))));
            }
            byte[] bytes = mapper.writeValueAsBytes(body);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (var context = new SpringApplicationBuilder(Config.class).web(WebApplicationType.SERVLET).run(
                "--spring.profiles.active=lastfm-import", "--spring.main.web-application-type=servlet", "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:catalog-api", "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.jpa.open-in-view=false", "--lastfm.api-key=test-only", "--lastfm.base-url=http://localhost:" + server.getAddress().getPort() + "/")) {
            var service = context.getBean(LastFmCatalogService.class);
            var songs = context.getBean(MusicaRepository.class);
            var albums = context.getBean(AlbumRepository.class);
            var artists = context.getBean(ArtistaRepository.class);
            assertTrue(service.search("s").getSongs().isEmpty());
            assertEquals(0, calls.get());
            String base = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
            var http = HttpClient.newHttpClient();
            var response = http.send(HttpRequest.newBuilder(URI.create(base + "/search?q=Starboy")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            var json = mapper.readTree(response.body());
            assertTrue(json.path("warning").isNull(), response.body());
            assertEquals(1, json.path("songs").size());
            var song = json.path("songs").get(0);
            assertEquals("PT3M50S", song.path("duracao").asText());
            assertEquals("r&b, pop", song.path("genero").asText());
            assertEquals(35456784L, song.path("lastFm").path("playcount").asLong());
            assertEquals("https://example.com/cover.jpg", song.path("lastFm").path("imageUrl").asText());
            assertEquals(3, song.path("lastFm").path("tags").size());
            assertFalse(response.body().contains("rawJson"));
            assertEquals(2, songs.count());
            assertEquals(1, albums.count());
            assertEquals(1, artists.count());
            assertEquals(longWiki.strip(), songs.findByTitulo("Starboy").getDescricao());
            assertEquals(Duration.ofSeconds(249), songs.findByTitulo("Party Monster").getDuracao());
            assertEquals(5000000000L, artists.findByNome("The Weeknd").getLastFm().getPlaycount());
            assertTrue(songs.findByTitulo("Starboy").getLastFm().getRawJson().contains("track-mbid"));
            assertNull(albums.findByTitulo("Starboy").getDataLancamento(), "Wiki publication must not become release date");
            int before = calls.get();
            assertEquals(song.path("id").asLong(), service.search("  STARBOY  ").getSongs().get(0).id());
            assertEquals(before, calls.get(), "Identical search uses short-lived cache");
            service.search("different query");
            assertEquals(2, songs.count(), "Different search must reuse existing names");
            assertEquals(before + 3, calls.get(), "Fresh metadata must not be downloaded twice");
            assertEquals("The Weeknd", songs.findByTitulo("Starboy").getLastFm().getArtistName());
            assertEquals("https://www.last.fm/music/The+Weeknd/_/Starboy", songs.findByTitulo("Starboy").getLastFm().getUrl(),
                    "Basic search matches with the same title must not overwrite complete metadata");
            var detail = http.send(HttpRequest.newBuilder(URI.create(base + "/catalog/songs/" + song.path("id").asLong())).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, detail.statusCode());
            assertEquals(2463433L, mapper.readTree(detail.body()).path("lastFm").path("listeners").asLong());
            offline.set(true);
            var fallback = service.search("Star");
            assertEquals("local", fallback.getSource());
            assertFalse(fallback.getSongs().isEmpty());
            assertNotNull(fallback.getWarning());
            assertEquals(longWiki.strip(), songs.findByTitulo("Starboy").getDescricao());
            assertEquals(0, context.getBean(LastFmScrobbleRepository.class).count(), "Searching must not add listens to user history");
        } finally { server.stop(0); }
    }
}
