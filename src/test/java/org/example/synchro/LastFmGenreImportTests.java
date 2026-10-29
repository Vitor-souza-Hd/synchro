package org.example.synchro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.importer.*;
import org.example.synchro.repositories.MusicaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LastFmGenreImportTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.example.synchro.entities")
    @EnableJpaRepositories("org.example.synchro.repositories")
    @Import({LastFmImportStore.class, LastFmGenreStore.class})
    static class DatabaseConfig {}

    @Test
    void filtersAndNormalizesMusicStylesByTagPopularity() throws Exception {
        var tags = mapper.readTree("""
                {"tag":[{"name":"seen live","count":100},{"name":"HIP-HOP","count":80},
                {"name":"rock","count":90},{"name":"hiphop","count":70},
                {"name":"favorites","count":60},{"name":"rnb","count":50},{"name":"jazz","count":40}]}
                """);
        assertEquals(List.of("rock", "hip hop", "r&b"), LastFmGenres.fromTags(tags));
        assertEquals(List.of("mpb"), LastFmGenres.fromTags(mapper.readTree("{\"tag\":{\"name\":\"Música Popular Brasileira\"}}")));
        assertTrue(LastFmGenres.fromTags(mapper.readTree("{}")).isEmpty());
    }

    @Test
    void backfillsExistingHistoryAndResumesWithoutRepeatingSuccessfulQueries() throws Exception {
        try (var context = new SpringApplicationBuilder(DatabaseConfig.class).run(
                "--spring.profiles.active=lastfm-import", "--spring.datasource.url=jdbc:h2:mem:genre-test",
                "--spring.jpa.hibernate.ddl-auto=create-drop")) {
            var history = context.getBean(LastFmImportStore.class);
            var store = context.getBean(LastFmGenreStore.class);
            var tracks = context.getBean(MusicaRepository.class);
            history.begin("jinka070");
            var rows = new ArrayList<Map<String, Object>>();
            for (String title : List.of("A", "B", "C", "D")) rows.add(Map.of("name", title,
                    "artist", Map.of("name", "Artist"), "date", Map.of("uts", "100")));
            history.savePage("jinka070", mapper.valueToTree(Map.of("@attr", Map.of("page", 1, "totalPages", 1), "track", rows)));
            var calls = new HashMap<String, Integer>();
            var client = new LastFmHistoryClient(null) {
                @Override
                public JsonNode page(String user, long from, long to, int page) {
                    return mapper.valueToTree(Map.of("@attr", Map.of("page", 1, "totalPages", 0), "track", List.of()));
                }
                @Override
                public JsonNode trackTags(String artist, String title) throws Exception {
                    calls.merge(title, 1, Integer::sum);
                    if (title.equals("C") && calls.get(title) == 1) throw new IllegalStateException("Temporary failure");
                    return mapper.readTree(title.equals("B") ? "{\"tag\":[{\"name\":\"favorites\"}]}"
                            : "{\"tag\":[{\"name\":\"rock\"}]}");
                }
            };
            var importer = new LastFmHistoryImporter(client, history, new LastFmGenreImporter(client, store));
            assertThrows(IllegalStateException.class, () -> importer.run("jinka070"));
            assertEquals("rock", tracks.findByTitulo("A").getGenero());
            assertNull(tracks.findByTitulo("B").getGenero());
            assertNotNull(tracks.findByTitulo("B").getGeneroConsultadoEm());
            assertNull(tracks.findByTitulo("C").getGeneroConsultadoEm());
            var manual = tracks.findByTitulo("D");
            manual.setGenero("manual genre");
            tracks.saveAndFlush(manual);
            importer.run("jinka070");
            assertEquals("rock", tracks.findByTitulo("C").getGenero());
            assertEquals("manual genre", tracks.findByTitulo("D").getGenero());
            assertEquals(Map.of("A", 1, "B", 1, "C", 2), calls);
            assertTrue(store.candidates(0, java.time.Instant.now().minusSeconds(7 * 86400), "jinka070").isEmpty());
            // Negative results become eligible again after the retry interval.
            assertEquals(List.of("B"), store.candidates(0, java.time.Instant.now().plusSeconds(1), "jinka070").stream()
                    .map(LastFmGenreStore.Candidate::title).toList());
        }
    }
}
