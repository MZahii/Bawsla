package tn.esprit.bawsla.forum.dto;

import java.util.List;

/** Page de résultats (« Charger plus » : suivante indique s'il reste des éléments). */
public record PageResponse<T>(List<T> contenu, int page, int taille, long total, boolean suivante) {

    public static <T> PageResponse<T> of(List<T> contenu, int page, int taille, long total) {
        return new PageResponse<>(contenu, page, taille, total, (long) (page + 1) * taille < total);
    }
}
