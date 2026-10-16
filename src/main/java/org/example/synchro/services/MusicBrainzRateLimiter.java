package org.example.synchro.services;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Compartilha o intervalo minimo entre chamadas ao MusicBrainz
 * feitas pelos servicos desta instancia do Synchro.
 * Nao bloqueia as threads do servidor enquanto aguarda.
 */
@Component
public class MusicBrainzRateLimiter {

    private static final long INTERVALO_NANOS =
            Duration.ofMillis(1100).toNanos();

    private long proximoHorarioRequisicao = 0L;

    public Mono<Void> aguardarPermissao() {
        return Mono.defer(() -> {
            long espera;

            synchronized (this) {
                long agora = System.nanoTime();
                espera = Math.max(0L, proximoHorarioRequisicao - agora);
                proximoHorarioRequisicao = agora + espera + INTERVALO_NANOS;
            }

            if (espera == 0L) {
                return Mono.empty();
            }

            return Mono.delay(Duration.ofNanos(espera)).then();
        });
    }
}
