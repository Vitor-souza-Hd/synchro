package org.example.synchro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.entities.Artista;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.repositories.ArtistaRepository;
import org.example.synchro.resources.ArtistPhotoResource;
import org.example.synchro.resources.exception.ResourceExceptionHandler;
import org.example.synchro.services.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArtistPhotoTests {
    private static final String SEARCH = """
        {"search":[{"id":"Q1","label":"Test Artist","description":"politician"},
          {"id":"Q2","label":"Another Artist","description":"rock band"},
          {"id":"Q42","label":"Test Artist","description":"English rock band"}]}
        """;
    private static final String ENTITY = """
        {"entities":{"Q42":{"labels":{"en":{"value":"Test Artist"}},
          "descriptions":{"en":{"value":"English rock band"}},
          "claims":{"P18":[{"rank":"deprecated","mainsnak":{"datavalue":{"value":"Old.jpg"}}},
            {"rank":"normal","mainsnak":{"datavalue":{"value":"Artist photo.jpg"}}}]}}}}
        """;
    private static final String PHOTO = "https://commons.wikimedia.org/wiki/Special:FilePath/Artist%20photo.jpg?width=500";

    private WebClient provider(AtomicInteger calls, String entity) {
        return WebClient.builder().baseUrl("https://provider.test/api")
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body(
                            request.url().getQuery().contains("wbsearchentities") ? SEARCH : entity).build());
                }).build();
    }

    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.example.synchro.entities")
    @EnableJpaRepositories("org.example.synchro.repositories")
    @Import({ArtistPhotoStore.class, ArtistPhotoService.class, ArtistPhotoResource.class,
            LastFmCatalogStore.class, ResourceExceptionHandler.class})
    static class Config {
        @Bean LastFmConfig lastFmConfig() { return new LastFmConfig(); }
        @Bean WebClient lastFmClient() {
            return WebClient.builder().baseUrl("https://lastfm.test/api").exchangeFunction(request ->
                    Mono.just(ClientResponse.create(HttpStatus.OK).body("""
                        {"artist":{"name":"Last.fm Artist","image":[
                          {"size":"extralarge","#text":"https://lastfm.freetls.fastly.net/artist.jpg"}]}}
                        """).build())).build();
        }
    }

    private ArtistPhotoService service(ArtistPhotoStore store, Duration timeout) {
        var service = new ArtistPhotoService(store, WebClient.builder().build(), new LastFmConfig(), timeout);
        noPublicPhotos(service);
        return service;
    }

    private void noPublicPhotos(ArtistPhotoService service) {
        ReflectionTestUtils.setField(service, "lastFmSite", lastFmProvider("<ul class='image-list'></ul>"));
    }

    private WebClient lastFmProvider(String body) {
        return WebClient.builder().baseUrl("https://lastfm.test/api")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build())).build();
    }

    @Test
    void lastFmPhotoIsChosenBySizeSavedAndSharedWithoutQueryingWikidata() {
        var store = mock(ArtistPhotoStore.class);
        when(store.reference(1)).thenReturn(new ArtistPhotoStore.Reference(1, "Test Artist", null));
        when(store.savePhoto(any(), anyString(), anyBoolean())).thenAnswer(call -> call.getArgument(1));
        var config = new LastFmConfig(); config.setApiKey("test-key");
        String url = "https://lastfm.freetls.fastly.net/i/u/300x300/photo.jpg";
        var client = lastFmProvider("""
            {"artist":{"name":"Test Artist","image":[
              {"size":"small","#text":"https://lastfm.freetls.fastly.net/small.jpg"},
              {"size":"mega","#text":"https://lastfm.freetls.fastly.net/2a96cbd8b46e442fc41c2b86b821562f.png"},
              {"size":"extralarge","#text":"https://lastfm.freetls.fastly.net/i/u/300x300/photo.jpg"}]}}
            """);
        var service = new ArtistPhotoService(store, client, config, Duration.ofSeconds(1));
        ReflectionTestUtils.setField(service, "wikidata", WebClient.builder().exchangeFunction(request -> {
            fail("A valid Last.fm photo must not trigger Wikidata"); return Mono.empty();
        }).build());
        var photos = Mono.zip(service.find(1, false), service.find(1, false)).block();
        assertEquals(url, photos.getT1().imgUrl());
        assertEquals(url, photos.getT2().imgUrl());
        verify(store, times(1)).savePhoto(any(), eq(url), eq(false));
    }

    @Test
    void missingLastFmPhotosOrProviderFailureFallBackToWikidata() {
        var config = new LastFmConfig(); config.setApiKey("test-key");
        for (String body : new String[]{
                "{\"artist\":{\"name\":\"Test Artist\",\"image\":[{\"#text\":\"https://lastfm.freetls.fastly.net/2a96cbd8b46e442fc41c2b86b821562f.png\"}]}}",
                "{\"error\":11}", "{\"artist\":{\"name\":\"Other Artist\",\"image\":[{\"#text\":\"https://example.test/wrong.jpg\"}]}}"}) {
            var store = mock(ArtistPhotoStore.class);
            when(store.reference(1)).thenReturn(new ArtistPhotoStore.Reference(1, "Test Artist", null));
            when(store.savePhoto(any(), anyString(), anyBoolean())).thenAnswer(call -> call.getArgument(1));
            var service = new ArtistPhotoService(store, lastFmProvider(body), config, Duration.ofSeconds(1));
            noPublicPhotos(service);
            ReflectionTestUtils.setField(service, "wikidata", provider(new AtomicInteger(), ENTITY));
            assertEquals(PHOTO, service.find(1, false).block().imgUrl());
            verify(store).savePhoto(any(), eq(PHOTO), eq(false));
        }
    }

    @Test
    void slowLastFmLeavesTimeForTheAlternativePhotoSource() {
        var store = mock(ArtistPhotoStore.class);
        when(store.reference(1)).thenReturn(new ArtistPhotoStore.Reference(1, "Test Artist", null));
        when(store.savePhoto(any(), anyString(), anyBoolean())).thenAnswer(call -> call.getArgument(1));
        var config = new LastFmConfig(); config.setApiKey("test-key");
        var slow = WebClient.builder().baseUrl("https://lastfm.test").exchangeFunction(request -> Mono.never()).build();
        var service = new ArtistPhotoService(store, slow, config, Duration.ofMillis(400));
        noPublicPhotos(service);
        ReflectionTestUtils.setField(service, "wikidata", provider(new AtomicInteger(), ENTITY));
        assertEquals(PHOTO, service.find(1, false).block(Duration.ofSeconds(2)).imgUrl());
    }

    @Test
    void photoEndpointPersistsVerifiedPhotoAndNewServiceInstancesReuseIt() throws Exception {
        try (var app = new SpringApplicationBuilder(Config.class).profiles("artist-photo-test").run(
                "--server.port=0", "--spring.main.web-application-type=servlet",
                "--spring.datasource.url=jdbc:h2:mem:artist-photo", "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.jpa.open-in-view=false")) {
            var artists = app.getBean(ArtistaRepository.class);
            var artist = artists.saveAndFlush(new Artista("Test Artist", null, null));
            var service = app.getBean(ArtistPhotoService.class);
            noPublicPhotos(service);
            var calls = new AtomicInteger();
            ReflectionTestUtils.setField(service, "wikidata", provider(calls, ENTITY));
            String base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                    URI.create(base + "/catalog/artists/" + artist.getId() + "/photo")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            assertEquals(PHOTO, new ObjectMapper().readTree(response.body()).path("imgUrl").asText());
            assertEquals(PHOTO, artists.findById(artist.getId()).orElseThrow().getImgUrl());
            assertEquals(2, calls.get(), "Only the matching musician entity is queried");
            var restarted = service(app.getBean(ArtistPhotoStore.class), Duration.ofSeconds(1));
            ReflectionTestUtils.setField(restarted, "wikidata", WebClient.builder().exchangeFunction(request -> {
                fail("A saved photo must not trigger another provider lookup"); return Mono.empty();
            }).build());
            assertEquals(PHOTO, restarted.find(artist.getId(), false).block().imgUrl());
            app.getBean(LastFmConfig.class).setApiKey("test-key");
            var lastFmArtist = artists.saveAndFlush(new Artista("Last.fm Artist", null, null));
            assertEquals("https://lastfm.freetls.fastly.net/artist.jpg", service.find(lastFmArtist.getId(), false).block().imgUrl());
            assertEquals("https://lastfm.freetls.fastly.net/artist.jpg", artists.findById(lastFmArtist.getId()).orElseThrow().getImgUrl());
            assertEquals(2, calls.get(), "Valid Last.fm photo avoids Wikimedia requests");
            String gallery = "<ul class='image-list'><li><a href='/music/slowGM/+images/0123456789abcdef0123456789abcdef'>" +
                    "<img src='https://lastfm.freetls.fastly.net/i/u/300x300/0123456789abcdef0123456789abcdef.jpg'></a></li></ul>";
            ReflectionTestUtils.setField(service, "lastFmSite", lastFmProvider(gallery));
            var publicArtist = artists.saveAndFlush(new Artista("slowGM", null, null));
            String publicPhoto = service.find(publicArtist.getId(), false).block().imgUrl();
            assertEquals("https://lastfm.freetls.fastly.net/i/u/300x300/0123456789abcdef0123456789abcdef.jpg", publicPhoto);
            assertEquals(publicPhoto, artists.findById(publicArtist.getId()).orElseThrow().getImgUrl());
            assertEquals(2, calls.get(), "Public gallery fills missing API photos before Wikimedia");
            app.getBean(LastFmCatalogStore.class).save("artist", new ObjectMapper().readTree(
                    "{\"name\":\"Test Artist\",\"image\":[{\"#text\":\"https://example.test/other.jpg\"}]}"), true);
            assertEquals(PHOTO, artists.findById(artist.getId()).orElseThrow().getImgUrl(), "Metadata updates preserve the saved photo");
            var missing = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/catalog/artists/999999/photo"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, missing.statusCode());
        }
    }

    @Test
    void simultaneousLookupsShareTheRequestAndDoNotSavePhotosOfOtherPeople() {
        var store = mock(ArtistPhotoStore.class);
        when(store.reference(1)).thenReturn(new ArtistPhotoStore.Reference(1, "Test Artist", null));
        var service = service(store, Duration.ofSeconds(1));
        var calls = new AtomicInteger();
        ReflectionTestUtils.setField(service, "wikidata", provider(calls, ENTITY.replace("\"value\":\"Test Artist\"", "\"value\":\"Other Person\"")));
        var result = Mono.zip(service.find(1, false), service.find(1, false)).block();
        assertEquals("not_found", result.getT1().status());
        assertEquals("not_found", result.getT2().status());
        assertEquals(4, calls.get(), "One shared search, with English and Portuguese alternatives");
        verify(store, never()).savePhoto(any(), anyString(), anyBoolean());
    }

    @Test
    void unavailableProviderHasABoundedResponseAndDoesNotErasePhotos() {
        var store = mock(ArtistPhotoStore.class);
        when(store.reference(1)).thenReturn(new ArtistPhotoStore.Reference(1, "Test Artist", null));
        var service = service(store, Duration.ofMillis(50));
        var calls = new AtomicInteger();
        ReflectionTestUtils.setField(service, "wikidata", WebClient.builder().baseUrl("https://provider.test")
                .exchangeFunction(request -> { calls.incrementAndGet(); return Mono.never(); }).build());
        assertEquals("unavailable", service.find(1, false).block(Duration.ofSeconds(2)).status());
        assertEquals("unavailable", service.find(1, false).block(Duration.ofSeconds(2)).status());
        assertEquals(1, calls.get(), "Do not hammer a slow provider");
        verify(store, never()).savePhoto(any(), anyString(), anyBoolean());
    }
}
