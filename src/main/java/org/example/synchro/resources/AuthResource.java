package org.example.synchro.resources;

import jakarta.validation.Valid;
import org.example.synchro.dto.LoginRequest;
import org.example.synchro.dto.RegistroRequest;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/auth")
public class AuthResource {

    @Autowired
    AuthService authService;
    @Autowired
    UserRepository userRepository;

    @PostMapping(value = "/registro")
    public ResponseEntity<User> registro(@Valid @RequestBody RegistroRequest request) {
        User user = authService.registro(request);
        userRepository.save(user);
        return  ResponseEntity.ok().body(user);
    }

    @PostMapping(value = "/login")
    public ResponseEntity<User> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request);
        return  ResponseEntity.ok().body(user);
    }

}
