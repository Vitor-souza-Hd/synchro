package org.example.synchro;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.synchro.resources.*;
import org.example.synchro.resources.exception.DiscoveryTimeoutHandler;
import org.example.synchro.services.*;
import org.example.synchro.config.LastFmConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class DiscoveryTimeoutTests {
    private static final Duration DEADLINE = Duration.ofMillis(300);

    @Test
    void lastFmPublicQueriesReturnRetryableTimeoutInsteadOfAnUnhandledError() throws Exception {
        var config = mock(LastFmConfig.class);
        when(config.getApiKey()).thenReturn("test-key");
        var attempts = new AtomicInteger();
        var provider = WebClient.builder().baseUrl("https://example.test")
                .exchangeFunction(req -> attempts.getAndIncrement() % 2 == 0
                        ? Mono.error(new java.util.concurrent.TimeoutException("Last.fm deadline"))
                        : Mono.just(ClientResponse.create(HttpStatus.OK)
                                .body("{\"results\":{}}").build())).build();
        var service = new LastFmArtistService(provider, config);
        var mvc = MockMvcBuilders.standaloneSetup(
                new LastFmDiscoveryResource(provider, config, mock(LastFmCatalogService.class),
                        mock(org.example.synchro.services.DailyPopularSelections.class)), new LastFmArtistResource(service))
                .setControllerAdvice(new DiscoveryTimeoutHandler()).build();
        for (String url : new String[]{
                "/last-fm/discovery?method=track.getInfo&artist=Artist&track=Track",
                "/last-fm/discovery?method=album.getInfo&artist=Artist&album=Album",
                "/last-fm/artist/search?q=Artist", "/last-fm/track/search?q=Track"}) {
            request(mvc, url, 504);
            assertTrue(request(mvc, url, 200).contains("results"));
        }
        assertEquals(8, attempts.get(), "Retry must perform a new query");
        assertEquals(400, mvc.perform(get("/last-fm/discovery?method=invalid"))
                .andReturn().getResponse().getStatus());
    }

    private String request(MockMvc mvc, String path, int status) throws Exception {
        var pending = mvc.perform(get(path)).andReturn();
        pending.getAsyncResult(3000);
        var response = mvc.perform(asyncDispatch(pending)).andReturn().getResponse();
        assertEquals(status, response.getStatus());
        if (status == 504) {
            assertEquals("no-store", response.getHeader("Cache-Control"));
            assertTrue(response.getContentAsString().contains("Tente novamente"));
        }
        return response.getContentAsString();
    }

    @Test
    void queueDelayHasABoundedResponseAndTheSameSearchCanBeRetried() throws Exception {
        var limiter = mock(MusicBrainzRateLimiter.class);
        when(limiter.aguardarPermissao()).thenReturn(Mono.never(), Mono.empty());
        var service = new MusicBrainzService(limiter, DEADLINE);
        var provider = WebClient.builder().baseUrl("https://example.test")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .body("{\"release-groups\":[]}").build())).build();
        ReflectionTestUtils.setField(service, "musicBrainzClient", provider);
        var mvc = MockMvcBuilders.standaloneSetup(new MusicBrainzResource(service))
                .setControllerAdvice(new DiscoveryTimeoutHandler()).build();
        String url = "/musicbrainz/album/search?artist=Artist&album=Album";
        request(mvc, url, 504);
        assertTrue(request(mvc, url, 200).contains("release-groups"));
    }

    @Test
    void lastFmRankingTimeoutAllowsRetryWithTheSameSession() throws Exception {
        var cookies = mock(CookieService.class);
        when(cookies.checkCookie("session")).thenReturn(true);
        when(cookies.getId("session")).thenReturn(42L);
        var service = mock(LastfmService.class);
        when(service.getTopBySynchroId(42L, "artists", "7day")).thenReturn(
                Mono.error(new java.util.concurrent.TimeoutException()), Mono.just("{\"topartists\":{}}"));
        var mvc = MockMvcBuilders.standaloneSetup(new LastFmResource(service,cookies)).build();
        for (int status : new int[]{504,200}) {
            var pending = mvc.perform(get("/last-fm/me/top/artists")
                    .cookie(new jakarta.servlet.http.Cookie("token_jwt","session"))).andReturn();
            pending.getAsyncResult(3000);
            var response = mvc.perform(asyncDispatch(pending)).andReturn().getResponse();
            assertEquals(status,response.getStatus());
            assertTrue(response.getContentAsString().contains(status==504?"Tente novamente":"topartists"));
        }
    }

    @Test
    void slowCoverIsRetryableAndDistinctFromAnAlbumWithoutArtwork() throws Exception {
        var service = new CoverArtService(new MusicBrainzRateLimiter(), DEADLINE);
        var attempts = new AtomicInteger();
        var provider = WebClient.builder().baseUrl("https://example.test")
                .exchangeFunction(req -> attempts.incrementAndGet() == 1 ? Mono.never()
                        : Mono.just(ClientResponse.create(HttpStatus.OK)
                            .body("{\"images\":[{\"front\":true,\"approved\":true,\"image\":\"https://archive.org/cover.jpg\"}]}").build()))
                .build();
        ReflectionTestUtils.setField(service, "coverArtClient", provider);
        var mvc = MockMvcBuilders.standaloneSetup(new CoverArtResource(service))
                .setControllerAdvice(new DiscoveryTimeoutHandler()).build();
        String url = "/cover-art/release-group/00000000-0000-0000-0000-000000000001";
        request(mvc, url, 504);
        assertTrue(request(mvc, url, 200).contains("https://archive.org/cover.jpg"));
        assertEquals(2, attempts.get());
    }
}
