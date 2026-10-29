package org.example.synchro.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SearchResultDTO {

    private List<SearchUserDTO> users = new ArrayList<>();
    private List<Song> songs = new ArrayList<>();
    private List<Artist> artists = new ArrayList<>();
    private List<AlbumResult> albums = new ArrayList<>();
    private String source = "lastfm";
    private String warning;

    public SearchResultDTO() {
    }

    public List<SearchUserDTO> getUsers() {
        return users;
    }

    public void setUsers(List<SearchUserDTO> users) {
        this.users = users;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    public List<Artist> getArtists() {
        return artists;
    }

    public void setArtists(List<Artist> artists) {
        this.artists = artists;
    }

    public List<AlbumResult> getAlbums() {
        return albums;
    }

    public void setAlbums(List<AlbumResult> albums) {
        this.albums = albums;
    }

    // Only immutable values cross the HTTP boundary; no entity or Hibernate proxy is serialized.
    public record Artist(Long id, String nome, String imgUrl, String biografia, Metadata lastFm) {
        public static Artist from(Artista artist) {
            return new Artist(artist.getId(), artist.getNome(), artist.getImgUrl(), artist.getBiografia(), Metadata.from(artist.getLastFm()));
        }
    }

    public record AlbumReference(Long id, String titulo, String imgUrl) {
        public static AlbumReference from(Album album) {
            return new AlbumReference(album.getId(), album.getTitulo(), album.getLastFm().getImageUrl());
        }
    }

    public record Song(Long id, String titulo, String descricao, String duracao, String genero,
                       List<Artist> artistas, AlbumReference album, Metadata lastFm) {
        public static Song from(Musica song) {
            return new Song(song.getId(), song.getTitulo(), song.getDescricao(),
                    song.getDuracao() == null ? null : song.getDuracao().toString(), song.getGenero(),
                    song.getArtistas().stream().map(Artist::from).toList(),
                    song.getAlbum() == null ? null : AlbumReference.from(song.getAlbum()), Metadata.from(song.getLastFm()));
        }
    }

    public record AlbumResult(Long id, String titulo, String descricao, String dataLancamento,
                              List<Artist> artistas, Metadata lastFm) {
        public static AlbumResult from(Album album) {
            return new AlbumResult(album.getId(), album.getTitulo(), album.getDescricao(),
                    album.getDataLancamento() == null ? null : album.getDataLancamento().toString(),
                    album.getArtistas().stream().map(Artist::from).toList(), Metadata.from(album.getLastFm()));
        }
    }

    public record Metadata(String mbid, String artistName, String url, String imageUrl, Long listeners, Long playcount,
                           List<String> tags, String updatedAt) {
        public static Metadata from(org.example.synchro.entities.LastFmMetadata meta) {
            return new Metadata(meta.getMbid(), meta.getArtistName(), meta.getUrl(), meta.getImageUrl(), meta.getListeners(), meta.getPlaycount(),
                    org.example.synchro.services.LastFmCatalogStore.tags(meta),
                    meta.getUpdatedAt() == null ? null : meta.getUpdatedAt().toString());
        }
    }
}
