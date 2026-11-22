package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.exception.InvalidTokenException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class ProfilePageResource {
    private static final String PAGES = "/frontend/pages/";
    private static final Resource PRIVATE_PROFILE = new ClassPathResource("private/perfil.html");

    private final CookieService cookies;
    private final UserRepository users;

    @GetMapping(PAGES + "perfil.html")
    public ResponseEntity<Resource> privateProfile(
            @CookieValue(name = "token_jwt", required = false) String token,
            @RequestParam(name = "user", required = false) String publicUser) {
        if (publicUser != null) {
            String query = publicUser.matches("[0-9]+") ? "?user=" + publicUser : "";
            return redirect(PAGES + "perfil-publico.html" + query);
        }
        if (!hasAccount(token)) return redirect(PAGES + "login.html");

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_HTML)
                .body(PRIVATE_PROFILE);
    }

    private boolean hasAccount(String token) {
        try {
            return cookies.checkCookie(token) && users.existsById(cookies.getId(token));
        } catch (InvalidTokenException | NumberFormatException ignored) {
            return false;
        }
    }

    private ResponseEntity<Resource> redirect(String location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .cacheControl(CacheControl.noStore())
                .location(URI.create(location))
                .build();
    }
}
