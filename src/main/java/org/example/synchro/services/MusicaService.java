package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.MusicaDto;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;
import org.example.synchro.repositories.ArtistaRepository;
import org.example.synchro.repositories.MusicaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MusicaService {

    private final MusicaRepository musicaRepository;
    private final ArtistaRepository artistaRepository;

    public MusicaDto findById(Long id) {
        Optional<Musica> m1 = musicaRepository.findById(id);
        return new MusicaDto(m1.get());
    }


    public MusicaDto findByTitulo(String titulo) {

        Musica musica = musicaRepository.findByTitulo(titulo);

        if (musica == null) {
            throw new org.springframework.web.server
                    .ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Musica nao encontrada"
            );
        }

        return new MusicaDto(musica);
    }


    public List<MusicaDto> findByArtistas(String artista) {
        List<MusicaDto>dto = new ArrayList<>();
        List<Musica> m1 = musicaRepository.findByArtistas(artistaRepository.findByNome(artista));
        for (Musica musica : m1) {
            dto.add(new MusicaDto(musica));
        }
        return dto;
    }

    // =====================================================
    // BUSCAR MUSICAS POR TITULO OU ARTISTA
    // =====================================================

    @Transactional(readOnly = true)
    public List<MusicaDto> buscarMusicas(String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<Musica> resultados = musicaRepository
                .findByTituloContainingIgnoreCaseOrArtistasNomeContainingIgnoreCase(
                        query.trim(),
                        query.trim()
                );

        List<MusicaDto> dtos = new ArrayList<>();

        for (Musica musica : resultados) {
            dtos.add(new MusicaDto(musica));
        }

        return dtos;
    }


    // =====================================================
    // LOCALIZAR MUSICA PELO TITULO E ARTISTA
    // =====================================================

    @Transactional(readOnly = true)
    public MusicaDto buscarPorTituloEArtista(
            String titulo,
            String artista
    ) {

        if (titulo == null || titulo.isBlank()
                || artista == null || artista.isBlank()) {
            return null;
        }

        List<Musica> resultados =
                musicaRepository.buscarPorTituloEArtista(
                        titulo.trim(),
                        artista.trim()
                );

        // Nunca escolher automaticamente entre
        // registros ambiguos.
        if (resultados.size() != 1) {
            return null;
        }

        return new MusicaDto(resultados.get(0));
    }


    // =====================================================
    // CADASTRAR MUSICA CONFIRMADA PELO LAST.FM
    // =====================================================

    @Transactional
    public MusicaDto cadastrarMusicaConfirmada(
            String titulo,
            String artista
    ) {

        String tituloLimpo = titulo.trim();
        String artistaLimpo = artista.trim();

        // 1. Verifica se a musica ja existe.
        List<Musica> existentes =
                musicaRepository.buscarPorTituloEArtista(
                        tituloLimpo,
                        artistaLimpo
                );

        if (existentes.size() > 1) {
            throw new org.springframework.web.server
                    .ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Existem musicas duplicadas no banco"
            );
        }

        if (existentes.size() == 1) {
            return new MusicaDto(existentes.get(0));
        }

        // 2. Localiza o artista, sem criar duplicatas
        // quando houver um registro correspondente.
        List<Artista> artistas =
                artistaRepository.findByNomeIgnoreCase(artistaLimpo);

        if (artistas.size() > 1) {
            throw new org.springframework.web.server
                    .ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Existem artistas duplicados no banco"
            );
        }

        Artista artistaEntity;

        if (artistas.isEmpty()) {
            artistaEntity = new Artista(
                    artistaLimpo,
                    "",
                    null
            );

            artistaEntity =
                    artistaRepository.save(artistaEntity);

        } else {
            artistaEntity = artistas.get(0);
        }

        // 3. Cria a musica.
        // Nao inventamos duracao, genero ou descricao.
        Musica musica = new Musica(
                tituloLimpo,
                "",
                null,
                null
        );

        // 4. Associa o artista correto.
        musica.addArtista(artistaEntity);

        // 5. Salva a musica.
        musica = musicaRepository.saveAndFlush(musica);

        // 6. Retorna o registro com ID real.
        return new MusicaDto(musica);
    }

}
