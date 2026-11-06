
package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.FavoriteTrack;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.FavoriteTrackRepository;
import org.example.synchro.repositories.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FavoriteTrackService {

    private final FavoriteTrackRepository repository;
    private final UserRepository userRepository;
    private final CookieService cookieService;
    private final LastFmCatalogService catalog;

    public record TrackResponse(
            Long id,
            String track,
            String artist,
            boolean favorite
    ) {
    }

    public record FavoriteStatus(boolean favorite) {
    }

    public List<TrackResponse> listar(String token) {
        Long userId = autenticar(token);

        return repository
                .findByUserIdOrderByArtistNameAscTrackNameAsc(userId)
                .stream()
                .map(this::resposta)
                .toList();
    }

    public FavoriteStatus verificar(
            String token,
            String track,
            String artist
    ) {
        Long userId = autenticar(token);
        String key = criarChave(track, artist);

        return new FavoriteStatus(
                repository.existsByUserIdAndTrackKey(userId, key)
        );
    }

    public TrackResponse adicionar(
            String token,
            String track,
            String artist
    ) {
        Long userId = autenticar(token);
        String key = criarChave(track, artist);

        var existente = repository.findByUserIdAndTrackKey(
                userId, key
        );
        catalog.register("track", track, artist, null, null);

        if (existente.isPresent()) {
            return resposta(existente.get());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuario nao encontrado."
                ));

        FavoriteTrack favorite = new FavoriteTrack(
                user,
                track.trim().replaceAll("\\s+", " "),
                artist.trim().replaceAll("\\s+", " "),
                key
        );

        try {
            return resposta(repository.saveAndFlush(favorite));
        } catch (DataIntegrityViolationException error) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Musica ja favoritada.",
                    error
            );
        }
    }

    public FavoriteStatus remover(
            String token,
            String track,
            String artist
    ) {
        Long userId = autenticar(token);
        String key = criarChave(track, artist);

        repository.findByUserIdAndTrackKey(userId, key)
                .ifPresent(repository::delete);

        return new FavoriteStatus(false);
    }

    private Long autenticar(String token) {
        try {
            if (!cookieService.checkCookie(token)) {
                throw new IllegalArgumentException();
            }

            Long userId = cookieService.getId(token);

            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException();
            }

            return userId;

        } catch (RuntimeException error) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Faca login para acessar seus favoritos."
            );
        }
    }

    private String normalizar(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe musica e artista."
            );
        }

        String text = value.trim().replaceAll("\\s+", " ");

        if (text.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nome muito longo."
            );
        }

        return Normalizer
                .normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String criarChave(String track, String artist) {
        String artistKey = normalizar(artist);
        String trackKey = normalizar(track);

        // Separador reservado para impedir colisoes
        // entre combinacoes de nomes diferentes.
        return artistKey + "\u001F" + trackKey;
    }

    private TrackResponse resposta(FavoriteTrack favorite) {
        return new TrackResponse(
                favorite.getId(),
                favorite.getTrackName(),
                favorite.getArtistName(),
                true
        );
    }
}
