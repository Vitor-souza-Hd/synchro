package org.example.synchro.resources;

import jakarta.validation.Valid;
import org.example.synchro.dto.LoginRequest;
import org.example.synchro.dto.RegistroRequest;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.AuthService;
import org.example.synchro.services.CookieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/auth")
public class AuthResource {

    @Autowired
    AuthService authService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    CookieService cookieService;

    @PostMapping(value = "/registro")
    public ResponseEntity<String> registro(@Valid @RequestBody RegistroRequest request) {
        String token = authService.registro(request);

        ResponseCookie cookie = cookieService.createCookie("token_jwt", token);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body("registro concluido");
    }

    @PostMapping(value = "/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);

        ResponseCookie cookie = cookieService.createCookie("token_jwt", token);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body("login concluido");
    }

}
