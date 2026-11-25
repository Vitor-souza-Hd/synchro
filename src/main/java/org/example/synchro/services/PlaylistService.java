package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.PlaylistDto;
import org.example.synchro.dto.PlaylistRequestDto;
import org.example.synchro.dto.PlaylistSummaryDto;
import org.example.synchro.dto.ProfileSelectionDto;
import org.example.synchro.dto.SearchUserDTO;
import org.example.synchro.entities.*;
import org.example.synchro.repositories.MidiaRepository;
import org.example.synchro.repositories.PlaylistRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.InvalidTokenException;
import org.example.synchro.services.exception.ResourceConflictException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Base64;
import java.util.Set;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final CookieService cookieService;
    private final MidiaRepository midiaRepository;

    @Transactional(readOnly = true)
    public List<PlaylistDto> findMyPlaylists(String cookie) {
        if (!cookieService.checkCookie(cookie)) {
            throw new UnauthorizedException("Sessão inválida ou ausente");
        }
        User user = userRepository.findById(cookieService.getId(cookie))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        var playlists = new LinkedHashMap<Long, Playlist>();
        playlistRepository.findByCriador(user).forEach(p -> playlists.put(p.getId(), p));
        playlistRepository.findByColaboradoresContaining(user).forEach(p -> playlists.put(p.getId(), p));
        return playlists.values().stream()
                .sorted(java.util.Comparator.comparing(Playlist::getId).reversed())
                .map(playlist -> new PlaylistDto(playlist, user)).toList();
    }


    private static final Set<String> ARTWORK_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    public record Artwork(String contentType, byte[] bytes) {}

    @Transactional(readOnly = true)
    public List<PlaylistSummaryDto> findMyPlaylistSummaries(String cookie) {
        User user = requireUser(cookie);
        return playlistRepository.findSummaries(user).stream()
                .map(row -> new PlaylistSummaryDto(row.getId(), row.getTitulo(), row.getTotalMusicas(),
                        new SearchUserDTO(row.getCriadorId(), row.getCriadorNome()),
                        row.getCapaInterna() ? artworkUrl(row.getId(), "cover") : row.getCapaExterna(),
                        row.getBannerInterno() ? artworkUrl(row.getId(), "banner") : row.getBannerExterno(),
                        user.getId().equals(row.getCriadorId()), row.getExibirNoPerfil()))
                .toList();
    }

    public record ProfilePlaylist(Long id, String titulo, int totalMusicas, String capa, String banner, UUID token) {}

    @Transactional(readOnly = true)
    public List<ProfilePlaylist> findProfilePlaylists(long userId) {
        var published = playlistRepository.findProfilePlaylists(userId);
        var available = published.stream().map(PlaylistRepository.SummaryRow::getId).toList();
        String selected = available.size() > ProfileSelectionDto.LIMIT ? userRepository.findProfilePlaylistSelection(userId) : null;
        var visible = ProfileSelection.visibleIds(available, selected);
        return published.stream().filter(row -> visible.contains(row.getId())).map(row -> {
            String path = "/playlists/shared/" + row.getShareToken() + "/artwork/";
            return new ProfilePlaylist(row.getId(), row.getTitulo(), row.getTotalMusicas(),
                    row.getCapaInterna() ? path + "cover" : row.getCapaExterna(),
                    row.getBannerInterno() ? path + "banner" : row.getBannerExterno(), row.getShareToken());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ProfileSelectionDto playlistSelection(String cookie) {
        User user = requireUser(cookie);
        var items = playlistRepository.findProfilePlaylists(user.getId()).stream()
                .map(row -> new ProfileSelectionDto.Item(row.getId(), row.getTitulo())).toList();
        return ProfileSelection.options(items, user.getProfilePlaylistSelection());
    }

    @Transactional
    public void savePlaylistSelection(String cookie, List<Long> ids) {
        long userId = requireUser(cookie).getId();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        var available = playlistRepository.findProfilePlaylists(userId).stream()
                .map(PlaylistRepository.SummaryRow::getId).toList();
        user.setProfilePlaylistSelection(ProfileSelection.checkedValue(available, ids));
    }

    @Transactional(readOnly = true)
    public PlaylistDto findMyPlaylist(String cookie, long id) {
        User user = requireUser(cookie);
        Playlist playlist = playlistRepository.findById(id)
                .filter(item -> item.colaborador(user))
                .orElseThrow(() -> new ResourceNotFoundException("Playlist não encontrada."));
        PlaylistDto result = new PlaylistDto(playlist, user);
        if (isInternalArtwork(result.getCapa())) result.setCapa(artworkUrl(id, "cover"));
        if (isInternalArtwork(result.getBanner())) result.setBanner(artworkUrl(id, "banner"));
        return result;
    }

    @Transactional
    public void deletePlaylist(String cookie, long id) {
        User user = requireUser(cookie);
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist não encontrada."));
        if (!playlist.criadaPor(user)) {
            throw new UnauthorizedException("Somente quem criou a playlist pode excluí-la.");
        }
        playlistRepository.delete(playlist);
    }

    @Transactional(readOnly = true)
    public Artwork findMyPlaylistArtwork(String cookie, long id, String kind) {
        User user = requireUser(cookie);
        if (!Set.of("cover", "banner").contains(kind)) throw missingArtwork();
        String data = playlistRepository.findArtwork(id, user, kind.equals("cover")).orElseThrow(this::missingArtwork);
        return decodeArtwork(data);
    }

    @Transactional
    public UUID sharePlaylist(String cookie, long id) {
        return ensureShareToken(requireOwnedForSharing(cookie, id));
    }

    @Transactional
    public void setProfileVisibility(String cookie, long id, boolean visible) {
        Playlist playlist = requireOwnedForSharing(cookie, id);
        if (visible) ensureShareToken(playlist);
        playlist.setExibirNoPerfil(visible);
    }

    private Playlist requireOwnedForSharing(String cookie, long id) {
        User user = requireUser(cookie);
        return playlistRepository.findForSharing(id)
                .filter(item -> item.criadaPor(user))
                .orElseThrow(() -> new ResourceNotFoundException("Playlist não encontrada."));
    }

    private UUID ensureShareToken(Playlist playlist) {
        if (playlist.getShareToken() == null) playlist.setShareToken(UUID.randomUUID());
        return playlist.getShareToken();
    }

    @Transactional(readOnly = true)
    public PlaylistDto findSharedPlaylist(UUID token) {
        Playlist playlist = sharedPlaylist(token);
        var result = new PlaylistDto(playlist);
        result.setUsuarios(Set.of());
        String path = "/playlists/shared/" + token + "/artwork/";
        if (isInternalArtwork(result.getCapa())) result.setCapa(path + "cover");
        if (isInternalArtwork(result.getBanner())) result.setBanner(path + "banner");
        return result;
    }

    @Transactional(readOnly = true)
    public Artwork findSharedPlaylistArtwork(UUID token, String kind) {
        if (!Set.of("cover", "banner").contains(kind)) throw missingArtwork();
        Playlist playlist = sharedPlaylist(token);
        return decodeArtwork(kind.equals("cover") ? playlist.getCapa() : playlist.getBanner());
    }

    private Playlist sharedPlaylist(UUID token) {
        return playlistRepository.findByShareToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist compartilhada não encontrada."));
    }

    private Artwork decodeArtwork(String data) {
        if (!isInternalArtwork(data)) throw missingArtwork();
        int separator = data.indexOf(',');
        if (separator < 0 || !data.substring(0, separator).endsWith(";base64")) throw missingArtwork();
        String type = data.substring("data:".length(), separator - ";base64".length());
        if (!ARTWORK_TYPES.contains(type)) throw missingArtwork();
        try {
            return new Artwork(type, Base64.getDecoder().decode(data.substring(separator + 1)));
        } catch (IllegalArgumentException invalidImage) {
            throw missingArtwork();
        }
    }

    private User requireUser(String cookie) {
        if (!cookieService.checkCookie(cookie)) throw new UnauthorizedException("Sessão inválida ou ausente");
        return userRepository.findById(cookieService.getId(cookie))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private static boolean isInternalArtwork(String value) {
        return value != null && value.startsWith("data:image/");
    }

    private static String artworkUrl(long id, String kind) {
        return "/playlists/me/" + id + "/artwork/" + kind;
    }

    private ResourceNotFoundException missingArtwork() {
        return new ResourceNotFoundException("Imagem da playlist não encontrada.");
    }

    @Transactional
    public PlaylistDto createPlaylist(PlaylistRequestDto request, String cookie) {
        if(!cookieService.checkCookie(cookie)){
            throw new UnauthorizedException("Sessão inválida ou ausente");
        }

        User user = userRepository.findById(cookieService.getId(cookie))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        Playlist playlist = new Playlist(request.getTitulo(), request.getDescricao(), user);
        playlist.setCapa(request.getCapa());
        playlist.setBanner(request.getBanner());
        Playlist savedPlaylist = playlistRepository.save(playlist);
        return new PlaylistDto(savedPlaylist, user);
    }

    @Transactional
    public PlaylistDto updatePlaylist(PlaylistRequestDto request, String cookie,Long id) {
        if(!cookieService.checkCookie(cookie)){
            throw new UnauthorizedException("Sessão inválida ou ausente");
        }
        Optional<Playlist> playlistOptional = playlistRepository.findById(id);
        Playlist playlist = playlistOptional.orElseThrow(()->new ResourceNotFoundException("Playlist não encontrada"));
        User user = userRepository.findById(cookieService.getId(cookie))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        if(!playlist.colaborador(user)){
            throw new UnauthorizedException("Usuário não autorizado a modificar a playlist");
        }
        playlist.setTitulo(request.getTitulo());
        playlist.setDescricao(request.getDescricao());
        if (request.getCapa() != null) playlist.setCapa(request.getCapa());
        if (request.getBanner() != null) playlist.setBanner(request.getBanner());
        playlistRepository.save(playlist);
        return new PlaylistDto(playlist, user);
    }

    @Transactional
    public void addUserToPlaylist(String cookie,Long playlistId, Long colaboradorid){
        if(!cookieService.checkCookie(cookie)){
            throw new UnauthorizedException("Sessão inválida ou ausente");
        }
        Optional<Playlist> playlistOptional = playlistRepository.findById(playlistId);
        Playlist playlist = playlistOptional.orElseThrow(()->new ResourceNotFoundException("Playlist não encontrado"));
        Optional<User> userOptional = userRepository.findById(cookieService.getId(cookie));
        User Usuario =  userOptional.get();
        if(!playlist.colaborador(Usuario)){
            throw new UnauthorizedException("usuário não autorizado a modificar a playlist");

        }
        Optional<User> colaboradorOptional = userRepository.findById(colaboradorid);
        User colaborador = colaboradorOptional.orElseThrow(()-> new ResourceNotFoundException("usuário não encontrado"));
        if(playlist.colaborador(colaborador)){
            throw new ResourceConflictException("usuário já é um colaborador da playlist");
        }
        playlist.addUsuario(colaborador);
    }

    @Transactional
    public void removeUserToPlaylist(String cookie,Long playlistId, Long colaboradorid){
        if(!cookieService.checkCookie(cookie)){
            throw new UnauthorizedException("Sessão inválida ou ausente");
        }
        Optional<Playlist> playlistOptional = playlistRepository.findById(playlistId);
        Playlist playlist = playlistOptional.orElseThrow(()->new ResourceNotFoundException("Playlist não encontrado"));
        Optional<User> userOptional = userRepository.findById(cookieService.getId(cookie));
        User Usuario =  userOptional.get();
        if(!playlist.colaborador(Usuario)){
            throw new UnauthorizedException("usuário não autorizado a modificar a playlist");

        }
        Optional<User> colaboradorOptional = userRepository.findById(colaboradorid);
        User colaborador = colaboradorOptional.orElseThrow(()-> new ResourceNotFoundException("usuário não encontrado"));
        if(!playlist.colaborador(colaborador)){
            throw new ResourceConflictException("usuário não é um colaborador da playlist");
        }
        if (colaborador.equals(playlist.getCriador())) {
            throw new ResourceConflictException("o criador não pode ser removido da playlist");
        }
        playlist.removeUsuario(colaborador);
    }

    @Transactional
    public void addMidiaToPlaylist(String cookie,Long playlistId, Long midiaId){
        if(!cookieService.checkCookie(cookie)){
            throw new InvalidTokenException("sessão invalida");
        }
        Optional<Playlist> playlistOptional = playlistRepository.findById(playlistId);
        Optional<User> userOptional = userRepository.findById(cookieService.getId(cookie));

        Playlist playlist = playlistOptional.orElseThrow(()-> new ResourceNotFoundException("Playlist não encontrado"));
        User user = userOptional.orElseThrow(() -> new ResourceNotFoundException("usuário não encontrado"));

        if(!playlist.colaborador(user)){
            throw new UnauthorizedException("usuário não autorizado a modificar a playlist");
        }

        Optional<Midia>midiaOptional = midiaRepository.findById(midiaId);

        Midia midia = midiaOptional.orElseThrow(()-> new ResourceNotFoundException("conteúdo não encontrado"));
        if(midia instanceof Musica musica) {
            playlist.addMusica(musica);
            playlistRepository.save(playlist);
        }
        else if (midia instanceof Album album) {
            playlist.addAlbumMusicas(album);
            playlistRepository.save(playlist);
        } else if (midia instanceof Playlist outraPlaylist) {
            if(playlist.getId() != outraPlaylist.getId()) {
                playlist.addPlaylistMusicas(outraPlaylist);
                playlistRepository.save(playlist);
            }
        }
        else throw new ResourceConflictException("conteúdo incompativel com playlist");

    }
}
