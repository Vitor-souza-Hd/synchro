package org.example.synchro.importer;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Last.fm tags are free text. Only recognized music styles populate the genre field. */
public final class LastFmGenres {
    private LastFmGenres() {}
    private static final Set<String> STYLES = Set.of(
            "rock", "pop", "alternative", "alternative rock", "alternative metal", "alternative hip hop",
            "indie", "indie rock", "indie pop", "indie folk", "folk", "folk rock", "folk metal", "folk pop",
            "metal", "heavy metal", "death metal", "black metal", "thrash metal", "doom metal", "power metal",
            "progressive metal", "symphonic metal", "nu metal", "metalcore", "deathcore", "grindcore",
            "hard rock", "classic rock", "progressive rock", "psychedelic rock", "post rock", "post punk",
            "punk", "punk rock", "pop punk", "hardcore", "hardcore punk", "post hardcore", "emo", "screamo",
            "shoegaze", "dream pop", "grunge", "garage rock", "noise rock", "math rock", "gothic rock",
            "hip hop", "rap", "trap", "boom bap", "cloud rap", "emo rap", "gangsta rap", "drill", "phonk",
            "electronic", "electronica", "edm", "house", "deep house", "tech house", "progressive house",
            "techno", "trance", "dubstep", "drum and bass", "breakbeat", "ambient", "downtempo", "trip hop",
            "idm", "industrial", "electro", "electropop", "synthpop", "synthwave", "vaporwave", "hyperpop",
            "dance", "dance pop", "disco", "eurodance", "uk garage", "dub", "jungle", "hardstyle",
            "jazz", "smooth jazz", "jazz fusion", "blues", "blues rock", "soul", "neo soul", "r&b", "funk",
            "country", "americana", "bluegrass", "reggae", "ska", "reggaeton", "dancehall", "latin pop",
            "classical", "opera", "orchestral", "chamber music", "soundtrack", "instrumental", "experimental",
            "gospel", "christian rock", "christian pop", "mpb", "samba", "bossa nova", "pagode", "sertanejo",
            "forró", "axé", "brega", "brazilian funk", "funk carioca", "funk paulista", "piseiro", "tropicália",
            "k pop", "j pop", "j rock", "afrobeat", "afrobeats", "amapiano", "world", "new age", "lo fi");

    public static List<String> fromTags(JsonNode response) {
        var tags = response.path("tag");
        var rows = new ArrayList<JsonNode>();
        if (tags.isArray()) tags.forEach(rows::add);
        else if (tags.isObject()) rows.add(tags);
        rows.sort(Comparator.comparingInt((JsonNode tag) -> tag.path("count").asInt()).reversed());
        var result = new LinkedHashSet<String>();
        for (var tag : rows) {
            String style = tag.path("name").asText().strip().toLowerCase(Locale.ROOT)
                    .replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ");
            style = switch (style) {
                case "hiphop" -> "hip hop";
                case "rnb", "rhythm and blues", "rhythm & blues" -> "r&b";
                case "dnb", "drum & bass", "drum n bass" -> "drum and bass";
                case "synth pop" -> "synthpop";
                case "kpop" -> "k pop";
                case "jpop" -> "j pop";
                case "postrock" -> "post rock";
                case "lofi" -> "lo fi";
                case "musica popular brasileira", "música popular brasileira" -> "mpb";
                case "forro" -> "forró";
                case "axe" -> "axé";
                case "tropicalia" -> "tropicália";
                default -> style;
            };
            if (STYLES.contains(style)) result.add(style);
            if (result.size() == 3) break;
        }
        return List.copyOf(result);
    }
}
