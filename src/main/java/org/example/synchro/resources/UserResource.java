
package org.example.synchro.resources;

import org.example.synchro.dto.UserDto;
import org.example.synchro.entities.User;
import org.example.synchro.entities.UserData;
import org.example.synchro.repositories.UserDataRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserResource {

    private final UserService service;
    private final UserRepository userRepository;
    private final UserDataRepository userDataRepository;
    private final CookieService cookieService;

    public UserResource(
            UserService service,
            UserRepository userRepository,
            UserDataRepository userDataRepository,
            CookieService cookieService
    ) {
        this.service = service;
        this.userRepository = userRepository;
        this.userDataRepository = userDataRepository;
        this.cookieService = cookieService;
    }

    // =====================================================
    // FUNCIONALIDADE EXISTENTE: LISTAR USUARIOS
    // =====================================================

    @GetMapping
    public ResponseEntity<List<UserDto>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    // =====================================================
    // IDENTIFICAR USUARIO AUTENTICADO
    // =====================================================

    private Long obterIdAutenticado(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            if (!cookieService.checkCookie(token)) {
                return null;
            }

            return cookieService.getId(token);

        } catch (RuntimeException error) {
            return null;
        }
    }

    // =====================================================
    // GET /users/me
    // =====================================================

    @GetMapping("/me")
    public ResponseEntity<MeResponse> findMe(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token
    ) {

        Long userId = obterIdAutenticado(token);

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(
                        new MeResponse(
                                user.getUsername(),
                                user.getLastFmUsername()
                        )
                ))
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    // =====================================================
    // GET /users/me/profile
    // =====================================================

    @GetMapping("/me/profile")
    @Transactional(readOnly = true)
    public ResponseEntity<ProfileResponse> getProfile(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token
    ) {

        Long userId = obterIdAutenticado(token);

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        return userRepository.findById(userId)
                .map(user -> {
                    UserData data = user.getData();

                    String biography =
                            data != null
                                    ? data.getBiography()
                                    : null;

                    return ResponseEntity.ok(
                            new ProfileResponse(
                                    biography == null
                                            ? ""
                                            : biography
                            )
                    );
                })
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    // =====================================================
    // PUT /users/me/profile
    // =====================================================

    @PutMapping("/me/profile")
    @Transactional
    public ResponseEntity<ProfileResponse> updateProfile(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token,
            @RequestBody ProfileRequest request
    ) {

        Long userId = obterIdAutenticado(token);

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        if (request == null || request.biography() == null) {
            return ResponseEntity.badRequest().build();
        }

        String biography = request.biography().trim();

        if (biography.length() > 2000) {
            return ResponseEntity.badRequest().build();
        }

        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        UserData data = user.getData();

        // Compatibilidade com usuarios que ainda
        // nao possuem um registro UserData.
        if (data == null) {
            data = new UserData(user);
            user.setData(data);
        }

        data.setBiography(biography);

        // Salva a biografia associada ao usuario.
        userDataRepository.save(data);

        return ResponseEntity.ok(
                new ProfileResponse(biography)
        );
    }

    // =====================================================
    // FUNCIONALIDADE EXISTENTE: BUSCAR POR USERNAME
    // =====================================================

    @GetMapping("/{username}")
    public ResponseEntity<UserDto> findByUsername(
            @PathVariable String username
    ) {
        return ResponseEntity.ok(
                service.FindByUsername(username)
        );
    }

    // =====================================================
    // DTOs DE RESPOSTA E EDICAO
    // =====================================================

    public record MeResponse(
            String username,
            String lastFmUsername
    ) {
    }

    public record ProfileResponse(
            String biography
    ) {
    }

    public record ProfileRequest(
            String biography
    ) {
    }
}
