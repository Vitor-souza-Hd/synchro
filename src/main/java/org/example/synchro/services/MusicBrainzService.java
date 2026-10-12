
package org.example.synchro.services;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class MusicBrainzService {

    private final WebClient musicBrainzClient;

    public MusicBrainzService() {

        this.musicBrainzClient = WebClient.builder()
                .baseUrl("https://musicbrainz.org/ws/2")
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "SynchroTCC/0.1 (https://github.com/Vitor-souza-Hd/synchro)"
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    // =========================================
    // PESQUISAR ÁLBUNS NO MUSICBRAINZ
    // =========================================

    public Mono<String> searchAlbums(
            String artistName,
            String albumName
    ) {

        String artist = limparPesquisa(artistName);
        String album = limparPesquisa(albumName);

        String query =
                "releasegroup:\"" + album + "\"" +
                        " AND artist:\"" + artist + "\"" +
                        " AND primarytype:album";

        return musicBrainzClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/release-group/")
                        .queryParam("query", query)
                        .queryParam("fmt", "json")
                        .queryParam("limit", 10)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15));
    }

    // =========================================
    // LIMPAR TERMOS DE PESQUISA
    // =========================================

    private String limparPesquisa(String valor) {

        if (valor == null) {
            return "";
        }

        String texto = valor
                .replace("\\", " ")
                .replace("\"", " ")
                .trim();

        if (texto.length() > 120) {
            texto = texto.substring(0, 120);
        }

        return texto;
    }
}
