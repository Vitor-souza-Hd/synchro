package org.example.synchro.config;

import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.*;
import org.example.synchro.services.PasswordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static tools.jackson.databind.type.LogicalType.DateTime;

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
    @Autowired
    private AlbumRepository albumRepository;
    @Autowired
    private UserDataRepository userDataRepository;

    @Override
    public void run(String... args)  throws  Exception{

        Artista a1 = new Artista("YungLixo","musico de trap brasileiro",null );
        Artista a2 = new Artista("Biffe", "produtor musical", null);
        Artista a3 = new Artista("MegaDeath", "banda de rock", null);
        artistaRepository.saveAll(Arrays.asList(a1,a2,a3));
        Album album1 = new Album("Validation","album do artista yunglixo feito em collab com biffe", LocalDate.of(2022,12,22));
        Album album2 = new Album("Megadeath","Sétimo album da banda de mesmo nome",LocalDate.of(2026,1,26));
        albumRepository.saveAll(Arrays.asList(album1,album2));
        album1.addArtista(a1);
        album1.addArtista(a2);
        album2.addArtista(a3);

        Musica m1 = new Musica("Rumo à vitória","musica do album validation", Duration.ofSeconds(207),"trap");
        Musica m2 = new Musica("Tipping Point","musica do album MegaDeath", Duration.ofSeconds(389),"rock");
        Musica m3 = new Musica("Hey,God?!","musica do album MegaDeath", Duration.ofSeconds(209),"rock");
        Musica m4 = new Musica("Puppet Parade","musica do album MegaDeath", Duration.ofSeconds(401),"rock");
        Musica m5 = new Musica("Let There Be Shred","musica do album MegaDeath", Duration.ofSeconds(238),"rock");
        Musica m6 = new Musica("Another Bad Day","musica do album MegaDeath", Duration.ofSeconds(217),"rock");
        List<Musica>musicasMD = new ArrayList<>();
        musicasMD.addAll(Arrays.asList(m2,m3,m4,m5,m6));

        for (Musica musica: musicasMD){
            musica.addArtista(a3);
        }
        m1.addArtista(a1);
        m1.addArtista(a2);


        musicaRepository.saveAll(Arrays.asList(m1,m2,m3,m4,m5,m6));
        album1.addMusica(m1);
        for (Musica musica: musicasMD){
            album2.addMusica(musica);
        }
        albumRepository.saveAll(Arrays.asList(album1,album2));
        User u1 = new User(null, "vitor souza", "vitor@gmail.com", passwordService.hashPassword("1234567"), LocalDate.of(2009,1,6), "jinka070");
        User u2 = new User(null, "otavio ramos", "tavio@gmail.com", passwordService.hashPassword("1234567"), LocalDate.of(2008,7,14),null);
        User u3 = new User(null, "brenno", "brenno@gmail.com", passwordService.hashPassword("1234567"), LocalDate.of(2008,10,7),null);
        User u4 = new User(null, "vitor souza", "vitor@gmail.com", passwordService.hashPassword("1234567"), LocalDate.of(2009,1,6),null);

       List<User>users = new ArrayList<>();
       users.addAll(Arrays.asList(u1,u2,u3,u4));
       for (User user: users){
           userDataRepository.save(user.getData());
       }

        userRepository.saveAll(Arrays.asList(u1,u2,u3));
    }
}
