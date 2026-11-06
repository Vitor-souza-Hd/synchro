package org.example.synchro.services;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Read only photos linked to this artist's gallery, never avatars or recommendations. */
public final class LastFmArtistPage {
    private LastFmArtistPage() {}
    private static final Set<String> IMAGE_HOSTS = Set.of("lastfm.freetls.fastly.net", "lastfm-img2.akamaized.net", "userserve-ak.last.fm");
    private static class Candidate {
        final String hash;
        String image;
        Candidate(String hash) { this.hash = hash; }
        String url() { return image != null ? image : "https://lastfm.freetls.fastly.net/i/u/300x300/" + hash + ".jpg"; }
    }

    public static Optional<String> photo(String html, String artist) {
        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains("cf-chl-") || lower.contains("challenge-platform")) {
            throw new IllegalStateException("A página de fotos do Last.fm está indisponível.");
        }
        List<Candidate> photos = new ArrayList<>();
        var parser = new HTMLEditorKit.ParserCallback() {
            Candidate current;
            boolean gallery;
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int position) {
                inspect(tag, attrs);
            }
            @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attrs, int position) {
                inspect(tag, attrs);
            }
            private void inspect(HTML.Tag tag, MutableAttributeSet attrs) {
                if (attribute(attrs, "class").contains("image-list")) gallery = true;
                if (tag == HTML.Tag.A) {
                    current = null;
                    String hash = galleryHash(attribute(attrs, "href"), artist);
                    if (hash != null) {
                        gallery = true;
                        current = new Candidate(hash);
                        photos.add(current);
                    }
                } else if (tag == HTML.Tag.IMG && current != null) {
                    for (String key : List.of("data-src", "src")) {
                        String url = attribute(attrs, key);
                        if (validImage(url, current.hash)) { current.image = url.startsWith("//") ? "https:" + url : url; break; }
                    }
                }
            }
            @Override public void handleEndTag(HTML.Tag tag, int position) { if (tag == HTML.Tag.A) current = null; }
        };
        try { new ParserDelegator().parse(new StringReader(html), parser, true); }
        catch (IOException error) { throw new IllegalStateException("Não foi possível ler a galeria do Last.fm.", error); }
        if (!parser.gallery) throw new IllegalStateException("A resposta do Last.fm não contém uma galeria de fotos.");
        return photos.stream().map(Candidate::url).findFirst();
    }

    private static String galleryHash(String href, String artist) {
        try {
            URI uri = URI.create(href);
            if (uri.getHost() != null && !Set.of("last.fm", "www.last.fm").contains(uri.getHost())) return null;
            String[] parts = uri.getRawPath().split("/");
            if (parts.length != 5 || !parts[1].equals("music") || !parts[3].equals("+images")) return null;
            String name = URLDecoder.decode(parts[2], StandardCharsets.UTF_8);
            if (!name.equalsIgnoreCase(artist) && !URLDecoder.decode(name, StandardCharsets.UTF_8).equalsIgnoreCase(artist)) return null;
            String hash = parts[4];
            return hash.matches("[a-fA-F0-9]{32}") && !hash.equalsIgnoreCase("2a96cbd8b46e442fc41c2b86b821562f") ? hash : null;
        } catch (IllegalArgumentException | NullPointerException ignored) { return null; }
    }

    private static boolean validImage(String url, String hash) {
        try {
            var uri = URI.create(url.startsWith("//") ? "https:" + url : url);
            return "https".equalsIgnoreCase(uri.getScheme()) && IMAGE_HOSTS.contains(uri.getHost())
                    && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getPath().contains("/" + hash + ".");
        } catch (IllegalArgumentException | NullPointerException ignored) { return false; }
    }
    private static String attribute(MutableAttributeSet attrs, String name) {
        var names = attrs.getAttributeNames();
        while (names.hasMoreElements()) {
            Object key = names.nextElement();
            if (key.toString().equalsIgnoreCase(name)) return String.valueOf(attrs.getAttribute(key));
        }
        return "";
    }
}
