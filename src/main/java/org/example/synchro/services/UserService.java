package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.RegistroRequest;
import org.example.synchro.dto.UserDto;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

    public List<UserDto> findAll() {

        List<UserDto> list = new ArrayList<>();

        for (User user : repository.findAll()) {
            list.add(new UserDto(user));
        }

        return list;
    }

    public UserDto FindByUsername(String username) {
        return new UserDto(repository.findByUsername(username));
    }

    public UserDto register(RegistroRequest request) {

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException(
                    "As senhas não coincidem"
            );
        }

        if (repository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Este e-mail já está cadastrado"
            );
        }

        User user = new User();

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setBirthDay(request.getBirthDay());
        user.setPassword(request.getPassword());
        user.setLastFmUsername(request.getLastFmUsername());

        User savedUser = repository.save(user);

        return new UserDto(savedUser);
    }
}
