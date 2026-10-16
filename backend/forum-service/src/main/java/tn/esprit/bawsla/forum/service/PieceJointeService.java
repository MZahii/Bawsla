package tn.esprit.bawsla.forum.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tn.esprit.bawsla.forum.common.exception.BadRequestException;
import tn.esprit.bawsla.forum.common.exception.ForbiddenException;
import tn.esprit.bawsla.forum.common.exception.ResourceNotFoundException;
import tn.esprit.bawsla.forum.common.security.CurrentUser;
import tn.esprit.bawsla.forum.dto.PieceJointeResponse;
import tn.esprit.bawsla.forum.entity.PieceJointe;
import tn.esprit.bawsla.forum.repository.PieceJointeRepository;
import tn.esprit.bawsla.forum.service.FilService.Fil;
import tn.esprit.bawsla.forum.service.FilService.FilReponse;

/**
 * Images jointes aux messages : PNG, JPEG ou WebP, 2 Mo au plus. Le type est déduit des OCTETS MAGIQUES
 * (jamais de l'extension ni du Content-Type envoyés), et le fichier est stocké sous un nom aléatoire.
 */
@Service
@Transactional(readOnly = true)
public class PieceJointeService {

    public static final long TAILLE_MAX = 2L * 1024 * 1024;

    /** Fichier prêt à être servi. */
    public record Contenu(byte[] octets, String type) {
    }

    private final PieceJointeRepository repository;
    private final FilService fils;
    private final AccesService acces;
    private final Path dossier;

    public PieceJointeService(PieceJointeRepository repository, FilService fils, AccesService acces,
                              @Value("${forum.pieces-jointes.dossier:./data/pieces-jointes}") String dossier) {
        this.repository = repository;
        this.fils = fils;
        this.acces = acces;
        this.dossier = Path.of(dossier).toAbsolutePath().normalize();
    }

    /** Téléversement ; discussionId ou reponseId rattache l'image à un message dont l'utilisateur est l'auteur. */
    @Transactional
    public PieceJointeResponse televerser(MultipartFile fichier, Long discussionId, Long reponseId, CurrentUser user) {
        acces.exigerConnecte(user);
        acces.exigerSansSanction(user);
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Aucun fichier reçu");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new BadRequestException("L'image fait plus de 2 Mo");
        }
        byte[] octets = lire(fichier);
        String type = typeReel(octets);
        if (type == null) {
            throw new BadRequestException("Format refusé : seules les images PNG, JPEG et WebP sont acceptées");
        }
        Long discussion = discussionId;
        if (reponseId != null) {
            FilReponse fil = fils.chargerReponse(reponseId, user);
            exigerAuteur(fil.reponse().getAuteurId(), user);
            discussion = fil.discussion().getId();
        } else if (discussionId != null) {
            Fil fil = fils.charger(discussionId, user);
            exigerAuteur(fil.discussion().getAuteurId(), user);
        }

        String nom = UUID.randomUUID() + extension(type);
        try {
            Files.createDirectories(dossier);
            Files.write(dossier.resolve(nom), octets);
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture de la pièce jointe impossible", e);
        }
        PieceJointe p = new PieceJointe();
        p.setDiscussionId(discussion);
        p.setReponseId(reponseId);
        p.setAuteurId(user.id());
        p.setNomStockage(nom);
        p.setType(type);
        p.setTaille(octets.length);
        return PresentationService.pieceJointe(repository.save(p));
    }

    public Contenu lire(Long id, CurrentUser user) {
        acces.exigerConnecte(user);
        PieceJointe p = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Pièce jointe", id));
        if (p.getDiscussionId() != null) {
            fils.charger(p.getDiscussionId(), user);
        }
        Path chemin = dossier.resolve(p.getNomStockage()).normalize();
        if (!chemin.startsWith(dossier) || !Files.exists(chemin)) {
            throw new ResourceNotFoundException("Pièce jointe", id);
        }
        try {
            return new Contenu(Files.readAllBytes(chemin), p.getType());
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture de la pièce jointe impossible", e);
        }
    }

    /** Type MIME d'après les premiers octets, ou null si ce n'est pas une image acceptée. */
    static String typeReel(byte[] o) {
        if (commencePar(o, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (commencePar(o, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (commencePar(o, 0, 'R', 'I', 'F', 'F') && commencePar(o, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        return null;
    }

    private static boolean commencePar(byte[] o, int debut, int... attendus) {
        if (o.length < debut + attendus.length) {
            return false;
        }
        for (int i = 0; i < attendus.length; i++) {
            if ((o[debut + i] & 0xFF) != attendus[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extension(String type) {
        return switch (type) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> ".webp";
        };
    }

    private static void exigerAuteur(Long auteurId, CurrentUser user) {
        if (!auteurId.equals(user.id())) {
            throw new ForbiddenException("Tu ne peux joindre une image qu'à tes propres messages");
        }
    }

    private static byte[] lire(MultipartFile fichier) {
        try {
            return fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Fichier illisible");
        }
    }
}
