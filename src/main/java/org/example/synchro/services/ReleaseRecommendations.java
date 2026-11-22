package org.example.synchro.services;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ReleaseRecommendations {
    private static final int LIMIT = DailyPopularSelections.SIZE;
    private static final int BRAZILIAN_SLOTS = 3;
    private static final double[] GENRE_WEIGHTS = {1.0, 0.7, 0.45, 0.35, 0.25};
    private static final List<Set<String>> GENRE_FAMILIES = List.of(
            Set.of("hip hop", "rap", "trap", "hip hop/rap", "rap nacional", "trap br", "brazilian hip hop", "emo rap"),
            Set.of("rock", "alternative rock", "indie rock", "rock alternativo", "pop rock", "grunge",
                    "brazilian rock", "rock progressivo", "progressive rock", "rock psicodelico", "psychedelic rock", "punk rock"),
            Set.of("alternative", "alternativo", "indie", "alternative rock", "rock alternativo", "indie rock", "indie pop", "shoegaze"),
            Set.of("pop", "art pop", "dream pop", "indie pop", "brazilian pop", "electropop", "pop rock"),
            Set.of("electronic", "eletronica", "electro", "dance", "techno", "house", "drum and bass", "ambient", "electropop"),
            Set.of("r&b", "rnb", "soul", "r&b/soul", "neo soul", "rhythm and blues"),
            Set.of("metal", "heavy metal", "black metal", "death metal", "nu metal"),
            Set.of("classical", "musica classica", "classica"),
            Set.of("mpb", "musica popular brasileira", "brazilian popular music", "bossa nova"),
            Set.of("baile funk", "funk brasileiro", "funk carioca")
    );
    private static final Set<String> BRAZILIAN_TAGS = Set.of("brazil", "brasil", "brazilian", "brasileiro", "mpb",
            "samba", "pagode", "sertanejo", "forro", "bossa nova", "brazilian rock", "brazilian pop", "rap nacional",
            "trap br", "brazilian hip hop", "baile funk", "funk brasileiro", "funk carioca", "musica popular brasileira");
    private static final Set<String> NON_GENRE_TAGS = Set.of("brazil", "brasil", "brazilian", "brasileiro", "music", "musica");

    private ReleaseRecommendations() {}

    public record Album(String name, String artist, LocalDate releaseDate, List<String> images, long listeners, long plays) {}
    public record Candidate(Album album, Set<String> tags, int rank) {}
    public record Selection(List<Album> albums, int matchedCount) {}
    private record Match(Candidate candidate, double affinity) {}

    public static List<String> profile(List<String> requestedGenres) {
        return requestedGenres.stream().map(ReleaseRecommendations::genreKey).filter(genre -> !genre.isEmpty())
                .distinct().limit(GENRE_WEIGHTS.length).toList();
    }

    public static Selection select(List<Candidate> candidates, List<String> profile, LocalDate today) {
        var earliest = today.minusDays(30);
        var ranked = candidates.stream()
                .filter(item -> !item.album().releaseDate().isBefore(earliest) && !item.album().releaseDate().isAfter(today))
                .map(item -> new Match(item, affinity(profile, item.tags())))
                .sorted(Comparator.comparingDouble(Match::affinity).reversed()
                        .thenComparing(Comparator.comparingLong((Match item) -> item.candidate().album().listeners()).reversed())
                        .thenComparing(Comparator.comparingLong((Match item) -> item.candidate().album().plays()).reversed())
                        .thenComparingInt(item -> item.candidate().rank())
                        .thenComparing(item -> item.candidate().album().name(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(item -> item.candidate().album().artist(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        var selected = new LinkedHashSet<Match>();
        ranked.stream().filter(item -> item.affinity() > 0 && isBrazilian(item.candidate().tags()))
                .limit(BRAZILIAN_SLOTS).forEach(selected::add);
        for (var match : ranked) {
            if (selected.size() == LIMIT) break;
            selected.add(match);
        }
        var result = ranked.stream().filter(selected::contains).toList();
        int matched = (int) result.stream().filter(item -> item.affinity() > 0).count();
        return new Selection(result.stream().map(item -> item.candidate().album()).toList(), matched);
    }

    public static boolean isBrazilian(Set<String> tags) {
        return tags.stream().anyMatch(BRAZILIAN_TAGS::contains);
    }

    public static boolean hasGenreTags(Set<String> tags) {
        return tags.stream().anyMatch(tag -> !NON_GENRE_TAGS.contains(tag));
    }

    public static String genreKey(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ").strip();
    }

    static double affinity(List<String> profile, Set<String> tags) {
        double score = 0;
        for (int i = 0; i < profile.size(); i++) {
            String genre = profile.get(i);
            if (tags.contains(genre)) {
                score += GENRE_WEIGHTS[i];
            } else if (GENRE_FAMILIES.stream().anyMatch(family ->
                    family.contains(genre) && tags.stream().anyMatch(family::contains))) {
                score += GENRE_WEIGHTS[i] * 0.5;
            }
        }
        return score;
    }
}
