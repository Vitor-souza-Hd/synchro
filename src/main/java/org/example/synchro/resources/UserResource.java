package org.example.synchro.resources;

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
    public ResponseEntity<List<User>> findAll() {
        List<User> list = service.findAll();
        return ResponseEntity.ok().body(list);
    }

    @PostMapping (value = "/registro")
    public ResponseEntity<User> register(@RequestBody User obj){
       obj = service.register(obj);
       return ResponseEntity.ok().body(obj);
    }

    @GetMapping (value = "/login")
    public ResponseEntity<User> login(@RequestBody User obj){
        obj =service.Login(obj);
        return ResponseEntity.ok().body(obj);
    }

}
