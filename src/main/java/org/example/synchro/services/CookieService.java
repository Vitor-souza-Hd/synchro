package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CookieValue;

@Service
@RequiredArgsConstructor
public class CookieService {
    private final JwtService jwtService;

    public ResponseCookie createCookie(String name,String value){

    return ResponseCookie.from(name, value)
            .path("/")
            .httpOnly(true)
            .maxAge(60 * 60)
            .sameSite("Lax")
            .build();
    }

    public boolean checkCookie(String token){
        if(token != null && !token.isEmpty()){
            if (jwtService.verificarToken(token)) {
                return true;
            }
            return false;
        }
        return false;
    }

    public Long getId (String token){
        return Long.parseLong(jwtService.getUserID(token));
    }
}
