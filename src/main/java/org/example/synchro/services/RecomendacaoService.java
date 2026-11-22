package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.CatalogName;
import org.example.synchro.repositories.AlbumRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecomendacaoService {
    private static final int LIMIT = 4;

    private final AlbumRepository albums;

    public record TopArtist(String name, String imageUrl) {}

    public record Suggestion(Long id, String titulo, String nomeArtista, String capaUrl) {}

    public record Recommendations(boolean personalized, List<Suggestion> items) {}

    public Recommendations forTopArtists(Long userId, List<TopArtist> topArtists) {
        List<Suggestion> suggestions = new ArrayList<>();
        Set<String> usedArtists = new HashSet<>();
        Set<Long> usedAlbums = new HashSet<>();
        for (TopArtist artist : topArtists) {
            String key = CatalogName.key(artist.name());
            if (!usedArtists.add(key)) continue;

            var nextAlbum = albums.findUnreviewedByArtist(userId, key, PageRequest.of(0, LIMIT)).stream()
                    .filter(album -> usedAlbums.add(album.getId()))
                    .findFirst();
            suggestions.add(nextAlbum.map(album -> albumCard(album, artist.name()))
                    .orElseGet(() -> new Suggestion(null, "Explorar artista", artist.name(), artist.imageUrl())));
            if (suggestions.size() == LIMIT) break;
        }
        return new Recommendations(true, suggestions);
    }

    public Recommendations fromCatalog() {
        var page = PageRequest.of(0, LIMIT, Sort.by("id"));
        return new Recommendations(false, albums.findAll(page).stream().map(album -> {
            String artist = album.getArtistas().stream().map(Artista::getNome).sorted()
                    .findFirst().orElse(album.getLastFm().getArtistName());
            return albumCard(album, artist);
        }).toList());
    }

    private Suggestion albumCard(Album album, String artist) {
        return new Suggestion(album.getId(), album.getTitulo(), artist, album.getLastFm().getImageUrl());
    }
}
