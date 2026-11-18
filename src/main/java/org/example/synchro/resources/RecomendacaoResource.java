package org.example.synchro.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.AlbumRepository;
import org.example.synchro.repositories.ReviewRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.LastfmService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/recomendacoes")
@RequiredArgsConstructor
public class RecomendacaoResource {

    private final CookieService cookieService;
    private final UserRepository userRepository;
    private final AlbumRepository albumRepository;
    private final ReviewRepository reviewRepository;
    private final LastfmService lastfmService;

    @GetMapping
    public Mono<ResponseEntity<?>> gerarRecomendacoes(@CookieValue(name = "token_jwt", required = false) String token) {
        return Mono.defer(() -> {
            try {
                if (!cookieService.checkCookie(token)) {
                    return Mono.just(ResponseEntity.status(401).body("Não autenticado"));
                }

                Long meuId = cookieService.getId(token);
                User user = userRepository.findById(meuId).orElse(null);

                if (user == null || user.getLastFmUsername() == null || user.getLastFmUsername().isEmpty()) {
                    // Plano B se não tiver Last.fm
                    return Mono.just(ResponseEntity.ok(converterParaMap(albumRepository.findAll().stream().limit(4).toList())));
                }

                System.out.println(">>> TENTANDO CONECTAR NA API DO LAST.FM (Usuário: " + user.getLastFmUsername() + ")...");

                return lastfmService.getTopBySynchroId(meuId, "artists", "overall")
                        .<ResponseEntity<?>>map(jsonString -> {
                            try {
                                ObjectMapper mapper = new ObjectMapper();
                                JsonNode root = mapper.readTree(jsonString);
                                JsonNode artistasNode = root.path("topartists").path("artist");

                                List<Map<String, Object>> resposta = new ArrayList<>();

                                if (artistasNode.isArray()) {
                                    for (JsonNode artista : artistasNode) {
                                        String nomeArtista = artista.path("name").asText();

                                        // Tenta achar a foto do artista no Last.fm
                                        String imgArtista = "https://placehold.co/150/333/fff?text=Artist";
                                        JsonNode images = artista.path("image");
                                        for (JsonNode img : images) {
                                            if ("mega".equals(img.path("size").asText()) || "extralarge".equals(img.path("size").asText())) {
                                                imgArtista = img.path("#text").asText();
                                            }
                                        }

                                        // Tenta achar um álbum desse artista no banco
                                        List<Album> albumsDoArtista = albumRepository.findByArtistasNomeIgnoreCase(nomeArtista);
                                        boolean achouAlbum = false;

                                        for (Album album : albumsDoArtista) {
                                            boolean jaAvaliou = reviewRepository.existsByAuthorAndMidia(user, album);
                                            if (!jaAvaliou) {
                                                resposta.add(converterParaMap(List.of(album)).get(0));
                                                achouAlbum = true;
                                                break; // Pega só 1 álbum por artista
                                            }
                                        }

                                        // SE NÃO ACHOU ÁLBUM NO BANCO, CRIA UM CARD DO ARTISTA DO LAST.FM
                                        if (!achouAlbum) {
                                            Map<String, Object> mapArtista = new HashMap<>();
                                            mapArtista.put("id", "artista_" + nomeArtista.hashCode());
                                            mapArtista.put("titulo", "Explorar Artista");
                                            mapArtista.put("capaUrl", imgArtista);
                                            mapArtista.put("nomeArtista", nomeArtista);
                                            resposta.add(mapArtista);
                                        }

                                        if (resposta.size() >= 4) break;
                                    }
                                }

                                System.out.println(">>> SUCESSO! O Last.fm retornou os artistas e montamos a tela.");
                                return ResponseEntity.ok(resposta);

                            } catch (Exception e) {
                                System.out.println(">>> ERRO AO LER O JSON. Usando Plano B.");
                                return ResponseEntity.ok(converterParaMap(albumRepository.findAll().stream().limit(4).toList()));
                            }
                        })
                        .onErrorResume(error -> {
                            System.out.println(">>> ERRO NA API DO LAST.FM. Usando Plano B. Detalhe: " + error.getMessage());
                            return Mono.just(ResponseEntity.ok(converterParaMap(albumRepository.findAll().stream().limit(4).toList())));
                        });

            } catch (Exception e) {
                return Mono.just(ResponseEntity.ok(converterParaMap(albumRepository.findAll().stream().limit(4).toList())));
            }
        });
    }

    private List<Map<String, Object>> converterParaMap(List<Album> albums) {
        List<Map<String, Object>> resposta = new ArrayList<>();
        for (Album a : albums) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", a.getId());
            map.put("titulo", a.getTitulo());
            map.put("capaUrl", "https://placehold.co/150/333/fff?text=Album");
            String nomeArt = "Desconhecido";
            if (a.getArtistas() != null && !a.getArtistas().isEmpty()) {
                nomeArt = a.getArtistas().iterator().next().getNome();
            }
            map.put("nomeArtista", nomeArt);
            resposta.add(map);
        }
        return resposta;
    }
}