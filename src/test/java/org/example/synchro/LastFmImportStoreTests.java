package org.example.synchro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.importer.LastFmImportStore;
import org.example.synchro.importer.CatalogNameMigration;
import org.example.synchro.entities.Artista;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.example.synchro.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.junit.jupiter.api.Assertions.*;

class LastFmImportStoreTests {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.example.synchro.entities")
    @EnableJpaRepositories("org.example.synchro.repositories")
    @Import({LastFmImportStore.class, CatalogNameMigration.class})
    static class DatabaseConfig {}

    @Test
    void savesHistoryAtomicallyAndUsesUniqueCatalogNamesAcrossArtists() throws Exception {
        try (var context = new SpringApplicationBuilder(DatabaseConfig.class)
                .profiles("lastfm-import")
                .run("--spring.datasource.url=jdbc:h2:mem:import-test;DB_CLOSE_DELAY=-1",
                        "--spring.jpa.hibernate.ddl-auto=create-drop")) {
            var store = context.getBean(LastFmImportStore.class);
            var states = context.getBean(LastFmImportStateRepository.class);
            var plays = context.getBean(LastFmScrobbleRepository.class);
            var mapper = new ObjectMapper();
            var initial = store.begin("jinka070");
            String row = "{\"name\":\"Song\",\"artist\":{\"name\":\"Artist A\"},\"album\":{\"#text\":\"Album\"},\"date\":{\"uts\":\"100\"}}";
            var first = mapper.readTree("{\"@attr\":{\"page\":1,\"totalPages\":2},\"track\":[" + row + "," + row + "]}");
            store.savePage("jinka070", first);
            assertEquals(2, plays.count()); // identical timestamps are separate listens
            assertEquals(2, store.begin("jinka070").getNextPage());
            assertEquals(initial.getToTimestamp(), store.begin("jinka070").getToTimestamp());
            assertThrows(IllegalStateException.class, () -> store.savePage("jinka070", first));
            assertEquals(2, plays.count());
            var bad = mapper.readTree("{\"@attr\":{\"page\":2,\"totalPages\":2},\"track\":[" + row.replace("Artist A", "Artist B") + ",{\"name\":\"Invalid\"}]}");
            assertThrows(IllegalStateException.class, () -> store.savePage("jinka070", bad));
            assertEquals(2, plays.count());
            assertEquals(1, context.getBean(ArtistaRepository.class).count());
            assertEquals(2, states.findById("jinka070").orElseThrow().getNextPage());
            var second = mapper.readTree("{\"@attr\":{\"page\":2,\"totalPages\":2},\"track\":" + row.replace("Artist A", "Artist B").replace("Song", " song ").replace("Album", " ALBUM ") + "}");
            store.savePage("jinka070", second);
            assertTrue(states.findById("jinka070").orElseThrow().isComplete());
            assertEquals(3, plays.count());
            assertEquals(1, context.getBean(AlbumRepository.class).count());
            assertEquals(1, context.getBean(MusicaRepository.class).count());
            var jdbc = context.getBean(JdbcTemplate.class);
            assertEquals(2, jdbc.queryForObject("select count(*) from musica_artista", Integer.class));
            assertEquals(2, jdbc.queryForObject("select count(*) from album_artistas", Integer.class));

            // Simulate rows created before unique names existed, with a scrobble pointing to them.
            jdbc.update("insert into artista(id,nome) values(99999,' ARTIST A ')");
            jdbc.update("insert into midia(id,titulo) values(100001,' ALBUM '),(100002,' SONG ')");
            jdbc.update("insert into album(id) values(100001)");
            jdbc.update("insert into musica(id,album_id) values(100002,100001)");
            jdbc.update("insert into musica_artista(musica_id,artista_id) values(100002,99999)");
            jdbc.update("insert into album_artistas(album_id,artista_id) values(100001,99999)");
            jdbc.update("update last_fm_scrobble set musica_id=100002,album_id=100001,artista_id=99999");
            context.getBean(CatalogNameMigration.class).run();
            assertEquals(2, context.getBean(ArtistaRepository.class).count());
            assertEquals(1, context.getBean(AlbumRepository.class).count());
            assertEquals(1, context.getBean(MusicaRepository.class).count());
            assertEquals(3, plays.count());
            assertEquals(0, jdbc.queryForObject("select count(*) from last_fm_scrobble where musica_id=100002 or album_id=100001 or artista_id=99999", Integer.class));
            context.getBean(CatalogNameMigration.class).run(); // repeatable
            assertEquals(3, plays.count());
            assertThrows(DataIntegrityViolationException.class, () -> context.getBean(ArtistaRepository.class)
                    .saveAndFlush(new Artista(" artist a ", null, null)));
            assertEquals(initial.getToTimestamp(), store.begin("jinka070").getFromTimestamp());
        }
    }
}
