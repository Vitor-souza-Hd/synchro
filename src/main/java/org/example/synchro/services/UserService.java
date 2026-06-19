package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.UserDto;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

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

}
