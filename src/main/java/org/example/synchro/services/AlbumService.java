package org.example.synchro.services;

import org.example.synchro.dto.AlbumDto;
import org.example.synchro.entities.Album;
import org.example.synchro.repositories.AlbumRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AlbumService {
    @Autowired
    private AlbumRepository albumRepository;

    public AlbumDto findByid(Long id){
        Optional<Album> album = albumRepository.findById(id);
        return  new AlbumDto(album.get());
    }

    public Set<AlbumDto> findAll(){
        Set<AlbumDto> albumDtos = new HashSet<>();
        List<Album>albums = albumRepository.findAll();
        for (Album album : albums) {
            albumDtos.add(new AlbumDto(album));
        }
        return albumDtos;
    }
}
