package org.example.synchro.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.synchro.entities.CatalogName;
import org.example.synchro.services.DailyPopularSelections;
import org.example.synchro.services.DailyPopularSelections.PopularAlbum;
import org.example.synchro.services.LastFmCatalogService;
import org.example.synchro.services.LastFmCatalogStore;
import org.example.synchro.services.ReleaseRecommendations;
import org.example.synchro.services.ReleaseRecommendations.Album;
import org.example.synchro.services.ReleaseRecommendations.Candidate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.net.http.HttpClient;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/catalog/albums")
public class AlbumReleaseResource {
    private static final Set<String> MARKETS = Set.of("br", "us");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int RELEASES_PER_MARKET = 12;
    private static final Duration CHART_TTL = Duration.ofMinutes(30);
    private static final Duration RELEASE_TTL = Duration.ofHours(3);
    private static final Duration RELEASE_RETRY = Duration.ofMinutes(2);
    private static final Duration RELEASE_STALE_LIMIT = Duration.ofHours(24);
    private static final Duration RELEASE_LOAD_BUDGET = Duration.ofSeconds(35);
    private final HttpClient charts = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final LastFmDiscoveryResource lastFm;
    private final LastFmCatalogService catalog;
    private final DailyPopularSelections dailySelections;
    private final Mono<List<PopularAlbum>> dailyAlbums;
    private final Map<String, Mono<JsonNode>> marketCharts;
    private final Map<String, Mono<JsonNode>> songCharts;
    private final Mono<ReleaseSnapshot> releaseRefresh;
    private volatile ReleaseSnapshot lastUsableReleases;

    public AlbumReleaseResource(LastFmDiscoveryResource lastFm, LastFmCatalogService catalog,
                                DailyPopularSelections dailySelections) {
        this.lastFm = lastFm;
        this.catalog = catalog;
        this.dailySelections = dailySelections;
        marketCharts = Map.of("br", cachedChart("br", "albums"), "us", cachedChart("us", "albums"));
        songCharts = Map.of("br", cachedChart("br", "songs"), "us", cachedChart("us", "songs"));
        releaseRefresh = Mono.defer(this::loadRecommendationCandidates)
                .onErrorReturn(new ReleaseSnapshot(List.of(), true, Instant.EPOCH))
                .doOnNext(this::rememberReleases)
                .cache(snapshot -> snapshot.unavailable() || snapshot.candidates().isEmpty() ? RELEASE_RETRY : RELEASE_TTL,
                        error -> RELEASE_RETRY, () -> Duration.ZERO);
        dailyAlbums = Mono.defer(() -> {
                    var today = LocalDate.now(DailyPopularSelections.TIME_ZONE);
                    return Mono.fromCallable(() -> dailySelections.findAlbums(today))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(saved -> saved.map(Mono::just).orElseGet(() -> loadDailyAlbums(today)));
                })
                .cache(albums -> {
                    if (albums.stream().map(PopularAlbum::id).distinct().count() != DailyPopularSelections.SIZE) {
                        return Duration.ofMinutes(1);
                    }
                    var now = ZonedDateTime.now(DailyPopularSelections.TIME_ZONE);
                    return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(DailyPopularSelections.TIME_ZONE));
                }, error -> Duration.ofMinutes(1), () -> Duration.ZERO);
    }

    private record Release(String name, String artist, LocalDate releaseDate, String cover, Set<String> tags,
                           boolean brazilChart, int rank) {}
    private record ConfirmedAlbum(Release release, List<String> images, long listeners, long plays, ObjectNode details) {}
    private record ReleaseSnapshot(List<Candidate> candidates, boolean unavailable, Instant updatedAt) {}
    public record Recommendations(List<Album> albums, int matchedCount, boolean unavailable) {}

    @GetMapping("/popular")
    public Mono<List<PopularAlbum>> popular() {
        return dailyAlbums;
    }

    @GetMapping("/recent")
    public Mono<List<Album>> recentReleases() {
        return Mono.defer(() -> acrossMarkets(LocalDate.now(DailyPopularSelections.TIME_ZONE), marketCharts, false)
                .map(releases -> releases.orElseThrow(AlbumReleaseResource::unavailableReleases).stream()
                        .sorted(Comparator.comparing(Release::releaseDate).reversed().thenComparingInt(Release::rank))
                        .limit(18).map(item -> chartCandidate(item).album()).toList()));
    }

    public Mono<List<ReleaseRecommendations.NewRelease>> recentMusic() {
        return Mono.defer(() -> {
            var today = LocalDate.now(DailyPopularSelections.TIME_ZONE);
            return Mono.zip(acrossMarkets(today, marketCharts, false), acrossMarkets(today, songCharts, true))
                    .map(sources -> {
                        var albums = sources.getT1();
                        var songs = sources.getT2();
                        if (albums.isEmpty() && songs.isEmpty()) throw unavailableReleases();
                        var results = new ArrayList<ReleaseRecommendations.NewRelease>();
                        albums.ifPresent(items -> items.forEach(item -> results.add(newRelease(item, "album"))));
                        songs.ifPresent(items -> items.forEach(item -> results.add(newRelease(item, "track"))));
                        return results;
                    });
        });
    }

    private Mono<Optional<List<Release>>> acrossMarkets(LocalDate today, Map<String, Mono<JsonNode>> feeds, boolean songs) {
        return Mono.zip(releases("br", today, 100, feeds.get("br"), songs).map(Optional::of).onErrorReturn(Optional.empty()),
                        releases("us", today, 100, feeds.get("us"), songs).map(Optional::of).onErrorReturn(Optional.empty()))
                .map(markets -> {
                    if (markets.getT1().isEmpty() && markets.getT2().isEmpty()) return Optional.empty();
                    var unique = new LinkedHashMap<List<String>, Release>();
                    for (var market : List.of(markets.getT1().orElseGet(List::of), markets.getT2().orElseGet(List::of))) {
                        for (var release : market) {
                            unique.merge(albumKey(release.name(), release.artist()), release, this::mergeRelease);
                        }
                    }
                    return Optional.of(List.copyOf(unique.values()));
                });
    }

    private static ReleaseRecommendations.NewRelease newRelease(Release release, String type) {
        return new ReleaseRecommendations.NewRelease(release.name(), release.artist(), release.releaseDate(),
                release.cover().isBlank() ? List.of() : List.of(release.cover()), type, release.tags(), release.rank());
    }

    private static ResponseStatusException unavailableReleases() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Lançamentos indisponíveis.");
    }

    @GetMapping("/recommendations")
    public Mono<Recommendations> recommendations(@RequestParam(name = "genre", defaultValue = "") List<String> genres) {
        if (genres.size() > 5 || genres.stream().anyMatch(genre -> genre.length() > 50)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe até cinco gêneros.");
        }
        var profile = ReleaseRecommendations.profile(genres);
        if (profile.isEmpty()) return Mono.just(new Recommendations(List.of(), 0, false));

        return Mono.defer(() -> {
            var saved = lastUsableReleases;
            if (saved != null && saved.updatedAt().isAfter(Instant.now().minus(RELEASE_STALE_LIMIT))) {
                // Serve the saved selection while the shared cache refreshes when it expires.
                releaseRefresh.subscribe();
                return Mono.just(rankReleases(saved, profile));
            }
            return releaseRefresh.map(snapshot -> rankReleases(snapshot, profile));
        });
    }

    private Recommendations rankReleases(ReleaseSnapshot snapshot, List<String> profile) {
        var selection = ReleaseRecommendations.select(snapshot.candidates(), profile,
                LocalDate.now(DailyPopularSelections.TIME_ZONE));
        return new Recommendations(selection.albums(), selection.matchedCount(), snapshot.unavailable());
    }

    private void rememberReleases(ReleaseSnapshot snapshot) {
        if (snapshot.candidates().isEmpty()) return;
        var saved = lastUsableReleases;
        if (!snapshot.unavailable() || saved == null || !saved.updatedAt().isAfter(Instant.now().minus(RELEASE_STALE_LIMIT))
                || snapshot.candidates().size() >= saved.candidates().size()) {
            lastUsableReleases = snapshot;
        }
    }

    private Mono<ReleaseSnapshot> loadRecommendationCandidates() {
        var day = LocalDate.now(DailyPopularSelections.TIME_ZONE);
        var incomplete = new AtomicBoolean();
        var artistTags = new ConcurrentHashMap<String, Mono<Set<String>>>();
        return Mono.zip(recommendationMarket("br", day, incomplete), recommendationMarket("us", day, incomplete))
                .flatMapMany(markets -> {
                    var candidates = new LinkedHashMap<List<String>, Release>();
                    for (int index = 0; index < RELEASES_PER_MARKET; index++) {
                        for (var market : List.of(markets.getT1(), markets.getT2())) {
                            if (index >= market.size()) continue;
                            var release = market.get(index);
                            candidates.merge(albumKey(release.name(), release.artist()), release, this::mergeRelease);
                        }
                    }
                    var releases = Flux.fromIterable(candidates.values());
                    return Flux.concat(releases.map(this::chartCandidate), releases.flatMap(release ->
                            enrichRecommendation(release, artistTags, incomplete), 4));
                })
                .takeUntilOther(Mono.delay(RELEASE_LOAD_BUDGET).doOnNext(ignored -> incomplete.set(true)))
                .collect(LinkedHashMap<List<String>, Candidate>::new, (albums, candidate) -> {
                    var album = candidate.album();
                    albums.put(albumKey(album.name(), album.artist()), candidate);
                })
                .map(albums -> new ReleaseSnapshot(List.copyOf(albums.values()), incomplete.get(), Instant.now()));
    }

    private Mono<List<Release>> recommendationMarket(String market, LocalDate day, AtomicBoolean incomplete) {
        return releases(market, day, 100).map(this::diverseReleases).onErrorResume(error -> {
            incomplete.set(true);
            return Mono.just(List.of());
        });
    }

    private List<Release> diverseReleases(List<Release> releases) {
        if (releases.size() <= RELEASES_PER_MARKET) return releases;

        int first = RELEASES_PER_MARKET / 2;
        var selected = new LinkedHashSet<>(releases.subList(0, first));
        var genres = new LinkedHashSet<String>();
        selected.forEach(release -> genres.addAll(release.tags()));
        for (var release : releases.subList(first, releases.size())) {
            if (selected.size() == RELEASES_PER_MARKET) break;
            if (ReleaseRecommendations.hasGenreTags(release.tags()) && genres.addAll(release.tags())) {
                selected.add(release);
            }
        }
        for (var release : releases) {
            if (selected.size() == RELEASES_PER_MARKET) break;
            selected.add(release);
        }
        return List.copyOf(selected);
    }

    private Release mergeRelease(Release first, Release second) {
        var tags = new LinkedHashSet<>(first.tags());
        tags.addAll(second.tags());
        return new Release(first.name(), first.artist(), first.releaseDate(),
                first.cover().isBlank() ? second.cover() : first.cover(), Set.copyOf(tags),
                first.brazilChart() || second.brazilChart(), Math.min(first.rank(), second.rank()));
    }

    private Candidate chartCandidate(Release release) {
        var images = release.cover().isBlank() ? List.<String>of() : List.of(release.cover());
        return new Candidate(new Album(release.name(), release.artist(), release.releaseDate(), images, 0, 0),
                release.tags(), release.rank());
    }

    private Mono<Candidate> enrichRecommendation(Release release, Map<String, Mono<Set<String>>> artistTags,
                                                AtomicBoolean incomplete) {
        var base = chartCandidate(release);
        return readAlbumMetadata(release).map(confirmed -> {
                    var tags = new LinkedHashSet<>(release.tags());
                    tags.addAll(readTags(confirmed.details().path("tags").path("tag")));
                    tags.addAll(readTags(confirmed.details().path("toptags").path("tag")));
                    var album = new Album(release.name(), release.artist(), release.releaseDate(), confirmed.images(),
                            confirmed.listeners(), confirmed.plays());
                    return new Candidate(album, Set.copyOf(tags), release.rank());
                })
                .defaultIfEmpty(base)
                .onErrorResume(error -> {
                    incomplete.set(true);
                    return Mono.just(base);
                })
                .flatMap(candidate -> addRecommendationTags(release, candidate, artistTags, incomplete));
    }

    private Mono<Candidate> addRecommendationTags(Release release, Candidate candidate,
            Map<String, Mono<Set<String>>> artistTags, AtomicBoolean incomplete) {
        if (ReleaseRecommendations.hasGenreTags(candidate.tags())
                && (!release.brazilChart() || ReleaseRecommendations.isBrazilian(candidate.tags()))) {
            return Mono.just(candidate);
        }
        var lookup = artistTags.computeIfAbsent(normalizedName(release.artist()), key ->
                lastFm.discover(Map.of("method", "artist.getTopTags", "artist", release.artist()))
                        .flatMap(body -> Mono.fromCallable(() -> readTags(JSON.readTree(body).path("toptags").path("tag"))))
                        .onErrorResume(error -> {
                            incomplete.set(true);
                            return Mono.just(Set.of());
                        })
                        .cache());
        return lookup.map(extra -> {
            var tags = new LinkedHashSet<>(candidate.tags());
            tags.addAll(extra);
            return new Candidate(candidate.album(), Set.copyOf(tags), candidate.rank());
        });
    }

    private static Set<String> readTags(JsonNode entries) {
        var result = new LinkedHashSet<String>();
        for (var node : LastFmCatalogStore.nodes(entries)) {
            String genre = ReleaseRecommendations.genreKey(node.path("name").asText(""));
            if (!genre.isEmpty()) result.add(genre);
        }
        return Set.copyOf(result);
    }

    private Mono<List<PopularAlbum>> loadDailyAlbums(LocalDate today) {
        return Mono.zip(releases("br", today, 8).onErrorReturn(List.of()), releases("us", today, 8).onErrorReturn(List.of()))
                .flatMapMany(markets -> {
                    var candidates = new LinkedHashMap<List<String>, Release>();
                    for (int index = 0; index < 8; index++) {
                        for (var market : List.of(markets.getT1(), markets.getT2())) {
                            if (index >= market.size()) continue;
                            var release = market.get(index);
                            candidates.putIfAbsent(albumKey(release.name(), release.artist()), release);
                        }
                    }
                    return Flux.fromIterable(candidates.values());
                })
                .flatMapSequential(release -> readAlbumMetadata(release).onErrorResume(error -> Mono.empty()), 4)
                .filter(album -> album.listeners() > 0)
                .sort(Comparator.comparingLong(ConfirmedAlbum::listeners).reversed()
                        .thenComparing(Comparator.comparingLong(ConfirmedAlbum::plays).reversed()))
                .distinct(album -> CatalogName.key(album.release().name()))
                .take(DailyPopularSelections.SIZE)
                .collectList()
                .filter(albums -> !albums.isEmpty())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ranking diário indisponível.")))
                .flatMap(albums -> Mono.fromCallable(() -> saveDailyAlbums(albums))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private List<PopularAlbum> saveDailyAlbums(List<ConfirmedAlbum> confirmed) {
        var ranked = new ArrayList<PopularAlbum>();
        for (var album : confirmed) {
            var release = album.release();
            var details = album.details().put("releasedate", release.releaseDate().toString());
            var id = catalog.importPopularAlbum(details);
            ranked.add(new PopularAlbum(id, release.name(), release.artist(), release.releaseDate(), album.images(),
                    album.listeners(), album.plays()));
        }
        dailySelections.saveAlbums(LocalDate.now(DailyPopularSelections.TIME_ZONE),
                ranked.stream().map(PopularAlbum::id).toList());
        return ranked;
    }

    private Mono<List<Release>> releases(String market, LocalDate today, int limit) {
        return releases(market, today, limit, readChart(market), false);
    }

    private Mono<List<Release>> releases(String market, LocalDate today, int limit, Mono<JsonNode> feed, boolean songs) {
        return feed.flatMapMany(Flux::fromIterable).index().<Release>handle((entry, sink) -> {
            var item = entry.getT2();
            String name = item.path("name").asText("").strip();
            String artist = item.path("artistName").asText("").strip();
            String date = item.path("releaseDate").asText("");
            if (name.isEmpty() || artist.isEmpty() || (!songs && name.toLowerCase(Locale.ROOT).endsWith(" - single"))) return;
            try {
                var released = LocalDate.parse(date);
                if (!released.isBefore(today.minusDays(30)) && !released.isAfter(today)) {
                    sink.next(new Release(name, artist, released, item.path("artworkUrl100").asText(""),
                            readTags(item.path("genres")), "br".equals(market), entry.getT1().intValue()));
                }
            } catch (java.time.format.DateTimeParseException ignored) {
                // Only include releases with a confirmed day.
            }
        }).take(limit).collectList();
    }

    private Mono<ConfirmedAlbum> readAlbumMetadata(Release release) {
        return lastFm.discover(Map.of("method", "album.getInfo", "album", release.name(),
                        "artist", release.artist(), "preview", "true"))
                .flatMap(body -> Mono.fromCallable(() -> JSON.readTree(body).path("album")))
                .filter(album -> {
                    var artist = album.path("artist");
                    String name = artist.isTextual() ? artist.asText("") : artist.path("name").asText("");
                    return albumKey(album.path("name").asText(""), name).equals(albumKey(release.name(), release.artist()));
                })
                .map(album -> {
                    var images = new ArrayList<String>();
                    for (var image : album.path("image")) {
                        String url = image.path("#text").asText("");
                        if (!url.isBlank()) images.add(0, url);
                    }
                    if (!release.cover().isBlank()) images.add(release.cover());
                    var details = (ObjectNode) album;
                    if (!release.cover().isBlank()) {
                        if (!details.path("image").isArray()) details.putArray("image");
                        ((com.fasterxml.jackson.databind.node.ArrayNode) details.path("image")).insertObject(0)
                                .put("#text", release.cover());
                    }
                    return new ConfirmedAlbum(release, List.copyOf(images), album.path("listeners").asLong(),
                            album.path("playcount").asLong(), details);
                });
    }

    private static List<String> albumKey(String title, String artist) {
        return List.of(normalizedName(title), normalizedName(artist));
    }

    private static String normalizedName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @GetMapping(value = "/release-chart", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> chart(@RequestParam String market) {
        return readChart(market).map(Object::toString);
    }

    private Mono<JsonNode> readChart(String market) {
        if (!MARKETS.contains(market)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mercado inválido.");
        }
        return marketCharts.get(market);
    }

    private Mono<JsonNode> cachedChart(String market, String type) {
        String appleMusic = "https://rss.marketingtools.apple.com/api/v2/" + market + "/music/most-played/100/" + type + ".json";
        String iTunes = "https://itunes.apple.com/" + market + "/rss/top" + type + "/limit=100/json";
        return readFeed(appleMusic, "results", Duration.ofSeconds(5))
                .onErrorResume(error -> readFeed(iTunes, "entry", Duration.ofSeconds(6)).map(this::normalizeITunes))
                .cacheInvalidateWhen(result -> Mono.delay(CHART_TTL).then());
    }

    private record FeedResponse(HttpResponse<String> response, Throwable error) {}

    private Mono<JsonNode> readFeed(String url, String entries, Duration timeout) {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).build();
        // The HTTP request owns the deadline. Complete the future normally even when a
        // disconnected subscriber cannot receive its eventual timeout or cancellation.
        return Mono.fromFuture(() -> charts.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .handle(FeedResponse::new), true)
                .flatMap(result -> result.error() == null ? Mono.just(result.response()) : Mono.error(result.error()))
                .filter(response -> response.statusCode() == 200)
                .flatMap(response -> Mono.fromCallable(() -> JSON.readTree(response.body()).path("feed").path(entries)))
                .filter(result -> result.isArray() && !result.isEmpty())
                .onErrorMap(error -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ranking indisponível.", error))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ranking inválido.")));
    }

    private JsonNode normalizeITunes(JsonNode entries) {
        var albums = JSON.createArrayNode();
        for (var entry : entries) {
            var date = entry.path("im:releaseDate").path("label").asText("");
            if (date.length() < 10) continue;
            var album = albums.addObject();
            album.put("name", entry.path("im:name").path("label").asText(""));
            album.put("artistName", entry.path("im:artist").path("label").asText(""));
            album.put("releaseDate", date.substring(0, 10));
            var images = entry.path("im:image");
            album.put("artworkUrl100", images.path(images.size() - 1).path("label").asText(""));
            album.putArray("genres").addObject().put("name",
                    entry.path("category").path("attributes").path("label").asText(""));
        }
        return albums;
    }
}
