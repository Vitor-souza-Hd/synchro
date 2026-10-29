package org.example.synchro.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;

public class LastFmGenreImporter {
    private static final Logger log = LoggerFactory.getLogger(LastFmGenreImporter.class);
    private final LastFmHistoryClient client;
    private final LastFmGenreStore store;
    @Value("${lastfm.import.genres.enabled:true}")
    private boolean enabled = true;

    public LastFmGenreImporter(LastFmHistoryClient client, LastFmGenreStore store) {
        this.client = client;
        this.store = store;
    }

    public void run(String username) throws Exception {
        if (!enabled) return;
        long afterId = 0;
        int checked = 0;
        int enriched = 0;
        var retryBefore = Instant.now().minus(7, ChronoUnit.DAYS);
        while (true) {
            var batch = store.candidates(afterId, retryBefore, username);
            if (batch.isEmpty()) break;
            for (var track : batch) {
                var genres = new LinkedHashSet<String>();
                for (String artist : track.artists()) {
                    genres.addAll(LastFmGenres.fromTags(client.trackTags(artist, track.title())));
                    Thread.sleep(350);
                }
                store.save(track.id(), genres.stream().limit(3).toList());
                afterId = track.id();
                checked++;
                if (!genres.isEmpty()) enriched++;
                if (checked % 25 == 0) log.info("Last.fm gêneros {}: {} músicas consultadas; {} preenchidas", username, checked, enriched);
            }
        }
        log.info("Last.fm gêneros {}: concluído; {} músicas consultadas; {} preenchidas", username, checked, enriched);
    }
}
