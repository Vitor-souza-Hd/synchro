package org.example.synchro.config;

import org.example.synchro.entities.User;
import org.example.synchro.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class TestConfig implements CommandLineRunner {
    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args)  throws  Exception{

        User u1 = new User(null, "vitor souza", "vitor@gmail.com", "1234567");
        User u2 = new User(null, "vitor souza", "vitor@gmail.com", "1234567");
        User u3 = new User(null, "vitor souza", "vitor@gmail.com", "1234567");
        User u4 = new User(null, "vitor souza", "vitor@gmail.com", "1234567");


        userRepository.saveAllAndFlush(Arrays.asList(u1,u2,u3,u4));
    }
}
