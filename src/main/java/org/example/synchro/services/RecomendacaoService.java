package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.CatalogName;
import org.example.synchro.repositories.AlbumRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.repositories.FavoriteArtistRepository;
import org.example.synchro.services.ReleaseRecommendations.NewRelease;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.example.synchro.repositories.AlbumRepository.ArtistAlbumCandidate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecomendacaoService {
    public static final int MAX_SUGGESTIONS = 18;
    private static final int ARTIST_LIMIT = 8;
    private static final int CATALOG_LIMIT = 60;

    private final AlbumRepository albums;
    private final UserRepository users;
    private final FavoriteArtistRepository favoriteArtists;

    public record TopArtist(String name, String imageUrl) {}
    public record Suggestion(Long id, String titulo, String nomeArtista, String capaUrl, String motivo) {}
    public record Recommendations(boolean personalized, List<Suggestion> items) {}
    private record ListeningTaste(TopArtist artist, double weight, boolean recent) {}
    private record Discovery(Album album, String artist, double affinity) {}
    public record RecentItem(String name, String artist, LocalDate releaseDate, List<String> images,
                             String type, String motivo) {}
    public record RecentRecommendations(boolean personalized, List<RecentItem> items) {}
    private record RankedRelease(NewRelease release, double score, String reason) {}

    public Recommendations recommend(Long userId, List<TopArtist> recent, List<TopArtist> overall, int limit) {
        var scores = new LinkedHashMap<String, ListeningTaste>();
        addRanking(scores, recent, 2.0, true);
        addRanking(scores, overall, 1.0, false);
        var favorites = scores.values().stream().sorted(Comparator.comparingDouble(ListeningTaste::weight).reversed())
                .limit(ARTIST_LIMIT).toList();
        var genres = preferredGenres(userId);

        var result = new ArrayList<Suggestion>();
        var usedAlbums = new HashSet<Long>();
        var usedArtists = new HashSet<String>();
        var familiar = familiarAlbums(userId, favorites);

        familiar.stream().limit(limit / 2).forEach(item -> add(result, usedAlbums, usedArtists, item, limit));
        var catalog = albums.findUnreviewedPopular(userId, PageRequest.of(0, CATALOG_LIMIT)).stream()
                .map(album -> new Discovery(album, artistName(album), affinity(album, genres))).toList();
        catalog.stream().filter(candidate -> candidate.affinity() > 0 && !scores.containsKey(artistKey(candidate.artist())))
                .sorted(Comparator.comparingDouble(Discovery::affinity).reversed())
                .forEach(candidate -> add(result, usedAlbums, usedArtists, card(candidate.album(), candidate.artist(),
                        "Combina com seus gêneros favoritos"), limit));
        familiar.forEach(item -> add(result, usedAlbums, usedArtists, item, limit));
        var artistCards = favorites.stream().map(taste -> new Suggestion(null, "Explorar artista", taste.artist().name(),
                taste.artist().imageUrl(), "Entre os artistas que você mais ouve")).toList();
        if (result.isEmpty() && !artistCards.isEmpty()) {
            add(result, usedAlbums, usedArtists, artistCards.get(0), limit);
        }

        boolean personalized = !result.isEmpty();
        catalog.forEach(candidate -> add(result, usedAlbums, usedArtists,
                card(candidate.album(), candidate.artist(), "Em destaque no catálogo"), limit));
        int albumsShown = result.size();
        artistCards.forEach(item -> add(result, usedAlbums, usedArtists, item, limit));
        return new Recommendations(personalized || result.size() > albumsShown, List.copyOf(result));
    }

    public RecentRecommendations recommendRecent(Long userId, List<TopArtist> recent, List<TopArtist> overall,
                                                   List<NewRelease> releases) {
        var listening = new LinkedHashMap<String, ListeningTaste>();
        addRanking(listening, recent, 2.0, true);
        addRanking(listening, overall, 1.0, false);
        var genres = userId == null ? List.<String>of() : preferredGenres(userId);
        var favorites = new HashSet<String>();
        if (userId != null) {
            favoriteArtists.findByUserIdOrderByArtistNameAsc(userId)
                    .forEach(artist -> favorites.add(artistKey(artist.getArtistName())));
        }

        var ranked = releases.stream().map(release -> rankRelease(release, genres, favorites, listening))
                .sorted(Comparator.comparingDouble(RankedRelease::score).reversed()
                        .thenComparing(item -> item.release().releaseDate(), Comparator.reverseOrder())
                        .thenComparingInt(item -> item.release().rank())).toList();
        var albums = ranked.stream().filter(item -> item.release().type().equals("album")).toList();
        var tracks = ranked.stream().filter(item -> item.release().type().equals("track")).toList();
        var items = new ArrayList<RecentItem>();
        for (int index = 0; items.size() < MAX_SUGGESTIONS && index < Math.max(albums.size(), tracks.size()); index++) {
            if (index < albums.size()) items.add(recentItem(albums.get(index)));
            if (index < tracks.size() && items.size() < MAX_SUGGESTIONS) items.add(recentItem(tracks.get(index)));
        }
        return new RecentRecommendations(items.stream().anyMatch(item -> item.motivo() != null), List.copyOf(items));
    }

    private List<Suggestion> familiarAlbums(Long userId, List<ListeningTaste> favorites) {
        if (favorites.isEmpty()) return List.of();

        var keys = favorites.stream().map(taste -> CatalogName.key(taste.artist().name())).toList();
        var candidates = albums.findUnreviewedByArtists(userId, keys).stream()
                .collect(Collectors.groupingBy(ArtistAlbumCandidate::getArtistKey));
        var selected = new HashSet<Long>();
        var result = new ArrayList<Suggestion>();
        for (var taste : favorites) {
            String artist = taste.artist().name();
            for (var album : candidates.getOrDefault(CatalogName.key(artist), List.of())) {
                if (selected.add(album.getAlbumId())) {
                    String reason = taste.recent() ? "Você tem ouvido este artista" : "Entre os artistas que você mais ouve";
                    result.add(new Suggestion(album.getAlbumId(), album.getTitle(), artist, album.getCover(), reason));
                    break;
                }
            }
        }
        return result;
    }

    private List<String> preferredGenres(Long userId) {
        return users.findById(userId).map(user -> user.getData()).map(data -> data.getTopMusicGenres())
                .map(value -> ReleaseRecommendations.profile(value.lines().toList())).orElseGet(List::of);
    }

    private static RankedRelease rankRelease(NewRelease release, List<String> genres, Set<String> favorites,
                                             Map<String, ListeningTaste> listening) {
        String key = artistKey(release.artist());
        double genreScore = ReleaseRecommendations.affinity(genres, release.tags());
        boolean favorite = favorites.contains(key);
        var taste = listening.get(key);
        if (favorite) return new RankedRelease(release, 4 + genreScore, "De um artista que você favoritou");
        if (taste != null) {
            return new RankedRelease(release, 3 + taste.weight() + genreScore, "De um artista que você ouve");
        }
        return new RankedRelease(release, genreScore, genreScore > 0 ? "Combina com seus gêneros favoritos" : null);
    }

    private static RecentItem recentItem(RankedRelease ranked) {
        var release = ranked.release();
        return new RecentItem(release.name(), release.artist(), release.releaseDate(), release.images(),
                release.type(), ranked.reason());
    }

    private static void addRanking(Map<String, ListeningTaste> scores, List<TopArtist> artists, double weight, boolean recent) {
        for (int index = 0; index < artists.size(); index++) {
            var artist = artists.get(index);
            scores.merge(CatalogName.key(artist.name()), new ListeningTaste(artist, weight / (index + 1), recent),
                    (first, next) -> new ListeningTaste(first.artist(), first.weight() + next.weight(), first.recent() || next.recent()));
        }
    }

    private static double affinity(Album album, List<String> genres) {
        if (genres.isEmpty()) return 0;
        var tags = new HashSet<String>();
        LastFmCatalogStore.tags(album.getLastFm()).stream().map(ReleaseRecommendations::genreKey).forEach(tags::add);
        album.getArtistas().forEach(artist -> LastFmCatalogStore.tags(artist.getLastFm()).stream()
                .map(ReleaseRecommendations::genreKey).forEach(tags::add));
        return ReleaseRecommendations.affinity(genres, tags);
    }

    private static void add(List<Suggestion> result, Set<Long> albums, Set<String> artists, Suggestion item, int limit) {
        String key = artistKey(item.nomeArtista());
        if (result.size() >= limit || (!key.isEmpty() && artists.contains(key))
                || (item.id() != null && !albums.add(item.id()))) return;
        if (!key.isEmpty()) artists.add(key);
        result.add(item);
    }

    private static String artistKey(String artist) {
        return artist == null || artist.isBlank() ? "" : CatalogName.key(artist);
    }

    private static String artistName(Album album) {
        String artist = album.getLastFm().getArtistName();
        if (artist != null && !artist.isBlank()) return artist;
        return album.getArtistas().stream().map(Artista::getNome).sorted().findFirst().orElse(null);
    }

    private static Suggestion card(Album album, String artist, String reason) {
        return new Suggestion(album.getId(), album.getTitulo(), artist, album.getLastFm().getImageUrl(), reason);
    }
}
