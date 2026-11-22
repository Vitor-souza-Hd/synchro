package org.example.synchro.services;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Keeps recent lookups bounded while continuing to share active requests after eviction. */
final class BoundedLookupCache<K, V> {
    private final Map<K, Mono<V>> recentLookups = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<K, ActiveLookup<V>> activeLookups = new HashMap<>();
    private final int maxEntries;
    private final Function<V, Duration> lifetime;

    BoundedLookupCache(int maxEntries, Function<V, Duration> lifetime) {
        this.maxEntries = maxEntries;
        this.lifetime = lifetime;
    }

    Mono<V> get(K key, Supplier<Mono<V>> load) {
        return Mono.using(() -> acquire(key, load), active -> active.lookup, active -> release(key, active));
    }

    private synchronized ActiveLookup<V> acquire(K key, Supplier<Mono<V>> load) {
        var active = activeLookups.computeIfAbsent(key, lookupKey -> new ActiveLookup<>(
                recentLookups.computeIfAbsent(lookupKey, ignored -> cacheResults(load))));
        active.subscribers++;
        recentLookups.put(key, active.lookup);
        if (recentLookups.size() > maxEntries) {
            var eldest = recentLookups.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
        return active;
    }

    private synchronized void release(K key, ActiveLookup<V> active) {
        if (--active.subscribers == 0) activeLookups.remove(key, active);
    }

    private Mono<V> cacheResults(Supplier<Mono<V>> load) {
        // Check expiry on reads so an evicted result has no timer keeping it alive.
        return Mono.defer(load)
                .map(value -> new CachedValue<>(value, System.nanoTime() + lifetime.apply(value).toNanos()))
                .cacheInvalidateIf(cached -> System.nanoTime() - cached.expiresAt() >= 0)
                .map(CachedValue::value);
    }

    private static final class ActiveLookup<V> {
        private final Mono<V> lookup;
        private int subscribers;

        private ActiveLookup(Mono<V> lookup) {
            this.lookup = lookup;
        }
    }

    private record CachedValue<V>(V value, long expiresAt) {}
}
