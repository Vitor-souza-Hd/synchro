package org.example.synchro.repositories;

import org.example.synchro.entities.Playlist;
import org.example.synchro.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByCriador(User criador);

    List<Playlist> findByColaboradoresContaining(User colaborador);

    interface SummaryRow {
        Long getId();
        String getTitulo();
        int getTotalMusicas();
        Long getCriadorId();
        String getCriadorNome();
        boolean getCapaInterna();
        boolean getBannerInterno();
        String getCapaExterna();
        String getBannerExterno();
    }

    @Query("""
            select p.id as id, p.titulo as titulo, size(p.musicas) as totalMusicas,
                   p.criador.id as criadorId, p.criador.username as criadorNome,
                   case when p.capa like 'data:image/%' then true else false end as capaInterna,
                   case when p.banner like 'data:image/%' then true else false end as bannerInterno,
                   case when p.capa not like 'data:%' then p.capa else null end as capaExterna,
                   case when p.banner not like 'data:%' then p.banner else null end as bannerExterno
            from Playlist p
            where p.criador = :user or :user member of p.colaboradores
            order by p.id desc
            """)
    List<SummaryRow> findSummaries(@Param("user") User user);

    @Query("""
            select case when :cover = true then p.capa else p.banner end
            from Playlist p
            where p.id = :id and (p.criador = :user or :user member of p.colaboradores)
            """)
    Optional<String> findArtwork(@Param("id") long id, @Param("user") User user, @Param("cover") boolean cover);
}
