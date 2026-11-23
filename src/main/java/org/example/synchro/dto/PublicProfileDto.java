package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.synchro.entities.User;

import java.util.List;

public record PublicProfileDto(Long id, String username, String lastFmUsername, boolean lastFmConnected, Data data,
                               @JsonInclude(JsonInclude.Include.NON_NULL) Appearance appearance) {
    public static PublicProfileDto from(User user) {
        return create(user, null);
    }

    public static PublicProfileDto details(User user) {
        var data = user.getData();
        if (data == null) return from(user);
        var storedGenres = data.getTopMusicGenres();
        var genres = storedGenres == null ? List.<String>of() : storedGenres.lines()
                .map(String::strip).filter(genre -> !genre.isEmpty()).toList();
        return create(user, new Appearance(data.getProfileImageUrl(), data.getBannerImageUrl(), data.getBiography(), genres));
    }

    private static PublicProfileDto create(User user, Appearance appearance) {
        var data = user.getData();
        return new PublicProfileDto(user.getId(), user.getUsername(), user.getLastFmUsername(),
                user.hasLastFmConnection(), data == null ? null : new Data(data.getFullName(),
                data.getScrobbles(), data.getArtistas(), data.getLastFmImageUrl(),
                data.getLastFmTracks(), data.getLastFmAlbums()), appearance);
    }

    public record Appearance(String profileImageUrl, String bannerImageUrl, String biography, List<String> genres) {}

    public record Data(String fullName, Integer scrobbles, Integer artistas, String lastFmImageUrl,
                       Integer lastFmTracks, Integer lastFmAlbums) {}
}
