package org.example.synchro;

import org.example.synchro.services.LastFmArtistPage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LastFmArtistPageTests {
    private static final String HASH = "0123456789abcdef0123456789abcdef";
    private static final String PHOTO = "https://lastfm.freetls.fastly.net/i/u/300x300/" + HASH + ".jpg";

    @Test
    void readsOnlyThisArtistsGalleryPhotoAndIgnoresAvatarsAndRecommendations() {
        String html = "<img src='https://lastfm.freetls.fastly.net/avatar.jpg'>" +
                "<a href='/music/Another/+images/" + HASH + "'><img src='" + PHOTO + "'></a>" +
                "<ul class='image-list'><li><a href='/music/slowGM/+images/" + HASH + "'>" +
                "<img data-src='" + PHOTO + "'></a></li></ul>";
        assertEquals(PHOTO, LastFmArtistPage.photo(html, "slowGM").orElseThrow());
    }

    @Test
    void supportsEncodedArtistNamesAndRejectsForeignImageHosts() {
        String html = "<ul class='image-list'><a href='https://www.last.fm/music/AC%252FDC/+images/" + HASH + "'>" +
                "<img src='https://other.test/" + HASH + ".jpg'></a></ul>";
        assertEquals(PHOTO, LastFmArtistPage.photo(html, "AC/DC").orElseThrow(), "Use a Last.fm CDN URL derived from the gallery ID");
        assertTrue(LastFmArtistPage.photo(html, "Different Artist").isEmpty());
    }

    @Test
    void rejectsGenericImagesAndUntrustedLinks() {
        String html = "<ul class='image-list'><a href='/music/slowGM/+images/2a96cbd8b46e442fc41c2b86b821562f'>" +
                "<img src='https://lastfm.freetls.fastly.net/2a96cbd8b46e442fc41c2b86b821562f.jpg'></a>" +
                "<a href='https://other.test/music/slowGM/+images/" + HASH + "'>Photo</a></ul>";
        assertTrue(LastFmArtistPage.photo(html, "slowGM").isEmpty());
    }

    @Test
    void blockedOrChangedPagesAreFailuresRatherThanEvidenceThatNoPhotoExists() {
        assertThrows(IllegalStateException.class, () -> LastFmArtistPage.photo("<script src='/cdn-cgi/challenge-platform/test.js'></script>", "slowGM"));
        assertThrows(IllegalStateException.class, () -> LastFmArtistPage.photo("<html><h1>Please sign in</h1></html>", "slowGM"));
        assertTrue(LastFmArtistPage.photo("<ul class='image-list'></ul>", "slowGM").isEmpty());
    }
}
