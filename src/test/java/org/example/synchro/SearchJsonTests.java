package org.example.synchro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;
import org.example.synchro.repositories.*;
import org.example.synchro.resources.SearchResource;
import org.example.synchro.resources.CatalogResource;
import org.example.synchro.resources.exception.ResourceExceptionHandler;
import org.example.synchro.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SearchJsonTests {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.example.synchro.entities")
    @EnableJpaRepositories("org.example.synchro.repositories")
    @Import({SearchResource.class, CatalogResource.class, ResourceExceptionHandler.class,
            org.example.synchro.services.LastFmCatalogService.class, org.example.synchro.services.LastFmCatalogStore.class})
    static class SearchConfig {
        @org.springframework.context.annotation.Bean
        org.example.synchro.importer.LastFmHistoryClient client() {
            return new org.example.synchro.importer.LastFmHistoryClient(null) {
                @Override public JsonNode search(String type, String term, int limit) {
                    throw new IllegalStateException("Offline test");
                }
                @Override public JsonNode info(String type, String name, String artist) {
                    return new ObjectMapper().createObjectNode();
                }
            };
        }
    }

    @Test
    void searchSerializesCyclicCatalogAsBoundedJsonWithoutOpenSessionInView() throws Exception {
        try (var context = new SpringApplicationBuilder(SearchConfig.class)
                .web(WebApplicationType.SERVLET)
                .run("--spring.profiles.active=lastfm-import", "--spring.main.web-application-type=servlet",
                        "--server.port=0", "--spring.datasource.url=jdbc:h2:mem:search-json",
                        "--spring.jpa.hibernate.ddl-auto=create-drop", "--spring.jpa.open-in-view=false")) {
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> {
                var artist = context.getBean(ArtistaRepository.class)
                        .save(new Artista("Radiohead", "Banda", null));
                var album = context.getBean(AlbumRepository.class)
                        .save(new Album("Radio Album", "Descrição", LocalDate.of(2000, 1, 1)));
                album.addArtista(artist);
                var song = context.getBean(MusicaRepository.class)
                        .save(new Musica("Radio Song", null, Duration.ofSeconds(120), "rock"));
                song.addArtista(artist);
                album.addMusica(song);
                // Also exercise null album and duration, common in imported scrobbles.
                var single = context.getBean(MusicaRepository.class)
                        .save(new Musica("Radio Single", null, null, null));
                single.addArtista(artist);
                context.getBean(UserRepository.class).save(new User(null, "RadioListener", "private@example.test",
                        "private-password", LocalDate.of(2000, 1, 1), null));
                context.getBean(ArtistaRepository.class).save(new Artista("X", null, null));
            });
            int port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
            var client = HttpClient.newHttpClient();
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/search?q=radio")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
            JsonNode json = new ObjectMapper().readTree(response.body());
            assertEquals("local", json.path("source").asText());
            assertTrue(json.path("warning").asText().contains("indisponível"));
            assertEquals(2, json.path("songs").size());
            assertEquals(1, json.path("artists").size());
            assertEquals(1, json.path("albums").size());
            for (JsonNode song : json.path("songs")) {
                assertEquals("Radiohead", song.path("artistas").get(0).path("nome").asText());
                assertFalse(song.path("artistas").get(0).has("musicas"));
                if (song.path("titulo").asText().equals("Radio Song")) {
                    assertEquals("PT2M", song.path("duracao").asText());
                    assertEquals("Radio Album", song.path("album").path("titulo").asText());
                    assertEquals(3, song.path("album").size());
                } else {
                    assertTrue(song.path("album").isNull());
                    assertTrue(song.path("duracao").isNull());
                }
            }
            assertEquals("2000-01-01", json.path("albums").get(0).path("dataLancamento").asText());
            assertFalse(response.body().contains("nomeKey"));
            assertFalse(response.body().contains("tituloKey"));
            assertFalse(response.body().contains("hibernateLazyInitializer"));
            for (String group : new String[]{"songs", "albums", "artists", "users"}) {
                long id = json.path(group).get(0).path("id").asLong();
                var detail = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/catalog/" + group + "/" + id)).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, detail.statusCode(), detail.body());
                var body = new ObjectMapper().readTree(detail.body());
                assertFalse(detail.body().contains("private@example.test"));
                assertFalse(detail.body().contains("private-password"));
                if (group.equals("albums")) assertEquals(1, body.path("musicas").size());
                if (group.equals("artists")) assertEquals(2, body.path("musicas").size());
            }
            var missing = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/catalog/songs/999999")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, missing.statusCode());
            for (String name : new String[]{"%20radioHEAD%20", "x"}) {
                var byName = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/catalog/artists/by-name?name=" + name)).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, byName.statusCode(), byName.body());
                assertTrue(new ObjectMapper().readTree(byName.body()).path("artist").has("nome"));
            }
            for (String query : new String[]{"r", "zzzz-no-match"}) {
                var empty = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/search?q=" + query)).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, empty.statusCode());
                var result = new ObjectMapper().readTree(empty.body());
                for (String group : new String[]{"users", "songs", "artists", "albums"}) {
                    assertTrue(result.path(group).isArray());
                    assertEquals(0, result.path(group).size());
                }
            }
        }
    }
}
