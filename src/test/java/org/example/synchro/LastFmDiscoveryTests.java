package org.example.synchro;

import org.example.synchro.config.LastFmConfig;
import org.example.synchro.resources.LastFmDiscoveryResource;
import org.example.synchro.services.LastFmArtistService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Sinks;
import static org.junit.jupiter.api.Assertions.*;

class LastFmDiscoveryTests {
    @Test
    void permitsOnlyDiscoveryMethodsAndUsesTheServerKeyWithEncodedParameters() {
        var captured = new AtomicReference<ClientRequest>();
        var client = WebClient.builder().baseUrl("https://provider.test/api/")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("{\"artist\":{}}").build());
                }).build();
        var config = new LastFmConfig(); config.setApiKey("server-key");
        var catalog = org.mockito.Mockito.mock(org.example.synchro.services.LastFmCatalogService.class);
        var resource = new LastFmDiscoveryResource(client, config, catalog);
        assertThrows(ResponseStatusException.class, () -> resource.discover(Map.of("method", "auth.getSession")));
        assertNull(captured.get());
        assertEquals("{\"artist\":{}}", resource.discover(Map.of("method", "artist.getInfo", "artist", "A & {B}",
                "api_key", "untrusted", "token", "not-forwarded")).block());
        org.mockito.Mockito.verify(catalog).persistDiscovery("artist.getinfo", "{\"artist\":{}}");
        String uri = captured.get().url().toString();
        assertTrue(uri.contains("api_key=server-key"));
        assertFalse(uri.contains("untrusted"));
        assertFalse(uri.contains("token="));
        assertTrue(uri.contains("%26"), uri);
        assertTrue(uri.contains("%7B"), uri);
        new LastFmArtistService(client, config).searchArtists("A & {B}").block();
        assertTrue(captured.get().url().toString().contains("%7B"));
    }

    @Test
    void sharesConcurrentLookupsAndHonorsNoStore() {
        var calls = new AtomicInteger();
        var upstream = Sinks.<ClientResponse>one();
        String body = "{\"track\":{}}";
        var client = WebClient.builder().baseUrl("https://provider.test")
                .exchangeFunction(request -> calls.incrementAndGet() == 1 ? upstream.asMono()
                        : Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CACHE_CONTROL, "no-store, max-age=120").body(body).build())).build();
        var config = new LastFmConfig();
        config.setApiKey("test-key");
        var catalog = org.mockito.Mockito.mock(org.example.synchro.services.LastFmCatalogService.class);
        var resource = new LastFmDiscoveryResource(client, config, catalog);
        var query = Map.of("method", "track.getInfo", "track", "Track", "artist", "Artist");
        var first = resource.discover(query).toFuture();
        var second = resource.discover(query).toFuture();
        assertEquals(1, calls.get());
        upstream.tryEmitValue(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=60").body(body).build());
        assertEquals(body, first.orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).join());
        assertEquals(body, second.orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).join());
        assertEquals(body, resource.discover(query).block(Duration.ofSeconds(2)));
        assertEquals(1, calls.get());
        org.mockito.Mockito.verify(catalog).persistDiscovery("track.getinfo", body);

        var uncached = Map.of("method", "track.getInfo", "track", "Another track", "artist", "Artist");
        resource.discover(uncached).block(Duration.ofSeconds(2));
        resource.discover(uncached).block(Duration.ofSeconds(2));
        assertEquals(3, calls.get());
    }

    @Test
    void rateLimitCodeAndHttpStatusPauseOtherLookups() {
        for (HttpStatus upstreamStatus : new HttpStatus[]{HttpStatus.OK, HttpStatus.BAD_REQUEST, HttpStatus.TOO_MANY_REQUESTS}) {
            var calls = new AtomicInteger();
            var client = WebClient.builder().baseUrl("https://provider.test")
                    .exchangeFunction(request -> {
                        calls.incrementAndGet();
                        return Mono.just(ClientResponse.create(upstreamStatus)
                                .header(HttpHeaders.RETRY_AFTER, "12").body("{\"error\":29}").build());
                    }).build();
            var config = new LastFmConfig();
            config.setApiKey("test-key");
            var catalog = org.mockito.Mockito.mock(org.example.synchro.services.LastFmCatalogService.class);
            var resource = new LastFmDiscoveryResource(client, config, catalog);
            for (String artist : new String[]{"Artist one", "Artist two"}) {
                var error = assertThrows(ResponseStatusException.class, () -> resource.discover(
                        Map.of("method", "artist.getinfo", "artist", artist)).block(Duration.ofSeconds(2)));
                assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
                assertTrue(Integer.parseInt(error.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)) >= 11);
            }
            assertEquals(1, calls.get());
            org.mockito.Mockito.verifyNoInteractions(catalog);
        }
    }
}
