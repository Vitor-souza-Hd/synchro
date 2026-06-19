package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.dto.AlbumDto;
import org.example.synchro.entities.Album;
import org.example.synchro.repositories.AlbumRepository;
import org.example.synchro.repositories.ArtistaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final ArtistaService artistaService;

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
    public AlbumDto findByTitulo(String titulo){
        return  new AlbumDto(albumRepository.findByTitulo(titulo));
    }

    public List<AlbumDto> findByArtista(String nome){
        List<AlbumDto> albumDtos = new ArrayList<>();
        List<Album> list= albumRepository.findByArtistas(artistaService.findByNome(nome));
        for (Album album : list) {
            albumDtos.add(new AlbumDto(album));
        }
        return albumDtos;
    }
}
