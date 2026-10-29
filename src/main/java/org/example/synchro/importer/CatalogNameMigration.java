package org.example.synchro.importer;

import org.example.synchro.entities.CatalogName;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;

/** Merge legacy duplicates before assigning the new unique normalized names. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CatalogNameMigration implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    public CatalogNameMigration(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void run(String... args) {
        migrate("artista", "select id, nome as name from artista order by id", "nome_key");
        migrate("album", "select a.id, m.titulo as name from album a join midia m on m.id=a.id order by a.id", "titulo_key");
        migrate("musica", "select a.id, m.titulo as name from musica a join midia m on m.id=a.id order by a.id", "titulo_key");
    }

    private void migrate(String table, String query, String keyColumn) {
        var names = new HashMap<String, Long>();
        var rows = jdbc.queryForList(query);
        // Clear legacy keys to allow canonical ownership to move to the oldest row.
        jdbc.update("update " + table + " set " + keyColumn + "=null");
        for (var row : rows) {
            long id = ((Number) row.get("id")).longValue();
            String key = CatalogName.key((String) row.get("name"));
            Long canonical = names.putIfAbsent(key, id);
            if (canonical == null) {
                jdbc.update("update " + table + " set " + keyColumn + "=? where id=?", key, id);
            } else {
                merge(table, id, canonical);
            }
        }
    }

    private void merge(String table, long oldId, long id) {
        if (table.equals("artista")) {
            moveLinks("album_artistas", "artista_id", "album_id", oldId, id);
            moveLinks("musica_artista", "artista_id", "musica_id", oldId, id);
            jdbc.update("update last_fm_scrobble set artista_id=? where artista_id=?", id, oldId);
            jdbc.update("update artista set biografia=coalesce(biografia,(select biografia from artista where id=?)), "
                    + "img_url=coalesce(img_url,(select img_url from artista where id=?)) where id=?", oldId, oldId, id);
        } else {
            jdbc.update("update review set midia_id=? where midia_id=?", id, oldId);
            jdbc.update("update midia set descricao=coalesce(descricao,(select descricao from midia where id=?)) where id=?", oldId, id);
            if (table.equals("album")) {
                jdbc.update("update album set data_lancamento=coalesce(data_lancamento,(select data_lancamento from album where id=?)) where id=?", oldId, id);
                moveLinks("album_artistas", "album_id", "artista_id", oldId, id);
                jdbc.update("update musica set album_id=? where album_id=?", id, oldId);
                jdbc.update("update last_fm_scrobble set album_id=? where album_id=?", id, oldId);
            } else {
                jdbc.update("update musica set duracao=coalesce(duracao,(select duracao from musica where id=?)), "
                        + "genero=coalesce(genero,(select genero from musica where id=?)), "
                        + "album_id=coalesce(album_id,(select album_id from musica where id=?)) where id=?", oldId, oldId, oldId, id);
                moveLinks("musica_artista", "musica_id", "artista_id", oldId, id);
                moveLinks("playlist_musicas", "musicas_id", "playlist_id", oldId, id);
                jdbc.update("update last_fm_scrobble set musica_id=? where musica_id=?", id, oldId);
            }
        }
        jdbc.update("delete from " + table + " where id=?", oldId);
        if (!table.equals("artista")) jdbc.update("delete from midia where id=?", oldId);
    }

    private void moveLinks(String table, String column, String other, long oldId, long id) {
        jdbc.update("insert into " + table + " (" + column + "," + other + ") select ?, source." + other
                + " from " + table + " source where source." + column + "=? and not exists (select 1 from "
                + table + " dest where dest." + column + "=? and dest." + other + "=source." + other + ")", id, oldId, id);
        jdbc.update("delete from " + table + " where " + column + "=?", oldId);
    }
}
