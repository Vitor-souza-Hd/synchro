package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.UserSearchResult;
import org.example.synchro.services.CommunityPollService.PollRequest;
import org.example.synchro.services.CommunityPollService.PollView;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.CommunityPost;
import org.example.synchro.entities.Midia;
import org.example.synchro.entities.Musica;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.AlbumRepository;
import org.example.synchro.repositories.ArtistaRepository;
import org.example.synchro.repositories.CommunityPostRepository;
import org.example.synchro.repositories.MusicaRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.InvalidTokenException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostService {
    public static final int MAX_LENGTH = 2000;
    private static final int PAGE_SIZE = 12;
    private final CommunityPostRepository posts;
    private final UserRepository users;
    private final CookieService cookies;
    private final ArtistaRepository artists;
    private final AlbumRepository albums;
    private final MusicaRepository songs;
    private final PostInteractionService interactions;
    private final CommunityPollService polls;
    private final PostInteractionNotifications notifications;

    public enum Scope { ALL, FOLLOWING, SAVED }

    public record AttachmentView(String type, Long id, String title, String artist, String coverUrl) {}
    public record PostView(Long id, String body, Instant createdAt, Instant editedAt, UserSearchResult author,
                           String profileImageUrl, String lastFmImageUrl, boolean own, AttachmentView attachment,
                           PostInteractionService.View interaction, PollView poll) {}
    public record Feed(List<PostView> items, Long nextBefore, boolean canPublish, UserSearchResult author, boolean signedIn) {}
    public record FocusedPost(PostView post, boolean signedIn) {}
    private record Attachment(Artista artist, Midia media) {}

    public Feed feed(String token, Long before, Long authorId, Scope scope) {
        Long viewer = currentViewer(token);
        if (scope != Scope.ALL && viewer == null) throw new UnauthorizedException("Entre para acessar este feed.");
        if (scope != Scope.ALL && authorId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma lista do feed.");
        }
        UserSearchResult author = authorId == null ? null : publicAuthor(users.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado.")));
        long cursor = before == null ? Long.MAX_VALUE : before;
        var page = PageRequest.of(0, PAGE_SIZE + 1);
        var entries = switch (scope) {
            case FOLLOWING -> posts.findFollowing(viewer, cursor, page);
            case SAVED -> posts.findSaved(viewer, cursor, page);
            case ALL -> authorId == null ? posts.findByIdLessThanOrderByIdDesc(cursor, page)
                    : posts.findByAuthorIdAndIdLessThanOrderByIdDesc(authorId, cursor, page);
        };
        var visible = entries.stream().limit(PAGE_SIZE).toList();
        var ids = visible.stream().map(CommunityPost::getId).toList();
        var reactions = interactions.views(ids, viewer);
        var postPolls = polls.findForPosts(ids, viewer);
        var items = visible.stream().map(post -> view(post, viewer,
                reactions.getOrDefault(post.getId(), PostInteractionService.EMPTY),
                postPolls.get(post.getId()))).toList();
        Long nextBefore = entries.size() > PAGE_SIZE ? items.get(PAGE_SIZE - 1).id() : null;
        boolean canPublish = viewer != null && (authorId == null || viewer.equals(authorId));
        return new Feed(items, nextBefore, canPublish, author, viewer != null);
    }

    public FocusedPost focusedPost(String token, long id) {
        Long viewer = currentViewer(token);
        var post = posts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Publicação não encontrada."));
        return new FocusedPost(view(post, viewer, interactionFor(post, viewer), polls.findForPost(id, viewer)),
                viewer != null);
    }

    @Transactional
    public PostView publish(String token, String text, String attachmentType, Long attachmentId, PollRequest poll) {
        Long viewer = requireViewer(token);
        var author = users.findById(viewer).orElseThrow(() -> new UnauthorizedException("Sessão inválida."));
        var attachment = resolveAttachment(attachmentType, attachmentId);
        var post = posts.save(new CommunityPost(author, postBody(text, poll != null), attachment.artist(), attachment.media()));
        polls.create(post, poll);
        notifications.notifyPostMentions(post);
        return view(post, viewer, PostInteractionService.EMPTY, polls.findForPost(post.getId(), viewer));
    }

    @Transactional
    public PostView edit(String token, long id, String text, String attachmentType, Long attachmentId) {
        Long viewer = requireViewer(token);
        var post = ownedPost(id, viewer);
        var attachment = resolveAttachment(attachmentType, attachmentId);
        var poll = polls.findForPost(id, viewer);
        String body = postBody(text, poll != null);
        boolean textChanged = !post.getBody().equals(body);
        post.edit(body, attachment.artist(), attachment.media());
        if (textChanged) notifications.notifyPostMentions(post);
        return view(post, viewer, interactionFor(post, viewer), poll);
    }

    @Transactional
    public void delete(String token, long id) {
        posts.delete(ownedPost(id, requireViewer(token)));
    }

    private static String postBody(String text, boolean hasPoll) {
        String body = text == null ? "" : text.strip();
        if (!hasPoll && body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escreva algo ou adicione uma enquete.");
        }
        return body;
    }

    private PostInteractionService.View interactionFor(CommunityPost post, Long viewer) {
        return interactions.views(List.of(post.getId()), viewer).getOrDefault(post.getId(), PostInteractionService.EMPTY);
    }

    private CommunityPost ownedPost(long id, Long viewer) {
        var post = posts.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Publicação não encontrada."));
        if (!post.getAuthor().getId().equals(viewer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente o autor pode alterar esta publicação.");
        }
        return post;
    }

    private Long currentViewer(String token) {
        Long viewer = viewerId(token);
        return viewer != null && users.existsById(viewer) ? viewer : null;
    }

    private Attachment resolveAttachment(String type, Long id) {
        if (type == null && id == null) return new Attachment(null, null);
        var missing = new ResponseStatusException(HttpStatus.BAD_REQUEST, "O item selecionado não está no catálogo.");
        return switch (type) {
            case "artist" -> new Attachment(artists.findById(id).orElseThrow(() -> missing), null);
            case "album" -> new Attachment(null, albums.findById(id).orElseThrow(() -> missing));
            case "track" -> new Attachment(null, songs.findById(id).orElseThrow(() -> missing));
            default -> throw missing;
        };
    }

    private Long requireViewer(String token) {
        Long viewer = viewerId(token);
        if (viewer == null) throw new UnauthorizedException("Entre na sua conta para publicar.");
        return viewer;
    }

    private Long viewerId(String token) {
        try {
            return cookies.checkCookie(token) ? cookies.getId(token) : null;
        } catch (InvalidTokenException | NumberFormatException ignored) {
            return null;
        }
    }

    private static UserSearchResult publicAuthor(User author) {
        var data = author.getData();
        return new UserSearchResult(author.getId(), author.getUsername(), data == null ? null : data.getFullName());
    }

    private static PostView view(CommunityPost post, Long viewer, PostInteractionService.View reaction, PollView poll) {
        var author = post.getAuthor();
        var data = author.getData();
        return new PostView(post.getId(), post.getBody(), post.getCreatedAt(), post.getEditedAt(), publicAuthor(author),
                data == null ? null : data.getProfileImageUrl(), data == null ? null : data.getLastFmImageUrl(),
                author.getId().equals(viewer), attachmentView(post), reaction, poll);
    }

    private static AttachmentView attachmentView(CommunityPost post) {
        var artist = post.getAttachedArtist();
        if (artist != null) {
            String image = ArtistPhotoStore.hasUsableImage(artist.getImgUrl())
                    ? artist.getImgUrl() : artist.getLastFm().getImageUrl();
            return new AttachmentView("artist", artist.getId(), artist.getNome(), null, image);
        }

        // Read the concrete catalogue type even when the relation is a lazy JPA proxy.
        var media = org.hibernate.Hibernate.unproxy(post.getAttachedMedia());
        if (media instanceof Album album) {
            return mediaAttachment("album", album, album.getArtistas(), null);
        }
        if (media instanceof Musica song) {
            String backup = song.getAlbum() == null ? null : song.getAlbum().getLastFm().getImageUrl();
            return mediaAttachment("track", song, song.getArtistas(), backup);
        }
        return null;
    }

    private static AttachmentView mediaAttachment(String type, Midia media, Set<Artista> artists, String backupCover) {
        var metadata = media.getLastFm();
        String artist = metadata.getArtistName();
        if (artist == null || artist.isBlank()) {
            artist = artists.stream().map(Artista::getNome).sorted().findFirst().orElse(null);
        }
        String cover = metadata.getImageUrl();
        if (!ArtistPhotoStore.hasUsableImage(cover)) cover = backupCover;
        return new AttachmentView(type, media.getId(), media.getTitulo(), artist, cover);
    }
}
