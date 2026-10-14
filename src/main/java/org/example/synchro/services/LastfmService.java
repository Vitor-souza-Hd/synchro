package org.example.synchro.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.synchro.config.LastFmConfig;
import org.example.synchro.dto.lastFmDtos.LastFmSessionDto;
import org.example.synchro.dto.lastFmDtos.LastFmUserDto;
import org.example.synchro.entities.LastFmSession;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.LastFmSessionRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.InvalidTokenException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LastfmService {

    private final WebClient webClient;
    private final LastFmConfig config;
    private final CookieService cookieService;
    private final LastFmSessionRepository lastFmSessionRepository;
    private final UserRepository userRepository;


    // =========================================================
    // BUSCAR INFORMAÇÕES DO USUÁRIO LAST.FM
    // =========================================================

    public Mono<String> getUserInfo(String username) {

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("method", "user.getinfo")
                        .queryParam("user", username)
                        .queryParam("api_key", config.getApiKey())
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .bodyToMono(LastFmUserDto.class)
                .map(dto -> {

                    try {

                        ObjectMapper objectMapper =
                                new ObjectMapper();

                        return objectMapper
                                .writeValueAsString(dto);

                    } catch (Exception e) {

                        throw new RuntimeException(
                                "Erro ao converter dados do Last.fm para JSON",
                                e
                        );
                    }
                })
                .timeout(Duration.ofSeconds(15));
    }


    // =========================================================
    // BUSCAR DTO INTERNAMENTE
    // =========================================================

    private Mono<LastFmUserDto> getUserInfoDto(
            String username
    ) {

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("method", "user.getinfo")
                        .queryParam("user", username)
                        .queryParam("api_key", config.getApiKey())
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .bodyToMono(LastFmUserDto.class)
                .timeout(Duration.ofSeconds(15));
    }


    // =========================================================
    // CONTADORES
    // =========================================================

    public List<Integer> contadores(String username) {

        LastFmUserDto lfu =
                getUserInfoDto(username)
                        .block();

        if (lfu == null || lfu.getUser() == null) {

            throw new RuntimeException(
                    "Não foi possível obter os dados do usuário Last.fm"
            );
        }

        return Arrays.asList(
                Integer.parseInt(
                        lfu.getUser().getPlaycount()
                ),
                Integer.parseInt(
                        lfu.getUser().getArtistCount()
                )
        );
    }


    // =========================================================
    // OBTER TOKEN DO LAST.FM
    // =========================================================

    public Mono<String> getToken() {

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam(
                                "method",
                                "auth.gettoken"
                        )
                        .queryParam(
                                "api_key",
                                config.getApiKey()
                        )
                        .queryParam(
                                "format",
                                "json"
                        )
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {

                    try {

                        com.fasterxml.jackson.databind.JsonNode node =
                                new ObjectMapper()
                                        .readTree(json);

                        return node
                                .path("token")
                                .asText();

                    } catch (Exception e) {

                        throw new RuntimeException(
                                "Erro ao extrair token",
                                e
                        );
                    }
                });
    }


    // =========================================================
    // GERAR URL DE AUTENTICAÇÃO
    // =========================================================

    public String getAuthUrl(String token) {

        return "https://www.last.fm/api/auth/?api_key="
                + config.getApiKey()
                + "&token="
                + token;
    }


    // =========================================================
    // OBTER SESSÃO DO LAST.FM
    // =========================================================

    public Mono<LastFmSessionDto> getSession(
            String cookie,
            String token
    ) {

        try {

            String apiSig =
                    generateApiSignature(token);

            if (!cookieService.checkCookie(cookie)) {

                throw new InvalidTokenException(
                        "sessão inválida"
                );
            }

            return webClient
                    .post()
                    .uri("")
                    .contentType(
                            org.springframework.http.MediaType
                                    .APPLICATION_FORM_URLENCODED
                    )
                    .bodyValue(
                            "method=auth.getSession"
                                    + "&api_key="
                                    + config.getApiKey()
                                    + "&token="
                                    + token
                                    + "&api_sig="
                                    + apiSig
                                    + "&format=json"
                    )
                    .retrieve()
                    .bodyToMono(LastFmSessionDto.class)
                    .doOnSuccess(lastFmSessionDto -> {

                        Long id =
                                cookieService.getId(cookie);

                        LastFmSession lastFmSession =
                                new LastFmSession(
                                        lastFmSessionDto
                                );

                        Optional<User> user =
                                userRepository.findById(id);

                        user.ifPresent(user1 -> {

                            if (user1.getId() == null) {

                                throw new InvalidTokenException(
                                        "sessão inválida"
                                );
                            }

                            lastFmSession
                                    .setSynchroUser(user1);

                            user1.setLastFmUsername(
                                    lastFmSessionDto
                                            .getSession()
                                            .getName()
                            );

                            /*
                             * Salva a sessão Last.fm primeiro.
                             */
                            lastFmSessionRepository
                                    .save(lastFmSession);

                            /*
                             * Associa a sessão ao usuário Synchro.
                             */
                            user1.setSession(
                                    lastFmSession
                            );

                            userRepository
                                    .save(user1);
                        });
                    });

        } catch (InvalidTokenException e) {

            System.out.println(
                    e.getLocalizedMessage()
            );

            throw e;

        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }


    // =========================================================
    // BUSCAR LAST.FM DO USUÁRIO SYNCHRO LOGADO
    // =========================================================

    public Mono<String> getUserInfoBySynchroId(
            Long userId
    ) {

        Optional<User> user =
                userRepository.findById(userId);

        if (user.isEmpty()) {

            return Mono.error(
                    new RuntimeException(
                            "Usuário Synchro não encontrado"
                    )
            );
        }

        String lastFmUsername =
                user.get().getLastFmUsername();

        if (
                lastFmUsername == null
                        || lastFmUsername.isBlank()
        ) {

            return Mono.error(
                    new RuntimeException(
                            "Usuário ainda não conectou uma conta Last.fm"
                    )
            );
        }

        return getUserInfo(
                lastFmUsername
        );
    }


    // =========================================================
    // GERAR ASSINATURA DA API LAST.FM
    // =========================================================

    private String generateApiSignature(
            String token
    ) {

        String toSign =
                "api_key"
                        + config.getApiKey()
                        + "methodauth.getSession"
                        + "token"
                        + token
                        + config.getSharedSecret();

        try {

            java.security.MessageDigest md =
                    java.security.MessageDigest
                            .getInstance("MD5");

            byte[] digest =
                    md.digest(
                            toSign.getBytes("UTF-8")
                    );

            StringBuilder sb =
                    new StringBuilder();

            for (byte b : digest) {

                sb.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }

            return sb.toString();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Erro ao gerar api_sig",
                    e
            );
        }
    }
}