package org.example.synchro.importer;

import org.example.synchro.repositories.MusicaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

public class LastFmGenreStore {
    private final MusicaRepository tracks;
    public LastFmGenreStore(MusicaRepository tracks) { this.tracks = tracks; }

    @Transactional(readOnly = true)
    public List<Candidate> candidates(long afterId, Instant retryBefore, String username) {
        return tracks.findGenreCandidates(afterId, retryBefore, username, PageRequest.of(0, 50)).stream()
                .map(track -> new Candidate(track.getId(), track.getTitulo(), track.getArtistas().stream()
                        .sorted(java.util.Comparator.comparing(org.example.synchro.entities.Artista::getId))
                        .map(org.example.synchro.entities.Artista::getNome).toList())).toList();
    }

    @Transactional
    public void save(long id, List<String> genres) {
        var track = tracks.findById(id).orElseThrow();
        if (track.getGenero() == null || track.getGenero().isBlank()) {
            if (!genres.isEmpty()) track.setGenero(String.join(", ", genres));
            track.setGeneroConsultadoEm(Instant.now());
        }
    }

    public record Candidate(long id, String title, List<String> artists) {}
}
