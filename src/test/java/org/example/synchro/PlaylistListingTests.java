package org.example.synchro;

import org.example.synchro.entities.Playlist;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.PlaylistRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.PlaylistService;
import org.example.synchro.services.exception.UnauthorizedException;
import org.example.synchro.dto.PlaylistRequestDto;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaylistListingTests {
    private static final String IMAGE = "data:image/png;base64,aGVsbG8=";
    private final UserRepository users = mock(UserRepository.class);
    private final PlaylistRepository playlists = mock(PlaylistRepository.class);
    private final CookieService cookies = mock(CookieService.class);
    private final PlaylistService service = new PlaylistService(users, playlists, cookies);

    @Test
    void rejectsMissingSessionBeforeReadingPlaylists() {
        assertThrows(UnauthorizedException.class, () -> service.findMyPlaylists(null));
        verifyNoInteractions(users, playlists);
    }

    @Test
    void listsOwnedAndCollaborativePlaylistsWithoutDuplicatesNewestFirst() {
        User user = new User();
        when(cookies.checkCookie("session")).thenReturn(true);
        when(cookies.getId("session")).thenReturn(7L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        Playlist owned = new Playlist("Minha", "Descrição", user);
        owned.setId(10L);
        owned.setCapa(IMAGE);
        owned.setBanner(IMAGE);
        Playlist shared = new Playlist("Compartilhada", "", user);
        shared.setId(11L);
        when(playlists.findByCriador(user)).thenReturn(List.of(owned));
        when(playlists.findByColaboradoresContaining(user)).thenReturn(List.of(owned, shared));
        var result = service.findMyPlaylists("session");
        assertEquals(List.of("Compartilhada", "Minha"), result.stream().map(p -> p.getTitulo()).toList());
        assertEquals(IMAGE, result.get(1).getCapa());
        assertEquals(IMAGE, result.get(1).getBanner());
    }

    @Test
    void savesArtworkAndKeepsExistingImagesWhenEditingTextOnly() {
        User user = new User();
        user.setId(7L);
        when(cookies.checkCookie("session")).thenReturn(true);
        when(cookies.getId("session")).thenReturn(7L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(playlists.save(any())).thenAnswer(call -> call.getArgument(0));
        PlaylistRequestDto request = new PlaylistRequestDto();
        request.setTitulo("Com imagens");
        request.setCapa(IMAGE);
        request.setBanner(IMAGE);
        var created = service.createPlaylist(request, "session");
        assertEquals(IMAGE, created.getCapa());
        assertEquals(IMAGE, created.getBanner());

        Playlist existing = new Playlist("Original", "", user);
        existing.setId(20L);
        existing.setCapa(IMAGE);
        existing.setBanner(IMAGE);
        when(playlists.findById(20L)).thenReturn(Optional.of(existing));
        request.setCapa(null);
        request.setBanner(null);
        var updated = service.updatePlaylist(request, "session", 20L);
        assertEquals(IMAGE, updated.getCapa());
        assertEquals(IMAGE, updated.getBanner());
    }

    @Test
    void validatesImageFormatAndSize() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new PlaylistRequestDto();
            request.setTitulo("Teste");
            assertTrue(validator.validate(request).isEmpty());
            request.setCapa(IMAGE);
            request.setBanner(IMAGE);
            assertTrue(validator.validate(request).isEmpty());
            request.setCapa("data:image/svg+xml;base64,PHN2Zz4=");
            assertFalse(validator.validate(request).isEmpty());
            request.setCapa(IMAGE);
            request.setBanner("data:image/png;base64," + "A".repeat(2800000));
            assertFalse(validator.validate(request).isEmpty());
        }
    }
}
