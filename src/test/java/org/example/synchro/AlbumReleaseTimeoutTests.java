package org.example.synchro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.resources.AlbumReleaseResource;
import org.example.synchro.resources.LastFmDiscoveryResource;
import org.example.synchro.services.DailyPopularSelections;
import org.example.synchro.services.LastFmCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Hooks;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlbumReleaseTimeoutTests {
    private AlbumReleaseResource resource(HttpClient client) {
        var resource = new AlbumReleaseResource(mock(LastFmDiscoveryResource.class),
                mock(LastFmCatalogService.class), mock(DailyPopularSelections.class));
        ReflectionTestUtils.setField(resource, "charts", client);
        return resource;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void primaryTimeoutLoadsAndNormalizesTheAlternativeRanking() throws Exception {
        var client = mock(HttpClient.class);
        var primary = new CompletableFuture<HttpResponse<String>>();
        var fallback = response("""
                {"feed":{"entry":[{"im:name":{"label":"New Album"},
                "im:artist":{"label":"Artist"},"im:releaseDate":{"label":"2026-11-18T00:00:00-07:00"},
                "im:image":[{"label":"https://example.test/cover.jpg"}],
                "category":{"attributes":{"label":"Rock"}}}]}}
                """);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            if (request.uri().getHost().equals("rss.marketingtools.apple.com")) {
                assertEquals(Duration.ofSeconds(5), request.timeout().orElseThrow());
                return primary;
            }
            assertEquals("itunes.apple.com", request.uri().getHost());
            assertEquals(Duration.ofSeconds(6), request.timeout().orElseThrow());
            return CompletableFuture.completedFuture(fallback);
        });
        var result = resource(client).chart("br").toFuture();
        assertFalse(result.isDone());
        primary.completeExceptionally(new HttpTimeoutException("request timed out"));
        var albums = new ObjectMapper().readTree(result.get(2, TimeUnit.SECONDS));
        assertEquals("New Album", albums.get(0).path("name").asText());
        assertEquals("Artist", albums.get(0).path("artistName").asText());
        assertEquals("2026-11-18", albums.get(0).path("releaseDate").asText());
        verify(client, times(2)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void disconnectBeforeHttpTimeoutDoesNotDropAnUnhandledError() {
        var client = mock(HttpClient.class);
        var pending = new CompletableFuture<HttpResponse<String>>();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> pending);
        var dropped = new ArrayList<Throwable>();
        Hooks.onErrorDropped(dropped::add);
        try {
            var subscription = resource(client).chart("br").subscribe();
            subscription.dispose();
            assertFalse(pending.isCancelled());
            pending.completeExceptionally(new HttpTimeoutException("late timeout after disconnect"));
            assertTrue(dropped.isEmpty(), dropped.toString());
            verify(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        } finally {
            Hooks.resetOnErrorDropped();
        }
    }

    @Test
    void bothSourcesCanTimeOutAndTheNextVisitCanRetry() {
        var client = mock(HttpClient.class);
        var calls = new AtomicInteger();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            if (calls.incrementAndGet() <= 2) {
                return CompletableFuture.failedFuture(new HttpTimeoutException("request timed out"));
            }
            return CompletableFuture.completedFuture(response("{\"feed\":{\"results\":[{\"name\":\"Retry album\"}]}}"));
        });
        var resource = resource(client);
        var error = assertThrows(ResponseStatusException.class, () -> resource.chart("br").block(Duration.ofSeconds(2)));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertTrue(resource.chart("br").block(Duration.ofSeconds(2)).contains("Retry album"));
        assertEquals(3, calls.get());
    }
}
