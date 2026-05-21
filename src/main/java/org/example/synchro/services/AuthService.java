package org.example.synchro.services;

import org.example.synchro.dto.LoginRequest;
import org.example.synchro.dto.RegistroRequest;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.BadCredentialsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    public User registro(RegistroRequest obj){
        if(!userRepository.existsByEmail(obj.getEmail())){
            if(obj.getPassword().equals(obj.getConfirmPassword())){
                User user = new User(null,obj.getName(),obj.getEmail(), passwordService.hashPassword(obj.getPassword()),obj.getBirthDay());
                userRepository.save(user);
                return user;
            }else {
                throw new BadCredentialsException();
            }
        }else throw new BadCredentialsException();
    }

    public User login(LoginRequest obj){
        try {
            User user = userRepository.findByEmail(obj.getEmail());
            if(passwordService.hashPassword(obj.getPassword()).equals(user.getPassword())){
                return user;
            }else  {
                throw new BadCredentialsException();
            }
        }
        catch (RuntimeException e){
            throw new BadCredentialsException();
        }
    }

}
