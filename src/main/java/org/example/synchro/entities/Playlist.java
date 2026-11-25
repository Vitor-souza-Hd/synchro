package org.example.synchro.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OrderColumn;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
public class Playlist extends Midia {
    @jakarta.persistence.Column(unique = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private UUID shareToken;

    @jakarta.persistence.Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("false")
    private boolean exibirNoPerfil;

    @jakarta.persistence.Column(columnDefinition = "text")
    private String capa;

    @jakarta.persistence.Column(columnDefinition = "text")
    private String banner;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @OrderColumn(name = "posicao")
    private List<Musica> musicas = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User criador;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "playlist_colaboradores",
            joinColumns = @JoinColumn(name = "playlist_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> colaboradores = new HashSet<>();

    public Playlist() {
        super();
    }

    public Playlist(String titulo, String descricao, User user) {
        super(titulo, descricao);
        this.criador = user;
        this.addUsuario(user);
    }


    public void addMusica(Musica musica) {
        musicas.add(musica);
    }

    public void removeMusica(Musica musica) {
        musicas.remove(musica);
    }

    public void addAlbumMusicas(Album album) {
        for (Musica musica : album.getMusicas()) {
            musicas.add(musica);
        }
    }
    public void addPlaylistMusicas(Playlist playlist) {
        if(this.getId() != playlist.getId()){
        for (Musica musica : playlist.getMusicas()) {
            musicas.add(musica);
        }
        }
    }
    public void addUsuario(User usuario) {
        colaboradores.add(usuario);
        usuario.getPlaylists().add(this);
    }

    public void removeUsuario(User usuario) {
        colaboradores.remove(usuario);
        usuario.getPlaylists().remove(this);
    }

    public boolean criadaPor(User usuario) {
        return usuario != null && usuario.getId() != null && criador != null
                && usuario.getId().equals(criador.getId());
    }

    public boolean colaborador(User usuario) {
        if (usuario == null || usuario.getId() == null) return false;
        if (criadaPor(usuario)) return true;
        // Compare identifiers through getters: Hibernate may load users as proxies.
        Long userId = usuario.getId();
        return colaboradores.stream().anyMatch(user -> userId.equals(user.getId()));
    }
}
