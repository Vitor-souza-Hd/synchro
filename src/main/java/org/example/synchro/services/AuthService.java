package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.LoginRequest;
import org.example.synchro.dto.RegistroRequest;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserDataRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.BadCredentialsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final JwtService jwtService;

    private final UserDataService userDataService;

    private final UserDataRepository userDataRepository;

    public String registro(RegistroRequest obj){
        if(!userRepository.existsByEmail(obj.getEmail())){
            if(obj.getPassword().equals(obj.getConfirmPassword())){
                User user = new User(null,obj.getUsername(),obj.getEmail(), passwordService.hashPassword(obj.getPassword()),obj.getBirthDay(),obj.getLastFmUsername());
                userDataService.atualizarUserData(user);
                User userSalvo = userRepository.save(user);
                return token(userSalvo);
            }else {
                throw new BadCredentialsException();
            }
        }else throw new BadCredentialsException();
    }

    public String login(LoginRequest obj){
        try {
            User user = userRepository.findByEmail(obj.getEmail());
            if(passwordService.hashPassword(obj.getPassword()).equals(user.getPassword())){
                return token(user);
            }else  {
                throw new BadCredentialsException();
            }
        }
        catch (RuntimeException e){
            throw new BadCredentialsException();
        }
    }
    private String token(User user){
        return jwtService.generateToken(user.getId(),user.getUsername(),user.getEmail());
    }
}
