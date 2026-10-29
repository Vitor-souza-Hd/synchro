package org.example.synchro.config;

import org.example.synchro.importer.LastFmHistoryClient;
import org.example.synchro.importer.LastFmHistoryImporter;
import org.example.synchro.importer.LastFmImportStore;
import org.example.synchro.importer.LastFmGenreImporter;
import org.example.synchro.importer.LastFmGenreStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test & !lastfm-import")
@ConditionalOnProperty(name = "lastfm.import.on-startup", havingValue = "true")
@Import({LastFmHistoryClient.class, LastFmHistoryImporter.class, LastFmImportStore.class,
        LastFmGenreImporter.class, LastFmGenreStore.class})
public class LastFmStartupImportConfig {
    private static final Logger log = LoggerFactory.getLogger(LastFmStartupImportConfig.class);

    @Bean
    public CommandLineRunner importLastFmOnStartup(LastFmHistoryImporter importer,
            @Value("${lastfm.import.username:jinka070}") String username) {
        return args -> {
            try {
                importer.run(username);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Importação Last.fm interrompida. Será retomada na próxima inicialização.");
            } catch (Exception e) {
                log.warn("Não foi possível atualizar o Last.fm. Dados salvos continuam disponíveis; "
                        + "a importação será retomada na próxima inicialização. Motivo: {}", e.getMessage());
            }
        };
    }
}
