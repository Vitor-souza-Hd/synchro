package org.example.synchro.importer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import java.util.Locale;

public class LastFmImportRunner implements CommandLineRunner {
    private final LastFmHistoryImporter importer;
    @Value("${lastfm.import.username:jinka070}")
    private String username;

    public LastFmImportRunner(LastFmHistoryImporter importer) { this.importer = importer; }

    @Override
    public void run(String... args) throws Exception {
        if (!username.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Usuário Last.fm inválido.");
        importer.run(username.toLowerCase(Locale.ROOT));
    }
}
