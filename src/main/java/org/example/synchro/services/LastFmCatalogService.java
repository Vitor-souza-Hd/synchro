package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.SearchResultDTO;
import org.example.synchro.importer.LastFmHistoryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LastFmCatalogService {
    private final LastFmHistoryClient client;
    private final LastFmCatalogStore store;
    @Value("${lastfm.search.limit:5}")
    private int limit = 5;
    private record Cached(Instant expires, Map<String, List<Long>> ids) {}
    private final Map<String, Cached> searches = new LinkedHashMap<>();

    // Serialize catalog imports to avoid competing name-based inserts and excessive API traffic.
    // Network calls happen outside database transactions.
    public synchronized SearchResultDTO search(String query) {
        String term = query.strip();
        if (term.length() < 2) return new SearchResultDTO();
        String key = term.toLowerCase(Locale.ROOT);
        Cached cached = searches.get(key);
        if (cached != null && cached.expires().isAfter(Instant.now())) return store.result(term, cached.ids());
        Map<String, List<Long>> ids = new LinkedHashMap<>();
        boolean failed = false;
        int searched = 0;
        for (String type : List.of("track", "artist", "album")) {
            var matches = new ArrayList<Long>();
            ids.put(type, matches);
            try {
                var result = client.search(type, term, Math.max(1, Math.min(10, limit)));
                searched++;
                for (var node : LastFmCatalogStore.nodes(result.path(type + "matches").path(type))) {
                    if (node.path("name").asText("").isBlank()) continue;
                    matches.add(save(type, node, false));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed = true;
                break;
            } catch (Exception e) { failed = true; }
        }
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(35);
        outer: for (var group : ids.entrySet()) {
            for (long id : group.getValue()) {
                if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) { failed = true; break outer; }
                if (!refresh(group.getKey(), id)) { failed = true; break outer; }
            }
        }
        SearchResultDTO result;
        if (searched == 0) {
            result = store.local(term);
            result.setSource("local");
            result.setWarning("Last.fm indisponível. Exibindo os dados já salvos no Synchro.");
        } else {
            result = store.result(term, ids);
            if (failed) result.setWarning("Resultados salvos. Alguns detalhes do Last.fm estão indisponíveis; tente novamente para completá-los.");
            else {
                if (searches.size() >= 100) searches.remove(searches.keySet().iterator().next());
                searches.put(key, new Cached(Instant.now().plusSeconds(300), ids));
            }
        }
        return result;
    }

    public synchronized void enrich(String type, long id) {
        if (!refresh(type, id)) return;
        var ref = store.reference(type, id);
        if (ref == null) return;
        if (ref.albumId() != null) refresh("album", ref.albumId());
        for (long artistId : ref.artistIds()) refresh("artist", artistId);
    }

    private boolean refresh(String type, long id) {
        var ref = store.reference(type, id);
        if (ref == null || ref.updatedAt() != null && ref.updatedAt().isAfter(Instant.now().minusSeconds(86400))) return true;
        if (!type.equals("artist") && ref.artist().isBlank()) return true;
        try {
            var detail = client.info(type, ref.name(), ref.artist());
            if (!detail.path("name").asText("").isBlank()) {
                // Preserve catalog identity when Last.fm autocorrects a name.
                var saved = detail.deepCopy();
                if (!detail.path("name").asText().equalsIgnoreCase(ref.name())) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) saved).put("name", ref.name());
                    ((com.fasterxml.jackson.databind.node.ObjectNode) saved).put("lastfmCorrectedName", detail.path("name").asText());
                }
                save(type, saved, true);
            }
            return true;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        catch (Exception e) { return false; }
    }

    private long save(String type, com.fasterxml.jackson.databind.JsonNode node, boolean complete) {
        try { return store.save(type, node, complete); }
        catch (DataIntegrityViolationException concurrentImport) { return store.save(type, node, complete); }
    }
}
