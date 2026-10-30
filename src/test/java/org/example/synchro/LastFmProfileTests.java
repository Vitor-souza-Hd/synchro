package org.example.synchro;

import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.config.WebClientConfig;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.LastFmSessionRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.resources.CatalogResource;
import org.example.synchro.resources.LastFmResource;
import org.example.synchro.resources.UserResource;
import org.example.synchro.services.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.util.DigestUtils;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LastFmProfileTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    // Real Last.fm tokens are alphanumeric, not restricted to MD5/hex characters.
    private static final String TOKEN = "Az09".repeat(8);

    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.example.synchro.entities")
    @EnableJpaRepositories("org.example.synchro.repositories")
    @Import({LastFmConfig.class, WebClientConfig.class, LastfmService.class, LastFmAccountStore.class,
            LastFmResource.class, CookieService.class, JwtService.class, UserService.class, UserResource.class, CatalogResource.class,
            org.example.synchro.resources.exception.ResourceExceptionHandler.class})
    static class Config {
        @Bean Algorithm algorithm() { return Algorithm.HMAC256("lastfm-profile-test"); }
        @Bean LastFmCatalogService catalogService() { return org.mockito.Mockito.mock(LastFmCatalogService.class); }
    }

    @Test
    void authorizationImportsAndPersistsProfileWithSafeCallbackAndOfflineRetry() throws Exception {
        var offline = new AtomicBoolean();
        var sessionError = new AtomicInteger();
        var plays = new AtomicInteger(3210);
        var sessionCalls = new AtomicInteger();
        var sessionRequest = new AtomicReference<Map<String, String>>();
        var api = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        api.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query == null) query = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = params(query);
            String response;
            int httpStatus = 200;
            if ("auth.getSession".equals(params.get("method"))) {
                sessionCalls.incrementAndGet();
                sessionRequest.set(params);
                response = sessionError.get() > 0 ? "{\"error\":" + sessionError.get() + ",\"message\":\"Authorization failed\"}"
                        : "{\"session\":{\"name\":\"lastfm-listener\",\"key\":\"private-session-key\",\"subscriber\":0}}";
                if (sessionError.get() > 0) httpStatus = 403;
            } else if (offline.get()) {
                response = "{\"error\":11,\"message\":\"Offline\"}";
            } else {
                response = """
                        {"user":{"name":"lastfm-listener","realname":"Last.fm Name","country":"Brazil",
                        "playcount":"%d","artist_count":"87","track_count":"240","album_count":"33",
                        "image":[{"size":"small","#text":"https://lastfm.freetls.fastly.net/small.jpg"},
                        {"size":"extralarge","#text":"https://lastfm.freetls.fastly.net/photo.jpg"}],
                        "registered":{"unixtime":"1000000000","#text":1000000000}}}
                        """.formatted(plays.get());
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(httpStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        api.start();
        try (var context = new SpringApplicationBuilder(Config.class).web(WebApplicationType.SERVLET).run(
                "--spring.profiles.active=lastfm-import", "--spring.main.web-application-type=servlet", "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:lastfm-profile", "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.jpa.open-in-view=false", "--lastfm.api-key=test-key", "--lastfm.shared-secret=test-secret",
                "--lastfm.base-url=http://localhost:" + api.getAddress().getPort() + "/")) {
            var users = context.getBean(UserRepository.class);
            var sessions = context.getBean(LastFmSessionRepository.class);
            var jwt = context.getBean(JwtService.class);
            User alice = new User(null, "alice", "alice@example.com", "unused", LocalDate.of(2000, 1, 1), "name-only");
            alice.getData().setFullName("Synchro Name");
            alice = users.save(alice);
            User bob = users.save(new User(null, "bob", "bob@example.com", "unused", LocalDate.of(2001, 1, 1), null));
            String aliceCookie = "token_jwt=" + jwt.generateToken(alice.getId(), alice.getUsername(), alice.getEmail());
            String bobCookie = "token_jwt=" + jwt.generateToken(bob.getId(), bob.getUsername(), bob.getEmail());
            String base = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
            var browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .followRedirects(HttpClient.Redirect.NEVER).build();

            assertEquals(401, request(browser, base + "/last-fm/auth/login", null, "GET").statusCode());
            assertEquals(401, request(browser, base + "/last-fm/sync", null, "POST").statusCode());
            assertFalse(profile(browser, base, aliceCookie).path("lastFmConnected").asBoolean());

            String callback = start(browser, base, aliceCookie);
            assertTrue(callback.startsWith(base + "/last-fm/auth/callback?state="));
            assertRedirect(request(browser, callback.replace("state=", "state=wrong") + "&token=" + TOKEN, aliceCookie, "GET"), "invalid_state");
            assertRedirect(request(browser, callback + "&token=" + TOKEN, bobCookie, "GET"), "invalid_state");
            assertEquals(0, sessionCalls.get());
            assertRedirect(request(browser, callback + "&token=" + TOKEN, aliceCookie, "GET"), "connected");
            assertEquals(DigestUtils.md5DigestAsHex(("api_keytest-keymethodauth.getSessiontoken" + TOKEN
                    + "test-secret").getBytes(StandardCharsets.UTF_8)), sessionRequest.get().get("api_sig"));
            assertEquals(TOKEN, sessionRequest.get().get("token"));
            assertEquals(1, sessions.count());

            JsonNode saved = profile(browser, base, aliceCookie);
            assertTrue(saved.path("lastFmConnected").asBoolean());
            assertEquals("lastfm-listener", saved.path("lastFmUsername").asText());
            assertEquals("Synchro Name", saved.path("data").path("fullName").asText());
            assertEquals("alice", saved.path("username").asText());
            assertEquals("alice@example.com", saved.path("email").asText());
            assertEquals(3210, saved.path("data").path("scrobbles").asInt());
            assertEquals(87, saved.path("data").path("artistas").asInt());
            assertEquals(240, saved.path("data").path("lastFmTracks").asInt());
            assertEquals(33, saved.path("data").path("lastFmAlbums").asInt());
            assertEquals("Last.fm Name", saved.path("data").path("lastFmRealName").asText());
            assertEquals("Brazil", saved.path("data").path("lastFmCountry").asText());
            assertEquals("https://lastfm.freetls.fastly.net/photo.jpg", saved.path("data").path("lastFmImageUrl").asText());
            assertFalse(saved.path("data").path("lastFmUpdatedAt").isNull());
            assertFalse(saved.toString().contains("private-session-key"));
            assertFalse(saved.toString().contains("test-secret"));
            var publicResponse = request(browser, base + "/catalog/users/" + alice.getId(), null, "GET");
            assertEquals(200, publicResponse.statusCode(), publicResponse.body());
            JsonNode publicProfile = JSON.readTree(publicResponse.body());
            assertTrue(publicProfile.path("lastFmConnected").asBoolean());
            assertEquals(3210, publicProfile.path("data").path("scrobbles").asInt());
            assertEquals("Last.fm Name", publicProfile.path("data").path("lastFmRealName").asText());
            assertFalse(publicProfile.has("email"));
            assertFalse(publicResponse.body().contains("private-session-key"));
            assertFalse(profile(browser, base, bobCookie).path("lastFmConnected").asBoolean());
            assertRedirect(request(browser, callback + "&token=" + TOKEN, aliceCookie, "GET"), "invalid_state");
            assertEquals(1, sessionCalls.get());

            plays.set(4000);
            assertEquals(200, request(browser, base + "/last-fm/sync", aliceCookie, "POST").statusCode());
            assertEquals(4000, profile(browser, base, aliceCookie).path("data").path("scrobbles").asInt());
            offline.set(true);
            assertEquals(502, request(browser, base + "/last-fm/sync", aliceCookie, "POST").statusCode());
            assertEquals(4000, profile(browser, base, aliceCookie).path("data").path("scrobbles").asInt());
            assertRedirect(request(browser, start(browser, base, aliceCookie) + "&token=" + TOKEN, aliceCookie, "GET"), "sync_pending");
            assertEquals(1, sessions.count(), "Reconnect must update the existing one-to-one session");

            for (var failure : Map.of(14, "not_authorized", 15, "token_expired",
                    13, "api_configuration", 11, "unavailable").entrySet()) {
                sessionError.set(failure.getKey());
                assertRedirect(request(browser, start(browser, base, bobCookie) + "&token=" + TOKEN,
                        bobCookie, "GET"), failure.getValue());
            }
            assertFalse(profile(browser, base, bobCookie).path("lastFmConnected").asBoolean());
            assertEquals(1, sessions.count());
            assertRedirect(request(browser, start(browser, base, bobCookie), bobCookie, "GET"), "cancelled");
            assertRedirect(request(browser, start(browser, base, bobCookie) + "&token=" + TOKEN, null, "GET"), "session_expired");

            context.getBean(LastFmAccountStore.class).connect(alice.getId(),
                    new org.example.synchro.dto.lastFmDtos.LastFmSessionDto.Session("another-account", "new-private-key", 0));
            JsonNode changed = profile(browser, base, aliceCookie);
            assertEquals("another-account", changed.path("lastFmUsername").asText());
            assertTrue(changed.path("data").path("scrobbles").isNull());
            assertTrue(changed.path("data").path("lastFmImageUrl").isNull());
            assertEquals("Synchro Name", changed.path("data").path("fullName").asText());
            assertEquals(1, sessions.count());
        } finally {
            api.stop(0);
        }
    }

    private static HttpResponse<String> request(HttpClient client, String url, String cookie, String method) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).method(method, HttpRequest.BodyPublishers.noBody());
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode profile(HttpClient browser, String base, String cookie) throws Exception {
        var response = request(browser, base + "/users/me", cookie, "GET");
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    private static String start(HttpClient browser, String base, String cookie) throws Exception {
        var response = request(browser, base + "/last-fm/auth/login", cookie, "GET");
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        URI auth = URI.create(JSON.readTree(response.body()).path("authUrl").asText());
        assertEquals("www.last.fm", auth.getHost());
        return params(auth.getRawQuery()).get("cb");
    }

    private static void assertRedirect(HttpResponse<String> response, String result) {
        assertEquals(303, response.statusCode(), response.body());
        assertEquals("/frontend/pages/user.html?lastfm=" + result, response.headers().firstValue("Location").orElseThrow());
        assertEquals("", response.body());
    }

    private static Map<String, String> params(String query) {
        Map<String, String> result = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }
}
