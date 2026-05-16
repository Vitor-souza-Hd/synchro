package org.example.synchro.resources;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.example.synchro.dto.RegistroRequest;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.AuthService;
import org.example.synchro.services.PasswordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping(value = "/auth")
public class AuthResource {

    @Autowired
    AuthService authService;
    @Autowired
    UserRepository userRepository;

    @PostMapping(value = "/registro")
    public ResponseEntity<User> registro(@Valid @RequestBody RegistroRequest request) {
        User user = authService.registro(
                request.getName(), request.getEmail(), request.getBirthDay(),request.getPassword(), request.getConfirmPassword()
        );
        return  ResponseEntity.ok().body(user);
    }
}
