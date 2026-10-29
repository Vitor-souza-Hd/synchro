package org.example.synchro;

import org.example.synchro.config.LastFmStartupImportConfig;
import org.example.synchro.importer.LastFmHistoryImporter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LastFmStartupImportTests {
    @Test
    void startupImportsConfiguredUser() throws Exception {
        var importedUser = new AtomicReference<String>();
        var importer = new LastFmHistoryImporter(null, null, null) {
            @Override
            public void run(String username) { importedUser.set(username); }
        };
        new LastFmStartupImportConfig().importLastFmOnStartup(importer, "jinka070").run();
        assertEquals("jinka070", importedUser.get());
    }

    @Test
    void unavailableLastFmDoesNotPreventApplicationStartup() {
        var importer = new LastFmHistoryImporter(null, null, null) {
            @Override
            public void run(String username) { throw new IllegalStateException("Last.fm indisponível"); }
        };
        assertDoesNotThrow(() -> new LastFmStartupImportConfig()
                .importLastFmOnStartup(importer, "jinka070").run());
    }
}
