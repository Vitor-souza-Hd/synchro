package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.SearchResultDTO;
import org.example.synchro.importer.LastFmHistoryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class LastFmCatalogService {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    private final LastFmHistoryClient client;
    private final LastFmCatalogStore store;
    @Value("${lastfm.search.limit:5}")
    private int limit = 5;
    private record Matches(String type, List<Long> ids, boolean succeeded) {}
    private record Cached(Instant expires, Map<String, List<Long>> ids, int succeeded) {}
    private final Map<String, CompletableFuture<Cached>> searches = new LinkedHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> refreshes = new ConcurrentHashMap<>();
    private final Object catalogWrites = new Object();
    // A bounded pool keeps slow providers away from HTTP request threads.
    private final ExecutorService searchWorkers = new ThreadPoolExecutor(3, 3, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(30), task -> {
                Thread thread = new Thread(task, "lastfm-search");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public SearchResultDTO search(String query) {
        String term = query.strip();
        if (term.length() < 2) return new SearchResultDTO();
        String key = term.toLowerCase(Locale.ROOT);
        CompletableFuture<Cached> request;
        boolean start = false;
        synchronized (searches) {
            request = searches.get(key);
            if (request != null && request.isDone() && request.join().expires().isBefore(Instant.now())) {
                searches.remove(key);
                request = null;
            }
            if (request == null) {
                if (searches.size() >= 100) {
                    var oldest = searches.entrySet().stream().filter(entry -> entry.getValue().isDone()).findFirst();
                    oldest.ifPresent(entry -> searches.remove(entry.getKey()));
                }
                if (searches.size() >= 100) {
                    var local = store.local(term);
                    local.setSource("local");
                    local.setWarning("Exibindo os dados salvos. Tente a pesquisa novamente em instantes.");
                    return local;
                }
                request = new CompletableFuture<>();
                searches.put(key, request);
                start = true;
            }
        }
        if (start) startSearch(term, request);
        var cached = request.getNow(null);
        if (cached == null) {
            var result = store.local(term);
            result.setSource("local");
            result.setRefreshing(true);
            return result;
        }
        var result = cached.succeeded() == 0 ? store.local(term) : store.result(term, cached.ids());
        if (cached.succeeded() == 0) {
            result.setSource("local");
            result.setWarning("Last.fm indisponível. Exibindo os dados já salvos no Synchro.");
        } else if (cached.succeeded() < 3) {
            result.setWarning("Algumas consultas ao Last.fm estão indisponíveis. Exibindo os resultados disponíveis.");
        }
        return result;
    }

    private void startSearch(String term, CompletableFuture<Cached> request) {
        var tasks = new ArrayList<CompletableFuture<Matches>>();
        for (String type : List.of("track", "artist", "album")) {
            try {
                tasks.add(CompletableFuture.supplyAsync(() -> searchType(type, term), searchWorkers));
            } catch (RejectedExecutionException e) {
                tasks.add(CompletableFuture.completedFuture(new Matches(type, List.of(), false)));
            }
        }
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).thenRun(() -> {
            Map<String, List<Long>> ids = new LinkedHashMap<>();
            int succeeded = 0;
            for (var task : tasks) {
                var matches = task.join();
                ids.put(matches.type(), matches.ids());
                if (matches.succeeded()) succeeded++;
            }
            request.complete(new Cached(Instant.now().plusSeconds(succeeded == 3 ? 300 : 30), ids, succeeded));
        });
    }

    private Matches searchType(String type, String term) {
        var ids = new ArrayList<Long>();
        try {
            var result = client.search(type, term, Math.max(1, Math.min(10, limit)));
            for (var node : LastFmCatalogStore.nodes(result.path(type + "matches").path(type))) {
                if (!node.path("name").asText("").isBlank()) ids.add(save(type, node, false));
            }
            // Full metadata is refreshed when opening a record, not for every search hit.
            return new Matches(type, List.copyOf(ids), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) { /* Keep local results available when the provider fails. */ }
        return new Matches(type, List.copyOf(ids), false);
    }

    @PreDestroy
    public void stopSearchWorkers() { searchWorkers.shutdownNow(); }

    public void enrich(String type, long id) {
        if (!refresh(type, id)) return;
        var ref = store.reference(type, id);
        if (ref == null) return;
        if (ref.albumId() != null) refresh("album", ref.albumId());
        for (long artistId : ref.artistIds()) refresh("artist", artistId);
    }

    // Register the interaction immediately; external metadata is optional.
    public long register(String type, String name, String artist, String album, String albumArtist) {
        if (!Set.of("track", "album", "artist").contains(type == null ? "" : type)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Tipo de registro inválido.");
        }
        var node = JSON.createObjectNode().put("name", interactionName(name));
        if (!type.equals("artist")) node.put("artist", interactionName(artist));
        if (type.equals("track") && album != null && !album.isBlank()) {
            node.putObject("album").put("title", interactionName(album))
                    .put("artist", interactionName(albumArtist == null || albumArtist.isBlank() ? artist : albumArtist));
        }
        return save(type, node, false);
    }

    private String interactionName(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 255) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Informe um nome válido com até 255 caracteres.");
        }
        return value.strip().replaceAll("\\s+", " ");
    }

    public void persistDiscovery(String method, String body) {
        String type = switch (method) {
            case "track.getinfo" -> "track";
            case "album.getinfo" -> "album";
            case "artist.getinfo" -> "artist";
            default -> null;
        };
        if (type == null) return;
        try {
            var data = JSON.readTree(body);
            if (data == null || data.has("error")) return;
            var node = data.path(type);
            if (!node.path("name").asText("").isBlank()) save(type, node, true);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("O Last.fm retornou uma resposta inválida.", error);
        }
    }

    public String importRecommendedArtists(String body) {
        try {
            var data = JSON.readTree(body);
            var ranking = data.path(data.has("topartists") ? "topartists" : "artists");
            var rankedArtists = LastFmCatalogStore.nodes(ranking.path("artist"));
            var imported = JSON.createArrayNode();
            var names = new HashSet<String>();
            for (var node : rankedArtists) {
                var name = node.path("name").asText("").strip();
                if (name.isEmpty() || name.length() > 255
                        || !names.add(org.example.synchro.entities.CatalogName.key(name))) continue;
                long id = save("artist", node, false);
                var artist = (com.fasterxml.jackson.databind.node.ObjectNode) node;
                artist.put("catalogId", id);
                imported.add(artist);
                if (imported.size() == 6) break;
            }
            if (ranking instanceof com.fasterxml.jackson.databind.node.ObjectNode chart) {
                chart.set("artist", imported);
            }
            return data.toString();
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("O Last.fm retornou uma resposta inválida.", error);
        }
    }

    public long importPopularAlbum(com.fasterxml.jackson.databind.JsonNode album) {
        return save("album", album, true);
    }

    private boolean refresh(String type, long id) {
        String key = type + ':' + id;
        var pending = new CompletableFuture<Boolean>();
        var existing = refreshes.putIfAbsent(key, pending);
        if (existing != null) return existing.join();
        try {
            boolean refreshed = refreshRecord(type, id);
            pending.complete(refreshed);
            return refreshed;
        } catch (RuntimeException e) {
            pending.completeExceptionally(e);
            throw e;
        } finally {
            refreshes.remove(key, pending);
        }
    }

    private boolean refreshRecord(String type, long id) {
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
        // Only short database writes share a lock; provider requests never hold it.
        synchronized (catalogWrites) {
            try { return store.save(type, node, complete); }
            catch (DataIntegrityViolationException concurrentImport) { return store.save(type, node, complete); }
        }
    }
}
