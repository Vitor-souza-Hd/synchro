
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

import java.net.URI;
import java.util.List;
import java.util.Locale;

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
    // LISTAR USUARIOS - FUNCIONALIDADE EXISTENTE
    // =====================================================

    @GetMapping
    public ResponseEntity<List<UserDto>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    // =====================================================
    // IDENTIFICAR O USUARIO PELO COOKIE JWT
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
                .map(user -> ResponseEntity.ok(
                        criarRespostaPerfil(user.getData())
                ))
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    // =====================================================
    // PUT /users/me/profile
    // SALVAR BIOGRAFIA
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

        UserData data = obterOuCriarDados(user);

        data.setBiography(biography);

        userDataRepository.save(data);

        return ResponseEntity.ok(
                criarRespostaPerfil(data)
        );
    }

    // =====================================================
    // PUT /users/me/profile/image
    // SALVAR FOTO PERSONALIZADA
    // =====================================================

    @PutMapping("/me/profile/image")
    @Transactional
    public ResponseEntity<ProfileResponse> updateProfileImage(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token,
            @RequestBody ImageRequest request
    ) {
        Long userId = obterIdAutenticado(token);

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        if (request == null || request.imageUrl() == null) {
            return ResponseEntity.badRequest().build();
        }

        String imageUrl = request.imageUrl().trim();

        if (!urlImagemValida(imageUrl)) {
            return ResponseEntity.badRequest().build();
        }

        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        UserData data = obterOuCriarDados(user);

        // Altera apenas a foto.
        // A biografia permanece intacta.
        data.setProfileImageUrl(imageUrl);

        userDataRepository.save(data);

        return ResponseEntity.ok(
                criarRespostaPerfil(data)
        );
    }

    // =====================================================
    // DELETE /users/me/profile/image
    // REMOVER FOTO PERSONALIZADA
    // =====================================================

    @DeleteMapping("/me/profile/image")
    @Transactional
    public ResponseEntity<ProfileResponse> deleteProfileImage(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token
    ) {
        Long userId = obterIdAutenticado(token);

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        UserData data = obterOuCriarDados(user);

        data.setProfileImageUrl(null);

        userDataRepository.save(data);

        return ResponseEntity.ok(
                criarRespostaPerfil(data)
        );
    }

    // =====================================================
    // BANNER PERSONALIZADO DO USUARIO
    // =====================================================

    @GetMapping("/me/banner")
    @Transactional(readOnly = true)
    public ResponseEntity<BannerResponse> getBanner(
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

                    String url = data == null
                            ? null
                            : data.getBannerImageUrl();

                    return ResponseEntity.ok(
                            new BannerResponse(url)
                    );
                })
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    @PutMapping("/me/banner")
    @Transactional
    public ResponseEntity<BannerResponse> updateBanner(
            @CookieValue(
                    name = "token_jwt",
                    required = false
            ) String token,

            @RequestBody BannerRequest request
    ) {
        Long userId = obterIdAutenticado(token);

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        if (request == null || request.imageUrl() == null) {
            return ResponseEntity.badRequest().build();
        }

        String url = request.imageUrl().trim();

        // Uma string vazia remove a personalizacao.
        if (!url.isEmpty() && !urlImagemValida(url)) {
            return ResponseEntity.badRequest().build();
        }

        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        UserData data = obterOuCriarDados(user);

        data.setBannerImageUrl(
                url.isEmpty() ? null : url
        );

        userDataRepository.save(data);

        return ResponseEntity.ok(
                new BannerResponse(data.getBannerImageUrl())
        );
    }

    public record BannerRequest(String imageUrl) {
    }

    public record BannerResponse(String bannerImageUrl) {
    }

    // =====================================================
    // AUXILIARES DO PERFIL
    // =====================================================

    private UserData obterOuCriarDados(User user) {
        UserData data = user.getData();

        if (data == null) {
            data = new UserData(user);
            user.setData(data);
        }

        return data;
    }

    private ProfileResponse criarRespostaPerfil(UserData data) {
        if (data == null) {
            return new ProfileResponse("", null);
        }

        String biography = data.getBiography();

        return new ProfileResponse(
                biography == null ? "" : biography,
                data.getProfileImageUrl()
        );
    }

    // Aceita links HTTPS de imagens hospedadas externamente.
    // Nao aceita HTTP, URLs relativas ou enderecos com credenciais.
    private boolean urlImagemValida(String endereco) {
        if (endereco.isBlank() || endereco.length() > 2000) {
            return false;
        }

        try {
            URI uri = URI.create(endereco);

            return "https".equals(
                    uri.getScheme() == null
                            ? ""
                            : uri.getScheme().toLowerCase(Locale.ROOT)
            )
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getRawUserInfo() == null;

        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    // =====================================================
    // BUSCAR USUARIO POR NOME - FUNCIONALIDADE EXISTENTE
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
    // DTOs
    // =====================================================

    public record MeResponse(
            String username,
            String lastFmUsername
    ) {
    }

    public record ProfileResponse(
            String biography,
            String profileImageUrl
    ) {
    }

    public record ProfileRequest(
            String biography
    ) {
    }

    public record ImageRequest(
            String imageUrl
    ) {
    }
}
