package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.PlaylistDto;
import org.example.synchro.dto.PlaylistRequestDto;
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
import java.util.List;
import java.util.LinkedHashMap;

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
