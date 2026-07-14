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

    public boolean checkCookie(@CookieValue(name = "token_jwt", required = false) String token){
        if(token != null && !token.isEmpty()){
            return true;
        }
        return false;
    }

    public String getId(@CookieValue(name = "token_jwt", required = false) String token){
        return jwtService.getUserID(token);
    }
}
