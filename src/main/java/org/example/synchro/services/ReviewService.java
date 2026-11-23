package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
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
                .map(ReviewDto::new)
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

    private PublicReviewPage publicPage(List<Review> reviews) {
        var items = reviews.stream().limit(PUBLIC_PAGE_SIZE).map(this::toRecentReview).toList();
        Long nextBefore = reviews.size() > PUBLIC_PAGE_SIZE ? items.get(PUBLIC_PAGE_SIZE - 1).id() : null;
        return new PublicReviewPage(items, nextBefore);
    }

    private RecentReviewDto toRecentReview(Review review) {
        var author = review.getAuthor();
        var account = author.getUser();
        var targetArtist = review.getArtist();
        if (targetArtist != null) {
            String photo = targetArtist.getImgUrl();
            if (!ArtistPhotoStore.hasUsableImage(photo)) photo = targetArtist.getLastFm().getImageUrl();
            return new RecentReviewDto(review.getId(), review.getRating(), review.getReview(), account.getId(),
                    account.getUsername(), author.getLastFmImageUrl(), targetArtist.getId(), "artist",
                    targetArtist.getNome(), null, photo, author.getProfileImageUrl());
        }

        var media = review.getMidia();
        var track = media instanceof Musica musica ? musica : null;
        var artists = track == null ? ((Album) media).getArtistas() : track.getArtistas();
        var metadata = media.getLastFm();
        String artist = metadata.getArtistName();
        if (artist == null || artist.isBlank()) {
            artist = artists.stream().map(Artista::getNome).sorted().findFirst().orElse(null);
        }
        String cover = metadata.getImageUrl();
        if ((cover == null || cover.isBlank()) && track != null && track.getAlbum() != null) {
            cover = track.getAlbum().getLastFm().getImageUrl();
        }
        return new RecentReviewDto(review.getId(), review.getRating(), review.getReview(), account.getId(),
                account.getUsername(), author.getLastFmImageUrl(), media.getId(), track == null ? "album" : "track",
                media.getTitulo(), artist, cover, author.getProfileImageUrl());
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

    public void deleteReview(String cookie, Long reviewId) {
        if (!cookieService.checkCookie(cookie)) {
            throw new InvalidTokenException("Token invalido");
        }

        Long userId = cookieService.getId(cookie);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        if (!review.getAuthor().getUser().getId().equals(userId)) {
            throw new InvalidTokenException("Você não é o autor desta review");
        }

        reviewRepository.delete(review);
    }
}
