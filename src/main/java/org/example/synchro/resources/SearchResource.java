package org.example.synchro.resources;

import org.example.synchro.dto.SearchResultDTO;
import org.example.synchro.dto.SearchUserDTO;
import org.example.synchro.repositories.AlbumRepository;
import org.example.synchro.repositories.ArtistaRepository;
import org.example.synchro.repositories.MusicaRepository;
import org.example.synchro.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class SearchResource {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MusicaRepository musicaRepository;

    @Autowired
    private ArtistaRepository artistaRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @GetMapping
    public SearchResultDTO search(
            @RequestParam(name = "q", defaultValue = "") String query
    ) {
        SearchResultDTO result = new SearchResultDTO();

        String termo = query.trim();

        if (termo.length() < 2) {
            return result;
        }

        result.setUsers(
                userRepository
                        .findByUsernameContainingIgnoreCase(termo)
                        .stream()
                        .map(user -> new SearchUserDTO(
                                user.getId(),
                                user.getUsername()
                        ))
                        .toList()
        );

        result.setSongs(
                musicaRepository
                        .findByTituloContainingIgnoreCaseOrArtistasNomeContainingIgnoreCase(
                                termo, termo
                        )
        );

        result.setArtists(
                artistaRepository.findByNomeContainingIgnoreCase(termo)
        );

        result.setAlbums(
                albumRepository.findByTituloContainingIgnoreCase(termo)
        );

        return result;
    }
}
