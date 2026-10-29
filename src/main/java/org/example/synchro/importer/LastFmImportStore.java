package org.example.synchro.importer;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.synchro.entities.*;
import org.example.synchro.repositories.*;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

public class LastFmImportStore {
    private final LastFmImportStateRepository states;
    private final LastFmScrobbleRepository scrobbles;
    private final ArtistaRepository artists;
    private final AlbumRepository albums;
    private final MusicaRepository tracks;

    public LastFmImportStore(LastFmImportStateRepository states, LastFmScrobbleRepository scrobbles,
                            ArtistaRepository artists, AlbumRepository albums, MusicaRepository tracks) {
        this.states = states;
        this.scrobbles = scrobbles;
        this.artists = artists;
        this.albums = albums;
        this.tracks = tracks;
    }

    @Transactional
    public LastFmImportState begin(String username) {
        var state = states.findById(username).orElseGet(() -> {
            var fresh = new LastFmImportState();
            fresh.setUsername(username);
            return fresh;
        });
        if (state.getToTimestamp() == 0 || state.isComplete()) {
            state.setFromTimestamp(state.getToTimestamp());
            state.setToTimestamp(Math.max(state.getFromTimestamp(), Instant.now().getEpochSecond() - 2));
            state.setNextPage(1);
            state.setImported(0);
            state.setComplete(state.getFromTimestamp() == state.getToTimestamp());
        }
        return states.save(state);
    }

    /** A page and its checkpoint commit together; a failure rolls back both. */
    @Transactional
    public LastFmImportState savePage(String username, JsonNode response) throws Exception {
        var state = states.findById(username).orElseThrow();
        var attributes = response.path("@attr");
        int totalPages = attributes.path("totalPages").asInt(-1);
        if (totalPages < 0 || attributes.path("page").asInt(-1) != state.getNextPage()) {
            throw new IllegalStateException("Paginação Last.fm inválida; progresso preservado.");
        }
        JsonNode items = response.path("track");
        Iterable<JsonNode> rows = items.isArray() ? items : items.isObject() ? List.of(items) : List.of();
        int index = 0;
        int saved = 0;
        for (JsonNode row : rows) {
            int position = index++;
            if (row.path("@attr").path("nowplaying").asBoolean(false)) continue;
            long timestamp = row.path("date").path("uts").asLong(-1);
            String name = row.path("name").asText();
            String artistName = text(row.path("artist"));
            String albumName = text(row.path("album"));
            if (timestamp <= state.getFromTimestamp() || timestamp > state.getToTimestamp()
                    || name.isBlank() || artistName.isBlank()) {
                throw new IllegalStateException("Scrobble inválido ou fora do intervalo; página não salva.");
            }
            String key = username + ":" + state.getFromTimestamp() + ":" + state.getToTimestamp()
                    + ":" + state.getNextPage() + ":" + position;
            String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8)));
            if (scrobbles.existsById(id)) continue;
            Artista artist = artists.findByNomeKey(CatalogName.key(artistName)).orElse(null);
            if (artist == null) artist = artists.save(new Artista(artistName, null, null));
            Album album = null;
            if (!albumName.isBlank()) {
                album = albums.findByTituloKey(CatalogName.key(albumName)).orElse(null);
                if (album == null) {
                    album = new Album(albumName, null, null);
                    album.getArtistas().add(artist);
                    album = albums.save(album);
                }
                album.getArtistas().add(artist);
            }
            Musica track = tracks.findByTituloKey(CatalogName.key(name)).orElse(null);
            if (track == null) {
                track = new Musica(name, null, null, null);
                track.getArtistas().add(artist);
                track.setAlbum(album);
                track = tracks.save(track);
            }
            track.getArtistas().add(artist);
            var play = new LastFmScrobble();
            play.setId(id);
            play.setUsername(username);
            play.setPlayedAt(timestamp);
            play.setMusica(track);
            play.setArtista(artist);
            // The same song may have been heard on multiple editions of an album.
            play.setAlbum(album);
            play.setRawJson(row.toString());
            scrobbles.save(play);
            saved++;
        }
        if (index == 0 && state.getNextPage() < totalPages) {
            throw new IllegalStateException("Página vazia antes do fim do histórico.");
        }
        state.setImported(state.getImported() + saved);
        state.setComplete(state.getNextPage() >= totalPages);
        state.setNextPage(state.getNextPage() + 1);
        return states.save(state);
    }

    private String text(JsonNode node) {
        return node.isTextual() ? node.asText() : node.path("name").asText(node.path("#text").asText());
    }
}
