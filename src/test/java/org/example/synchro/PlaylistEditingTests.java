package org.example.synchro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.entities.Musica;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.MusicaRepository;
import org.example.synchro.repositories.PlaylistRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.resources.PlaylistResource;
import org.example.synchro.resources.exception.ResourceExceptionHandler;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.PlaylistService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaylistEditingTests {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.example.synchro.entities")
    @EnableJpaRepositories("org.example.synchro.repositories")
    @Import({PlaylistService.class, PlaylistResource.class, ResourceExceptionHandler.class})
    static class Config {
        @Bean CookieService cookies() {
            var cookies = mock(CookieService.class);
            when(cookies.checkCookie("test-session")).thenReturn(true);
            when(cookies.getId("test-session")).thenReturn(1L);
            when(cookies.checkCookie("other-session")).thenReturn(true);
            when(cookies.getId("other-session")).thenReturn(2L);
            return cookies;
        }
    }

    @Test
    void savesImageChangesThroughHttpAndKeepsThemAfterReload() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).profiles("playlist-edit-test")
                .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:playlist-edit;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop").run()) {
            User user = new User();
            user.setUsername("playlist-test");
            var owner = app.getBean(UserRepository.class).save(user);
            assertEquals(1L, owner.getId());
            String base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var client = HttpClient.newHttpClient();
            var mapper = new ObjectMapper();
            var create = client.send(HttpRequest.newBuilder(URI.create(base + "/playlists/new-playlist"))
                    .header("Cookie", "token_jwt=test-session").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"titulo\":\"Original\"}" )).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(201, create.statusCode(), create.body());
            long id = mapper.readTree(create.body()).get("id").asLong();
            User other = new User();
            other.setUsername("other-user");
            app.getBean(UserRepository.class).save(other);
            var denied = client.send(HttpRequest.newBuilder(URI.create(base + "/playlists/update-playlist/" + id))
                    .header("Cookie", "token_jwt=other-session").header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"titulo\":\"Sem permissão\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, denied.statusCode());
            String image = "data:image/gif;base64," + "A".repeat(2796204);
            var update = client.send(HttpRequest.newBuilder(URI.create(base + "/playlists/update-playlist/" + id))
                    .header("Cookie", "token_jwt=test-session").header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                            "titulo", "Atualizada", "descricao", "Nova descrição", "capa", image, "banner", image))))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, update.statusCode(), update.body().substring(0, Math.min(300, update.body().length())));
            var reload = client.send(HttpRequest.newBuilder(URI.create(base + "/playlists/me"))
                    .header("Cookie", "token_jwt=test-session").build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, reload.statusCode());
            var saved = mapper.readTree(reload.body()).get(0);
            assertEquals("Atualizada", saved.get("titulo").asText());
            assertEquals(image, saved.get("capa").asText());
            assertEquals(image, saved.get("banner").asText());
            var summary = client.send(HttpRequest.newBuilder(URI.create(base + "/playlists/me/summary"))
                    .header("Cookie", "token_jwt=test-session").build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, summary.statusCode(), summary.body());
            assertTrue(summary.body().length() < 1000, "The listing must not include the multi-megabyte images");
            var card = mapper.readTree(summary.body()).get(0);
            String cover = "/playlists/me/" + id + "/artwork/cover";
            assertEquals(cover, card.get("capa").asText());
            assertEquals(0, card.get("totalMusicas").asInt());
            assertFalse(card.has("musicas"));
            assertTrue(card.path("podeExcluir").asBoolean());

            var detail = client.send(HttpRequest.newBuilder(URI.create(base + "/playlists/me/" + id))
                    .header("Cookie", "token_jwt=test-session").build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, detail.statusCode());
            assertEquals(cover, mapper.readTree(detail.body()).get("capa").asText());
            var coverRequest = HttpRequest.newBuilder(URI.create(base + cover));
            var artwork = client.send(coverRequest.copy().header("Cookie", "token_jwt=test-session").build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, artwork.statusCode());
            assertEquals("image/gif", artwork.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(2796204 / 4 * 3, artwork.body().length);
            String etag = artwork.headers().firstValue("ETag").orElseThrow();
            assertEquals(304, client.send(coverRequest.copy().header("Cookie", "token_jwt=test-session")
                    .header("If-None-Match", etag).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(401, client.send(coverRequest.copy().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(404, client.send(coverRequest.copy().header("Cookie", "token_jwt=other-session")
                    .header("If-None-Match", etag).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            app.getBean(PlaylistService.class).addUserToPlaylist("test-session", id, other.getId());
            var collaboratorLibrary = client.send(HttpRequest.newBuilder(URI.create(base + "/playlists/me/summary"))
                    .header("Cookie", "token_jwt=other-session").build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, collaboratorLibrary.statusCode());
            assertFalse(mapper.readTree(collaboratorLibrary.body()).get(0).get("podeExcluir").asBoolean());
            assertEquals(200, client.send(coverRequest.copy().header("Cookie", "token_jwt=other-session").build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            var sharedUpdate = client.send(HttpRequest.newBuilder(URI.create(base + "/playlists/update-playlist/" + id))
                    .header("Cookie", "token_jwt=other-session").header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"titulo\":\"Editada por colaborador\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, sharedUpdate.statusCode());
            assertEquals(image, mapper.readTree(sharedUpdate.body()).get("capa").asText());
            assertFalse(mapper.readTree(sharedUpdate.body()).path("podeExcluir").asBoolean());
            assertTrue(mapper.readTree(detail.body()).path("podeExcluir").asBoolean());

            var songs = app.getBean(MusicaRepository.class);
            var song = songs.save(new Musica("Catalog track", "", null, null));
            app.getBean(PlaylistService.class).addMidiaToPlaylist("test-session", id, song.getId());
            var share = HttpRequest.newBuilder(URI.create(base + "/playlists/me/" + id + "/share"))
                    .POST(HttpRequest.BodyPublishers.noBody());
            assertEquals(401, client.send(share.copy().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(404, client.send(share.copy().header("Cookie", "token_jwt=other-session").build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            var ownerShare = share.copy().header("Cookie", "token_jwt=test-session").build();
            var link = client.send(ownerShare, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, link.statusCode(), link.body());
            String token = mapper.readTree(link.body()).get("token").asText();
            assertEquals(token, mapper.readTree(client.send(ownerShare, HttpResponse.BodyHandlers.ofString()).body())
                    .get("token").asText());
            var publicDetail = HttpRequest.newBuilder(URI.create(base + "/playlists/shared/" + token)).build();
            var shared = client.send(publicDetail, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, shared.statusCode());
            var visible = mapper.readTree(shared.body());
            assertEquals(1, visible.path("musicas").size());
            assertTrue(visible.path("usuarios").isEmpty());
            assertFalse(visible.path("podeExcluir").asBoolean());
            assertFalse(visible.path("colaborador").asBoolean());
            String sharedCover = visible.get("capa").asText();
            assertEquals("/playlists/shared/" + token + "/artwork/cover", sharedCover);
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base + sharedCover)).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());

            var publicList = HttpRequest.newBuilder(URI.create(base + "/playlists/users/" + owner.getId())).build();
            assertTrue(mapper.readTree(client.send(publicList, HttpResponse.BodyHandlers.ofString()).body()).isEmpty());
            var publish = HttpRequest.newBuilder(URI.create(base + "/playlists/me/" + id + "/profile-visibility"))
                    .header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString("{\"visible\":true}"));
            assertEquals(404, client.send(publish.copy().header("Cookie", "token_jwt=other-session").build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(204, client.send(publish.copy().header("Cookie", "token_jwt=test-session").build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            var published = mapper.readTree(client.send(publicList, HttpResponse.BodyHandlers.ofString()).body());
            assertEquals(1, published.size());
            assertEquals(token, published.get(0).get("token").asText());
            assertEquals(sharedCover, published.get(0).get("capa").asText());
            assertEquals(1, published.get(0).get("totalMusicas").asInt());

            var hide = publish.copy().header("Cookie", "token_jwt=test-session")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"visible\":false}")).build();
            assertEquals(204, client.send(hide, HttpResponse.BodyHandlers.discarding()).statusCode());
            assertTrue(mapper.readTree(client.send(publicList, HttpResponse.BodyHandlers.ofString()).body()).isEmpty());
            assertEquals(200, client.send(publicDetail, HttpResponse.BodyHandlers.discarding()).statusCode());

            var delete = HttpRequest.newBuilder(URI.create(base + "/playlists/me/" + id)).DELETE();
            assertEquals(401, client.send(delete.copy().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(401, client.send(delete.copy().header("Cookie", "token_jwt=other-session").build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertTrue(app.getBean(PlaylistRepository.class).existsById(id));

            var ownerDelete = delete.copy().header("Cookie", "token_jwt=test-session").build();
            assertEquals(204, client.send(ownerDelete, HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(404, client.send(ownerDelete, HttpResponse.BodyHandlers.discarding()).statusCode());
            assertFalse(app.getBean(PlaylistRepository.class).existsById(id));
            assertEquals(404, client.send(publicDetail, HttpResponse.BodyHandlers.discarding()).statusCode());
            assertTrue(songs.existsById(song.getId()));
            assertTrue(app.getBean(PlaylistService.class).findMyPlaylistSummaries("test-session").isEmpty());
            assertTrue(app.getBean(PlaylistService.class).findMyPlaylistSummaries("other-session").isEmpty());
        }
    }
}
