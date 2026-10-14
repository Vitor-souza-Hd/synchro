package org.example.synchro.resources;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.PlaylistDto;
import org.example.synchro.dto.PlaylistRequestDto;
import org.example.synchro.services.PlaylistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/playlists")
public class PlaylistResource {

    private final PlaylistService playlistService;

    @PostMapping("/new-playlist")
    public ResponseEntity<PlaylistDto> createPlaylist(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @Valid @RequestBody PlaylistRequestDto request
    ) {
        PlaylistDto playlist = playlistService.createPlaylist(request, cookie);
        return ResponseEntity.status(HttpStatus.CREATED).body(playlist);
    }
}
