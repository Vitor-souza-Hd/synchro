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
}
