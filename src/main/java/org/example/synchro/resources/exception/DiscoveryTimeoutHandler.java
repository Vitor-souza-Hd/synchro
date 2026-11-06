package org.example.synchro.resources.exception;

import org.example.synchro.resources.CoverArtResource;
import org.example.synchro.resources.MusicBrainzResource;
import org.example.synchro.resources.LastFmDiscoveryResource;
import org.example.synchro.resources.LastFmArtistResource;
import org.example.synchro.resources.LastFmResource;
import org.example.synchro.resources.MusicaResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.concurrent.TimeoutException;

@RestControllerAdvice(assignableTypes = {MusicBrainzResource.class, CoverArtResource.class,
        LastFmDiscoveryResource.class, LastFmArtistResource.class, LastFmResource.class, MusicaResource.class})
public class DiscoveryTimeoutHandler {
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<Map<String, String>> timeout() {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).cacheControl(CacheControl.noStore())
                .body(Map.of("error", "O serviço de música demorou para responder. Tente novamente."));
    }
}
