package org.example.synchro.dto;

import org.example.synchro.entities.User;

public record PublicProfileDto(Long id, String username, String lastFmUsername, boolean lastFmConnected, Data data) {
    public static PublicProfileDto from(User user) {
        var data = user.getData();
        return new PublicProfileDto(user.getId(), user.getUsername(), user.getLastFmUsername(),
                user.hasLastFmConnection(), data == null ? null : new Data(data.getFullName(),
                data.getScrobbles(), data.getArtistas(), data.getLastFmImageUrl(),
                data.getLastFmTracks(), data.getLastFmAlbums()));
    }

    public record Data(String fullName, Integer scrobbles, Integer artistas, String lastFmImageUrl,
                       Integer lastFmTracks, Integer lastFmAlbums) {}
}
