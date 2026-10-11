package tn.esprit.bawsla.cours.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tn.esprit.bawsla.cours.common.exception.ResourceNotFoundException;

/**
 * Stockage des fichiers des cours sur disque (dossier {@code cours.stockage.dossier}).
 * Les fichiers portent un nom UUID généré ici : le nom d'origine n'est jamais utilisé comme chemin.
 */
@Component
public class StockageService {

    public enum Type {
        IMAGES("images"),
        PDF("pdf"),
        AUDIO("audio");

        private final String sousDossier;

        Type(String sousDossier) {
            this.sousDossier = sousDossier;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(StockageService.class);
    private static final Pattern NOM_VALIDE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|pdf|mp3)");

    private final Path racine;

    public StockageService(@Value("${cours.stockage.dossier:./uploads/cours}") String dossier) {
        this.racine = Paths.get(dossier).toAbsolutePath().normalize();
    }

    /** Écrit les octets sous un nouveau nom UUID et renvoie ce nom. Le fichier est supprimé si la transaction est annulée. */
    public String sauvegarder(Type type, byte[] octets, String extension) {
        String nom = UUID.randomUUID() + "." + extension;
        try {
            Path dossier = racine.resolve(type.sousDossier);
            Files.createDirectories(dossier);
            Files.write(resoudre(type, nom), octets);
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture du fichier impossible", e);
        }
        supprimerSiAnnule(type, nom);
        return nom;
    }

    public Resource lire(Type type, String nom) {
        Path chemin = resoudre(type, nom);
        if (!Files.isReadable(chemin)) {
            throw new ResourceNotFoundException("Fichier", 0L);
        }
        return new PathResource(chemin);
    }

    /** Supprime le fichier une fois la transaction en cours validée (immédiatement s'il n'y en a pas). */
    public void supprimerApresValidation(Type type, String nom) {
        if (nom == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    supprimer(type, nom);
                }
            });
        } else {
            supprimer(type, nom);
        }
    }

    /** Évite un fichier orphelin quand la transaction qui l'a créé est annulée. */
    private void supprimerSiAnnule(Type type, String nom) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    supprimer(type, nom);
                }
            }
        });
    }

    private void supprimer(Type type, String nom) {
        try {
            Files.deleteIfExists(resoudre(type, nom));
        } catch (IOException | RuntimeException e) {
            log.warn("Suppression du fichier {} impossible : {}", nom, e.getMessage());
        }
    }

    /** Refuse tout nom qui n'est pas un UUID généré par ce service, puis vérifie que le chemin reste dans le dossier. */
    private Path resoudre(Type type, String nom) {
        if (nom == null || !NOM_VALIDE.matcher(nom).matches()) {
            throw new ResourceNotFoundException("Fichier", 0L);
        }
        Path dossier = racine.resolve(type.sousDossier).normalize();
        Path chemin = dossier.resolve(nom).normalize();
        if (!chemin.startsWith(dossier)) {
            throw new ResourceNotFoundException("Fichier", 0L);
        }
        return chemin;
    }
}
