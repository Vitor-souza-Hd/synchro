package org.example.synchro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.entities.User;
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
            app.getBean(PlaylistService.class).addUserToPlaylist("test-session", id, other.getId());
            var sharedUpdate = client.send(HttpRequest.newBuilder(URI.create(base + "/playlists/update-playlist/" + id))
                    .header("Cookie", "token_jwt=other-session").header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"titulo\":\"Editada por colaborador\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, sharedUpdate.statusCode());
            assertEquals(image, mapper.readTree(sharedUpdate.body()).get("capa").asText());
        }
    }
}
