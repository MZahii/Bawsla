package tn.esprit.bawsla.forum.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Crée au démarrage les index FULLTEXT que JPA ne sait pas déclarer (recherche lexicale et hybride du plan §4.1),
 * s'ils sont absents. Uniquement sur MySQL : en test (H2) la recherche lexicale retombe sur LIKE.
 * Une erreur est journalisée sans empêcher le service de démarrer.
 */
@Component
public class MigrationFulltext implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationFulltext.class);

    record IndexTexte(String table, String nom, String colonnes) {
    }

    static final List<IndexTexte> INDEX = List.of(
            new IndexTexte("discussion", "ft_discussion_texte", "titre, contenu"),
            new IndexTexte("passage_cours", "ft_passage_cours_texte", "texte"),
            new IndexTexte("passage_forum", "ft_passage_forum_texte", "texte"));

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public MigrationFulltext(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    public static boolean estMysql(DataSource dataSource) {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData meta = c.getMetaData();
            return meta.getDatabaseProductName().toLowerCase().contains("mysql");
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!estMysql(dataSource)) {
            return;
        }
        for (IndexTexte index : INDEX) {
            try {
                Integer existe = jdbc.queryForObject("""
                        select count(*) from information_schema.statistics
                        where table_schema = database() and table_name = ? and index_name = ?""",
                        Integer.class, index.table(), index.nom());
                if (existe != null && existe == 0) {
                    jdbc.execute("alter table " + index.table() + " add fulltext index " + index.nom()
                            + " (" + index.colonnes() + ")");
                    log.info("Index FULLTEXT {} créé sur {}", index.nom(), index.table());
                }
            } catch (RuntimeException e) {
                log.warn("Index FULLTEXT {} non créé : {}", index.nom(), e.getMessage());
            }
        }
    }
}
