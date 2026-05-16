package org.example.synchro.services;

import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    public User registro(String name, String email, LocalDate birthDay, String password, String confirmPassword){
        if(!userRepository.existsByEmail(email)){
            //if(password == confirmPassword){
                password = BCrypt.hashpw(password,BCrypt.gensalt(12));
                User user = new User(null,name,email,password,birthDay);
                userRepository.save(user);
                return user;
            /*}else {
                throw new RuntimeException("senhas incompátiveis");
            }*/
        }else throw new RuntimeException("email já em uso");
    }
}
