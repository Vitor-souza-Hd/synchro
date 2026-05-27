package org.example.synchro.config;

import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {
    @Value("${jwt.public.key.path}")
    private String publicKeyPath;

    @Value("${jwt.private.key.path}")
    private String privateKeyPath;

    @Bean
    public RSAPublicKey publicKey() throws Exception{
        return loadPublicKey(publicKeyPath);
    }

    @Bean
    public RSAPrivateKey privateKey() throws Exception{
        return loadPrivateKey(privateKeyPath);
    }

    @Bean
    public Algorithm jwtAlgorithm(RSAPublicKey publicKey, RSAPrivateKey privateKey){
        return Algorithm.RSA256(publicKey, privateKey);
    }

    private RSAPublicKey loadPublicKey(String publicKeyPath) throws Exception{
        String key = new String(Files.readAllBytes(Paths.get(publicKeyPath)))
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "");

        byte[] encoded = Base64.getMimeDecoder().decode(key);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }
    private RSAPrivateKey loadPrivateKey(String privateKeyPath) throws Exception{
        String key = new String(Files.readAllBytes(Paths.get(privateKeyPath)))
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "");

        byte[] encoded = Base64.getMimeDecoder().decode(key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }
}
