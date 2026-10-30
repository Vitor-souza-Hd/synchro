package org.example.synchro.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.Playlist;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@JsonPropertyOrder({"id", "titulo", "descricao", "musicas", "criador", "usuarios"})
public class PlaylistDto {

    private Long id;
    private String capa;
    private String banner;
    private String titulo;
    private String descricao;
    private Set<Musica_AlbumDto> musicas = new HashSet<>();
    private SearchUserDTO criador;
    private Set<SearchUserDTO> usuarios = new HashSet<>();

    public PlaylistDto() {
    }

    public PlaylistDto(Playlist playlist) {
        this.id = playlist.getId();
        this.capa = playlist.getCapa();
        this.banner = playlist.getBanner();
        this.titulo = playlist.getTitulo();
        this.descricao = playlist.getDescricao();
        playlist.getMusicas().forEach(musica -> musicas.add(new Musica_AlbumDto(musica)));

        if (playlist.getCriador() != null) {
            this.criador = new SearchUserDTO(
                    playlist.getCriador().getId(),
                    playlist.getCriador().getUsername()
            );
        }

        playlist.getColaboradores().forEach(usuario -> usuarios.add(
                new SearchUserDTO(usuario.getId(), usuario.getUsername())
        ));
    }
}
