package org.example.synchro.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.AlbumRepository;
import org.example.synchro.repositories.ArtistaRepository;
import org.example.synchro.repositories.ReviewRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.CookieService;
import org.example.synchro.services.LastfmService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private final ArtistaRepository artistaRepository;

    @Value("${lastfm.api-key}")
    private String lastfmApiKey;

    @GetMapping
    public Mono<ResponseEntity<?>> gerarRecomendacoes(@CookieValue(name = "token_jwt", required = false) String token) {
        return Mono.defer(() -> {
            // VERIFICAÇÃO RIGOROSA DE LOGIN
            if (token == null || token.isEmpty() || !cookieService.checkCookie(token)) {
                return Mono.just(ResponseEntity.status(401).body(Map.of("error", "Não autenticado")));
            }

            try {
                Long meuId = cookieService.getId(token);
                User user = userRepository.findById(meuId).orElse(null);

                if (user == null) {
                    return Mono.just(ResponseEntity.status(401).body(Map.of("error", "Usuário não encontrado")));
                }

                // Se o usuário está logado mas não tem Last.fm conectado, mostra álbuns gerais
                if (user.getLastFmUsername() == null || user.getLastFmUsername().isEmpty()) {
                    return Mono.just(ResponseEntity.ok(converterParaMap(albumRepository.findAll().stream().limit(5).toList())));
                }

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

                                        String imgArtista = null;

                                        List<Artista> artistasNoBanco = artistaRepository.findByNomeContainingIgnoreCase(nomeArtista);
                                        if (!artistasNoBanco.isEmpty() && artistasNoBanco.get(0).getImgUrl() != null && !artistasNoBanco.get(0).getImgUrl().isEmpty()) {
                                            imgArtista = artistasNoBanco.get(0).getImgUrl();
                                        }

                                        if (imgArtista == null || imgArtista.isEmpty()) {
                                            JsonNode imagesNode = artista.path("image");
                                            if (imagesNode.isArray()) {
                                                for (JsonNode img : imagesNode) {
                                                    String size = img.path("size").asText();
                                                    if ("extralarge".equals(size) || "mega".equals(size)) {
                                                        String urlImg = img.path("#text").asText();
                                                        if (urlImg != null && !urlImg.trim().isEmpty() && !urlImg.contains("2a96cbd8b46e442fc41c2b86b821562f")) {
                                                            imgArtista = urlImg;
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (imgArtista == null || imgArtista.isEmpty()) {
                                            imgArtista = buscarImagemDoLastfm(nomeArtista);
                                        }

                                        if (imgArtista == null || imgArtista.isEmpty()) {
                                            imgArtista = buscarImagemDoDeezer(nomeArtista);
                                        }

                                        if (imgArtista == null || imgArtista.isEmpty()) {
                                            imgArtista = "https://ui-avatars.com/api/?name=" + URLEncoder.encode(nomeArtista, StandardCharsets.UTF_8) + "&background=181818&color=1db954&size=400";
                                        }

                                        List<Album> albumsDoArtista = albumRepository.findByArtistasNomeIgnoreCase(nomeArtista);
                                        boolean achouAlbum = false;

                                        for (Album album : albumsDoArtista) {
                                            boolean jaAvaliou = reviewRepository.existsByAuthorAndMidia(user, album);
                                            if (!jaAvaliou) {
                                                Map<String, Object> mapAlbum = converterParaMap(List.of(album)).get(0);
                                                mapAlbum.put("capaUrl", imgArtista);
                                                resposta.add(mapAlbum);
                                                achouAlbum = true;
                                                break;
                                            }
                                        }

                                        if (!achouAlbum) {
                                            Map<String, Object> mapArtista = new HashMap<>();
                                            mapArtista.put("id", "artista_" + nomeArtista.hashCode());
                                            mapArtista.put("titulo", "Explorar Artista");
                                            mapArtista.put("capaUrl", imgArtista);
                                            mapArtista.put("nomeArtista", nomeArtista);
                                            resposta.add(mapArtista);
                                        }

                                        if (resposta.size() >= 5) break;
                                    }
                                }

                                return ResponseEntity.ok(resposta);

                            } catch (Exception e) {
                                e.printStackTrace();
                                return ResponseEntity.status(500).body(Map.of("error", "Erro ao processar Last.fm"));
                            }
                        })
                        .onErrorResume(error -> {
                            return Mono.just(ResponseEntity.status(500).body(Map.of("error", "Erro na chamada do Last.fm")));
                        });

            } catch (Exception e) {
                e.printStackTrace();
                return Mono.just(ResponseEntity.status(500).body(Map.of("error", "Erro interno no servidor")));
            }
        });
    }

    private String buscarImagemDoLastfm(String nomeArtista) {
        try {
            RestTemplate rt = new RestTemplate();
            String url = "http://ws.audioscrobbler.com/2.0/?method=artist.getinfo&artist="
                    + URLEncoder.encode(nomeArtista, StandardCharsets.UTF_8)
                    + "&api_key=" + lastfmApiKey + "&format=json";

            Map<String, Object> res = rt.getForObject(url, Map.class);
            if (res != null && res.containsKey("artist")) {
                Map<String, Object> artist = (Map<String, Object>) res.get("artist");
                List<Map<String, Object>> images = (List<Map<String, Object>>) artist.get("image");

                if (images != null) {
                    String[] sizesToTry = {"mega", "extralarge", "large"};
                    for (String size : sizesToTry) {
                        for (Map<String, Object> img : images) {
                            if (size.equals(img.get("size"))) {
                                String urlImg = (String) img.get("#text");
                                if (urlImg != null && !urlImg.trim().isEmpty() && !urlImg.contains("2a96cbd8b46e442fc41c2b86b821562f")) {
                                    return urlImg;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private String buscarImagemDoDeezer(String nomeArtista) {
        try {
            RestTemplate rt = new RestTemplate();
            String url = "https://api.deezer.com/search/artist?q=" + URLEncoder.encode(nomeArtista, StandardCharsets.UTF_8);
            Map<String, Object> res = rt.getForObject(url, Map.class);

            if (res != null && res.containsKey("data")) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) res.get("data");
                if (data != null && !data.isEmpty()) {
                    return (String) data.get(0).get("picture_xl");
                }
            }
        } catch (Exception e) {}
        return null;
    }

    private List<Map<String, Object>> converterParaMap(List<Album> albums) {
        List<Map<String, Object>> resposta = new ArrayList<>();
        for (Album a : albums) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", a.getId());
            map.put("titulo", a.getTitulo());
            map.put("capaUrl", "https://ui-avatars.com/api/?name=" + URLEncoder.encode(a.getTitulo(), StandardCharsets.UTF_8) + "&background=181818&color=ffffff&size=400");

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