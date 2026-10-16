package org.example.synchro.resources;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.LoginRequest;
import org.example.synchro.dto.RegistroRequest;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.AuthService;
import org.example.synchro.services.CookieService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/auth")
@RequiredArgsConstructor
public class AuthResource {

    private final AuthService authService;
    private final CookieService cookieService;
    private final UserRepository userRepository;

    @PostMapping(value = "/registro")
    public ResponseEntity<String> registro(@Valid @RequestBody RegistroRequest request) {
        String token = authService.registro(request);
        ResponseCookie cookie = cookieService.createCookie("token_jwt", token);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body("registro concluido");
    }

    @PostMapping(value = "/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);
        ResponseCookie cookie = cookieService.createCookie("token_jwt", token);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body("login concluido");
    }

    @GetMapping(value = "/cookie")
    public ResponseEntity<String> getCookie(@CookieValue(name = "token_jwt", required = false) String token) {
        if (cookieService.checkCookie(token)) {
            return ResponseEntity.ok().body("ok");
        } else {
            return ResponseEntity.ok().body("preto burro");
        }
    }

    // NOVO: retorna os dados do usuário logado para poder excluir
    @GetMapping(value = "/me")
    public ResponseEntity<?> me(@CookieValue(name = "token_jwt", required = false) String token) {
        if (!cookieService.checkCookie(token)) {
            return ResponseEntity.status(401).body("Não autenticado");
        }
        Long userId = cookieService.getId(token);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body("Usuário não encontrado");
        }
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail()
        ));
    }
}