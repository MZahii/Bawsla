package tn.esprit.bawsla.forum.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Cache mémoire minimal avec durée de vie par entrée (pas de dépendance supplémentaire).
 * La fonction de calcul renvoie la valeur et sa durée de vie : un résultat dégradé vit moins longtemps.
 */
public class CacheTemporaire<K, V> {

    public record Calcul<V>(V valeur, Duration duree) {
    }

    private record Entree<V>(V valeur, Instant expireLe) {
    }

    private final Map<K, Entree<V>> entrees = new ConcurrentHashMap<>();
    private final Clock horloge;

    public CacheTemporaire(Clock horloge) {
        this.horloge = horloge;
    }

    public V obtenir(K cle, Function<K, Calcul<V>> calcul) {
        Instant maintenant = horloge.instant();
        Entree<V> entree = entrees.get(cle);
        if (entree != null && entree.expireLe().isAfter(maintenant)) {
            return entree.valeur();
        }
        Calcul<V> nouveau = calcul.apply(cle);
        entrees.put(cle, new Entree<>(nouveau.valeur(), maintenant.plus(nouveau.duree())));
        return nouveau.valeur();
    }

    public void invalider(K cle) {
        entrees.remove(cle);
    }

    public void vider() {
        entrees.clear();
    }
}
