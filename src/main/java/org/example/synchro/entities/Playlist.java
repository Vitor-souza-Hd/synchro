package org.example.synchro.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
public class Playlist extends Midia {
    
    @ManyToMany(fetch = FetchType.LAZY)
    private Set<Musica> musicas = new HashSet<>();

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

    public void addUsuario(User usuario) {
        colaboradores.add(usuario);
        usuario.getPlaylists().add(this);
    }

    public void removeUsuario(User usuario) {
        colaboradores.remove(usuario);
        usuario.getPlaylists().remove(this);
    }

     public boolean colaborador(User usuario){
        boolean anwser = false;
        for( User colaborador : colaboradores ){
            if( colaborador.equals(usuario) ){
                anwser = true;
            }
        }
        return anwser;
     }
}
