package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.dto.LoginRequest;
import org.example.synchro.dto.RegistroRequest;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserDataRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.BadCredentialsException;
import org.example.synchro.services.exception.ResourceConflictException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final UserDataService userDataService;
    private final LastfmService lastfmService;
    private final LastFmConfig lastFmConfig;
    private final UserDataRepository userDataRepository;

    public String registro(RegistroRequest obj) {
        if (userRepository.existsByEmail(obj.getEmail())) {
            throw new ResourceConflictException("email", obj.getEmail());
        }

        if (!obj.getPassword().equals(obj.getConfirmPassword())) {
            throw new BadCredentialsException("senhas não coincidem");
        }

        User user = new User(
                null,
                obj.getUsername(),
                obj.getEmail(),
                passwordService.hashPassword(obj.getPassword()),
                obj.getBirthDay(),
                obj.getLastFmUsername()
        );

        try {
            userDataService.atualizarUserData(user);
        } catch (Exception e) {
            System.err.println("Aviso: Last.fm falhou — " + e.getMessage());
        }

        User userSalvo = userRepository.save(user);
        return token(userSalvo);
    }

    public String login(LoginRequest obj) {
        User user = userRepository.findByEmail(obj.getEmail());

        if (user == null) {
            throw new BadCredentialsException("email ou senha inválido");
        }

        if (!passwordService.checkPassword(obj.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("email ou senha inválido");
        }

        return token(user);
    }

    private String token(User user) {
        return jwtService.generateToken(user.getId(), user.getUsername(), user.getEmail());
    }

}
