package org.example.synchro.importer;

import org.example.synchro.config.LastFmConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Dedicated command: does not start the website, JWT configuration or demo data. */
@Configuration
@Profile("lastfm-import")
@EnableAutoConfiguration
@EntityScan("org.example.synchro.entities")
@EnableJpaRepositories("org.example.synchro.repositories")
@EnableConfigurationProperties(LastFmConfig.class)
@Import({LastFmImportRunner.class, LastFmHistoryImporter.class, LastFmImportStore.class, LastFmHistoryClient.class, CatalogNameMigration.class})
public class LastFmImportApplication {
    public static void main(String[] args) {
        try (var context = SpringApplication.run(LastFmImportApplication.class, args)) {
            // Closing the context flushes and closes the database after the runner finishes.
        }
    }
}
