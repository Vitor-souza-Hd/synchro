package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.ProfileSelectionDto;
import org.example.synchro.dto.RecentReviewDto;
import org.example.synchro.dto.ReviewDto;
import org.example.synchro.entities.Album;
import org.example.synchro.entities.Artista;
import org.example.synchro.entities.Musica;
import org.springframework.data.domain.PageRequest;
import org.example.synchro.dto.ReviewRequest;
import org.example.synchro.entities.Midia;
import org.example.synchro.entities.Review;
import org.example.synchro.entities.User;
import org.example.synchro.entities.UserRole;
import org.example.synchro.entities.UserData;
import org.example.synchro.repositories.ArtistaRepository;
import org.example.synchro.repositories.MidiaRepository;
import org.example.synchro.repositories.MusicaRepository;
import org.example.synchro.repositories.ReviewRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.InvalidTokenException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.synchro.dto.MidiaRatingDto;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Transactional
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final MusicaRepository musicaRepository;
    private final MidiaRepository midiaRepository;
    private final ArtistaRepository artistRepository;
    private static final int PUBLIC_PAGE_SIZE = 12;
    private final CookieService cookieService;

    public ReviewDto postReview(
            String cookie,
            ReviewRequest review,
            Long midiaId
    ) {
        if (!cookieService.checkCookie(cookie)) {
            throw new InvalidTokenException("Token invalido");
        }

        Long userId = cookieService.getId(cookie);

        Optional<User> userOptional = userRepository.findById(userId);
        Optional<Midia> midiaOptional = midiaRepository.findById(midiaId);

        User user = userOptional.orElseThrow(
                () -> new ResourceNotFoundException("User not found")
        );

        Midia midia = midiaOptional.orElseThrow(
                () -> new ResourceNotFoundException("Midia not found")
        );

        UserData userData = user.getData();

        Optional<Review> reviewExistente =
                reviewRepository.findByAuthorIdAndMidiaId(
                        userData.getId(),
                        midiaId
                );

        Review reviewEntity;

        if (reviewExistente.isPresent()) {
            reviewEntity = reviewExistente.get();
            reviewEntity.setRating(review.getRating());
            reviewEntity.setReview(review.getReview());
        } else {
            reviewEntity = new Review(
                    review.getRating(),
                    review.getReview(),
                    midia,
                    userData
            );
        }

        reviewRepository.save(reviewEntity);

        return new ReviewDto(reviewEntity);
    }

    // =====================================================
    // AVALIACOES DO USUARIO SYNCHRO AUTENTICADO
    // =====================================================

    @Transactional(readOnly = true)
    public List<ReviewDto> listarMinhasReviews(String cookie) {

        if (cookie == null ||
                cookie.isBlank() ||
                !cookieService.checkCookie(cookie)) {

            throw new InvalidTokenException(
                    "Sessao Synchro invalida"
            );
        }

        Long userId = cookieService.getId(cookie);

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                    "Usuario nao encontrado"
            );
        }

        return reviewRepository
                .findReviewsBySynchroUserId(userId)
                .stream()
                .map(review -> {
                    var dto = new ReviewDto(review);
                    dto.setArtistName(reviewArtistName(review.getMidia()));
                    dto.setCoverUrl(reviewCover(review));
                    return dto;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecentReviewDto> recentPublicReviews() {
        return reviewRepository.findRecentPublicReviews(PageRequest.of(0, 6)).stream()
                .map(this::toRecentReview).toList();
    }

    public record PublicReviewPage(List<RecentReviewDto> items, Long nextBefore) {}
    public record ArtistReviews(PublicReviewPage reviews, ReviewDto own, boolean signedIn, Double average, long total) {}

    public ReviewDto postArtistReview(String cookie, ReviewRequest request, long artistId) {
        Long userId = viewerId(cookie);
        if (userId == null) throw new InvalidTokenException("Entre para avaliar artistas.");
        var user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        var artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new ResourceNotFoundException("Artista não encontrado."));
        var existing = reviewRepository.findByAuthorUserIdAndArtistId(userId, artistId);
        var review = existing.orElseGet(() -> new Review(request.getRating(), request.getReview(), artist, user.getData()));
        review.setRating(request.getRating());
        review.setReview(request.getReview());
        return new ReviewDto(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public ArtistReviews artistReviews(String cookie, long artistId, long before) {
        if (!artistRepository.existsById(artistId)) throw new ResourceNotFoundException("Artista não encontrado.");
        Long viewer = viewerId(cookie);
        var own = viewer == null ? null
                : reviewRepository.findByAuthorUserIdAndArtistId(viewer, artistId).map(ReviewDto::new).orElse(null);
        var page = publicPage(reviewRepository.findPublicReviewsByArtist(artistId, before,
                PageRequest.of(0, PUBLIC_PAGE_SIZE + 1)));
        return new ArtistReviews(page, own, viewer != null, reviewRepository.averageForArtist(artistId),
                reviewRepository.countByArtistId(artistId));
    }

    private Long viewerId(String cookie) {
        try {
            return cookieService.checkCookie(cookie) ? cookieService.getId(cookie) : null;
        } catch (InvalidTokenException | NumberFormatException ignored) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public PublicReviewPage publicReviewsByUser(long userId, long before) {
        return publicPage(reviewRepository.findPublicReviewsByUser(userId, before,
                PageRequest.of(0, PUBLIC_PAGE_SIZE + 1)));
    }

    @Transactional(readOnly = true)
    public List<RecentReviewDto> reviewsOnProfile(long userId) {
        var available = reviewRepository.findProfileItems(userId).stream().map(ReviewRepository.ProfileItem::getId).toList();
        String selected = available.size() > ProfileSelectionDto.LIMIT ? userRepository.findProfileReviewSelection(userId) : null;
        var visible = ProfileSelection.visibleIds(available, selected);
        if (visible.isEmpty()) return List.of();
        return reviewRepository.findProfileReviews(userId, visible).stream().map(this::toRecentReview).toList();
    }

    @Transactional(readOnly = true)
    public ProfileSelectionDto reviewSelection(String cookie) {
        long userId = requireReviewUserId(cookie);
        var items = reviewRepository.findProfileItems(userId).stream()
                .map(row -> new ProfileSelectionDto.Item(row.getId(), row.getTitle())).toList();
        return ProfileSelection.options(items, userRepository.findProfileReviewSelection(userId));
    }

    @Transactional
    public void saveReviewSelection(String cookie, List<Long> ids) {
        long userId = requireReviewUserId(cookie);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        var available = reviewRepository.findProfileItems(userId).stream().map(ReviewRepository.ProfileItem::getId).toList();
        user.setProfileReviewSelection(ProfileSelection.checkedValue(available, ids));
    }

    private long requireReviewUserId(String cookie) {
        Long userId = viewerId(cookie);
        if (userId == null) throw new InvalidTokenException("Entre para editar seu perfil.");
        return userId;
    }

    private PublicReviewPage publicPage(List<Review> reviews) {
        var items = reviews.stream().limit(PUBLIC_PAGE_SIZE).map(this::toRecentReview).toList();
        Long nextBefore = reviews.size() > PUBLIC_PAGE_SIZE ? items.get(PUBLIC_PAGE_SIZE - 1).id() : null;
        return new PublicReviewPage(items, nextBefore);
    }

    private RecentReviewDto toRecentReview(Review review) {
        var author = review.getAuthor();
        var account = author.getUser();
        var targetArtist = review.getArtist();
        String cover = reviewCover(review);
        if (targetArtist != null) {
            return new RecentReviewDto(review.getId(), review.getRating(), review.getReview(), account.getId(),
                    account.getUsername(), author.getLastFmImageUrl(), targetArtist.getId(), "artist",
                    targetArtist.getNome(), null, cover, author.getProfileImageUrl(), account.getRole() == UserRole.VVIP);
        }

        var media = review.getMidia();
        return new RecentReviewDto(review.getId(), review.getRating(), review.getReview(), account.getId(),
                account.getUsername(), author.getLastFmImageUrl(), media.getId(), media instanceof Musica ? "track" : "album",
                media.getTitulo(), reviewArtistName(media), cover, author.getProfileImageUrl(), account.getRole() == UserRole.VVIP);
    }

    private String reviewArtistName(Midia media) {
        if (media == null) return null;
        String artist = media.getLastFm().getArtistName();
        if (artist != null && !artist.isBlank()) return artist;
        if (media instanceof Musica track) {
            return track.getArtistas().stream().map(Artista::getNome).sorted().findFirst().orElse(null);
        }
        if (media instanceof Album album) {
            return album.getArtistas().stream().map(Artista::getNome).sorted().findFirst().orElse(null);
        }
        return null;
    }

    private String reviewCover(Review review) {
        var artist = review.getArtist();
        if (artist != null) {
            String photo = artist.getImgUrl();
            return ArtistPhotoStore.hasUsableImage(photo) ? photo : artist.getLastFm().getImageUrl();
        }

        var media = review.getMidia();
        if (media == null) return null;
        String cover = media.getLastFm().getImageUrl();
        if ((cover == null || cover.isBlank()) && media instanceof Musica track && track.getAlbum() != null) {
            return track.getAlbum().getLastFm().getImageUrl();
        }
        return cover;
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> listarTodas() {
        return reviewRepository.findAll()
                .stream()
                .map(ReviewDto::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public MidiaRatingDto buscarMediaPorMidia(Long midiaId) {

        Double media = reviewRepository.calcularMediaPorMidia(midiaId);
        Long total = reviewRepository.contarReviewsPorMidia(midiaId);

        return new MidiaRatingDto(
                midiaId,
                media != null
                        ? Math.round(media * 100.0) / 100.0
                        : 0.0,
                total != null ? total : 0L
        );

    }
    public MidiaRatingDto getRatingDaMidia(Long midiaId) {
        Double media = reviewRepository.calcularMediaPorMidia(midiaId);
        Long total = reviewRepository.contarReviewsPorMidia(midiaId);

        // Arredonda para 1 casa decimal
        Double mediaArredondada = (media != null)
                ? Math.round(media * 10.0) / 10.0
                : 0.0;

        Long totalFinal = (total != null) ? total : 0L;

        return new MidiaRatingDto(midiaId, mediaArredondada, totalFinal);
    }
    // 4 teste para atualizar/apagar review
    public ReviewDto updateReview(String cookie, Long reviewId, ReviewRequest request) {
        if (!cookieService.checkCookie(cookie)) {
            throw new InvalidTokenException("Token invalido");
        }

        Long userId = cookieService.getId(cookie);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        // Verifica se o usuário logado é o autor
        if (!review.getAuthor().getUser().getId().equals(userId)) {
            throw new InvalidTokenException("Você não é o autor desta review");
        }

        review.setRating(request.getRating());
        review.setReview(request.getReview());
        reviewRepository.save(review);

        return new ReviewDto(review);
    }

    @Transactional
    public void deleteReview(String cookie, Long reviewId) {
        if (!cookieService.checkCookie(cookie)) {
            throw new InvalidTokenException("Token invalido");
        }

        Long userId = cookieService.getId(cookie);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        if (!review.getAuthor().getUser().getId().equals(userId) && !userRepository.canModerate(userId)) {
            throw new InvalidTokenException("Você não é o autor desta review");
        }

        reviewRepository.delete(review);
    }
}
