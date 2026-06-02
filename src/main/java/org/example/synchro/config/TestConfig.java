package org.example.synchro.config;

import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.ArtistaRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.repositories.MusicaRepository;
import org.example.synchro.services.PasswordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;

@Configuration
public class TestConfig implements CommandLineRunner {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordService passwordService;
    @Autowired
    private MusicaRepository  musicaRepository;
    @Autowired
    private ArtistaRepository artistaRepository;
    @Override
    public void run(String... args)  throws  Exception{

        Artista a1 = new Artista("YungLixo");
        Artista a2 = new Artista("Biffe");

        artistaRepository.saveAll(Arrays.asList(a1,a2));

        Musica m1 = new Musica("Rumo à vitória","musica do album validation", Duration.ofSeconds(207),"trap");
        m1.addArtista(a1);
        m1.addArtista(a2);


        musicaRepository.saveAll(Arrays.asList(m1));

        User u1 = new User(null, "vitor souza", "vitor@gmail.com", passwordService.hashPassword("1234567"), LocalDate.of(2009,1,6));
        User u2 = new User(null, "otavio ramos", "tavio@gmail.com", passwordService.hashPassword("1234567"), LocalDate.of(2008,7,14));
        User u3 = new User(null, "brenno", "brenno@gmail.com", passwordService.hashPassword("1234567"), LocalDate.of(2008,10,7));
        User u4 = new User(null, "vitor souza", "vitor@gmail.com", passwordService.hashPassword("1234567"), LocalDate.of(2009,1,6));


        userRepository.saveAllAndFlush(Arrays.asList(u1,u2,u3));
    }
}
