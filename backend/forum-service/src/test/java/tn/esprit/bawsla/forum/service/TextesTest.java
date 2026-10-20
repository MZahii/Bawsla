package tn.esprit.bawsla.forum.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Extrait d'un Markdown : seule la mise en forme disparaît, jamais un caractère du contenu. L'extrait sert aussi à la
 * question envoyée à La Vigie et à la vectorisation : « client_id » ou « COUNT(*) » doivent y arriver intacts.
 */
class TextesTest {

    private static String extrait(String markdown) {
        return Textes.extrait(markdown, 500);
    }

    @ParameterizedTest(name = "« {0} » reste « {0} »")
    @ValueSource(strings = {
            "client_id",
            "COUNT(*)",
            "SELECT * FROM t",
            "a_b_c",
            "2 * 3 * 4",
            "snake_case et date_commande",
            "COUNT(*) et COUNT(*)",
            "a || b"})
    void leContenuNEstJamaisModifie(String texte) {
        assertThat(extrait(texte)).isEqualTo(texte);
    }

    @ParameterizedTest(name = "« {0} » devient « {1} »")
    @CsvSource(delimiter = '|', value = {
            "**gras**|gras",
            "__gras__|gras",
            "*italique*|italique",
            "_italique_|italique",
            "~~barré~~|barré",
            "`code`|code",
            "# Titre|Titre",
            "### Sous-titre|Sous-titre",
            "> citation|citation",
            "> > citation imbriquée|citation imbriquée",
            "[le cours](https://bawsla.tn/cours/7)|le cours",
            "![schéma](schema.png)|schéma"})
    void laMiseEnFormeDisparait(String markdown, String attendu) {
        assertThat(extrait(markdown)).isEqualTo(attendu);
    }

    @Test
    void leCodeEnLigneGardeSesEtoilesEtSoulignes() {
        assertThat(extrait("Utilise `COUNT(*)` sur `client_id`")).isEqualTo("Utilise COUNT(*) sur client_id");
        assertThat(extrait("`*pas de l'italique*`")).isEqualTo("*pas de l'italique*");
    }

    @Test
    void texteMelantMiseEnFormeEtCode() {
        String markdown = """
                # Mon LEFT JOIN

                > Je veux **tous** les clients, même _sans_ commande.

                SELECT c.nom, COUNT(*) FROM client c LEFT JOIN commande co ON co.client_id = c.id;
                Le `WHERE co.total > 100` élimine les lignes à NULL, et 2 * 3 * 4 = 24.""";
        assertThat(extrait(markdown)).isEqualTo("Mon LEFT JOIN Je veux tous les clients, même sans commande. "
                + "SELECT c.nom, COUNT(*) FROM client c LEFT JOIN commande co ON co.client_id = c.id; "
                + "Le WHERE co.total > 100 élimine les lignes à NULL, et 2 * 3 * 4 = 24.");
    }

    @Test
    void lesBlocsDeCodeEtLesTableauxRestentTraitesCommeAvant() {
        assertThat(extrait("Avant\n```sql\nSELECT *\n```\nAprès")).isEqualTo("Avant Après");
        assertThat(extrait("| notion | erreurs |\n|---|---|\n| LEFT JOIN | 2 |")).isEqualTo("notion erreurs LEFT JOIN 2");
    }

    @Test
    void lExtraitEstCoupeSurUnEspace() {
        assertThat(Textes.extrait("un deux trois quatre cinq six", 15)).isEqualTo("un deux trois…");
        assertThat(Textes.extrait(null, 10)).isEmpty();
    }
}
