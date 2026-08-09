package org.example.synchro.config;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.*;
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
@RequiredArgsConstructor
public class TestConfig implements CommandLineRunner {

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final MusicaRepository  musicaRepository;

    private final ArtistaRepository artistaRepository;

    private final AlbumRepository albumRepository;

    private final UserDataRepository userDataRepository;

    private final LastFmSessionRepository lastFmSessionRepository;

    private final ReviewRepository reviewRepository;

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
        Musica m7 = new Musica("tomodachi","single do yunglixo feat. SHO-SENSEI!!", Duration.ofSeconds(156),"sla ksksk");
        List<Musica>musicasMD = new ArrayList<>();
        musicasMD.addAll(Arrays.asList(m2,m3,m4,m5,m6));

        for (Musica musica: musicasMD){
            musica.addArtista(a3);
        }
        m1.addArtista(a1);
        m1.addArtista(a2);
        m7.addArtista(a1);

        musicaRepository.saveAll(Arrays.asList(m1,m2,m3,m4,m5,m6,m7));
        album1.addMusica(m1);
        for (Musica musica: musicasMD){
            album2.addMusica(musica);
        }
        albumRepository.saveAll(Arrays.asList(album1,album2));

        LastFmSession lastFmSession1 = new LastFmSession(null,null,0);
        LastFmSession lastFmSession2 = new LastFmSession(null,null,0);
        LastFmSession lastFmSession3 = new LastFmSession(null,null,0);
        LastFmSession lastFmSession4 = new LastFmSession(null,null,0);

        List<LastFmSession> lastFmSessionsMD = new ArrayList<>();
        lastFmSessionsMD.addAll(Arrays.asList(lastFmSession1,lastFmSession2,lastFmSession3,lastFmSession4));
        lastFmSessionRepository.saveAll(lastFmSessionsMD);


        User u1 = new User(null, "vitor souza", "vitor@gmail.com", passwordService.hashPassword("1234567"), LocalDate.of(2009,1,6), "jinka070");
        User u2 = new User(null, "otavio ramos", "tavio@gmail.com", passwordService.hashPassword("1234567"), LocalDate.of(2008,7,14),null);
        User u3 = new User(null, "brenno", "brenno@gmail.com", passwordService.hashPassword("1234567"), LocalDate.of(2008,10,7),null);
        User u4 = new User(null, "vitor souza", "vitor@gmail.com", passwordService.hashPassword("1234567"), LocalDate.of(2009,1,6),null);

       List<User>users = new ArrayList<>();
       users.addAll(Arrays.asList(u1,u2,u3,u4));
       int i = 0;
       for (User user: users){
           userDataRepository.save(user.getData());
           lastFmSessionsMD.get(i).setSynchroUser(user);
           i++;
       }

        userRepository.saveAll(Arrays.asList(u1,u2,u3));

       Review r1 = new Review(5,"muito louco slk",m7,u1.getData());
       Review r2 = new Review(10, "nunca vi esse album", album1,u2.getData());
       reviewRepository.saveAll(Arrays.asList(r1,r2));
    }
}
