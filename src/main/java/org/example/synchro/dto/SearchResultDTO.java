package org.example.synchro.dto;

import java.util.List;
import org.example.synchro.entities.User;
import org.example.synchro.entities.Musica;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Album;

public class SearchResultDTO {
    private List<User> users;
    private List<Musica> songs;
    private List<Artista> artists;
    private List<Album> albums;

    public SearchResultDTO() {
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
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