package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.repositories.ArtistaRepository;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ArtistPhotoStore {
    private final ArtistaRepository artists;
    public record Reference(long id, String name, String image) {}

    @Transactional(readOnly = true)
    public Reference reference(long id) {
        var artist = artists.findById(id).orElseThrow(() -> new ResourceNotFoundException("Artista não encontrado"));
        return new Reference(id, artist.getNome(), artist.getImgUrl());
    }

    @Transactional
    public String savePhoto(Reference reference, String url, boolean replaceBroken) {
        var artist = artists.findById(reference.id()).orElseThrow(() -> new ResourceNotFoundException("Artista não encontrado"));
        if (!hasUsableImage(artist.getImgUrl()) || replaceBroken && Objects.equals(reference.image(), artist.getImgUrl())) {
            artist.setImgUrl(url);
        }
        return artist.getImgUrl();
    }

    public static boolean hasUsableImage(String url) {
        if (url == null || url.isBlank() || url.contains("2a96cbd8b46e442fc41c2b86b821562f")
                || url.contains("placehold.co/") || url.contains("catalog-placeholder.svg")
                || url.contains("artist-placeholder.svg")) return false;
        try {
            var uri = URI.create(url);
            if (uri.isAbsolute()) return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
            return url.startsWith("/frontend/") || url.startsWith("../assets/") || url.startsWith("frontend/assets/");
        } catch (IllegalArgumentException ignored) { return false; }
    }
}
