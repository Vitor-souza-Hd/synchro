package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.UpdateUsernameRequest;
import org.example.synchro.dto.UserDto;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.ResourceConflictException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final CookieService cookieService;

    public List<UserDto> findAll(){
        List<UserDto> list = new ArrayList<>();
        for(User user:repository.findAll()){
            list.add(new UserDto(user));
        }
        return list;
    }
    public UserDto FindByUsername(String username){
        return new UserDto(repository.findByUsername(username));
    }

    public UserDto findMe(String cookie) {
        if (!cookieService.checkCookie(cookie)) {
            throw new UnauthorizedException("Sessão inválida ou ausente");
        }

        User user = repository.findById(cookieService.getId(cookie))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        return new UserDto(user);
    }

    @Transactional
    public UserDto updateUser(String cookie, UpdateUsernameRequest request) {
        if (!cookieService.checkCookie(cookie)) {
            throw new UnauthorizedException("Sessão inválida ou ausente");
        }

        User user = repository.findById(cookieService.getId(cookie))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        boolean usernameTaken = repository.findByUsernameIgnoringCaseAndSpaces(request.getUsername())
                .stream()
                .anyMatch(existingUser -> !existingUser.getId().equals(user.getId()));
        if (usernameTaken) {
            throw new ResourceConflictException("Esse nome de usuário já está em uso");
        }

        user.setUsername(request.getUsername().trim());
        if (user.getData() != null) {
            user.getData().setUsername(user.getUsername());
        }
        return new UserDto(repository.save(user));
    }

    @Transactional
    public void saveProfileImage(String cookie, MultipartFile image) {
        if (!cookieService.checkCookie(cookie)) {
            throw new UnauthorizedException("Sessão inválida ou ausente");
        }

        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A imagem é obrigatória");
        }

        User user = repository.findById(cookieService.getId(cookie))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        try {
            user.setImagemPerfil(image.getBytes());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo enviado", exception);
        }

        repository.save(user);
    }

}
