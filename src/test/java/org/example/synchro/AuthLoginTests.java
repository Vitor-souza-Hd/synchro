package org.example.synchro;

import com.auth0.jwt.algorithms.Algorithm;
import org.example.synchro.dto.LoginRequest;
import org.example.synchro.dto.RegistroRequest;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.*;
import org.example.synchro.services.exception.BadCredentialsException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthLoginTests {
    @Test
    void registeredUserCanLogoutAndLoginAgainAfterRestartByUsernameOrEmail() {
        var repository = mock(UserRepository.class);
        var jwt = new JwtService(Algorithm.HMAC256("auth-regression-test-secret"));
        var signupService = new AuthService(repository, new PasswordService(), jwt,
                mock(UserDataService.class), null, null, null);
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            when(repository.findByEmail(user.getEmail())).thenReturn(user);
            when(repository.findByUsernameIgnoringCaseAndSpaces(user.getUsername())).thenReturn(List.of(user));
            return user;
        });

        var registration = new RegistroRequest();
        registration.setUsername("usuario-teste");
        registration.setEmail("teste@example.com");
        registration.setPassword("senha-correta-123");
        registration.setConfirmPassword("senha-correta-123");
        registration.setBirthDay(LocalDate.of(2000, 1, 1));
        assertTrue(jwt.verificarToken(signupService.registro(registration)));

        // Logout clears the browser cookie; persisted credentials must stay usable.
        assertEquals(0, new CookieService(jwt).clearCookie("token_jwt").getMaxAge().getSeconds());
        var restarted = new AuthService(repository, new PasswordService(), jwt,
                null, null, null, null);
        var login = new LoginRequest();
        login.setUsername("  usuario-teste  ");
        login.setPassword("senha-correta-123");
        assertTrue(jwt.verificarToken(restarted.login(login)));
        assertTrue(jwt.verificarToken(restarted.login(login)));

        login.setUsername("teste@example.com");
        assertTrue(jwt.verificarToken(restarted.login(login)));

        login.setPassword("senha-incorreta-123");
        assertThrows(BadCredentialsException.class, () -> restarted.login(login));
        login.setUsername("usuario-inexistente");
        assertThrows(BadCredentialsException.class, () -> restarted.login(login));
    }
}
