package org.example.synchro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.resources.AlbumReleaseResource;
import org.example.synchro.resources.RecomendacaoResource;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.LastfmService;
import org.example.synchro.services.RecomendacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationQueryTests {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.example.synchro.entities")
    @EnableJpaRepositories("org.example.synchro.repositories")
    @Import({RecomendacaoResource.class, RecomendacaoService.class})
    static class Config {
        @Bean CookieService cookies() {
            var cookies = mock(CookieService.class);
            when(cookies.checkCookie("listener")).thenReturn(true);
            when(cookies.getId("listener")).thenReturn(1L);
            return cookies;
        }

        @Bean LastfmService lastfm() throws Exception {
            var lastfm = mock(LastfmService.class);
            var artists = IntStream.rangeClosed(1, 8).mapToObj(id -> Map.of("name", "Artist " + id)).toList();
            var json = new ObjectMapper().writeValueAsString(Map.of("topartists", Map.of("artist", artists)));
            when(lastfm.getTopBySynchroId(eq(1L), eq("artists"), anyString())).thenReturn(Mono.just(json));
            return lastfm;
        }

        @Bean AlbumReleaseResource releases() { return mock(AlbumReleaseResource.class); }
    }

    @Test
    void ranksEightArtistsWithoutRepeatingSharedOrPreviouslyReviewedAlbums() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).run("--spring.profiles.active=recommendation-test",
                "--server.port=0", "--spring.datasource.url=jdbc:h2:mem:recommendation-pages",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa",
                "--spring.datasource.password=", "--spring.jpa.hibernate.ddl-auto=create-drop")) {
            var account = app.getBean(UserRepository.class).save(new User(null, "listener", "listener@example.test",
                    "hash", LocalDate.of(2000, 1, 1), null));
            var sql = app.getBean(JdbcTemplate.class);
            for (int id = 1; id <= 8; id++) {
                sql.update("insert into artista (id,nome,nome_key) values (?,?,?)", id, "Artist " + id, "artist " + id);
                insertAlbum(sql, id, id, 10);
            }
            insertAlbum(sql, 9, 1, 100);
            sql.update("insert into album_artistas (album_id, artista_id) values (9, 2)");
            insertAlbum(sql, 10, 1, 200);
            sql.update("insert into review (midia_id, autor_id, rating) values (10, ?, 7)", account.getData().getId());
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" +
                    app.getEnvironment().getProperty("local.server.port") + "/recomendacoes?limit=18"))
                    .header("Cookie", "token_jwt=listener").build();
            var client = HttpClient.newHttpClient();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            var items = new ObjectMapper().readTree(response.body()).path("items");
            assertEquals(8, items.size());
            for (int i = 0; i < 8; i++) {
                assertEquals(i == 0 ? 9L : i + 1L, items.get(i).path("id").asLong());
            }
        }
    }

    private static void insertAlbum(JdbcTemplate sql, int id, int artist, int listeners) {
        sql.update("insert into midia (id,titulo,lastfm_artist_name,lastfm_listeners) values (?,?,?,?)",
                id, "Album " + id, "Artist " + artist, listeners);
        sql.update("insert into album (id) values (?)", id);
        sql.update("insert into album_artistas (album_id,artista_id) values (?,?)", id, artist);
    }
}
