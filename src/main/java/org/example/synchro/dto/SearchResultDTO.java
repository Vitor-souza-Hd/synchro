
package org.example.synchro.dto;

import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;

import java.util.ArrayList;
import java.util.List;

public class SearchResultDTO {

    private List<SearchUserDTO> users = new ArrayList<>();
    private List<Musica> songs = new ArrayList<>();
    private List<Artista> artists = new ArrayList<>();
    private List<Album> albums = new ArrayList<>();

    public SearchResultDTO() {
    }

    public List<SearchUserDTO> getUsers() {
        return users;
    }

    public void setUsers(List<SearchUserDTO> users) {
        this.users = users;
    }

    public List<Musica> getSongs() {
        return songs;
    }

    public void setSongs(List<Musica> songs) {
        this.songs = songs;
    }

    public List<Artista> getArtists() {
        return artists;
    }

    public void setArtists(List<Artista> artists) {
        this.artists = artists;
    }

    public List<Album> getAlbums() {
        return albums;
    }

    public void setAlbums(List<Album> albums) {
        this.albums = albums;
    }
}
