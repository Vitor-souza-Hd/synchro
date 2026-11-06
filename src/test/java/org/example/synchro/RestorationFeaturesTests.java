package org.example.synchro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.entities.*;
import org.example.synchro.repositories.*;
import org.example.synchro.resources.*;
import org.example.synchro.resources.exception.ResourceExceptionHandler;
import org.example.synchro.services.*;
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
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestorationFeaturesTests {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.example.synchro.entities")
    @EnableJpaRepositories("org.example.synchro.repositories")
    @Import({ReviewService.class, ReviewResource.class, UserService.class, UserResource.class,
            FavoriteArtistService.class, FavoriteArtistResource.class,
            FavoriteAlbumService.class, FavoriteAlbumResource.class,
            FavoriteTrackService.class, FavoriteTrackResource.class, ResourceExceptionHandler.class,
            LastFmCatalogService.class, LastFmCatalogStore.class, CatalogResource.class})
    static class Config {
        @Bean org.example.synchro.importer.LastFmHistoryClient catalogClient() {
            return mock(org.example.synchro.importer.LastFmHistoryClient.class);
        }
        @Bean CookieService cookies() {
            var cookies = mock(CookieService.class);
            when(cookies.checkCookie("owner")).thenReturn(true);
            when(cookies.getId("owner")).thenReturn(1L);
            when(cookies.checkCookie("other")).thenReturn(true);
            when(cookies.getId("other")).thenReturn(2L);
            return cookies;
        }
    }

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String base;
    private HttpResponse<String> request(String method, String path, String session, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path));
        if (session != null) builder.header("Cookie", "token_jwt=" + session);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> expect(int code, String method, String path, String session, String body) throws Exception {
        var response = request(method, path, session, body);
        assertEquals(code, response.statusCode(), method + " " + path + " " + response.body());
        return response;
    }

    @Test
    void favoritesReviewsAndProfilePersistAndRespectAccountBoundaries() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).profiles("restoration-test")
                .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:restoration;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop").run()) {
            base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var users = app.getBean(UserRepository.class);
            users.save(new User(null, "owner", "owner@example.test", "hash", LocalDate.of(2000,1,1), null));
            users.save(new User(null, "other", "other@example.test", "hash", LocalDate.of(2000,1,1), null));
            var song = app.getBean(MusicaRepository.class).save(new Musica("Integration track", "", null, null));
            for (String kind : new String[]{"artists", "albums", "tracks"}) {
                expect(401,"GET","/favorites/" + kind,null,null);
            }
            String album = "{\"releaseGroupId\":\"c0c0485c-3112-3b04-8161-55be721f7d17\",\"album\":\"Album\",\"artist\":\"Artist\"}";
            String[] kinds={"artists","albums","tracks"};
            String[] bodies={"{\"artist\":\"Artist\"}",album,"{\"track\":\"Track\",\"artist\":\"Artist\"}"};
            for(int i=0;i<kinds.length;i++){
                String path="/favorites/"+kinds[i];
                expect(200,"POST",path,"owner",bodies[i]);
                expect(200,"POST",path,"owner",bodies[i]);
                assertEquals(1,json.readTree(expect(200,"GET",path,"owner",null).body()).size());
                assertEquals(0,json.readTree(expect(200,"GET",path,"other",null).body()).size());
            }
            assertNotNull(app.getBean(ArtistaRepository.class).findByNome("Artist"));
            assertNotNull(app.getBean(AlbumRepository.class).findByTitulo("Album"));
            assertNotNull(app.getBean(MusicaRepository.class).findByTitulo("Track"));
            assertEquals(1, app.getBean(AlbumRepository.class).count(), "Repeated favorites reuse the catalog record");
            expect(200,"DELETE","/favorites/tracks?track=Track&artist=Artist","other",null);
            assertEquals(1,json.readTree(expect(200,"GET","/favorites/tracks","owner",null).body()).size());
            expect(200,"DELETE","/favorites/tracks?track=Track&artist=Artist","owner",null);
            assertEquals(0,json.readTree(expect(200,"GET","/favorites/tracks","owner",null).body()).size());

            String media="/review/midia/"+song.getId();
            expect(401,"POST",media,null,"{\"rating\":8,\"review\":\"Great\"}");
            expect(400,"POST",media,"owner","{\"rating\":11,\"review\":\"Invalid\"}");
            var created=json.readTree(expect(200,"POST",media,"owner","{\"rating\":8,\"review\":\"Great\"}").body());
            long id=created.path("id").asLong();
            assertEquals(id,json.readTree(expect(200,"POST",media,"owner","{\"rating\":9,\"review\":\"Updated\"}").body()).path("id").asLong());
            expect(200,"POST",media,"other","{\"rating\":5,\"review\":\"Other\"}");
            var average=json.readTree(expect(200,"GET",media+"/rating",null,null).body());
            assertEquals(7.0,average.path("media").asDouble());
            assertEquals(2,average.path("total").asInt());
            assertEquals(1,json.readTree(expect(200,"GET","/review/me","owner",null).body()).size());
            expect(401,"PUT","/review/"+id,"other","{\"rating\":0,\"review\":\"Overwrite\"}");
            expect(401,"DELETE","/review/"+id,"other",null);
            expect(204,"DELETE","/review/"+id,"owner",null);

            expect(401,"PUT","/users/me/profile",null,"{\"biography\":\"Bio\"}");
            expect(200,"PUT","/users/me/profile","owner","{\"biography\":\"My music\"}");
            expect(400,"PUT","/users/me/profile/image","owner","{\"imageUrl\":\"javascript:alert(1)\"}");
            expect(200,"PUT","/users/me/profile/image","owner","{\"imageUrl\":\"https://example.test/photo.png\"}");
            expect(200,"PUT","/users/me/banner","owner","{\"imageUrl\":\"https://example.test/banner.png\"}");
            var profile=json.readTree(expect(200,"GET","/users/me/profile","owner",null).body());
            assertEquals("My music",profile.path("biography").asText());
            assertEquals("https://example.test/photo.png",profile.path("profileImageUrl").asText());
            assertEquals("",json.readTree(expect(200,"GET","/users/me/profile","other",null).body()).path("biography").asText());
            expect(200,"PUT","/users/me/username","owner","{\"username\":\"renamed\"}");
            assertEquals("renamed",json.readTree(expect(200,"GET","/users/me","owner",null).body()).path("username").asText());
        }
    }
}
