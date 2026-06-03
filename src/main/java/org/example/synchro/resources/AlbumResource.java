package org.example.synchro.resources;

import org.example.synchro.dto.AlbumDto;
import org.example.synchro.entities.Album;
import org.example.synchro.repositories.AlbumRepository;
import org.example.synchro.services.AlbumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;


@RestController
@RequestMapping("/album")
public class AlbumResource {

    @Autowired
    private AlbumService albumService;

    @GetMapping(value = "/")
    public ResponseEntity<Set<AlbumDto>> findAll(){
        Set<AlbumDto>albumsDto = albumService.findAll();
        return ResponseEntity.ok().body(albumsDto);
    }
}
