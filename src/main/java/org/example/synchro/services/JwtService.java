package org.example.synchro.services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.stereotype.Service;
import org.example.synchro.services.exception.InvalidTokenException;
import org.springframework.web.bind.annotation.CookieValue;

import java.util.Date;

@Service
public class JwtService {

    private final Algorithm algorithm;

    public JwtService(Algorithm algorithm){
        this.algorithm = algorithm;
    }

    public String generateToken(Long userID, String username, String email) {
        return JWT.create()
                .withSubject(String.valueOf(userID))
                .withClaim("email", email)
                .withClaim("username", username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600*1000))
                .sign(algorithm);
    }

    public Boolean verificarToken(@CookieValue(name = "token_jwt", required = false) String token){
        JWTVerifier verifier = JWT.require(algorithm).build();
        try {

            verifier.verify(JWT.decode(token).getToken());
            return true;
        } catch (JWTVerificationException e) {
            System.out.println("sdsddfff");
            System.out.println(e.getMessage());
            System.out.println(e.getCause());
            throw new InvalidTokenException("sessão inválida");
        }
    }

    public String getUserID(String token){
        return JWT.decode(token).getSubject();
    }
}

