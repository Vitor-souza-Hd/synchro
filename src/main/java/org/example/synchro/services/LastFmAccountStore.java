package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.UserDto;
import org.example.synchro.dto.lastFmDtos.LastFmSessionDto;
import org.example.synchro.dto.lastFmDtos.UserInfo;
import org.example.synchro.entities.LastFmSession;
import org.example.synchro.entities.User;
import org.example.synchro.entities.UserData;
import org.example.synchro.repositories.LastFmSessionRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.ResourceConflictException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LastFmAccountStore {
    private final UserRepository users;
    private final LastFmSessionRepository sessions;

    @Transactional
    public void connect(Long userId, LastFmSessionDto.Session authorized) {
        User user = findUser(userId);
        if (!authorized.getName().equalsIgnoreCase(user.getLastFmUsername()) && user.getData() != null) {
            // Never display the previous account's data if importing the new account fails.
            UserData data = user.getData();
            data.setScrobbles(null);
            data.setArtistas(null);
            data.setLastFmTracks(null);
            data.setLastFmAlbums(null);
            data.setLastFmRealName(null);
            data.setLastFmCountry(null);
            data.setLastFmImageUrl(null);
            data.setLastFmUpdatedAt(null);
        }
        LastFmSession session = user.getSession();
        if (session == null) session = new LastFmSession();
        session.setName(authorized.getName());
        session.setSessionKey(authorized.getKey());
        session.setSubscriber(authorized.getSubscriber());
        session.setSynchroUser(user);
        sessions.save(session);
        user.setLastFmUsername(authorized.getName());
        users.save(user);
    }

    @Transactional(readOnly = true)
    public String connectedUsername(Long userId) {
        User user = findUser(userId);
        if (!user.hasLastFmConnection()) {
            throw new ResourceConflictException("Conecte sua conta Last.fm primeiro.");
        }
        return user.getLastFmUsername();
    }

    @Transactional
    public UserDto saveProfile(Long userId, String expectedUsername, UserInfo info) {
        User user = findUser(userId);
        if (!user.hasLastFmConnection() || !expectedUsername.equalsIgnoreCase(user.getLastFmUsername())
                || !expectedUsername.equalsIgnoreCase(info.getName())) {
            throw new ResourceConflictException("A conta Last.fm mudou. Atualize o perfil novamente.");
        }
        UserData data = user.getData();
        if (data == null) {
            data = new UserData(user);
            user.setData(data);
        }
        // Keep the user's Synchro identity; Last.fm details have their own fields.
        data.setScrobbles(count(info.getPlaycount(), data.getScrobbles()));
        data.setArtistas(count(info.getArtistCount(), data.getArtistas()));
        data.setLastFmTracks(count(info.getTrackCount(), data.getLastFmTracks()));
        data.setLastFmAlbums(count(info.getAlbumCount(), data.getLastFmAlbums()));
        data.setLastFmRealName(info.getRealname());
        data.setLastFmCountry(info.getCountry());
        data.setLastFmImageUrl(profileImage(info));
        data.setLastFmUpdatedAt(Instant.now());
        return new UserDto(users.save(user));
    }

    private User findUser(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private Integer count(String value, Integer previous) {
        try {
            return Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(0, Long.parseLong(value))));
        } catch (NumberFormatException exception) {
            return previous;
        }
    }

    private String profileImage(UserInfo info) {
        if (info.getImages() == null) return null;
        var sizes = List.of("small", "medium", "large", "extralarge", "mega");
        return info.getImages().stream().filter(image -> image != null && safeImage(image.getUrl()))
                .max(Comparator.comparingInt(image -> image.getSize() == null ? -1 : sizes.indexOf(image.getSize())))
                .map(org.example.synchro.dto.lastFmDtos.ImageDto::getUrl).orElse(null);
    }

    private boolean safeImage(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
