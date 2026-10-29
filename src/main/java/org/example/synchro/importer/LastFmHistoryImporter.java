package org.example.synchro.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LastFmHistoryImporter {
    private static final Logger log = LoggerFactory.getLogger(LastFmHistoryImporter.class);
    private final LastFmHistoryClient client;
    private final LastFmImportStore store;
    private final LastFmGenreImporter genres;

    public LastFmHistoryImporter(LastFmHistoryClient client, LastFmImportStore store, LastFmGenreImporter genres) {
        this.client = client;
        this.store = store;
        this.genres = genres;
    }

    public void run(String username) throws Exception {
        if (username == null || !username.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Usuário Last.fm inválido.");
        }
        username = username.toLowerCase(java.util.Locale.ROOT);
        var state = store.begin(username);
        while (!state.isComplete()) {
            var response = client.page(username, state.getFromTimestamp(), state.getToTimestamp(), state.getNextPage());
            state = store.savePage(username, response);
            log.info("Last.fm {}: página {} salva; {} reproduções novas; concluído={}",
                    username, state.getNextPage() - 1, state.getImported(), state.isComplete());
            if (!state.isComplete()) Thread.sleep(350);
        }
        genres.run(username);
    }
}
