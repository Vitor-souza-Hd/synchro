package org.example.synchro.resources;

import org.example.synchro.dto.UserDto;
import org.example.synchro.entities.User;
import org.example.synchro.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/users")
public class UserResource {

    @Autowired
    private UserService service;

    @GetMapping
    public ResponseEntity<List<UserDto>> findAll() {
        return ResponseEntity.ok().body(service.findAll());
    }

    @GetMapping
    @RequestMapping(value = "{username}")
    public ResponseEntity<UserDto> findByUsername(@PathVariable String username){
        return ResponseEntity.ok().body(service.FindByUsername(username));
    }

}
