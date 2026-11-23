package org.example.synchro;

import org.example.synchro.entities.FavoriteArtist;
import org.example.synchro.repositories.AlbumRepository;
import org.example.synchro.repositories.FavoriteArtistRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.RecomendacaoService;
import org.example.synchro.services.RecomendacaoService.TopArtist;
import org.example.synchro.services.ReleaseRecommendations.NewRelease;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecentRecommendationsTests {
    private final UserRepository users = mock(UserRepository.class);
    private final FavoriteArtistRepository favorites = mock(FavoriteArtistRepository.class);
    private final RecomendacaoService service = new RecomendacaoService(mock(AlbumRepository.class), users, favorites);

    @Test
    void promotesFavoriteArtistsAndListeningHistoryWhileShowingBothKindsOfRelease() {
        when(favorites.findByUserIdOrderByArtistNameAsc(7L))
                .thenReturn(List.of(new FavoriteArtist(null, "Artista favorito", "artista favorito")));

        var result = service.recommendRecent(7L, List.of(new TopArtist("Artista ouvido", null)), List.of(), List.of(
                release("Faixa geral", "Outro artista", "track", 0),
                release("Álbum geral", "Outro artista", "album", 0),
                release("Faixa para você", "Artista ouvido", "track", 5),
                release("Álbum para você", "artista favorito", "album", 10)));

        assertTrue(result.personalized());
        assertEquals(List.of("Álbum para você", "Faixa para você", "Álbum geral", "Faixa geral"),
                result.items().stream().map(RecomendacaoService.RecentItem::name).toList());
        assertNotNull(result.items().get(0).motivo());
        assertNotNull(result.items().get(1).motivo());
        assertNull(result.items().get(2).motivo());
    }

    @Test
    void visitorsReceiveTheNewestTracksWithoutLookingUpAnAccount() {
        var result = service.recommendRecent(null, List.of(), List.of(), List.of(
                release("Anterior", "Artista", "track", 5),
                release("Nova", "Artista", "track", 0)));

        assertFalse(result.personalized());
        assertEquals(List.of("Nova", "Anterior"), result.items().stream().map(RecomendacaoService.RecentItem::name).toList());
        verifyNoInteractions(users, favorites);
    }

    private static NewRelease release(String title, String artist, String type, int daysAgo) {
        return new NewRelease(title, artist, LocalDate.of(2026, 11, 22).minusDays(daysAgo), List.of(), type, Set.of(), 1);
    }
}
