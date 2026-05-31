package org.example.synchro.services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Service;

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

    public Boolean verificarToken(String token){
        JWTVerifier verifier = JWT.require(algorithm).build();
        try {
            verifier.verify(token);
            return true;
        } catch (JWTVerificationException e) {
            throw new RuntimeException(e);
        }
    }
}

