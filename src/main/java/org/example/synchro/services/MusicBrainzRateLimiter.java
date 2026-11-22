package org.example.synchro.services;

import org.example.synchro.services.exception.MusicBrainzUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Shares the MusicBrainz request budget across the metadata and artwork clients. */
@Component
public class MusicBrainzRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(MusicBrainzRateLimiter.class);
    private static final Duration DEFAULT_PAUSE = Duration.ofSeconds(10);
    private static final long INTERVAL_NANOS = Duration.ofMillis(1100).toNanos();
    private static final int MAX_WAITING = 4;

    private final Deque<Sinks.One<Void>> waiting = new ArrayDeque<>();
    private long nextRequest = System.nanoTime();
    private Instant pausedUntil = Instant.EPOCH;
    private boolean scheduled;

    public Mono<Void> aguardarPermissao() {
        return Mono.defer(() -> {
            var permit = Sinks.<Void>one();
            return permit.asMono().doOnSubscribe(subscription -> enqueue(permit))
                    .doOnCancel(() -> cancel(permit));
        });
    }

    public RuntimeException tratarFalha(WebClientResponseException error) {
        int status = error.getStatusCode().value();
        if (status != 429 && status != 503) return error;

        List<Sinks.One<Void>> queued;
        MusicBrainzUnavailableException failure;
        synchronized (this) {
            Instant now = Instant.now();
            Instant retryAt = RetryAfter.deadline(error.getHeaders().getFirst(HttpHeaders.RETRY_AFTER), now, DEFAULT_PAUSE);
            boolean justPaused = !now.isBefore(pausedUntil);
            if (retryAt.isAfter(pausedUntil)) pausedUntil = retryAt;
            failure = unavailable(now);
            if (justPaused) {
                log.warn("MusicBrainz retornou HTTP {}. Consultas pausadas por {} s.", status, failure.getRetryAfterSeconds());
            }

            queued = new ArrayList<>(waiting);
            waiting.clear();
        }
        queued.forEach(permit -> permit.tryEmitError(new MusicBrainzUnavailableException(failure.getRetryAfterSeconds())));
        return failure;
    }

    private MusicBrainzUnavailableException unavailable(Instant now) {
        return new MusicBrainzUnavailableException(RetryAfter.secondsRemaining(now, pausedUntil));
    }

    private synchronized void enqueue(Sinks.One<Void> permit) {
        Instant now = Instant.now();
        if (now.isBefore(pausedUntil)) {
            permit.tryEmitError(unavailable(now));
            return;
        }
        if (waiting.size() >= MAX_WAITING) {
            permit.tryEmitError(new MusicBrainzUnavailableException(1));
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
