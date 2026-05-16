package org.example.synchro.services;

import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository repository;

    public List<User> findAll(){
        return repository.findAll();
    }

    public User register(User obj){
        boolean emailEmUso = repository.existsByEmail(obj.getEmail());
        if (!emailEmUso){
            return repository.save(obj);
        }
        else {
            throw  new RuntimeException();
        }
        }

        public User Login(User obj){
            User user = repository.findByEmail(obj.getEmail());
            if (user.getPassword().equals(obj.getPassword())){
                return user;
            }
            else{
               throw  new RuntimeException("erro no email ou senha");
            }
        }

}
