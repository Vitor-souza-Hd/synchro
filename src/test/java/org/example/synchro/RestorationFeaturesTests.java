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
            LastFmCatalogService.class, LastFmCatalogStore.class, CatalogResource.class, FollowResource.class, FriendshipResource.class, FriendshipService.class,
            CommunityPostResource.class, CommunityPostService.class,
            PostInteractionResource.class, PostInteractionService.class, PostInteractionNotifications.class,
            CommunityPollResource.class, CommunityPollService.class})
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
            when(cookies.checkCookie("visitor")).thenReturn(true);
            when(cookies.getId("visitor")).thenReturn(3L);
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
    void postsArePublicButOnlyTheirAuthorsCanEditOrDeleteThem() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).profiles("restoration-test")
                .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:community-posts",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop").run()) {
            base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var users = app.getBean(UserRepository.class);
            var owner = users.save(new User(null, "owner", "owner@example.test", "hash", LocalDate.of(2000, 1, 1), null));
            users.save(new User(null, "other", "other@example.test", "hash", LocalDate.of(2000, 1, 1), null));

            expect(401, "POST", "/posts", null, "{\"text\":\"Sem conta\"}");
            expect(400, "POST", "/posts", "owner", "{\"text\":\"    \"}");
            expect(400, "POST", "/posts", "owner", json.writeValueAsString(java.util.Map.of("text", "a".repeat(2001))));
            var post = json.readTree(expect(201, "POST", "/posts", "owner", "{\"text\":\"  Olá Synchro!  \"}").body());
            long id = post.path("id").asLong();
            assertEquals("Olá Synchro!", post.path("body").asText());
            assertTrue(post.path("own").asBoolean());
            assertTrue(post.path("createdAt").isTextual());
            assertFalse(post.path("author").has("email"));
            assertTrue(post.path("attachment").isNull());
            var artist = app.getBean(ArtistaRepository.class).save(new Artista("Artista compartilhado", "", null));
            var song = app.getBean(MusicaRepository.class).save(new Musica("Faixa compartilhada", "", null, null));
            var album = app.getBean(AlbumRepository.class).save(new Album("Álbum compartilhado", "", null));
            var artistRequest = json.writeValueAsString(java.util.Map.of("text", "Ouvindo", "attachment",
                    java.util.Map.of("type", "artist", "id", artist.getId(), "title", "Título alterado pelo cliente")));
            var artistPost = json.readTree(expect(201, "POST", "/posts", "owner", artistRequest).body());
            long artistPostId = artistPost.path("id").asLong();
            assertEquals("Artista compartilhado", artistPost.path("attachment").path("title").asText());
            var focused = json.readTree(expect(200, "GET", "/posts/" + artistPostId, "other", null).body());
            assertTrue(focused.path("signedIn").asBoolean());
            assertFalse(focused.path("post").path("own").asBoolean());
            assertEquals("artist", focused.path("post").path("attachment").path("type").asText());
            assertFalse(json.readTree(expect(200, "GET", "/posts/" + artistPostId, null, null).body()).path("signedIn").asBoolean());
            assertTrue(json.readTree(expect(200, "GET", "/posts?user=1", "other", null).body()).path("signedIn").asBoolean());
            for (var item : java.util.Map.of("track", song.getId(), "album", album.getId()).entrySet()) {
                var body = json.writeValueAsString(java.util.Map.of("text", "Novo anexo", "attachment",
                        java.util.Map.of("type", item.getKey(), "id", item.getValue())));
                var attached = json.readTree(expect(200, "PUT", "/posts/" + artistPostId, "owner", body).body());
                assertEquals(item.getKey(), attached.path("attachment").path("type").asText());
                assertEquals(item.getValue().longValue(), attached.path("attachment").path("id").asLong());
            }
            expect(400, "PUT", "/posts/" + artistPostId, "owner", "{\"text\":\"Link inválido\",\"attachment\":{\"type\":\"track\",\"id\":99999}}");
            var without = json.readTree(expect(200, "PUT", "/posts/" + artistPostId, "owner", "{\"text\":\"Sem anexo\"}").body());
            assertTrue(without.path("attachment").isNull());
            expect(204, "DELETE", "/posts/" + artistPostId, "owner", null);
            expect(404, "GET", "/posts/" + artistPostId, null, null);

            var visitor = json.readTree(expect(200, "GET", "/posts", null, null).body());
            assertFalse(visitor.path("canPublish").asBoolean());
            assertFalse(visitor.path("items").get(0).path("own").asBoolean());
            assertEquals(id, visitor.path("items").get(0).path("id").asLong());
            assertTrue(json.readTree(expect(200, "GET", "/posts?user=1", "owner", null).body()).path("canPublish").asBoolean());
            assertFalse(json.readTree(expect(200, "GET", "/posts?user=1", "other", null).body()).path("canPublish").asBoolean());
            assertEquals(0, json.readTree(expect(200, "GET", "/posts?user=2", null, null).body()).path("items").size());

            expect(403, "PUT", "/posts/" + id, "other", "{\"text\":\"Alteração alheia\"}");
            expect(403, "DELETE", "/posts/" + id, "other", null);
            var edited = json.readTree(expect(200, "PUT", "/posts/" + id, "owner", "{\"text\":\"Texto editado\"}").body());
            assertEquals("Texto editado", edited.path("body").asText());
            assertTrue(edited.path("editedAt").isTextual());
            var repository = app.getBean(CommunityPostRepository.class);
            for (int i = 1; i <= 12; i++) repository.save(new CommunityPost(owner, "Post " + i));
            var page = json.readTree(expect(200, "GET", "/posts", null, null).body());
            assertEquals(12, page.path("items").size());
            assertEquals("Post 12", page.path("items").get(0).path("body").asText());
            var next = json.readTree(expect(200, "GET", "/posts?before=" + page.path("nextBefore").asLong(), null, null).body());
            assertEquals(1, next.path("items").size());
            assertEquals(id, next.path("items").get(0).path("id").asLong());
            expect(204, "DELETE", "/posts/" + id, "owner", null);
            assertFalse(repository.existsById(id));
        }
    }

    @Test
    void feedRespectsFollowsSavedPostsLikesAndPollVotes() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).profiles("restoration-test")
                .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:post-upgrades",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop").run()) {
            base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var users = app.getBean(UserRepository.class);
            users.save(new User(null, "owner", "owner@example.test", "hash", LocalDate.of(2000, 1, 1), null));
            users.save(new User(null, "other", "other@example.test", "hash", LocalDate.of(2000, 1, 1), null));

            expect(401, "GET", "/posts?scope=FOLLOWING", null, null);
            expect(401, "GET", "/posts?scope=SAVED", null, null);
            long first = json.readTree(expect(201, "POST", "/posts", "owner", "{\"text\":\"Meu post\"}").body())
                    .path("id").asLong();
            long second = json.readTree(expect(201, "POST", "/posts", "other", "{\"text\":\"Outro post\"}").body())
                    .path("id").asLong();

            assertEquals(0, postItems("FOLLOWING", "owner").size());
            expect(200, "POST", "/follow/2", "owner", null);
            assertEquals(1, postItems("FOLLOWING", "owner").size());
            assertEquals(second, postItems("FOLLOWING", "owner").get(0).path("id").asLong());
            expect(200, "DELETE", "/follow/2", "owner", null);
            assertEquals(0, postItems("FOLLOWING", "owner").size());

            expect(401, "PUT", "/posts/" + second + "/like", null, null);
            String like = "/posts/" + second + "/like";
            assertEquals(1, json.readTree(expect(200, "PUT", like, "owner", null).body()).path("likes").asInt());
            assertEquals(1, json.readTree(expect(200, "PUT", like, "owner", null).body()).path("likes").asInt());
            assertEquals(2, json.readTree(expect(200, "PUT", like, "other", null).body()).path("likes").asInt());
            var publicItem = postItems("ALL", null).get(0);
            assertEquals(2, publicItem.path("interaction").path("likes").asInt());
            assertFalse(publicItem.path("interaction").path("liked").asBoolean());

            String bookmark = "/posts/" + second + "/bookmark";
            expect(200, "PUT", bookmark, "owner", null);
            assertEquals(second, postItems("SAVED", "owner").get(0).path("id").asLong());
            assertEquals(0, postItems("SAVED", "other").size());
            var state = json.readTree(expect(200, "DELETE", like, "owner", null).body());
            assertEquals(1, state.path("likes").asInt());
            assertTrue(state.path("saved").asBoolean());
            expect(200, "DELETE", bookmark, "owner", null);
            assertEquals(0, postItems("SAVED", "owner").size());
            assertEquals(first, postItems("ALL", null).get(1).path("id").asLong());

            long previous = app.getBean(CommunityPostRepository.class).count();
            expect(400, "POST", "/posts", "owner",
                    "{\"poll\":{\"question\":\"Qual?\",\"options\":[\"Rock\",\" rock \"]}}");
            assertEquals(previous, app.getBean(CommunityPostRepository.class).count());
            var poll = json.readTree(expect(201, "POST", "/posts", "owner",
                    "{\"poll\":{\"question\":\"Qual gênero?\",\"options\":[\"Rock\",\"MPB\"]}}").body());
            long pollPost = poll.path("id").asLong();
            assertEquals("Qual gênero?", poll.path("poll").path("question").asText());
            String vote = "/posts/" + pollPost + "/poll/vote";
            expect(401, "PUT", vote, null, "{\"optionId\":0}");
            expect(400, "PUT", vote, "owner", "{\"optionId\":99}");
            expect(200, "PUT", vote, "owner", "{\"optionId\":0}");
            expect(200, "PUT", vote, "owner", "{\"optionId\":1}");
            expect(200, "PUT", vote, "other", "{\"optionId\":0}");
            var pollView = json.readTree(expect(200, "GET", "/posts/" + pollPost, "owner", null).body())
                    .path("post").path("poll");
            assertEquals(2, pollView.path("totalVotes").asInt());
            assertEquals(1, pollView.path("selectedOption").asInt());
            assertEquals(1, pollView.path("options").get(0).path("votes").asInt());
            assertEquals(1, pollView.path("options").get(1).path("votes").asInt());
            assertTrue(postItems("ALL", null).get(0).path("poll").path("selectedOption").isNull());
            expect(204, "DELETE", "/posts/" + pollPost, "owner", null);
            expect(404, "PUT", vote, "owner", "{\"optionId\":0}");
        }
    }

    private com.fasterxml.jackson.databind.JsonNode postItems(String scope, String session) throws Exception {
        return json.readTree(expect(200, "GET", "/posts?scope=" + scope, session, null).body()).path("items");
    }

    @Test
    void friendshipRequiresAcceptanceAndOnlyParticipantsCanManageIt() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).profiles("restoration-test")
                .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:friendship",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop").run()) {
            base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var users = app.getBean(UserRepository.class);
            for (String username : new String[]{"owner", "other", "visitor"}) {
                users.save(new User(null, username, username + "@example.test", "hash", LocalDate.of(2000, 1, 1), null));
            }

            expect(401, "GET", "/friendships", null, null);
            expect(401, "POST", "/friendships/2", null, null);
            assertEquals("SELF", friendshipState("GET", 1, "owner"));
            expect(400, "POST", "/friendships/1", "owner", null);
            expect(404, "POST", "/friendships/999", "owner", null);
            assertEquals("NONE", friendshipState("GET", 2, "owner"));

            assertEquals("SENT", friendshipState("POST", 2, "owner"));
            assertEquals("SENT", friendshipState("POST", 2, "owner"));
            assertEquals("RECEIVED", friendshipState("GET", 1, "other"));
            assertEquals("NONE", friendshipState("GET", 1, "visitor"));
            assertEquals(0, json.readTree(expect(200, "GET", "/friendships", "visitor", null).body()).size());
            assertEquals(1, app.getBean(FriendshipRepository.class).count());
            expect(409, "PATCH", "/friendships/2", "owner", null);
            expect(409, "PATCH", "/friendships/1", "visitor", null);
            expect(409, "POST", "/friendships/1", "other", null);
            assertEquals("FRIENDS", friendshipState("PATCH", 1, "other"));
            expect(409, "DELETE", "/friendships/2?state=SENT", "owner", null);
            assertEquals("FRIENDS", friendshipState("GET", 2, "owner"));
            var connections = json.readTree(expect(200, "GET", "/friendships", "owner", null).body());
            assertEquals("FRIENDS", connections.get(0).path("state").asText());
            assertEquals("other", connections.get(0).path("user").path("username").asText());
            assertFalse(connections.get(0).path("user").has("email"));
            assertEquals(0, app.getBean(FollowRepository.class).count());

            expect(200, "DELETE", "/friendships/2?state=FRIENDS", "owner", null);
            assertEquals("NONE", friendshipState("GET", 1, "other"));
            assertEquals("SENT", friendshipState("POST", 1, "other"));
            expect(200, "DELETE", "/friendships/2?state=RECEIVED", "owner", null);
            assertEquals("NONE", friendshipState("GET", 1, "other"));
            assertEquals("SENT", friendshipState("POST", 1, "other"));
            expect(200, "DELETE", "/friendships/1?state=SENT", "other", null);
            assertEquals("NONE", friendshipState("GET", 2, "owner"));
        }
    }

    private String friendshipState(String method, long target, String session) throws Exception {
        return json.readTree(expect(200, method, "/friendships/" + target, session, null).body()).path("state").asText();
    }

    @Test
    void reviewPagesFilterBeforeLimitingAndKeepTheOlderOwnReviewEditable() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).run("--spring.profiles.active=review-page-test",
                "--server.port=0", "--spring.datasource.url=jdbc:h2:mem:review-pages",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa",
                "--spring.datasource.password=", "--spring.jpa.hibernate.ddl-auto=create-drop")) {
            base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var users = app.getBean(UserRepository.class);
            var songs = app.getBean(MusicaRepository.class);
            var song = songs.save(new Musica("Faixa avaliada", "", null, null));
            var other = songs.save(new Musica("Outra faixa", "", null, null));
            var sql = app.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            for (int i = 1; i <= 13; i++) {
                var user = users.save(new User(null, "user" + i, i + "@example.test", "hash", null, null));
                sql.update("insert into review (midia_id, autor_id, rating) values (?, ?, ?)",
                        song.getId(), user.getData().getId(), 7);
            }
            var url = "/review?midiaId=" + song.getId();
            var first = expect(200, "GET", url, null, null);
            var items = json.readTree(first.body());
            assertEquals(12, items.size());
            var cursor = first.headers().firstValue("X-Next-Before").orElseThrow();
            assertEquals(items.get(11).path("id").asText(), cursor);
            var last = expect(200, "GET", url + "&before=" + cursor, null, null);
            assertTrue(last.headers().firstValue("X-Next-Before").isEmpty());
            var older = json.readTree(last.body());
            assertEquals(1, older.size());
            assertTrue(older.get(0).path("id").asLong() < Long.parseLong(cursor));
            var mine = json.readTree(expect(200, "GET", "/review/me?midiaId=" + song.getId(), "owner", null).body());
            assertEquals(older, mine);
            assertEquals(0, json.readTree(expect(200, "GET", "/review?midiaId=" + other.getId(), null, null).body()).size());
            assertEquals(12, json.readTree(expect(200, "GET", "/review", null, null).body()).size());
            var empty = json.readTree(expect(200, "GET", "/review/midia/" + other.getId() + "/rating", null, null).body());
            assertEquals(0, empty.path("total").asInt());
            assertEquals(0.0, empty.path("media").asDouble());
        }
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
            expect(200, "POST", "/favorites/artists", "other", "{\"artist\":\"Other Artist\"}");
            var publicArtists = json.readTree(expect(200, "GET", "/favorites/artists/users/1", "other", null).body());
            assertEquals(json.readTree("[\"Artist\"]"), publicArtists);
            assertEquals(json.readTree("[\"Other Artist\"]"),
                    json.readTree(expect(200, "GET", "/favorites/artists/users/2", null, null).body()));

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
            expect(200, "PUT", "/users/me/profile/image", "owner", "{\"imageUrl\":\"https://example.test/owner.jpg\"}");
            var recent = json.readTree(expect(200, "GET", "/review/recent", null, null).body());
            assertEquals("other", recent.get(0).path("username").asText());
            assertTrue(recent.get(0).path("profileImageUrl").isNull());
            assertEquals("owner", recent.get(1).path("username").asText());
            assertEquals("https://example.test/owner.jpg", recent.get(1).path("profileImageUrl").asText());
            var publicReviews = json.readTree(expect(200, "GET", "/review/users/1", "other", null).body());
            assertEquals(1, publicReviews.path("items").size());
            assertEquals("Updated", publicReviews.path("items").get(0).path("review").asText());
            assertEquals(0, json.readTree(expect(200, "GET", "/review/users/1?before=" + id, null, null).body())
                    .path("items").size());

            assertEquals(1, json.readTree(expect(200, "POST", "/follow/1", "other", null).body()).path("followers").asLong());
            assertEquals(1, json.readTree(expect(200, "GET", "/follow/2", null, null).body()).path("followingCount").asLong());
            assertEquals(0, json.readTree(expect(200, "DELETE", "/follow/1", "other", null).body()).path("followers").asLong());
            assertEquals(1,json.readTree(expect(200,"GET","/review/me","owner",null).body()).size());
            expect(401,"PUT","/review/"+id,"other","{\"rating\":0,\"review\":\"Overwrite\"}");
            expect(401,"DELETE","/review/"+id,"other",null);
            expect(204,"DELETE","/review/"+id,"owner",null);

            long artistId = app.getBean(ArtistaRepository.class).findByNome("Artist").getId();
            String artistReviews = "/review/artists/" + artistId;
            expect(401, "POST", artistReviews, null, "{\"rating\":8}");
            expect(400, "POST", artistReviews, "owner", "{\"rating\":11}");
            expect(404, "GET", "/review/artists/999999", null, null);
            var artistReview = json.readTree(expect(200, "POST", artistReviews, "owner",
                    "{\"rating\":8,\"review\":\"Ótimo artista\"}").body());
            long artistReviewId = artistReview.path("id").asLong();
            assertEquals(artistId, artistReview.path("artistId").asLong());
            assertTrue(artistReview.path("midiaId").isNull());
            assertEquals("Artista", artistReview.path("midiatype").asText());
            var revised = json.readTree(expect(200, "POST", artistReviews, "owner",
                    "{\"rating\":10,\"review\":\"Favorito\"}").body());
            assertEquals(artistReviewId, revised.path("id").asLong());
            expect(200, "POST", artistReviews, "other", "{\"rating\":6,\"review\":\"Gostei\"}");
            var publicArtistReviews = json.readTree(expect(200, "GET", artistReviews, null, null).body());
            assertFalse(publicArtistReviews.path("signedIn").asBoolean());
            assertTrue(publicArtistReviews.path("own").isNull());
            assertEquals(2, publicArtistReviews.path("total").asInt());
            assertEquals(8.0, publicArtistReviews.path("average").asDouble());
            assertEquals(2, publicArtistReviews.path("reviews").path("items").size());
            var ownArtistReviews = json.readTree(expect(200, "GET", artistReviews, "owner", null).body());
            assertEquals(artistReviewId, ownArtistReviews.path("own").path("id").asLong());
            assertEquals(artistReviewId, json.readTree(expect(200, "GET", "/review/me", "owner", null).body())
                    .get(0).path("id").asLong());
            var publicProfileReviews = json.readTree(expect(200, "GET", "/review/users/1", null, null).body());
            assertEquals("artist", publicProfileReviews.path("items").get(0).path("mediaType").asText());
            assertEquals("artist", json.readTree(expect(200, "GET", "/review/recent", null, null).body())
                    .get(0).path("mediaType").asText());
            expect(401, "DELETE", "/review/" + artistReviewId, "other", null);
            expect(204, "DELETE", "/review/" + artistReviewId, "owner", null);
            assertEquals(1, json.readTree(expect(200, "GET", artistReviews, null, null).body()).path("total").asInt());

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
