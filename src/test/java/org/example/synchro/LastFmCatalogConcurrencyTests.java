package org.example.synchro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.synchro.dto.SearchResultDTO;
import org.example.synchro.importer.LastFmHistoryClient;
import org.example.synchro.services.LastFmCatalogService;
import org.example.synchro.services.LastFmCatalogStore;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LastFmCatalogConcurrencyTests {
    @Test
    void savedResultsRemainAvailableWhileThreeProviderQueriesRunInParallel() throws Exception {
        var client = mock(LastFmHistoryClient.class);
        var store = mock(LastFmCatalogStore.class);
        var service = new LastFmCatalogService(client, store);
        var started = new CountDownLatch(3);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(store.local(anyString())).thenAnswer(call -> {
            var local = new SearchResultDTO();
            local.setUsers(List.of(new org.example.synchro.dto.SearchUserDTO(7L, "saved-user")));
            return local;
        });
        when(store.result(anyString(), anyMap())).thenReturn(new SearchResultDTO());
        when(client.search(anyString(), anyString(), anyInt())).thenAnswer(call -> {
            calls.incrementAndGet(); started.countDown();
            if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Test release missing");
            return new ObjectMapper().createObjectNode();
        });
        try {
            var first = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> service.search("radio"));
            assertTrue(first.isRefreshing());
            assertEquals("saved-user", first.getUsers().get(0).username());
            assertTrue(started.await(1, TimeUnit.SECONDS), "All three categories must start before any returns");
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                assertTrue(service.search(" RADIO ").isRefreshing());
                assertTrue(service.search("other").isRefreshing());
            });
            assertEquals(3, calls.get(), "Repeated term shares its in-flight queries");
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (service.search("radio").isRefreshing() || service.search("other").isRefreshing()) {
                assertTrue(System.nanoTime() < deadline);
                Thread.sleep(10);
            }
            assertEquals(6, calls.get());
            service.search("radio");
            assertEquals(6, calls.get(), "Completed search uses cache");
            verify(client, never()).info(anyString(), anyString(), anyString());
        } finally { release.countDown(); service.stopSearchWorkers(); }
    }

    @Test
    void slowMetadataForOneRecordDoesNotBlockAnotherRecordOrLocalSearch() throws Exception {
        var client = mock(LastFmHistoryClient.class);
        var store = mock(LastFmCatalogStore.class);
        var service = new LastFmCatalogService(client, store);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        when(store.reference(eq("track"), anyLong())).thenAnswer(call ->
                new LastFmCatalogStore.Reference("Track" + call.getArgument(1), "Artist", null, null, List.of()));
        when(store.local(anyString())).thenReturn(new SearchResultDTO());
        when(client.search(anyString(), anyString(), anyInt())).thenReturn(new ObjectMapper().createObjectNode());
        when(client.info(eq("track"), anyString(), eq("Artist"))).thenAnswer(call -> {
            if (call.getArgument(1).equals("Track1")) {
                started.countDown();
                if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Test release missing");
            }
            return new ObjectMapper().createObjectNode().put("name", (String) call.getArgument(1));
        });
        try {
            var slow = workers.submit(() -> service.enrich("track", 1));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            workers.submit(() -> service.enrich("track", 2)).get(1, TimeUnit.SECONDS);
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> service.search("local"));
            release.countDown(); slow.get(1, TimeUnit.SECONDS);
        } finally { release.countDown(); workers.shutdownNow(); service.stopSearchWorkers(); }
    }
}
