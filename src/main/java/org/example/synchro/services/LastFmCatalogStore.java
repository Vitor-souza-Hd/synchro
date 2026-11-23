package org.example.synchro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.SearchResultDTO;
import org.example.synchro.dto.SearchUserDTO;
import org.example.synchro.entities.*;
import org.example.synchro.importer.LastFmGenres;
import org.example.synchro.repositories.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LastFmCatalogStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PageRequest SEARCH_PAGE = PageRequest.of(0, 10);
    private final MusicaRepository songs;
    private final ArtistaRepository artists;
    private final AlbumRepository albums;
    private final UserRepository users;

    public static List<JsonNode> nodes(JsonNode node) {
        if (node.isArray()) {
            var result = new ArrayList<JsonNode>();
            node.forEach(result::add);
            return result;
        }
        return node.isObject() ? List.of(node) : List.of();
    }

    public static List<String> tags(LastFmMetadata metadata) {
        if (metadata.getTagsJson() == null) return List.of();
        try { return nodes(JSON.readTree(metadata.getTagsJson())).stream().map(n -> n.path("name").asText()).toList(); }
        catch (Exception ignored) { return List.of(); }
    }

    private static String text(JsonNode node, String field) { return node.path(field).asText("").strip(); }

    public static String artistName(JsonNode node) {
        JsonNode artist = node.path("artist");
        return artist.isTextual() ? artist.asText().strip() : text(artist, "name");
    }

    private static Long number(JsonNode node) {
        try { long value = Long.parseLong(node.asText()); return value >= 0 ? value : null; }
        catch (NumberFormatException e) { return null; }
    }

    private static String image(JsonNode node) {
        return nodes(node.path("image")).stream().map(n -> text(n, "#text"))
                .filter(s -> !s.isBlank() && !s.contains("2a96cbd8b46e442fc41c2b86b821562f"))
                .reduce((first, last) -> last).orElse("");
    }

    private static void metadata(LastFmMetadata meta, JsonNode node, boolean complete) {
        if (!complete && meta.getRawJson() != null) return;
        if (!artistName(node).isBlank()) meta.setArtistName(artistName(node));
        if (!text(node, "mbid").isBlank()) meta.setMbid(text(node, "mbid"));
        if (!text(node, "url").isBlank()) meta.setUrl(text(node, "url"));
        if (!image(node).isBlank()) meta.setImageUrl(image(node));
        JsonNode stats = node.has("stats") ? node.path("stats") : node;
        Long listeners = number(stats.path("listeners"));
        Long plays = number(stats.has("playcount") ? stats.path("playcount") : stats.path("plays"));
        if (listeners != null) meta.setListeners(listeners);
        if (plays != null) meta.setPlaycount(plays);
        JsonNode tags = node.path(node.has("toptags") ? "toptags" : "tags").path("tag");
        if (!nodes(tags).isEmpty()) meta.setTagsJson(JSON.valueToTree(nodes(tags)).toString());
        if (complete || meta.getRawJson() == null) meta.setRawJson(node.toString());
        if (complete) meta.setUpdatedAt(Instant.now());
    }

    private Artista artist(JsonNode node, boolean complete) {
        String name = text(node, "name");
        if (name.isBlank()) throw new IllegalArgumentException("Artista sem nome");
        var artist = artists.findByNomeKey(CatalogName.key(name))
                .orElseGet(() -> artists.saveAndFlush(new Artista(name, null, null)));
        var meta = artist.getLastFm();
        if (!complete && meta.getRawJson() != null) {
            // A ranking can supply fields missing from a previously imported artist.
            String mbid = text(node, "mbid");
            String url = text(node, "url");
            String photo = image(node);
            if ((meta.getMbid() == null || meta.getMbid().isBlank()) && !mbid.isBlank()) meta.setMbid(mbid);
            if ((meta.getUrl() == null || meta.getUrl().isBlank()) && !url.isBlank()) meta.setUrl(url);
            if (!ArtistPhotoStore.hasUsableImage(meta.getImageUrl()) && ArtistPhotoStore.hasUsableImage(photo)) {
                meta.setImageUrl(photo);
            }
        } else {
            metadata(meta, node, complete);
        }
        if (!ArtistPhotoStore.hasUsableImage(artist.getImgUrl()) && ArtistPhotoStore.hasUsableImage(artist.getLastFm().getImageUrl())) {
            artist.setImgUrl(artist.getLastFm().getImageUrl());
        }
        String bio = description(node.path("bio"));
        if (!bio.isBlank()) artist.setBiografia(bio);
        return artist;
    }

    private Artista namedArtist(String name) {
        return artist(JSON.createObjectNode().put("name", name), false);
    }

    private static String description(JsonNode wiki) {
        String content = text(wiki, "content");
        return content.isBlank() ? text(wiki, "summary") : content;
    }

    private Album album(JsonNode node, boolean complete) {
        String name = text(node, "name");
        if (name.isBlank()) name = text(node, "title");
        final String title = name;
        var album = albums.findByTituloKey(CatalogName.key(title))
                .orElseGet(() -> albums.saveAndFlush(new Album(title, null, null)));
        metadata(album.getLastFm(), node, complete);
        String artist = artistName(node);
        if (!artist.isBlank()) album.addArtista(namedArtist(artist));
        String wiki = description(node.path("wiki"));
        if (!wiki.isBlank()) album.setDescricao(wiki);
        // wiki.published is the article date, not the release date.
        String release = text(node, "releasedate");
        if (!release.isBlank()) {
            try { album.setDataLancamento(java.time.LocalDate.parse(release)); }
            catch (java.time.format.DateTimeParseException ignored) {
                try { album.setDataLancamento(java.time.LocalDate.parse(release.split(",")[0].strip(),
                        java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH))); }
                catch (java.time.format.DateTimeParseException missingDate) { /* Keep unknown dates empty. */ }
            }
        }
        for (JsonNode track : nodes(node.path("tracks").path("track"))) {
            if (text(track, "name").isBlank()) continue;
            String trackArtist = artistName(track);
            String credited = trackArtist.isBlank() ? artist : trackArtist;
            if (!credited.isBlank() && !compatibleArtist(
                    songs.findByTituloKey(CatalogName.key(text(track, "name"))).orElse(null), credited)) continue;
            var song = song(track, false);
            if (song.getArtistas().isEmpty() && !credited.isBlank()) song.addArtista(namedArtist(credited));
            if (song.getAlbum() == null) song.setAlbum(album);
            Long seconds = number(track.path("duration"));
            if (seconds != null && seconds > 0 && song.getDuracao() == null) song.setDuracao(Duration.ofSeconds(seconds));
        }
        return album;
    }

    private Musica song(JsonNode node, boolean complete) {
        String name = text(node, "name");
        var song = songs.findByTituloKey(CatalogName.key(name))
                .orElseGet(() -> songs.saveAndFlush(new Musica(name, null, null, null)));
        metadata(song.getLastFm(), node, complete);
        String artist = artistName(node);
        if (!artist.isBlank()) song.addArtista(namedArtist(artist));
        var albumNode = node.path("album");
        String albumTitle = text(albumNode, "title");
        if (albumNode.isObject() && !albumTitle.isBlank()) {
            String albumArtist = artistName(albumNode);
            if (albumArtist.isBlank()) albumArtist = artist;
            var existingAlbum = albums.findByTituloKey(CatalogName.key(albumTitle)).orElse(null);
            if (albumArtist.isBlank() || compatibleArtist(existingAlbum, albumArtist)) {
                if (complete || song.getAlbum() == null) song.setAlbum(album(albumNode, false));
                if (!image(albumNode).isBlank()) song.getLastFm().setImageUrl(image(albumNode));
            }
        }
        if (complete) {
            Long millis = number(node.path("duration"));
            if (millis != null && millis > 0) song.setDuracao(Duration.ofMillis(millis));
            String wiki = description(node.path("wiki"));
            if (!wiki.isBlank()) song.setDescricao(wiki);
            var genres = LastFmGenres.fromTags(node.path("toptags"));
            if ((song.getGenero() == null || song.getGenero().isBlank()) && !genres.isEmpty()) {
                song.setGenero(String.join(", ", genres));
                song.setGeneroConsultadoEm(Instant.now());
            }
        }
        return song;
    }

    @Transactional(readOnly = true)
    public boolean knowsArtist(String name) {
        return artists.findByNomeKey(CatalogName.key(name)).isPresent();
    }

    @Transactional
    public Long saveRankedArtistItem(String type, JsonNode node, String artist) {
        String title = CatalogName.key(text(node, "name"));
        Midia existing = "album".equals(type) ? albums.findByTituloKey(title).orElse(null)
                : songs.findByTituloKey(title).orElse(null);
        if (!compatibleArtist(existing, artist)) return null;
        Midia media = "album".equals(type) ? album(node, false) : song(node, false);
        var metadata = media.getLastFm();
        if (!ArtistPhotoStore.hasUsableImage(metadata.getImageUrl()) && ArtistPhotoStore.hasUsableImage(image(node))) {
            metadata.setImageUrl(image(node));
        }
        if (metadata.getPlaycount() == null) metadata.setPlaycount(number(node.path("playcount")));
        if (metadata.getListeners() == null) metadata.setListeners(number(node.path("listeners")));
        if (metadata.getUrl() == null && !text(node, "url").isBlank()) metadata.setUrl(text(node, "url"));
        if (metadata.getMbid() == null && !text(node, "mbid").isBlank()) metadata.setMbid(text(node, "mbid"));
        if (media instanceof Musica track && track.getDuracao() == null) {
            Long seconds = number(node.path("duration"));
            if (seconds != null && seconds > 0) track.setDuracao(Duration.ofSeconds(seconds));
        }
        return media.getId();
    }

    private boolean compatibleArtist(Midia existing, String artist) {
        if (existing == null) return true;
        String target = CatalogName.key(artist);
        var linked = existing instanceof Album album ? album.getArtistas() : ((Musica) existing).getArtistas();
        String credit = existing.getLastFm().getArtistName();
        return linked.stream().anyMatch(item -> target.equals(CatalogName.key(item.getNome())))
                || (credit != null && !credit.isBlank() ? target.equals(CatalogName.key(credit)) : linked.isEmpty());
    }

    @Transactional
    public long save(String type, JsonNode node, boolean complete) {
        return switch (type) {
            case "track" -> song(node, complete).getId();
            case "album" -> album(node, complete).getId();
            case "artist" -> artist(node, complete).getId();
            default -> throw new IllegalArgumentException("Tipo desconhecido");
        };
    }

    public record Reference(String name, String artist, Instant updatedAt, Long albumId, List<Long> artistIds) {}

    @Transactional(readOnly = true)
    public Reference reference(String type, long id) {
        if (type.equals("artist")) return artists.findById(id).map(a ->
                new Reference(a.getNome(), "", a.getLastFm().getUpdatedAt(), null, List.of())).orElse(null);
        Midia media = type.equals("track") ? songs.findById(id).orElse(null) : albums.findById(id).orElse(null);
        if (media == null) return null;
        Set<Artista> related = media instanceof Musica m ? m.getArtistas() : ((Album) media).getArtistas();
        var ordered = related.stream().sorted(Comparator.comparing(Artista::getId)).toList();
        String primary = media.getLastFm().getArtistName();
        return new Reference(media.getTitulo(), primary != null ? primary : ordered.isEmpty() ? "" : ordered.get(0).getNome(),
                media.getLastFm().getUpdatedAt(), media instanceof Musica m && m.getAlbum() != null ? m.getAlbum().getId() : null,
                ordered.stream().map(Artista::getId).toList());
    }

    @Transactional(readOnly = true)
    public SearchResultDTO result(String term, Map<String, List<Long>> ids) {
        var result = new SearchResultDTO();
        result.setSongs(ids.getOrDefault("track", List.of()).stream().distinct().map(songs::findById)
                .flatMap(Optional::stream).map(SearchResultDTO.Song::from).toList());
        result.setArtists(ids.getOrDefault("artist", List.of()).stream().distinct().map(artists::findById)
                .flatMap(Optional::stream).map(SearchResultDTO.Artist::from).toList());
        result.setAlbums(ids.getOrDefault("album", List.of()).stream().distinct().map(albums::findById)
                .flatMap(Optional::stream).map(SearchResultDTO.AlbumResult::from).toList());
        addPeople(term, result);
        return result;
    }

    @Transactional(readOnly = true)
    public SearchResultDTO local(String term) {
        var result = new SearchResultDTO();
        result.setSongs(songs.searchCatalog(term, SEARCH_PAGE).stream().map(SearchResultDTO.Song::from).toList());
        result.setArtists(artists.searchCatalog(term, SEARCH_PAGE).stream().map(SearchResultDTO.Artist::from).toList());
        result.setAlbums(albums.searchCatalog(term, SEARCH_PAGE).stream().map(SearchResultDTO.AlbumResult::from).toList());
        addPeople(term, result);
        return result;
    }

    private void addPeople(String term, SearchResultDTO result) {
        var people = users.searchPeople(term, SEARCH_PAGE);
        result.setUsers(people.stream()
                .map(user -> new SearchUserDTO(user.id(), user.username(), user.fullName())).toList());
        result.setHasMoreUsers(people.hasNext());
    }
}
