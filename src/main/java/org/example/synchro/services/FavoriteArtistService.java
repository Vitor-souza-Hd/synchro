
package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.FavoriteArtist;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.FavoriteArtistRepository;
import org.example.synchro.repositories.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FavoriteArtistService {

    private final FavoriteArtistRepository favoriteRepository;
    private final UserRepository userRepository;
    private final CookieService cookieService;

    public List<Map<String, Object>> listar(String token) {

        Long userId = obterUsuarioId(token);

        return favoriteRepository
                .findByUserIdOrderByArtistNameAsc(userId)
                .stream()
                .map(this::converterResposta)
                .toList();
    }

    public Map<String, Object> verificar(
            String token,
            String artist
    ) {

        Long userId = obterUsuarioId(token);
        String artistKey = normalizarArtista(artist);

        boolean favoritado = favoriteRepository
                .existsByUserIdAndArtistKey(
                        userId,
                        artistKey
                );

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("artist", artist.trim());
        resposta.put("favorite", favoritado);

        return resposta;
    }

    public Map<String, Object> adicionar(
            String token,
            String artist
    ) {

        Long userId = obterUsuarioId(token);
        String artistKey = normalizarArtista(artist);

        var existente = favoriteRepository
                .findByUserIdAndArtistKey(
                        userId,
                        artistKey
                );

        if (existente.isPresent()) {
            return converterResposta(existente.get());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Usuario nao encontrado."
                        )
                );

        FavoriteArtist favorito = new FavoriteArtist(
                user,
                artist.trim(),
                artistKey
        );

        try {
            FavoriteArtist salvo = favoriteRepository.save(favorito);
            return converterResposta(salvo);

        } catch (DataIntegrityViolationException error) {
            // Protege contra duas tentativas simultaneas
            // de salvar o mesmo artista.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Artista ja adicionado aos favoritos.",
                    error
            );
        }
    }

    public Map<String, Object> remover(
            String token,
            String artist
    ) {

        Long userId = obterUsuarioId(token);
        String artistKey = normalizarArtista(artist);

        favoriteRepository
                .findByUserIdAndArtistKey(userId, artistKey)
                .ifPresent(favoriteRepository::delete);

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("artist", artist.trim());
        resposta.put("favorite", false);

        return resposta;
    }

    private Long obterUsuarioId(String token) {

        try {

            if (!cookieService.checkCookie(token)) {
                throw new IllegalArgumentException(
                        "Sessao nao autenticada."
                );
            }

            Long userId = cookieService.getId(token);

            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException(
                        "Identificador invalido."
                );
            }

            return userId;

        } catch (RuntimeException error) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Faca login para acessar seus favoritos."
            );
        }
    }

    private String normalizarArtista(String artist) {

        if (artist == null || artist.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe o nome do artista."
            );
        }

        String nome = artist.trim().replaceAll("\\s+", " ");

        if (nome.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nome do artista muito longo."
            );
        }

        return Normalizer
                .normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> converterResposta(
            FavoriteArtist favorito
    ) {

        Map<String, Object> resposta = new LinkedHashMap<>();

        resposta.put("id", favorito.getId());
        resposta.put("artist", favorito.getArtistName());
        resposta.put("favorite", true);

        return resposta;
    }
}
