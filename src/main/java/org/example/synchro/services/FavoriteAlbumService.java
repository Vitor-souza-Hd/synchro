
package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.FavoriteAlbum;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.FavoriteAlbumRepository;
import org.example.synchro.repositories.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteAlbumService {

    private final FavoriteAlbumRepository repository;
    private final UserRepository userRepository;
    private final CookieService cookieService;
    private final LastFmCatalogService catalog;

    public record AlbumResponse(
            Long id,
            String releaseGroupId,
            String album,
            String artist,
            boolean favorite
    ) {}

    public record FavoriteStatus(boolean favorite) {}

    // LISTAR

    public List<AlbumResponse> listar(String token) {
        Long userId = autenticar(token);

        return repository
                .findByUserIdOrderByArtistNameAscAlbumTitleAsc(userId)
                .stream()
                .map(this::resposta)
                .toList();
    }

    // VERIFICAR

    public FavoriteStatus verificar(
            String token,
            String releaseGroupId
    ) {
        Long userId = autenticar(token);
        String id = validarId(releaseGroupId);

        boolean favorite =
                repository.existsByUserIdAndReleaseGroupId(
                        userId,
                        id
                );

        return new FavoriteStatus(favorite);
    }

    // ADICIONAR

    public AlbumResponse adicionar(
            String token,
            String releaseGroupId,
            String album,
            String artist
    ) {
        Long userId = autenticar(token);

        String id = validarId(releaseGroupId);
        String titulo = validarTexto(album, "album");
        String artista = validarTexto(artist, "artista");
        catalog.register("album", titulo, artista, null, null);

        var existente = repository
                .findByUserIdAndReleaseGroupId(userId, id);

        if (existente.isPresent()) {
            return resposta(existente.get());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Usuario nao encontrado."
                        )
                );

        FavoriteAlbum favorite = new FavoriteAlbum(
                user,
                id,
                titulo,
                artista
        );

        try {
            return resposta(
                    repository.saveAndFlush(favorite)
            );

        } catch (DataIntegrityViolationException error) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Album ja favoritado.",
                    error
            );
        }
    }

    // REMOVER

    public FavoriteStatus remover(
            String token,
            String releaseGroupId
    ) {
        Long userId = autenticar(token);
        String id = validarId(releaseGroupId);

        repository
                .findByUserIdAndReleaseGroupId(userId, id)
                .ifPresent(repository::delete);

        return new FavoriteStatus(false);
    }

    // AUTENTICACAO

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

    // VALIDACOES

    private String validarId(String value) {
        if (value == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe o releaseGroupId."
            );
        }

        try {
            // Confirma o formato UUID e padroniza a chave.
            String id = UUID.fromString(
                    value.trim()
            ).toString();

            if (!id.equalsIgnoreCase(value.trim())
                    || value.trim().length() != 36) {
                throw new IllegalArgumentException();
            }

            return id.toLowerCase(Locale.ROOT);

        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identificador MusicBrainz invalido."
            );
        }
    }

    private String validarTexto(
            String value,
            String campo
    ) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe o " + campo + "."
            );
        }

        String texto = value.trim().replaceAll("\\s+", " ");

        if (texto.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Texto muito longo: " + campo
            );
        }

        return texto;
    }

    private AlbumResponse resposta(FavoriteAlbum album) {
        return new AlbumResponse(
                album.getId(),
                album.getReleaseGroupId(),
                album.getAlbumTitle(),
                album.getArtistName(),
                true
        );
    }
}
