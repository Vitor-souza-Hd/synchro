package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.example.synchro.dto.UpdateUsernameRequest;
import org.example.synchro.dto.UserDto;
import org.example.synchro.services.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/users")
public class UserResource {

    private final UserService service;

    @GetMapping
    public ResponseEntity<List<UserDto>> findAll() {
        return ResponseEntity.ok().body(service.findAll());
    }

    @GetMapping
    @RequestMapping(value = "{username}")
    public ResponseEntity<UserDto> findByUsername(@PathVariable String username){
        return ResponseEntity.ok().body(service.FindByUsername(username));
    }

    @PutMapping("/me/username")
    public ResponseEntity<UserDto> updateUser(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @Valid @RequestBody UpdateUsernameRequest request
    ) {
        return ResponseEntity.ok(service.updateUser(cookie, request));
    }

    @PostMapping(value = "/me/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadProfileImage(
            @CookieValue(name = "token_jwt", required = false) String cookie,
            @RequestParam("imagem") MultipartFile image
    ) {
        service.saveProfileImage(cookie, image);
        return ResponseEntity.noContent().build();
    }

}
