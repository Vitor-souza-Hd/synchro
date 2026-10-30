package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.SearchResultDTO;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.CatalogName;
import org.example.synchro.repositories.*;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogResource {
    private final MusicaRepository songs;
    private final AlbumRepository albums;
    private final ArtistaRepository artists;
    private final UserRepository users;
    private final org.example.synchro.services.LastFmCatalogService lastFm;

    @PostMapping("/{type}/{id}/refresh")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void refresh(@PathVariable String type, @PathVariable long id) {
        String apiType = switch (type) {
            case "songs" -> "track";
            case "albums" -> "album";
            case "artists" -> "artist";
            default -> throw new ResourceNotFoundException("Tipo não encontrado");
        };
        lastFm.enrich(apiType, id);
    }

    @GetMapping("/songs/{id}")
    public SearchResultDTO.Song song(@PathVariable Long id) {
        return SearchResultDTO.Song.from(songs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Música não encontrada")));
    }

    @GetMapping("/albums/{id}")
    public AlbumDetails album(@PathVariable Long id) {
        var album = albums.findById(id).orElseThrow(() -> new ResourceNotFoundException("Álbum não encontrado"));
        return new AlbumDetails(SearchResultDTO.AlbumResult.from(album), album.getMusicas().stream()
                .sorted(java.util.Comparator.comparing(org.example.synchro.entities.Musica::getTitulo))
                .map(SearchResultDTO.Song::from).toList());
    }

    @GetMapping("/artists/{id}")
    public ArtistDetails artist(@PathVariable Long id) {
        var artist = artists.findById(id).orElseThrow(() -> new ResourceNotFoundException("Artista não encontrado"));
        return artistDetails(artist);
    }

    @GetMapping("/artists/by-name")
    public ArtistDetails artistByName(@RequestParam String name) {
        if (name.isBlank()) throw new ResourceNotFoundException("Artista não encontrado");
        return artistDetails(artists.findByNomeKey(CatalogName.key(name))
                .orElseThrow(() -> new ResourceNotFoundException("Artista não encontrado")));
    }

    private ArtistDetails artistDetails(Artista artist) {
        return new ArtistDetails(SearchResultDTO.Artist.from(artist), songs.findByArtistas(artist).stream()
                .map(SearchResultDTO.Song::from).toList(), albums.findByArtistas(artist).stream()
                .map(SearchResultDTO.AlbumResult::from).toList());
    }

    @GetMapping("/users/{id}")
    public PublicProfile user(@PathVariable Long id) {
        var user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        var data = user.getData();
        return new PublicProfile(user.getUsername(), user.getLastFmUsername(), user.hasLastFmConnection(),
                new PublicProfileData(data == null ? null : data.getFullName(),
                        data == null ? null : data.getScrobbles(), data == null ? null : data.getArtistas(),
                        data == null ? null : data.getLastFmRealName(), data == null ? null : data.getLastFmImageUrl(),
                        data == null ? null : data.getLastFmCountry(), data == null ? null : data.getLastFmTracks(),
                        data == null ? null : data.getLastFmAlbums(), data == null ? null : data.getLastFmUpdatedAt()));
    }

    public record AlbumDetails(SearchResultDTO.AlbumResult album, List<SearchResultDTO.Song> musicas) {}
    public record ArtistDetails(SearchResultDTO.Artist artist, List<SearchResultDTO.Song> musicas,
                                List<SearchResultDTO.AlbumResult> albums) {}
    public record PublicProfile(String username, String lastFmUsername, boolean lastFmConnected, PublicProfileData data) {}
    public record PublicProfileData(String fullName, Integer scrobbles, Integer artistas,
                                    String lastFmRealName, String lastFmImageUrl, String lastFmCountry,
                                    Integer lastFmTracks, Integer lastFmAlbums, java.time.Instant lastFmUpdatedAt) {}
}
