package org.example.synchro.resources;

import org.example.synchro.dto.MusicaDto;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;
import org.example.synchro.services.MusicaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(value = "/musica")
public class MusicaResource {

    @Autowired

    private MusicaService musicaService;

    @Autowired
    private org.example.synchro.services.LastfmService lastfmService;

    @Autowired
    private org.example.synchro.services.CookieService cookieService;


    // =====================================================
    // GET /musica/buscar?query=...
    // =====================================================

    @GetMapping("/buscar")
    public ResponseEntity<List<MusicaDto>> buscarMusicas(
            @RequestParam("query") String query
    ) {

        List<MusicaDto> resultados =
                musicaService.buscarMusicas(query);

        return ResponseEntity.ok(resultados);
    }


    // =====================================================
    // BUSCAR MUSICA PELO TITULO E ARTISTA
    // =====================================================

    @GetMapping("/identificar")
    public ResponseEntity<MusicaDto> identificarMusica(
            @RequestParam String titulo,
            @RequestParam String artista
    ) {

        MusicaDto musica =
                musicaService.buscarPorTituloEArtista(
                        titulo,
                        artista
                );

        if (musica == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(musica);
    }



    // =====================================================
    // POST /musica/registrar
    // =====================================================

    @org.springframework.web.bind.annotation.PostMapping("/registrar")
    public ResponseEntity<MusicaDto> registrarMusica(

            @org.springframework.web.bind.annotation.CookieValue(
                    name = "token_jwt",
                    required = false
            ) String cookie,

            @org.springframework.web.bind.annotation.RequestParam
            String titulo,

            @org.springframework.web.bind.annotation.RequestParam
            String artista
    ) {

        // Somente usuarios autenticados podem registrar
        // novas musicas para avaliacao.
        try {
            if (cookie == null
                    || !cookieService.checkCookie(cookie)) {

                return ResponseEntity.status(401).build();
            }

            cookieService.getId(cookie);

        } catch (RuntimeException error) {
            return ResponseEntity.status(401).build();
        }

        // Validacao dos parametros.
        if (titulo == null || titulo.isBlank()
                || artista == null || artista.isBlank()
                || titulo.length() > 200
                || artista.length() > 200) {

            return ResponseEntity.badRequest().build();
        }

        Boolean confirmada;

        try {
            // A consulta externa acontece ANTES da
            // transacao que grava no banco.
            confirmada = lastfmService
                    .confirmarMusica(titulo, artista)
                    .block(java.time.Duration.ofSeconds(20));

        } catch (RuntimeException error) {

            System.err.println(
                    "Erro ao confirmar musica no Last.fm: "
                            + error.getMessage()
            );

            return ResponseEntity.status(502).build();
        }

        if (!Boolean.TRUE.equals(confirmada)) {
            return ResponseEntity.notFound().build();
        }

        // Somente musicas confirmadas chegam aqui.
        MusicaDto musica = musicaService
                .cadastrarMusicaConfirmada(
                        titulo,
                        artista
                );

        return ResponseEntity.ok(musica);
    }

    @GetMapping(value = "/{titulo}")
    public ResponseEntity<MusicaDto> findByTitulo(@PathVariable String titulo){
        return ResponseEntity.ok().body(musicaService.findByTitulo(titulo));
    }

    @GetMapping(value = "artista/{artista}")
    public ResponseEntity<List<MusicaDto>> findByArtista(@PathVariable String artista){
        return ResponseEntity.ok().body(musicaService.findByArtistas(artista));
    }

}
