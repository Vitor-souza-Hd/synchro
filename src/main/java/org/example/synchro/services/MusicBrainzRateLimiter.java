package org.example.synchro.services;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

/** Shares the MusicBrainz request budget across the metadata and artwork clients. */
@Component
public class MusicBrainzRateLimiter {
    private static final long INTERVAL_NANOS = Duration.ofMillis(1100).toNanos();
    private static final int MAX_WAITING = 4;

    private final Deque<Sinks.One<Void>> waiting = new ArrayDeque<>();
    private long nextRequest = System.nanoTime();
    private boolean scheduled;

    public Mono<Void> aguardarPermissao() {
        return Mono.defer(() -> {
            var permit = Sinks.<Void>one();
            return permit.asMono().doOnSubscribe(subscription -> enqueue(permit))
                    .doOnCancel(() -> cancel(permit));
        });
    }

    private synchronized void enqueue(Sinks.One<Void> permit) {
        if (waiting.size() >= MAX_WAITING) {
            permit.tryEmitError(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "MusicBrainz está ocupado. Tente novamente em instantes."));
            return;
        }
        waiting.addLast(permit);
        schedule();
    }

    private synchronized void cancel(Sinks.One<Void> permit) {
        waiting.remove(permit);
    }

    private void schedule() {
        if (scheduled || waiting.isEmpty()) return;
        scheduled = true;
        long delay = Math.max(0, nextRequest - System.nanoTime());
        Schedulers.parallel().schedule(this::releaseNext, delay, TimeUnit.NANOSECONDS);
    }

    private void releaseNext() {
        Sinks.One<Void> permit;
        synchronized (this) {
            scheduled = false;
            permit = waiting.pollFirst();
            if (permit == null) return;
            nextRequest = System.nanoTime() + INTERVAL_NANOS;
            schedule();
        }
        permit.tryEmitEmpty();
    }
}
