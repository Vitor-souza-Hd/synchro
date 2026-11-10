package org.example.synchro;

import org.example.synchro.services.MusicBrainzRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class MusicBrainzRateLimiterTests {
    @Test
    void limitsTheQueueAndCancelledRequestsDoNotKeepTheirSlots() throws Exception {
        var limiter = new MusicBrainzRateLimiter();
        limiter.aguardarPermissao().block(Duration.ofSeconds(2));
        var cancelled = new ArrayList<Disposable>();
        try {
            for (int i = 0; i < 4; i++) cancelled.add(limiter.aguardarPermissao().subscribe());
            var rejected = assertThrows(ResponseStatusException.class,
                    () -> limiter.aguardarPermissao().block(Duration.ofMillis(500)));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, rejected.getStatusCode());
        } finally {
            cancelled.forEach(Disposable::dispose);
        }
        var next = limiter.aguardarPermissao().toFuture();
        assertThrows(TimeoutException.class, () -> next.get(100, TimeUnit.MILLISECONDS));
        next.get(2, TimeUnit.SECONDS);
    }

    @Test
    void sharedSearchCancelsTheQueueWhenItsLastSubscriberLeaves() {
        var limiter = org.mockito.Mockito.mock(MusicBrainzRateLimiter.class);
        var subscriptions = new java.util.concurrent.atomic.AtomicInteger();
        var cancellations = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.when(limiter.aguardarPermissao()).thenReturn(reactor.core.publisher.Mono.defer(() -> {
            subscriptions.incrementAndGet();
            return reactor.core.publisher.Mono.<Void>never().doOnCancel(cancellations::incrementAndGet);
        }));
        var service = new org.example.synchro.services.MusicBrainzService(limiter, Duration.ofSeconds(5));
        var first = service.searchAlbums("Artist", "Album").subscribe();
        var second = service.searchAlbums("Artist", "Album").subscribe();
        assertEquals(1, subscriptions.get());
        first.dispose();
        assertEquals(0, cancellations.get());
        second.dispose();
        assertEquals(1, cancellations.get());
    }
}
