package org.example.synchro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.PostCommentRepository;
import org.example.synchro.repositories.PostNotificationRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.resources.PostDiscussionResource;
import org.example.synchro.services.PostDiscussionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PostDiscussionTests {
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String base;

    @Test
    void commentsAndRepliesNotifyOnlyTheirRecipientsAndAreRemovedWithThePost() throws Exception {
        try (var app = new SpringApplicationBuilder(RestorationFeaturesTests.Config.class,
                PostDiscussionResource.class, PostDiscussionService.class).profiles("restoration-test")
                .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:post-discussions",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop").run()) {
            base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var users = app.getBean(UserRepository.class);
            users.save(new User(null, "owner", "owner@example.test", "hash", LocalDate.of(2000, 1, 1), null));
            users.save(new User(null, "other", "other@example.test", "hash", LocalDate.of(2000, 1, 1), null));
            users.save(new User(null, "visitor", "visitor@example.test", "hash", LocalDate.of(2000, 1, 1), null));
            long post = expect(201, "POST", "/posts", "owner", "{\"text\":\"Post\"}").path("id").asLong();
            long anotherPost = expect(201, "POST", "/posts", "visitor", "{\"text\":\"Outro post\"}").path("id").asLong();
            String thread = "/posts/" + post + "/comments";
            String otherThread = "/posts/" + anotherPost + "/comments";

            expect(401, "POST", thread, null, "{\"text\":\"Sem sessão\"}");
            expect(401, "GET", "/post-notifications", null, null);
            expect(400, "POST", thread, "other", "{\"text\":\"  \"}");
            expect(404, "GET", "/posts/999999/comments", null, null);

            var comment = expect(201, "POST", thread, "other", "{\"text\":\"  Olá  \"}");
            long commentId = comment.path("id").asLong();
            assertEquals("Olá", comment.path("body").asText());
            assertEquals("other", comment.path("author").path("username").asText());
            assertFalse(comment.path("author").has("email"));
            assertTrue(comment.path("parentId").isNull());

            var reply = expect(201, "POST", thread, "owner",
                    "{\"text\":\"Uma resposta\",\"parentId\":" + commentId + "}");
            long replyId = reply.path("id").asLong();
            assertEquals(commentId, reply.path("parentId").asLong());
            assertEquals("other", reply.path("replyTo").path("username").asText());
            assertEquals(reply, expect(200, "GET", thread + "/" + replyId, null, null));
            expect(404, "GET", otherThread + "/" + replyId, "owner", null);
            expect(404, "POST", otherThread, "visitor", "{\"text\":\"Outro post\",\"parentId\":" + commentId + "}");

            expect(201, "POST", thread, "owner", "{\"text\":\"Comentário no meu post\"}");
            expect(201, "POST", thread, "other", "{\"text\":\"Resposta a mim\",\"parentId\":" + commentId + "}");

            var ownerAlerts = expect(200, "GET", "/post-notifications", "owner", null);
            var otherAlerts = expect(200, "GET", "/post-notifications", "other", null);
            assertEquals(1, ownerAlerts.path("unread").asInt());
            assertEquals(1, otherAlerts.path("unread").asInt());
            var commentAlert = ownerAlerts.path("items").get(0);
            assertEquals("COMMENT", commentAlert.path("type").asText());
            assertEquals(post, commentAlert.path("postId").asLong());
            assertEquals(commentId, commentAlert.path("commentId").asLong());
            var replyAlert = otherAlerts.path("items").get(0);
            assertEquals("REPLY", replyAlert.path("type").asText());
            assertEquals(replyId, replyAlert.path("commentId").asLong());
            assertEquals("owner", replyAlert.path("actor").path("username").asText());
            assertEquals(0, expect(200, "GET", "/post-notifications", "visitor", null).path("items").size());

            String commentAlertPath = "/post-notifications/" + commentAlert.path("id").asLong();
            expect(404, "PATCH", commentAlertPath, "other", null);
            expect(204, "PATCH", commentAlertPath, "owner", null);
            expect(204, "PATCH", commentAlertPath, "owner", null);
            var marked = expect(200, "GET", "/post-notifications", "owner", null);
            assertEquals(0, marked.path("unread").asInt());
            assertTrue(marked.path("items").get(0).path("read").asBoolean());
            assertEquals(1, expect(200, "GET", "/post-notifications", "other", null).path("unread").asInt());
            expect(204, "PATCH", "/post-notifications/read-all", "other", null);
            assertEquals(0, expect(200, "GET", "/post-notifications", "other", null).path("unread").asInt());

            for (int i = 0; i < 9; i++) {
                expect(201, "POST", thread, "owner", "{\"text\":\"Mais um comentário\"}");
            }
            var page = expect(200, "GET", thread, null, null);
            assertEquals(12, page.path("items").size());
            long cursor = page.path("nextBefore").asLong();
            assertTrue(cursor > commentId);
            var next = expect(200, "GET", thread + "?before=" + cursor, null, null);
            assertEquals(1, next.path("items").size());
            assertEquals(commentId, next.path("items").get(0).path("id").asLong());

            expect(204, "DELETE", "/posts/" + post, "owner", null);
            assertEquals(0, app.getBean(PostCommentRepository.class).count());
            assertEquals(0, app.getBean(PostNotificationRepository.class).count());
            expect(404, "GET", thread, null, null);
            assertEquals(0, expect(200, "GET", otherThread, null, null).path("items").size());
        }
    }

    private JsonNode expect(int status, String method, String path, String session, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path));
        if (session != null) builder.header("Cookie", "token_jwt=" + session);
        if (body != null) builder.header("Content-Type", "application/json");
        var request = builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), method + " " + path + " " + response.body());
        return response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
    }
}
