package org.example.synchro.resources;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.PlaylistDto;
import org.example.synchro.dto.PlaylistRequestDto;
import org.example.synchro.dto.PlaylistSummaryDto;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.example.synchro.services.PlaylistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/playlists")
public class PlaylistResource {

    private final PlaylistService playlistService;

    @GetMapping("/me")
    public ResponseEntity<java.util.List<PlaylistDto>> findMyPlaylists(
            @CookieValue(name = "token_jwt", required = false) String cookie) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(playlistService.findMyPlaylists(cookie));
    }


    @GetMapping("/me/summary")
    public ResponseEntity<List<PlaylistSummaryDto>> findMyPlaylistSummaries(
            @CookieValue(name = "token_jwt", required = false) String cookie) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(playlistService.findMyPlaylistSummaries(cookie));
    }

    @GetMapping("/me/{id}")
    public ResponseEntity<PlaylistDto> findMyPlaylist(
            @CookieValue(name = "token_jwt", required = false) String cookie, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(playlistService.findMyPlaylist(cookie, id));
    }

    @GetMapping("/me/{id}/artwork/{kind}")
    public ResponseEntity<byte[]> findMyPlaylistArtwork(
            @CookieValue(name = "token_jwt", required = false) String cookie, @PathVariable long id, @PathVariable String kind) {
        var artwork = playlistService.findMyPlaylistArtwork(cookie, id, kind);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artwork.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .varyBy(HttpHeaders.COOKIE)
                .header("X-Content-Type-Options", "nosniff")
                .eTag(artworkTag(artwork.bytes()))
                .body(artwork.bytes());
    }

    private static String artworkTag(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    @PostMapping("/new-playlist")
    public ResponseEntity<PlaylistDto> createPlaylist(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @Valid @RequestBody PlaylistRequestDto request
    ) {
        PlaylistDto playlist = playlistService.createPlaylist(request, cookie);
        return ResponseEntity.status(HttpStatus.CREATED).body(playlist);
    }

    @PutMapping("/update-playlist/{id}")
    public ResponseEntity<PlaylistDto>  updatePlaylist(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @Valid @RequestBody PlaylistRequestDto request,
            @PathVariable Long id
    ) {
        PlaylistDto playlist = playlistService.updatePlaylist(request,cookie,id);
        return ResponseEntity.status(HttpStatus.OK).body(playlist);
    }

    @PutMapping("/update-playlist/{playlistId}/add-user/{userId}")
    public ResponseEntity<Void> addUserToPlaylist(
            @CookieValue(name = "token_jwt",required = false) String cookie,
            @PathVariable("playlistId") Long playlistId,
            @PathVariable("userId") Long userId
    ) {
        playlistService.addUserToPlaylist(cookie, playlistId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{playlistId}/midias/{midiaId}")
    public ResponseEntity<Void> addMidiaToPlaylist(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @PathVariable("playlistId") Long playlistId,
            @PathVariable("midiaId") Long midiaId
    ) {
        playlistService.addMidiaToPlaylist(cookie, playlistId, midiaId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/update-playlist/{playlistId}/remove-user/{userId}")
    public ResponseEntity<Void> removeUserToPlaylist(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @PathVariable("playlistId") Long playlistId,
            @PathVariable("userId") Long userId
    ) {
        playlistService.removeUserToPlaylist(cookie, playlistId, userId);
        return ResponseEntity.noContent().build();
    }

}
