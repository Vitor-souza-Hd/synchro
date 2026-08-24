package org.example.synchro.resources;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.LoginRequest;
import org.example.synchro.dto.RegistroRequest;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.AuthService;
import org.example.synchro.services.CookieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/auth")
@RequiredArgsConstructor
public class AuthResource {


    private final AuthService authService;
    private final CookieService cookieService;

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

    @GetMapping(value = "/cookie")
    public ResponseEntity<String> getCookie(@CookieValue(name = "token_jwt", required = false) String token){
        if(cookieService.checkCookie(token)){
            return ResponseEntity.ok().body("ok");
        }
        else {
            return ResponseEntity.ok().body("preto burro");
        }
    }

}
