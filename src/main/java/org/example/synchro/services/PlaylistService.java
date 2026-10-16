package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.PlaylistDto;
import org.example.synchro.dto.PlaylistRequestDto;
import org.example.synchro.entities.Playlist;
import org.example.synchro.entities.User;
import org.example.synchro.repositories.PlaylistRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.ResourceConflictException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final CookieService cookieService;

    @Transactional
    public PlaylistDto createPlaylist(PlaylistRequestDto request, String cookie) {
        if(!cookieService.checkCookie(cookie)){
            throw new UnauthorizedException("Sessão inválida ou ausente");
        }

        User user = userRepository.findById(cookieService.getId(cookie))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        Playlist playlist = new Playlist(request.getTitulo(), request.getDescricao(), user);
        Playlist savedPlaylist = playlistRepository.save(playlist);
        return new PlaylistDto(savedPlaylist);
    }

    @Transactional
    public PlaylistDto updatePlaylist(PlaylistRequestDto request, String cookie,Long id) {
        if(!cookieService.checkCookie(cookie)){
            throw new UnauthorizedException("Sessão inválida ou ausente");
        }
        Optional<Playlist> playlistOptional = playlistRepository.findById(id);
        Playlist playlist = playlistOptional.orElseThrow(()->new ResourceNotFoundException("Playlist não encontrada"));
        Optional<User> userOptional = userRepository.findById(cookieService.getId(cookie));
        User user = userOptional.get();
        if(!playlist.colaborador(user)){
            throw new UnauthorizedException("Usuário não autorizado a modificar a playlist");
        }
        playlist.setTitulo(request.getTitulo());
        playlist.setDescricao(request.getDescricao());
        playlistRepository.save(playlist);
        return new PlaylistDto(playlist);
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
            System.out.println("aquii");
            throw new UnauthorizedException("usuário não autorizado a modificar a playlist");

        }
        Optional<User> colaboradorOptional = userRepository.findById(colaboradorid);
        User colaborador = colaboradorOptional.orElseThrow(()-> new ResourceNotFoundException("usuário não encontrado"));
        if(playlist.colaborador(colaborador)){
            throw new ResourceConflictException("usuário já é um colaborador da playlist");
        }
        playlist.addUsuario(colaborador);
    }
}
