package org.example.synchro.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.DailyPopularSelection;
import org.example.synchro.repositories.AlbumRepository;
import org.example.synchro.repositories.ArtistaRepository;
import org.example.synchro.repositories.DailyPopularSelectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyPopularSelections {
    public static final ZoneId TIME_ZONE = ZoneId.of("America/Sao_Paulo");
    public static final int SIZE = 6;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DailyPopularSelectionRepository selections;
    private final ArtistaRepository artists;
    private final AlbumRepository albums;

    public record PopularAlbum(Long id, String name, String artist, LocalDate releaseDate, List<String> images,
                               long listeners, long plays) {}

    @Transactional(readOnly = true)
    public Optional<String> findArtists(LocalDate date) {
        var ids = selections.findById(date).map(DailyPopularSelection::getArtistIds)
                .map(DailyPopularSelections::readIds).orElseGet(List::of);
        if (ids.size() != SIZE) return Optional.empty();
        var response = JSON.createObjectNode();
        var chart = response.putObject("artists").putArray("artist");
        for (var id : ids) {
            var artist = artists.findById(id);
            if (artist.isEmpty()) return Optional.empty();
            var saved = artist.get();
            var entry = chart.addObject().put("name", saved.getNome()).put("catalogId", id);
            var photo = saved.getImgUrl();
            if (photo == null || photo.isBlank()) photo = saved.getLastFm().getImageUrl();
            if (photo != null && !photo.isBlank()) entry.putArray("image").addObject().put("#text", photo);
        }
        return Optional.of(response.toString());
    }

    @Transactional(readOnly = true)
    public Optional<List<PopularAlbum>> findAlbums(LocalDate date) {
        var ids = selections.findById(date).map(DailyPopularSelection::getAlbumIds)
                .map(DailyPopularSelections::readIds).orElseGet(List::of);
        if (ids.size() != SIZE) return Optional.empty();
        var chart = ids.stream().map(albums::findById).flatMap(Optional::stream).map(album -> {
            var meta = album.getLastFm();
            return new PopularAlbum(album.getId(), album.getTitulo(), meta.getArtistName(), album.getDataLancamento(),
                    meta.getImageUrl() == null ? List.of() : List.of(meta.getImageUrl()),
                    meta.getListeners() == null ? 0 : meta.getListeners(),
                    meta.getPlaycount() == null ? 0 : meta.getPlaycount());
        }).toList();
        return chart.size() == SIZE && chart.stream().allMatch(album -> album.releaseDate() != null && album.artist() != null)
                ? Optional.of(chart) : Optional.empty();
    }

    public synchronized void saveArtists(LocalDate date, List<Long> ids) {
        if (!isComplete(ids)) return;
        var selection = selections.findById(date).orElseGet(() -> new DailyPopularSelection(date));
        selection.setArtistIds(writeIds(ids));
        selections.saveAndFlush(selection);
    }

    public synchronized void saveAlbums(LocalDate date, List<Long> ids) {
        if (!isComplete(ids)) return;
        var selection = selections.findById(date).orElseGet(() -> new DailyPopularSelection(date));
        selection.setAlbumIds(writeIds(ids));
        selections.saveAndFlush(selection);
    }

    public static boolean isComplete(List<Long> ids) {
        return ids.size() == SIZE && ids.stream().allMatch(id -> id != null && id > 0)
                && ids.stream().distinct().count() == SIZE;
    }

    private static String writeIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Long> readIds(String ids) {
        return Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
    }
}
