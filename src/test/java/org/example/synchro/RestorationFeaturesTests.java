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
    @Import({ReviewService.class, ReviewResource.class, PlaylistService.class, PlaylistResource.class, UserService.class, UserResource.class,
            FavoriteArtistService.class, FavoriteArtistResource.class,
            FavoriteAlbumService.class, FavoriteAlbumResource.class,
            FavoriteTrackService.class, FavoriteTrackResource.class, ResourceExceptionHandler.class,
            LastFmCatalogService.class, LastFmCatalogStore.class, CatalogResource.class, SearchResource.class, UserSearchResource.class, FollowResource.class, FriendshipResource.class, FriendshipService.class,
            CommunityPostResource.class, CommunityPostService.class,
            MuralSignatureResource.class, MuralSignatureService.class,
            PostInteractionResource.class, PostInteractionService.class, PostInteractionNotifications.class,
            CommunityPollResource.class, CommunityPollService.class,
            PostDiscussionResource.class, PostDiscussionService.class,
            VvipInvitationResource.class, VvipInvitationService.class})
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
            var account = new User(null, "owner", "owner@example.test", "hash", LocalDate.of(2000, 1, 1), null);
            account.getData().setFullName("Nome Particular do Autor");
            var owner = users.save(account);
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
            assertEquals("owner", post.path("author").path("username").asText());
            assertFalse(post.path("author").has("fullName"));
            var names = json.readTree(expect(200, "GET", "/search/users?q=%40owner", null, null).body());
            assertEquals("owner", names.path("items").get(0).path("username").asText());
            assertFalse(names.path("items").get(0).has("fullName"));
            var integrated = json.readTree(expect(200, "GET", "/search?q=%40owner", null, null).body());
            assertEquals("owner", integrated.path("users").get(0).path("username").asText());
            assertFalse(integrated.path("users").get(0).has("fullName"));
            var byRealName = json.readTree(expect(200, "GET", "/search/users?q=Particular", null, null).body());
            assertTrue(byRealName.path("items").isEmpty());
            var publicProfiles = expect(200, "GET", "/users", null, null).body();
            assertFalse(publicProfiles.contains("Nome Particular do Autor"));
            var privateOwner = json.readTree(expect(200, "GET", "/users/me", "owner", null).body());
            assertEquals("Nome Particular do Autor", privateOwner.path("data").path("fullName").asText());
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
            assertEquals(1, artistPost.path("attachments").size());
            var attachments = java.util.List.of(
                    java.util.Map.of("type", "artist", "id", artist.getId()),
                    java.util.Map.of("type", "track", "id", song.getId()),
                    java.util.Map.of("type", "album", "id", album.getId()));
            var payload = json.writeValueAsString(java.util.Map.of("text", "Três itens", "attachments", attachments));
            var multiple = json.readTree(expect(201, "POST", "/posts", "owner", payload).body());
            long multipleId = multiple.path("id").asLong();
            var persisted = json.readTree(expect(200, "GET", "/posts/" + multipleId, null, null).body())
                    .path("post").path("attachments");
            assertEquals(3, persisted.size());
            assertEquals("artist", persisted.get(0).path("type").asText());
            assertEquals("track", persisted.get(1).path("type").asText());
            assertEquals("album", persisted.get(2).path("type").asText());
            var trimmed = json.writeValueAsString(java.util.Map.of("text", "Dois itens",
                    "attachments", attachments.subList(1, 3)));
            var editedItems = json.readTree(expect(200, "PUT", "/posts/" + multipleId, "owner", trimmed).body())
                    .path("attachments");
            assertEquals(2, editedItems.size());
            assertEquals("track", editedItems.get(0).path("type").asText());
            assertEquals("album", editedItems.get(1).path("type").asText());
            var duplicate = json.writeValueAsString(java.util.Map.of("text", "Duplicado",
                    "attachments", java.util.List.of(attachments.get(0), attachments.get(0))));
            expect(400, "POST", "/posts", "owner", duplicate);
            var oversized = json.writeValueAsString(java.util.Map.of("text", "Quatro itens",
                    "attachments", java.util.List.of(attachments.get(0), attachments.get(1), attachments.get(2), attachments.get(0))));
            expect(400, "POST", "/posts", "owner", oversized);
            expect(204, "DELETE", "/posts/" + multipleId, "owner", null);
            var without = json.readTree(expect(200, "PUT", "/posts/" + artistPostId, "owner", "{\"text\":\"Sem anexo\"}").body());
            assertTrue(without.path("attachment").isNull());
            expect(204, "DELETE", "/posts/" + artistPostId, "owner", null);
            expect(404, "GET", "/posts/" + artistPostId, null, null);

            var visitor = json.readTree(expect(200, "GET", "/posts", null, null).body());
            assertFalse(visitor.path("canPublish").asBoolean());
            assertFalse(visitor.path("items").get(0).path("own").asBoolean());
            assertEquals(id, visitor.path("items").get(0).path("id").asLong());
            assertFalse(visitor.path("items").get(0).path("author").has("fullName"));
            assertTrue(json.readTree(expect(200, "GET", "/posts?user=1", "owner", null).body()).path("canPublish").asBoolean());
            assertFalse(json.readTree(expect(200, "GET", "/posts?user=1", "other", null).body()).path("canPublish").asBoolean());
            assertEquals(0, json.readTree(expect(200, "GET", "/posts?user=2", null, null).body()).path("items").size());

            expect(403, "PUT", "/posts/" + id, "other", "{\"text\":\"Alteração alheia\"}");
            expect(403, "DELETE", "/posts/" + id, "other", null);
            var edited = json.readTree(expect(200, "PUT", "/posts/" + id, "owner", "{\"text\":\"Texto editado\"}").body());
            assertEquals("Texto editado", edited.path("body").asText());
            assertTrue(edited.path("editedAt").isTextual());
            var repository = app.getBean(CommunityPostRepository.class);
            var albumRepository = app.getBean(AlbumRepository.class);
            var songRepository = app.getBean(MusicaRepository.class);
            String albumCover = "https://example.test/album-cover.jpg";
            String trackCover = "https://example.test/track-cover.jpg";
            for (int i = 1; i <= 12; i++) {
                var backup = new Album("Álbum do post " + i, "", null);
                backup.getLastFm().setImageUrl(albumCover);
                var track = new Musica("Faixa do post " + i, "", null, null);
                track.setAlbum(albumRepository.save(backup));
                if (i != 12) track.getLastFm().setImageUrl(trackCover);
                repository.save(new CommunityPost(owner, "Post " + i,
                        java.util.List.of(new PostCatalogAttachment(null, songRepository.save(track)))));
            }
            var statistics = app.getBean(jakarta.persistence.EntityManagerFactory.class)
                    .unwrap(org.hibernate.SessionFactory.class).getStatistics();
            var statisticsWereEnabled = statistics.isStatisticsEnabled();
            com.fasterxml.jackson.databind.JsonNode page;
            statistics.setStatisticsEnabled(true);
            long initialStatements = statistics.getPrepareStatementCount();
            try {
                page = json.readTree(expect(200, "GET", "/posts", null, null).body());
                long statements = statistics.getPrepareStatementCount() - initialStatements;
                assertTrue(statements <= 10, "Consultas para 12 posts com faixas: " + statements);
            } finally {
                statistics.setStatisticsEnabled(statisticsWereEnabled);
            }
            assertEquals(albumCover, page.path("items").get(0).path("attachment").path("coverUrl").asText());
            assertEquals(trackCover, page.path("items").get(1).path("attachment").path("coverUrl").asText());
            assertEquals(12, page.path("items").size());
            assertEquals("Post 12", page.path("items").get(0).path("body").asText());
            var preview = json.readTree(expect(200, "GET", "/posts?limit=3", "owner", null).body());
            assertTrue(preview.path("signedIn").asBoolean());
            assertEquals(3, preview.path("items").size());
            assertEquals("Post 12", preview.path("items").get(0).path("body").asText());
            assertEquals("Post 10", preview.path("items").get(2).path("body").asText());
            var nextPreview = json.readTree(expect(200, "GET",
                    "/posts?limit=3&before=" + preview.path("nextBefore").asLong(), "owner", null).body());
            assertEquals("Post 9", nextPreview.path("items").get(0).path("body").asText());
            expect(400, "GET", "/posts?limit=0", null, null);
            expect(400, "GET", "/posts?limit=13", null, null);
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
    void favoritesReviewsAndProfilePersistAndRespectAccountBoundaries() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).profiles("restoration-test")
                .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:restoration;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop").run()) {
            base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var users = app.getBean(UserRepository.class);
            users.save(new User(null, "owner", "owner@example.test", "hash", LocalDate.of(2000,1,1), null));
            users.save(new User(null, "other", "other@example.test", "hash", LocalDate.of(2000,1,1), null));
            String reviewCover = "https://example.test/integration-track.jpg";
            var song = new Musica("Integration track", "", null, null);
            song.getLastFm().setImageUrl(reviewCover);
            song.getLastFm().setArtistName("Integration artist");
            app.getBean(MusicaRepository.class).save(song);
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
            assertEquals(reviewCover, publicReviews.path("items").get(0).path("coverUrl").asText());
            assertEquals(0, json.readTree(expect(200, "GET", "/review/users/1?before=" + id, null, null).body())
                    .path("items").size());

            assertEquals(1, json.readTree(expect(200, "POST", "/follow/1", "other", null).body()).path("followers").asLong());
            assertEquals(1, json.readTree(expect(200, "GET", "/follow/2", null, null).body()).path("followingCount").asLong());
            assertEquals(0, json.readTree(expect(200, "DELETE", "/follow/1", "other", null).body()).path("followers").asLong());
            var privateReviews = json.readTree(expect(200, "GET", "/review/me", "owner", null).body());
            assertEquals(1, privateReviews.size());
            assertEquals(reviewCover, privateReviews.get(0).path("coverUrl").asText());
            assertEquals("Integration artist", privateReviews.get(0).path("artistName").asText());
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
    @Test
    void muralShowsSavedSignaturesPubliclyAndOnlyChangesTheSignedInAuthorsDrawing() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).profiles("restoration-test")
                .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:mural",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop").run()) {
            base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var users = app.getBean(UserRepository.class);
            users.save(new User(null, "owner", "owner@example.test", "hash", LocalDate.of(2000, 1, 1), null));
            users.save(new User(null, "other", "other@example.test", "hash", LocalDate.of(2000, 1, 1), null));
            String mural = "/api/mural-assinaturas";
            String mine = mural + "/minha";
            expect(401, "GET", mine, null, null);
            expect(204, "GET", mine, "owner", null);
            assertEquals(401, uploadSignature(mine, null, signaturePng(720, 300, true)).statusCode());
            assertEquals(400, uploadSignature(mine, "owner", signaturePng(1, 1, true)).statusCode());
            assertEquals(400, uploadSignature(mine, "owner", signaturePng(720, 300, false)).statusCode());
            assertEquals(400, uploadSignature(mine, "owner", new byte[]{1, 2, 3}).statusCode());

            var created = uploadSignature(mine, "owner", signaturePng(720, 300, true));
            assertEquals(200, created.statusCode(), created.body());
            long id = json.readTree(created.body()).path("id").asLong();
            var publicList = json.readTree(expect(200, "GET", mural, null, null).body()).path("items");
            assertEquals("owner", publicList.get(0).path("username").asText());
            assertFalse(publicList.get(0).has("image"));
            assertEquals(id, json.readTree(expect(200, "GET", mine, "owner", null).body()).path("id").asLong());
            var response = client.send(HttpRequest.newBuilder(URI.create(base + mural + "/" + id + "/imagem")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertEquals("image/png", response.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(0xffef3344, javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(response.body())).getRGB(0, 0));

            var updated = uploadSignature(mine, "owner", signaturePng(720, 300, true));
            assertEquals(200, updated.statusCode(), updated.body());
            assertEquals(id, json.readTree(updated.body()).path("id").asLong());
            expect(204, "GET", mine, "other", null);
            expect(204, "DELETE", mine, "other", null);
            assertEquals(1, app.getBean(MuralSignatureRepository.class).count());
            expect(204, "DELETE", mine, "owner", null);
            expect(404, "GET", mural + "/" + id + "/imagem", null, null);
        }
    }

    @Test
    void profileShowsAtMostTenAndSavesIndependentOwnerSelections() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).profiles("restoration-test")
                .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:profile-selection",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop").run()) {
            base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var users = app.getBean(UserRepository.class);
            users.save(new User(null, "owner", "owner@example.test", "hash", LocalDate.of(2000, 1, 1), null));
            users.save(new User(null, "other", "other@example.test", "hash", LocalDate.of(2000, 1, 1), null));
            var tracks = app.getBean(MusicaRepository.class);
            for (int i = 1; i <= 11; i++) {
                var track = tracks.save(new Musica("Faixa " + i, "", null, null));
                expect(200, "POST", "/review/midia/" + track.getId(), "owner", "{\"rating\":8}");
                var playlist = json.readTree(expect(201, "POST", "/playlists/new-playlist", "owner",
                        "{\"titulo\":\"Playlist " + i + "\"}").body());
                expect(204, "PUT", "/playlists/me/" + playlist.get("id").asLong() + "/profile-visibility",
                        "owner", "{\"visible\":true}");
            }
            var hidden = json.readTree(expect(201, "POST", "/playlists/new-playlist", "owner",
                    "{\"titulo\":\"Privada\"}").body()).get("id").asLong();
            String reviewSelection = "/review/me/profile-selection";
            String playlistSelection = "/playlists/me/profile-selection";
            String publicReviews = "/review/users/1/profile";
            String publicPlaylists = "/playlists/users/1";
            expect(401, "GET", reviewSelection, null, null);
            expect(401, "PUT", playlistSelection, null, "{\"ids\":[]}");
            assertEquals(10, json.readTree(expect(200, "GET", publicReviews, null, null).body()).size());
            assertEquals(10, json.readTree(expect(200, "GET", publicPlaylists, null, null).body()).size());
            var reviews = json.readTree(expect(200, "GET", reviewSelection, "owner", null).body());
            var playlists = json.readTree(expect(200, "GET", playlistSelection, "owner", null).body());
            assertEquals(11, reviews.path("items").size());
            assertEquals(11, playlists.path("items").size());
            assertEquals(10, reviews.path("selectedIds").size());
            assertEquals(10, playlists.path("selectedIds").size());
            long oldestReview = reviews.path("items").get(10).get("id").asLong();
            long oldestPlaylist = playlists.path("items").get(10).get("id").asLong();
            expect(400, "PUT", playlistSelection, "owner", "{\"ids\":[" + hidden + "]}");
            expect(409, "PUT", reviewSelection, "other", "{\"ids\":[" + oldestReview + "]}");
            expect(204, "PUT", reviewSelection, "owner", "{\"ids\":[" + oldestReview + "]}");
            expect(204, "PUT", playlistSelection, "owner", "{\"ids\":[" + oldestPlaylist + "]}");
            var shownReviews = json.readTree(expect(200, "GET", publicReviews, null, null).body());
            var shownPlaylists = json.readTree(expect(200, "GET", publicPlaylists, null, null).body());
            assertEquals(1, shownReviews.size());
            assertEquals(1, shownPlaylists.size());
            assertEquals(oldestReview, shownReviews.get(0).get("id").asLong());
            assertEquals(oldestPlaylist, shownPlaylists.get(0).get("id").asLong());
            assertEquals(11, json.readTree(expect(200, "GET", "/review/me", "owner", null).body()).size());
            assertEquals(12, json.readTree(expect(200, "GET", "/playlists/me/summary", "owner", null).body()).size());
        }
    }

    @Test
    void vvipCanDeleteOthersContentButVipCannotAndEditingRemainsWithAuthor() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).profiles("restoration-test")
                .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:account-roles",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop").run()) {
            base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var users = app.getBean(UserRepository.class);
            users.save(new User(null, "owner", "owner@example.test", "hash", LocalDate.of(2000, 1, 1), null));
            var vip = new User(null, "vip", "vip@example.test", "hash", LocalDate.of(2000, 1, 1), null);
            vip.setRole(UserRole.VIP);
            users.save(vip);
            var moderator = new User(null, "moderator", "moderator@example.test", "hash", LocalDate.of(2000, 1, 1), null);
            moderator.setRole(UserRole.VVIP);
            users.save(moderator);
            assertEquals("USER", json.readTree(expect(200, "GET", "/users/me", "owner", null).body()).get("role").asText());
            assertEquals("VVIP", json.readTree(expect(200, "GET", "/users/me", "visitor", null).body()).get("role").asText());
            assertEquals("VIP", json.readTree(expect(200, "GET", "/catalog/users/2", null, null).body()).get("role").asText());

            long post = json.readTree(expect(201, "POST", "/posts", "owner", "{\"text\":\"Post de teste\"}").body()).get("id").asLong();
            String postPath = "/posts/" + post;
            assertFalse(json.readTree(expect(200, "GET", postPath, null, null).body()).path("post").get("authorVvip").asBoolean());
            long comment = json.readTree(expect(201, "POST", postPath + "/comments", "owner",
                    "{\"text\":\"Comentário de teste\"}").body()).get("id").asLong();
            String commentPath = postPath + "/comments/" + comment;
            var music = app.getBean(MusicaRepository.class).save(new Musica("Faixa moderada", "", null, null));
            long review = json.readTree(expect(200, "POST", "/review/midia/" + music.getId(), "owner",
                    "{\"rating\":8}").body()).get("id").asLong();
            String reviewPath = "/review/" + review;
            var image = uploadSignature("/api/mural-assinaturas/minha", "owner", signaturePng(720, 300, true));
            assertEquals(200, image.statusCode(), image.body());
            long signature = json.readTree(image.body()).get("id").asLong();
            String signaturePath = "/api/mural-assinaturas/" + signature;

            expect(403, "DELETE", postPath, "other", null);
            expect(403, "DELETE", commentPath, "other", null);
            expect(401, "DELETE", reviewPath, "other", null);
            expect(403, "DELETE", signaturePath, "other", null);
            expect(401, "DELETE", signaturePath, null, null);
            expect(403, "PUT", postPath, "visitor", "{\"text\":\"Edição alheia\"}");
            expect(403, "PUT", commentPath, "visitor", "{\"text\":\"Edição alheia\"}");
            expect(401, "PUT", reviewPath, "visitor", "{\"rating\":1}");

            expect(204, "DELETE", commentPath, "visitor", null);
            assertTrue(json.readTree(expect(200, "GET", commentPath, null, null).body()).path("deleted").asBoolean());
            expect(204, "DELETE", reviewPath, "visitor", null);
            assertFalse(app.getBean(ReviewRepository.class).existsById(review));
            expect(204, "DELETE", signaturePath, "visitor", null);
            expect(404, "GET", signaturePath + "/imagem", null, null);
            expect(204, "DELETE", postPath, "visitor", null);
            expect(404, "GET", postPath, null, null);
            var vvipPost = json.readTree(expect(201, "POST", "/posts", "visitor", "{\"text\":\"Post VVIP\"}").body());
            assertTrue(vvipPost.get("authorVvip").asBoolean());
            long vvipPostId = vvipPost.get("id").asLong();
            var vvipReview = json.readTree(expect(200, "POST", "/review/midia/" + music.getId(), "visitor",
                    "{\"rating\":9,\"review\":\"Avaliação VVIP\"}").body());
            assertTrue(vvipReview.get("authorVvip").asBoolean());
            assertTrue(json.readTree(expect(200, "GET", "/review", null, null).body()).get(0).get("authorVvip").asBoolean());
            assertTrue(json.readTree(expect(200, "GET", "/review/recent", null, null).body()).get(0).get("authorVvip").asBoolean());
            assertTrue(json.readTree(expect(200, "GET", "/review/users/3/profile", null, null).body()).get(0).get("authorVvip").asBoolean());
            assertTrue(json.readTree(expect(200, "GET", "/posts/" + vvipPostId, null, null).body())
                    .path("post").get("authorVvip").asBoolean());
            assertTrue(json.readTree(expect(200, "GET", "/posts", null, null).body())
                    .path("items").get(0).get("authorVvip").asBoolean());
        }
    }

    @Test
    void onlyTheInvitedAccountCanAcceptVvipInvitations() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).profiles("restoration-test")
                .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:vvip-invitations",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop").run()) {
            base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var users = app.getBean(UserRepository.class);
            var sender = users.save(new User(null, "owner", "owner@example.test", "hash", LocalDate.of(2000, 1, 1), null));
            var vip = new User(null, "other", "other@example.test", "hash", LocalDate.of(2000, 1, 1), null);
            vip.setRole(UserRole.VIP);
            users.save(vip);
            users.save(new User(null, "visitor", "visitor@example.test", "hash", LocalDate.of(2000, 1, 1), null));

            String path = "/vvip-invitations";
            String invite = "{\"username\":\" @OtHeR \"}";
            expect(401, "GET", path, null, null);
            expect(403, "POST", path, "owner", invite);
            expect(403, "POST", path, "other", "{\"username\":\"visitor\"}");
            sender.setRole(UserRole.VVIP);
            users.save(sender);
            expect(204, "POST", path, "owner", invite);
            expect(204, "POST", path, "owner", invite);
            var received = json.readTree(expect(200, "GET", path, "other", null).body());
            assertFalse(received.get("canInvite").asBoolean());
            assertEquals(1, received.path("incoming").size());
            long first = received.path("incoming").get(0).get("id").asLong();
            expect(404, "PUT", path + "/" + first, "visitor", "{\"accept\":true}");
            expect(404, "DELETE", path + "/" + first, "other", null);
            expect(204, "DELETE", path + "/" + first, "owner", null);
            expect(204, "DELETE", path + "/" + first, "owner", null);
            assertEquals("CANCELED", json.readTree(expect(200, "GET", path, "owner", null).body())
                    .path("outgoing").get(0).path("status").asText());
            assertTrue(json.readTree(expect(200, "GET", path, "other", null).body()).path("incoming").isEmpty());
            expect(409, "PUT", path + "/" + first, "other", "{\"accept\":true}");
            expect(204, "POST", path, "owner", invite);
            long declined = json.readTree(expect(200, "GET", path, "other", null).body())
                    .path("incoming").get(0).get("id").asLong();
            assertNotEquals(first, declined);
            expect(204, "PUT", path + "/" + declined, "other", "{\"accept\":false}");
            assertEquals(UserRole.VIP, users.findById(vip.getId()).orElseThrow().getRole());
            assertEquals("DECLINED", json.readTree(expect(200, "GET", path, "owner", null).body())
                    .path("outgoing").get(0).path("status").asText());

            expect(204, "POST", path, "owner", invite);
            long second = json.readTree(expect(200, "GET", path, "other", null).body())
                    .path("incoming").get(0).get("id").asLong();
            assertNotEquals(first, second);
            expect(204, "PUT", path + "/" + second, "other", "{\"accept\":true}");
            expect(204, "PUT", path + "/" + second, "other", "{\"accept\":true}");
            assertEquals(UserRole.VVIP, users.findById(vip.getId()).orElseThrow().getRole());
            expect(409, "DELETE", path + "/" + second, "owner", null);
            expect(409, "POST", path, "owner", invite);
            expect(204, "POST", path, "other", "{\"username\":\"visitor\"}");
            long pending = json.readTree(expect(200, "GET", path, "visitor", null).body())
                    .path("incoming").get(0).get("id").asLong();
            var promoted = users.findById(vip.getId()).orElseThrow();
            promoted.setRole(UserRole.VIP);
            users.save(promoted);
            expect(403, "PUT", path + "/" + pending, "visitor", "{\"accept\":true}");
        }
    }

    private static byte[] signaturePng(int width, int height, boolean ink) throws Exception {
        var image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        if (ink) image.setRGB(0, 0, 0xffef3344);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private HttpResponse<String> uploadSignature(String path, String session, byte[] png) throws Exception {
        String boundary = "synchro-mural-test";
        var payload = new java.io.ByteArrayOutputStream();
        payload.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"imagem\"; filename=\"assinatura.png\""
                + "\r\nContent-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        payload.write(png);
        payload.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(payload.toByteArray()));
        if (session != null) request.header("Cookie", "token_jwt=" + session);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
