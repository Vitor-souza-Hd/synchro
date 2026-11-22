package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.CatalogName;
import org.example.synchro.repositories.AlbumRepository;
import org.example.synchro.repositories.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecomendacaoService {
    private static final int LIMIT = 6;
    private static final int FAMILIAR_SLOTS = LIMIT / 2;
    private static final int ARTIST_LIMIT = 8;
    private static final int CATALOG_LIMIT = 60;

    private final AlbumRepository albums;
    private final UserRepository users;

    public record TopArtist(String name, String imageUrl) {}
    public record Suggestion(Long id, String titulo, String nomeArtista, String capaUrl, String motivo) {}
    public record Recommendations(boolean personalized, List<Suggestion> items) {}
    private record ListeningTaste(TopArtist artist, double weight, boolean recent) {}
    private record Discovery(Album album, String artist, double affinity) {}

    public Recommendations recommend(Long userId, List<TopArtist> recent, List<TopArtist> overall) {
        var scores = new LinkedHashMap<String, ListeningTaste>();
        addRanking(scores, recent, 2.0, true);
        addRanking(scores, overall, 1.0, false);
        var favorites = scores.values().stream().sorted(Comparator.comparingDouble(ListeningTaste::weight).reversed())
                .limit(ARTIST_LIMIT).toList();
        var genres = users.findById(userId).map(user -> user.getData()).map(data -> data.getTopMusicGenres())
                .map(value -> ReleaseRecommendations.profile(value.lines().toList())).orElseGet(List::of);

        var result = new ArrayList<Suggestion>();
        var usedAlbums = new HashSet<Long>();
        var usedArtists = new HashSet<String>();
        var familiar = new ArrayList<Suggestion>();
        for (var taste : favorites) {
            String artist = taste.artist().name();
            albums.findUnreviewedByArtist(userId, CatalogName.key(artist), PageRequest.of(0, 3)).stream()
                    .filter(album -> familiar.stream().noneMatch(item -> album.getId().equals(item.id())))
                    .findFirst().ifPresent(album -> familiar.add(card(album, artist, taste.recent()
                            ? "Você tem ouvido este artista" : "Entre os artistas que você mais ouve")));
        }

        familiar.stream().limit(FAMILIAR_SLOTS).forEach(item -> add(result, usedAlbums, usedArtists, item));
        var catalog = albums.findUnreviewedPopular(userId, PageRequest.of(0, CATALOG_LIMIT)).stream()
                .map(album -> new Discovery(album, artistName(album), affinity(album, genres))).toList();
        catalog.stream().filter(candidate -> candidate.affinity() > 0 && !scores.containsKey(artistKey(candidate.artist())))
                .sorted(Comparator.comparingDouble(Discovery::affinity).reversed())
                .forEach(candidate -> add(result, usedAlbums, usedArtists, card(candidate.album(), candidate.artist(),
                        "Combina com seus gêneros favoritos")));
        familiar.forEach(item -> add(result, usedAlbums, usedArtists, item));
        var artistCards = favorites.stream().map(taste -> new Suggestion(null, "Explorar artista", taste.artist().name(),
                taste.artist().imageUrl(), "Entre os artistas que você mais ouve")).toList();
        if (result.isEmpty() && !artistCards.isEmpty()) {
            add(result, usedAlbums, usedArtists, artistCards.get(0));
        }

        boolean personalized = !result.isEmpty();
        catalog.forEach(candidate -> add(result, usedAlbums, usedArtists,
                card(candidate.album(), candidate.artist(), "Em destaque no catálogo")));
        int albumsShown = result.size();
        artistCards.forEach(item -> add(result, usedAlbums, usedArtists, item));
        return new Recommendations(personalized || result.size() > albumsShown, List.copyOf(result));
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

    private static void add(List<Suggestion> result, Set<Long> albums, Set<String> artists, Suggestion item) {
        String key = artistKey(item.nomeArtista());
        if (result.size() == LIMIT || (!key.isEmpty() && artists.contains(key))
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
